package ch.epfl.biop.sourceandconverter.importer;

import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.plugin.ChannelSplitter;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;
import sc.fiji.bdvpg.sourceandconverter.display.ColorChanger;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SourcesFromImagePlusGetter implements Runnable {

    ImagePlus imp;

    List<SourceAndConverter> sacs;

    public SourcesFromImagePlusGetter(ImagePlus imp) {
        this.imp = imp;
    }

    @Override
    public void run() {
        ImagePlus[] imps = ChannelSplitter.split(imp);
        List<ImagePlus> impList = Arrays.asList(imps);
        sacs = impList.stream().map(image -> transformSingleChannel(image)).collect(Collectors.toList());
    }

    public List<SourceAndConverter> getSources() {
        return sacs;
    }

    int nChannel = 0;

    public SourceAndConverter transformSingleChannel(ImagePlus im) {
        if (im.getT()>1) {
            System.err.println("Multiple timepoints unsupported in imageplus to SourceAndConverter convertion");
        }

        RandomAccessibleInterval rai = ImageJFunctions.wrapReal(im);
        // Adds a third dimension because Bdv needs 3D
        if (rai.numDimensions()==2) {
            rai = Views.addDimension(rai, 0, 0);
        }

        AffineTransform3D at3D = new AffineTransform3D();

        at3D.set(imp.getCalibration().pixelWidth,0,0);
        at3D.set(imp.getCalibration().pixelHeight,1,1);
        at3D.set(imp.getCalibration().pixelDepth,2,2);

        // Makes Bdv Source
        Source src = new RandomAccessibleIntervalSource(rai, Util.getTypeFromInterval(rai), at3D, imp.getTitle()+"_Ch"+nChannel);
        nChannel++;
        SourceAndConverter sac = SourceAndConverterUtils.createSourceAndConverter(src);
        new ColorChanger(sac, new ARGBType( im.getProcessor().getLut().getRGB( 255 ) ) ).run();
        new BrightnessAdjuster(sac, im.getDisplayRangeMin(), im.getDisplayRangeMax()).run();

        return sac;
    }
}
