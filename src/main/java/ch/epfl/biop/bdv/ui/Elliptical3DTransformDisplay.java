package ch.epfl.biop.bdv.ui;

import ch.epfl.biop.bdv.transform.ellipticaltransform.Elliptical3DTransform;
import org.scijava.Priority;
import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Plugin;

@Plugin(type = Display.class, priority = Priority.FIRST)
public class Elliptical3DTransformDisplay extends AbstractDisplay<Elliptical3DTransform> {
    public Elliptical3DTransformDisplay() {
        super(Elliptical3DTransform.class);
    }
}
