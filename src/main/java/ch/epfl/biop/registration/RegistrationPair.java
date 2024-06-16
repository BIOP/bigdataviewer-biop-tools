package ch.epfl.biop.registration;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.RegistrationPluginHelper;
import ch.epfl.biop.sourceandconverter.processor.SourcesAffineTransformer;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Named;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RegistrationPair implements Named {

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

    public String getRegistrationErrorMessage() {
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

        SourceAndConverterServices.getSourceAndConverterService().register(rp.sacs[0]);
        registrationPairSteps.add(rp);
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
}
