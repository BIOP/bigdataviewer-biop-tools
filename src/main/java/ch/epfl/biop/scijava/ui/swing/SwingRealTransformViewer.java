package ch.epfl.biop.scijava.ui.swing;

import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealTransform;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.viewer.EasySwingDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

import javax.swing.*;

@Plugin(type = DisplayViewer.class, priority = Priority.LOW)
public class SwingRealTransformViewer extends
        EasySwingDisplayViewer<RealTransform> {

    public SwingRealTransformViewer()
    {
        super( RealTransform.class );
    }

    @Override
    protected boolean canView(RealTransform rt) {
        return !(rt instanceof AffineTransform);
    }

    @Override
    protected void redoLayout() {

    }

    @Override
    protected void setLabel(String s) {

    }

    @Override
    protected void redraw() {

    }

    RealTransform rt;
    JPanel mainPanel;

    @Override
    protected JPanel createDisplayPanel(RealTransform rt) {
        this.rt = rt;
        mainPanel = new JPanel();
        mainPanel.add(new JLabel(rt.toString()));
        return mainPanel;
    }
}
