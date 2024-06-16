package ch.epfl.biop.registration;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.RegistrationPluginHelper;
import ch.epfl.biop.sourceandconverter.processor.SourcesAffineTransformer;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Named;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.ArrayList;
import java.util.List;

public class RegistrationPair implements Named {

    final SourceAndConverter<?>[] movingSourcesOrigin;
    final SourceAndConverter<?>[] fixedSources;

    final int timepointMoving;
    final int timepointFixed;
    final String name;

    SourceAndConverter<?>[] movingSourcesRegistered;
    final List<RegistrationAndSources> registrationAndSources = new ArrayList<>();

    public RegistrationPair(SourceAndConverter<?>[] fixedSources,
                            int timepointFixed,
                            SourceAndConverter<?>[] movingSources,
                            int timepointMoving,
                            String name, boolean removezOffset
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

    public synchronized void appendRegistration(Registration<SourceAndConverter<?>[]> reg) {
        RegistrationAndSources ras = new RegistrationAndSources(reg, reg.getTransformedImageMovingToFixed(getMovingSourcesRegistered()));
        movingSourcesRegistered = ras.sacs;
        SourceAndConverterServices.getSourceAndConverterService().register(ras.sacs[0]);
        registrationAndSources.add(ras);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException("You can't rename a registration pair object");
    }

    public synchronized void removeLastRegistration() {
        if (registrationAndSources.size() == 0) return;
        if (registrationAndSources.size()==1) {
            registrationAndSources.remove(0);
            this.movingSourcesRegistered = movingSourcesOrigin;
        } else {
            RegistrationAndSources ras = registrationAndSources.get(registrationAndSources.size()-2);
            registrationAndSources.remove(registrationAndSources.size()-1);
            this.movingSourcesRegistered = ras.sacs;
        }
    }

    public synchronized void editLastRegistration() {
        if (registrationAndSources.size() == 0) {
            System.err.println("There is no registration to edit");
            return;
        }

        Registration lastReg = registrationAndSources.get(registrationAndSources.size()-1).reg;
        if (!RegistrationPluginHelper.isEditable(lastReg)) {
            System.err.println("The last registration is not editable");
            return;
        }

        removeLastRegistration();

        lastReg.edit();

        appendRegistration(lastReg);
    }

    public int getFixedTimepoint() {
        return timepointFixed;
    }

    public int getMovingTimepoint() {
        return timepointMoving;
    }

    private static class RegistrationAndSources {

        final Registration<SourceAndConverter<?>[]> reg;
        final SourceAndConverter<?>[] sacs;

        public RegistrationAndSources(Registration<SourceAndConverter<?>[]> reg, SourceAndConverter<?>[] sacs) {
            this.reg = reg;
            this.sacs = sacs;
        }
    }

    @Override
    public String toString() {
        return name+" [#f="+fixedSources.length+" #m="+movingSourcesOrigin.length+" #regs="+registrationAndSources.size()+"]";
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
