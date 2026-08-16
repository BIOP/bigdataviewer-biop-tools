package ch.epfl.biop.registration;

import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.RegistrationPluginHelper;
import ch.epfl.biop.source.processor.SourcesAffineTransformer;
import ch.epfl.biop.source.processor.SourcesProcessor;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.Named;
import sc.fiji.persist.ScijavaGsonHelper;
import ch.epfl.biop.source.transform.SourceTimeMapper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static bdv.util.source.time.MappedTimeSource.withName;

public class RegistrationPair implements Named, Closeable {

    SourceAndConverter<?>[] movingSourcesOrigin;
    SourceAndConverter<?>[] fixedSources;

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

        // Remove t offsets
        this.fixedSources = Arrays.stream(this.fixedSources)
                .map(source -> new SourceTimeMapper(source, withName((t) -> t + timepointFixed, "(t) -> t + "+timepointFixed), source.getSpimSource().getName() + "-T" + timepointFixed).get()).toArray(SourceAndConverter[]::new);

        this.movingSourcesOrigin = Arrays.stream(this.movingSourcesOrigin)
                .map(sour -> new SourceTimeMapper(sour, withName((t) -> t + timepointMoving, "(t) -> t + "+timepointMoving), sour.getSpimSource().getName() + "-T" + timepointMoving).get()).toArray(SourceAndConverter[]::new);

        this.movingSourcesRegistered = movingSourcesOrigin;

        this.timepointFixed = 0;//timepointFixed;
        this.timepointMoving = 0;//timepointMoving;
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
        notifyListeners(RegistrationEvents.STEP_ADDED);
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
            this.movingSourcesRegistered = rs.sources;
        }
        notifyListeners(RegistrationEvents.STEP_REMOVED);
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
        if (resultFile.exists() && (!allowOverwrite)) {
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
        close(true);
    }

    boolean forceClose = false;
    private void setForceClose(boolean forceClose) {
        this.forceClose = forceClose;
    }

    public boolean getForceClose() {
        return forceClose;
    }

    public void close(boolean askForUserConfirmation) throws IOException {
        setForceClose(!askForUserConfirmation);
        notifyListeners(RegistrationEvents.CLOSE);
        listeners.clear();
    }

    public synchronized List<SourceAndConverter<?>[]> getAllSourcesPerStep() {
        List<SourceAndConverter<?>[]> sourcesPerStep = new ArrayList<>();
        for (RegistrationStep rs: registrationPairSteps) {
            sourcesPerStep.add(rs.sources);
        }
        return sourcesPerStep;
    }

    public String getRegistrationName(int step) {
        if ((step>=0)&&(step<registrationPairSteps.size())) {
            Registration reg = registrationPairSteps.get(step).reg;//.toString();
            if (RegistrationPluginHelper.isEditable(reg)) {
                return reg+" (editable)";
            } else {
                return reg.toString();
            }
        } else {
            return "";
        }
    }

    private static class RegistrationStep {

        final Registration<SourceAndConverter<?>[]> reg;
        final SourceAndConverter<?>[] sources;
        final SourcesProcessor fixedProcessor;
        final SourcesProcessor movingProcessor;

        public RegistrationStep(Registration<SourceAndConverter<?>[]> reg,
                                SourceAndConverter<?>[] sources,
                                SourcesProcessor fixedProcessor,
                                SourcesProcessor movingProcessor) {
            this.reg = reg;
            this.sources = sources;
            this.fixedProcessor = fixedProcessor;
            this.movingProcessor = movingProcessor;
        }
    }

    @Override
    public String toString() {
        return name;//+" [#f="+fixedSources.length+" #m="+movingSourcesOrigin.length+" #regs="+registrationAndSources.size()+"]";
    }

    /**
     * Computes the world space transformation which brings the center of a source to z = 0.
     * x and y are left untouched: the returned transform is a pure translation along z.
     *
     * @param source the source used as a reference (usually the first one of a group)
     * @param timePoint the timepoint at which the source geometry is evaluated
     * @return a translation along z only
     */
    private static AffineTransform3D findZ0Transform(SourceAndConverter<?> source, int timePoint) {
        Interval interval = source.getSpimSource().getSource(timePoint, 0);

        AffineTransform3D sourceToWorld = new AffineTransform3D();
        source.getSpimSource().getSourceTransform(timePoint, 0, sourceToWorld);

        // Center of the stack, in pixel coordinates, voxel center convention
        double[] centerPixel = new double[]{
                (interval.min(0) + interval.max(0)) / 2.0,
                (interval.min(1) + interval.max(1)) / 2.0,
                (interval.min(2) + interval.max(2)) / 2.0
        };

        double[] centerWorld = new double[3];
        sourceToWorld.apply(centerPixel, centerWorld);

        AffineTransform3D zToZero = new AffineTransform3D();
        zToZero.translate(0, 0, -centerWorld[2]);
        return zToZero;
    }

    private final AtomicInteger runningRegistrations = new AtomicInteger();

    /**
     * @return the number of registrations currently running on this pair, or waiting for
     * a running one to finish
     */
    public int getRunningRegistrationCount() {
        return runningRegistrations.get();
    }

    /**
     * @return true if at least one registration is running or queued on this pair
     */
    public boolean isBusy() {
        return runningRegistrations.get() > 0;
    }

    /**
     * Signals that a registration is about to be run on this pair, and notifies the listeners
     * so that they can display some feedback to the user.
     * <p>
     * This method is deliberately NOT synchronized: a registration which is queued behind a
     * running one must be able to announce itself immediately, instead of waiting for the
     * monitor of this object - which is precisely the situation the user needs to be told about.
     * <p>
     * Each call must be matched by a call to {@link RegistrationPair#registrationEnded()} in a
     * finally block.
     */
    public void registrationStarted() {
        runningRegistrations.incrementAndGet();
        notifyListeners(RegistrationEvents.BUSY_CHANGED);
    }

    /**
     * Signals that a registration started with {@link RegistrationPair#registrationStarted()}
     * is over, whether it succeeded or not.
     */
    public void registrationEnded() {
        runningRegistrations.updateAndGet(n -> n > 0 ? n - 1 : 0);
        notifyListeners(RegistrationEvents.BUSY_CHANGED);
    }

    public enum RegistrationEvents {
        STEP_ADDED,
        STEP_REMOVED,
        BUSY_CHANGED,
        CLOSE
    }

    final List<RegistrationPairListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(RegistrationPairListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RegistrationPairListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all listeners of an event. Listeners are called on the calling thread, which is
     * usually not the EDT, and a listener which misbehaves cannot prevent the others from being
     * notified.
     *
     * @param event the event to broadcast
     */
    private void notifyListeners(RegistrationEvents event) {
        for (RegistrationPairListener listener : listeners) { // snapshot iteration, no CME
            try {
                listener.newEvent(event);
            } catch (Exception e) {
                System.err.println("Error while notifying a registration pair listener of "
                        + event + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public interface RegistrationPairListener {
        void newEvent(RegistrationEvents event);
    }

}
