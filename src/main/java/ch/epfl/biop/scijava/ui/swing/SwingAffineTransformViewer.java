package ch.epfl.biop.scijava.ui.swing;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.viewer.EasySwingDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

import javax.swing.*;
import java.awt.*;

//@Plugin(type = DisplayViewer.class, priority = Priority.HIGH)
public class SwingAffineTransformViewer extends
        EasySwingDisplayViewer<AffineTransform3D> {

    public SwingAffineTransformViewer()
    {
        super( AffineTransform3D.class );
    }

    @Override
    protected boolean canView(AffineTransform3D affineTransform) {
        return true;
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

    AffineTransform3D at;

    JPanel mainPanel;
    JTextArea ta;

    @Override
    protected JPanel createDisplayPanel(AffineTransform3D affineTransform) {
        this.at = affineTransform;
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        ta = new JTextArea();
        ta.setText(at.toString());
        ta.setEditable(false);
        mainPanel.add(ta, BorderLayout.CENTER);
        return mainPanel;
    }
}
