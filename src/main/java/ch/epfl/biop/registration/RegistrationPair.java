package ch.epfl.biop.registration;

import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.RegistrationPluginHelper;
import ch.epfl.biop.sourceandconverter.processor.SourcesAffineTransformer;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.Named;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RegistrationPair implements Named, Closeable {

    final SourceAndConverter<?>[] movingSourcesOrigin;
    final SourceAndConverter<?>[] fixedSources;

    final int timepointMoving;
    final int timepointFixed;
    final String name;

    SourceAndConverter<?>[] movingSourcesRegistered;
    final List<RegistrationStep> registrationPairSteps = new ArrayList<>();

    public RegistrationPair(SourceAndConverter<?>[] fixedSources,
                            int timepointFixed,
                            SourceAndConverter<?>[] movingSources,
                            int timepointMoving,
                            String name,
                            boolean removezOffset
                             ) {

        if (removezOffset) {
            this.fixedSources = new SourcesAffineTransformer(findZ0Transform(fixedSources[0], timepointFixed)).apply(fixedSources);
            this.movingSourcesOrigin = new SourcesAffineTransformer(findZ0Transform(movingSources[0], timepointMoving)).apply(movingSources);
        } else {
            this.fixedSources = fixedSources;
            this.movingSourcesOrigin = movingSources;
        }

        this.movingSourcesRegistered = movingSourcesOrigin;

        this.timepointFixed = timepointFixed;
        this.timepointMoving = timepointMoving;
        this.name = name;
    }

    public SourceAndConverter<?>[] getFixedSources() {
        return fixedSources;
    }

    public SourceAndConverter<?>[] getMovingSourcesOrigin() {
        return movingSourcesOrigin;
    }

    public synchronized SourceAndConverter<?>[] getMovingSourcesRegistered() {
        return movingSourcesRegistered;
    }

    String errorMessage = "";

    public String getLastErrorMessage() {
        return errorMessage;
    }

    public synchronized boolean executeRegistration(Registration<SourceAndConverter<?>[]> reg,
                                                Map<String, String> parameters,
                                                SourcesProcessor fixedProcessorForRegistration,
                                                SourcesProcessor movingProcessorForRegistration) {
        reg.setRegistrationParameters(parameters);
        reg.setMovingImage(movingProcessorForRegistration.apply(getMovingSourcesRegistered()));
        reg.setFixedImage(fixedProcessorForRegistration.apply(getFixedSources()));

        boolean success = reg.register();

        if (!success) {
            errorMessage = reg.getExceptionMessage();
            return false;
        }

        appendRegistration(reg, fixedProcessorForRegistration, movingProcessorForRegistration);

        return true;
    }

    private void appendRegistration(Registration<SourceAndConverter<?>[]> reg,
                                    SourcesProcessor fixedProcessorForRegistration,
                                    SourcesProcessor movingProcessorForRegistration) {

        movingSourcesRegistered = reg.getTransformedImageMovingToFixed(getMovingSourcesRegistered());

        RegistrationStep rp = new RegistrationStep(
                reg,
                getMovingSourcesRegistered(),
                fixedProcessorForRegistration, movingProcessorForRegistration);

        registrationPairSteps.add(rp);
        listeners.forEach(listener -> listener.newEvent(RegistrationEvents.STEP_ADDED));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException("You can't rename a registration pair sequence object");
    }

    public synchronized void removeLastRegistration() {
        if (registrationPairSteps.isEmpty()) return;
        if (registrationPairSteps.size()==1) {
            registrationPairSteps.remove(0);
            this.movingSourcesRegistered = movingSourcesOrigin;
        } else {
            RegistrationStep rs = registrationPairSteps.get(registrationPairSteps.size()-2);
            registrationPairSteps.remove(registrationPairSteps.size()-1);
            this.movingSourcesRegistered = rs.sacs;
        }
        listeners.forEach(listener -> listener.newEvent(RegistrationEvents.STEP_REMOVED));
    }

    public synchronized void editLastRegistration() {
        if (registrationPairSteps.isEmpty()) {
            System.err.println("There is no registration to edit");
            return;
        }

        RegistrationStep lastStep = registrationPairSteps.get(registrationPairSteps.size()-1);
        if (!RegistrationPluginHelper.isEditable(lastStep.reg)) {
            System.err.println("The last registration is not editable");
            return;
        }

        removeLastRegistration();
        lastStep.reg.edit();
        appendRegistration(lastStep.reg, lastStep.fixedProcessor, lastStep.movingProcessor);
    }

    public int getFixedTimepoint() {
        return timepointFixed;
    }

    public int getMovingTimepoint() {
        return timepointMoving;
    }

    public boolean checkQuPathCompatibility() {
        // A few checks are necessary in order to know if this registration pair is compatible with an export to QuPath

        // Is the first fixed source belonging to a QuPath project -> Strict requirement
        if (!QuPathBdvHelper.isSourceLinkedToQuPath(fixedSources[0])) {
            errorMessage = "The first fixed source is not linked to a QuPath project";
            return false;
        }

        // Is the first moving source belonging to a QuPath project -> Strict requirement
        if (!QuPathBdvHelper.isSourceLinkedToQuPath(movingSourcesOrigin[0])) {
            errorMessage = "The first moving source is not linked to a QuPath project";
            return false;
        }

        // Do they belong to the same QuPath project ? -> Strict requirement
        File quPathProject = QuPathBdvHelper.getProjectFile(fixedSources[0]);

        if (!quPathProject.equals(QuPathBdvHelper.getProjectFile(movingSourcesOrigin[0]))) {
            errorMessage = "Moving and fixed sources do not belong to the same QuPath project.";
            return false;
        }

        // Do moving and fixed sources belong to different entries ? -> Strict requirement
        int entryIdFixed = QuPathBdvHelper.getEntryId(fixedSources[0]);
        int entryIdMoving = QuPathBdvHelper.getEntryId(movingSourcesOrigin[0]);

        if (entryIdFixed==entryIdMoving) {
            errorMessage = "The first moving source and the first fixed source belong to the same QuPath entry";
            return false;
        }

        // Are all moving sources belonging to a QuPath entry ? -> Warning
        for (int i = 1; i<fixedSources.length; i++) {
            if (QuPathBdvHelper.isSourceLinkedToQuPath(fixedSources[i])) {
                if (QuPathBdvHelper.getEntryId(fixedSources[i]) != entryIdFixed) {
                    System.out.println("Warning: all fixed sources do not belong to the same QuPath entry");
                    break;
                }
            } else {
                System.out.println("Warning: some sources do not belong to a QuPath project.");
            }
        }

        // Are all moving sources belonging to the same QuPath entry ? -> Warning
        for (int i = 1; i<movingSourcesOrigin.length; i++) {
            if (QuPathBdvHelper.isSourceLinkedToQuPath(movingSourcesOrigin[i])) {
                if (QuPathBdvHelper.getEntryId(movingSourcesOrigin[i])!=entryIdMoving) {
                    System.out.println("Warning: all moving sources do not belong to the same QuPath entry");
                    break;
                }
            } else {
                System.out.println("Warning: some sources do not belong to a QuPath project.");
            }
        }

        // We should be fine, let's just create the data folder in case it doesn't exist

        File fixedEntryFolder = QuPathBdvHelper.getDataEntryFolder(fixedSources[0]);
        fixedEntryFolder.mkdirs();
        if (!fixedEntryFolder.exists()) {
            errorMessage = "Could not create fixed entry folder "+fixedEntryFolder.getAbsolutePath();
            return false;
        }

        File movingEntryFolder = QuPathBdvHelper.getDataEntryFolder(movingSourcesOrigin[0]);
        movingEntryFolder.mkdirs();
        if (!movingEntryFolder.exists()) {
            errorMessage = "Could not create moving entry folder "+movingEntryFolder.getAbsolutePath();
            return false;
        }

        return true;
    }

    public synchronized boolean exportToQuPath(boolean allowOverwrite, Context scijavaCtx) {
        boolean result = checkQuPathCompatibility();
        if (!result) return false;

        SourceAndConverter<?> moving_source = movingSourcesOrigin[0];
        SourceAndConverter<?> fixed_source = fixedSources[0];
        // Is there already a registration ? Can I erase it ?
        // All right, now it is the

        // Because QuPath works in pixel coordinates and bdv playground in real space coordinates
        // We need to account for this

        AffineTransform3D movingToPixel = new AffineTransform3D();

        moving_source.getSpimSource().getSourceTransform(0,0,movingToPixel);

        AffineTransform3D fixedToPixel = new AffineTransform3D();

        fixed_source.getSpimSource().getSourceTransform(0,0,fixedToPixel);

        InvertibleRealTransformSequence rt = new InvertibleRealTransformSequence();
        for (int iReg = 0; iReg<registrationPairSteps.size(); iReg++) {
            RegistrationStep rp = registrationPairSteps.get(registrationPairSteps.size()-iReg-1);
            RealTransform rt_temp = rp.reg.getTransformAsRealTransform();
            if (rt_temp instanceof InvertibleRealTransform) {
                rt.add((InvertibleRealTransform) rt_temp);
            } else {
                errorMessage = "A transformation within the sequence is not invertible!";
                return false;
            }
        }

        InvertibleRealTransformSequence irts = new InvertibleRealTransformSequence();

        irts.add(fixedToPixel);
        irts.add(rt);
        irts.add(movingToPixel.inverse());

        String jsonMovingToFixed = ScijavaGsonHelper.getGson(scijavaCtx).toJson(irts, RealTransform.class);

        int moving_series_entry_id = QuPathBdvHelper.getEntryId(moving_source);
        int fixed_series_entry_id = QuPathBdvHelper.getEntryId(fixed_source);

        String movingToFixedLandmarkName = "transform_"+moving_series_entry_id+"_"+fixed_series_entry_id+".json";

        File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(movingSourcesOrigin[0]);

        File resultFile = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
        if (resultFile.exists() && (allowOverwrite == false)) {
            errorMessage = "The registration file already exists, overwrite not allowed.";
            return false;
        }
        try {
            FileUtils.writeStringToFile(resultFile, jsonMovingToFixed, Charset.defaultCharset());
        } catch (IOException e) {
            errorMessage = e.getMessage();
            return false;
        }

        System.out.println("Fixed: "+fixed_source.getSpimSource().getName()+" | Moving: "+moving_source.getSpimSource().getName());
        System.out.println("Transformation file successfully written to QuPath project: "+result);
        return true;
    }

    @Override
    public void close() throws IOException {
        listeners.forEach(listener -> listener.newEvent(RegistrationEvents.CLOSED));
        listeners.clear();
    }

    public synchronized List<SourceAndConverter<?>[]> getAllSourcesPerStep() {
        List<SourceAndConverter<?>[]> sourcesPerStep = new ArrayList<>();
        for (RegistrationStep rs: registrationPairSteps) {
            sourcesPerStep.add(rs.sacs);
        }
        return sourcesPerStep;
    }

    private static class RegistrationStep {

        final Registration<SourceAndConverter<?>[]> reg;
        final SourceAndConverter<?>[] sacs;
        final SourcesProcessor fixedProcessor;
        final SourcesProcessor movingProcessor;

        public RegistrationStep(Registration<SourceAndConverter<?>[]> reg,
                                SourceAndConverter<?>[] sacs,
                                SourcesProcessor fixedProcessor,
                                SourcesProcessor movingProcessor) {
            this.reg = reg;
            this.sacs = sacs;
            this.fixedProcessor = fixedProcessor;
            this.movingProcessor = movingProcessor;
        }
    }

    @Override
    public String toString() {
        return name;//+" [#f="+fixedSources.length+" #m="+movingSourcesOrigin.length+" #regs="+registrationAndSources.size()+"]";
    }

    private static AffineTransform3D findZ0Transform(SourceAndConverter<?> source, int timePoint) {
        long sz = source.getSpimSource().getSource(timePoint, 0).dimension(2);
        AffineTransform3D at3D = new AffineTransform3D();
        source.getSpimSource().getSourceTransform(timePoint, 0, at3D);
        AffineTransform3D at3DCenter = new AffineTransform3D();
        at3DCenter.concatenate(at3D.inverse());
        at3DCenter.translate(0, 0, -sz/2.0+0.5); // Humpf
        at3D.set(0,2,3);
        at3DCenter.preConcatenate(at3D);
        return at3DCenter;
    }

    public enum RegistrationEvents {
        STEP_ADDED,
        STEP_REMOVED,
        CLOSED
    }

    final List<RegistrationPairListener> listeners = new ArrayList<>();

    public void addListener(RegistrationPairListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RegistrationPairListener listener) {
        listeners.remove(listener);
    }

    public interface RegistrationPairListener {
        void newEvent(RegistrationEvents event);
    }

}
