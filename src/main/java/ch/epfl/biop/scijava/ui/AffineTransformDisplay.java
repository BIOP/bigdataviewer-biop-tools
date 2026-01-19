package ch.epfl.biop.scijava.ui;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Priority;
import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Plugin;

/**
 * Display adapter for AffineTransform3D objects in the SciJava framework.
 */
//@Plugin(type = Display.class, priority = Priority.HIGH)
public class AffineTransformDisplay extends AbstractDisplay<AffineTransform3D> {
    /**
     * Creates a new AffineTransformDisplay.
     */
    public AffineTransformDisplay() {
        super(AffineTransform3D.class);
    }
}
