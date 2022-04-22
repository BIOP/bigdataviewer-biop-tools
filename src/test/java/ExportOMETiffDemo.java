import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.OMETiffPyramidizerExporter;
import ij.IJ;
import ij.ImagePlus;
import loci.common.DebugTools;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import spimdata.imageplus.SpimDataFromImagePlusGetter;

import java.util.List;


public class ExportOMETiffDemo {

    static {
        LegacyInjector.preinit();
    }

    static SourceAndConverter source;

    static SourceAndConverter movingSource;

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        DebugTools.enableLogging ("OFF");
        // Creates a demo bdv frame with demo images
        BdvHandle bdvh = initAndShowSources();

        //OMETiffExporter.builder()
        OMETiffPyramidizerExporter.builder()
                .nThreads(4)
                .savePath("C:/Users/nicol/Desktop/ometiff/blobs.ome.tiff")//C:/Users/nicol/Desktop/ometiff/blobs.tiff")
                .millimeter()
                .downsample(2)
                .nResolutionLevels(3)
                .tileSize(64,64)
                .create(source).export();

    }

    static BdvHandle initAndShowSources() throws Exception {
        // load and convert the famous blobs image
        ImagePlus imp = IJ.openImage("src/test/resources/blobs.tif");
        RandomAccessibleInterval blob = ImageJFunctions.wrapReal(imp);

        // load 3d mri image spimdataset
        SpimData sd = new XmlIoSpimData().load("src/test/resources/mri-stack.xml");

        // Display mri image
        BdvStackSource bss = BdvFunctions.show(sd).get(0);
        bss.setDisplayRange(0,255);

        source = (SourceAndConverter) bss.getSources().get(0);

        // Gets reference of BigDataViewer
        BdvHandle bdvh = bss.getBdvHandle();
        bss.removeFromBdv();
        // Defines location of blobs image
        AffineTransform3D m = new AffineTransform3D();
        m.rotate(2,Math.PI/20);
        m.translate(0, -40,0);

        // Display first blobs image
        bss = BdvFunctions.show(blob, "Blobs 1", BdvOptions.options().sourceTransform(m).addTo(bdvh));
        bss.setColor(new ARGBType(ARGBType.rgba(255,0,0,0)));

        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/25);
        m.translate(0,-60,0);

        // Display second blobs image
        bss = BdvFunctions.show(blob, "Blobs 2", BdvOptions.options().sourceTransform(m).addTo(bdvh));
        bss.setColor(new ARGBType(ARGBType.rgba(0,255,255,0)));
        /*
        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/6);
        m.rotate(0,Math.PI/720);
        m.translate(312,256,0);

        // Display third blobs image
        BdvFunctions.show(blob, "Blobs Rot Z Y ", BdvOptions.options().sourceTransform(m).addTo(bdvh));
        */
        // Sets BigDataViewer view
        m.identity();
        m.scale(1);
        m.translate(150,0,0);

        bdvh.getViewerPanel().state().setViewerTransform(m);
        bdvh.getViewerPanel().requestRepaint();

        ImagePlus impRGB = IJ.openImage("src/test/resources/blobsrgb.tif");
        AbstractSpimData sdblob = (new SpimDataFromImagePlusGetter()).apply(impRGB);
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.rotate(2,15);
        List<BdvStackSource<?>> bssL = BdvFunctions.show(sdblob, BdvOptions.options().addTo(bdvh));
        //SourceAndConverter rgbSac = bssL.get(0).getSources().get(0);
        //bssL.remove(0);

        return bdvh;
    }


}
