package ch.epfl.biop.scijava.ui;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Priority;
import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Plugin;

@Plugin(type = Display.class, priority = Priority.HIGH)
public class AffineTransformDisplay extends AbstractDisplay<AffineTransform3D> {
    public AffineTransformDisplay() {
        super(AffineTransform3D.class);
    }
}
