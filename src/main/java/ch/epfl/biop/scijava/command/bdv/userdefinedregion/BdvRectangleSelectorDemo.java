package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.select.SelectedSourcesListener;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.Collection;

/**
 * Source Selector Behaviour Demo
 */

public class BdvRectangleSelectorDemo {

    static public void main(String... args) throws Exception {
        // Creates a demo bdv frame with demo images
        BdvHandle bdvh = initAndShowSources();

        RectangleSelectorBehaviour rsb = new RectangleSelectorBehaviour(bdvh);
        rsb.install();
        rsb.waitForSelection(10000);
        rsb.uninstall();

    }

    public static BdvHandle initAndShowSources() throws Exception {
        // load and convert the famous blobs image
        ImagePlus imp = IJ.openImage("src/test/resources/blobs.tif");
        RandomAccessibleInterval blob = ImageJFunctions.wrapReal(imp);

        // load 3d mri image spimdataset
        SpimData sd = new XmlIoSpimData().load("src/test/resources/mri-stack.xml");

        // Display mri image
        BdvStackSource bss = BdvFunctions.show(sd).get(0);
        bss.setDisplayRange(0,255);

        // Gets reference of BigDataViewer
        BdvHandle bdvh = bss.getBdvHandle();

        // Defines location of blobs image
        AffineTransform3D m = new AffineTransform3D();
        m.rotate(0,Math.PI/4);
        m.translate(256, 0,0);

        // Display first blobs image
        BdvFunctions.show(blob, "Blobs Rot X", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/4);
        m.translate(0,256,0);

        // Display second blobs image
        BdvFunctions.show(blob, "Blobs Rot Z ", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/6);
        m.rotate(0,Math.PI/720);
        m.translate(312,256,0);

        // Display third blobs image
        BdvFunctions.show(blob, "Blobs Rot Z Y ", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        // Sets BigDataViewer view
        m.identity();
        m.scale(0.75);
        m.translate(150,100,0);

        bdvh.getViewerPanel().setCurrentViewerTransform(m);
        bdvh.getViewerPanel().requestRepaint();

        return bdvh;
    }

}
