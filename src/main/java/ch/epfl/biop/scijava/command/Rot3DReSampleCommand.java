package ch.epfl.biop.scijava.command;

import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.importer.SourcesFromImagePlusGetter;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Plugin(type = Command.class, menuPath = "3DRot>Rotation 3D Resample")
public class Rot3DReSampleCommand implements Command {

    @Parameter
    ImagePlus imp_in;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter[] sacs_out;

    @Parameter
    RoiManager rm;

    @Parameter
    double radiusx, radiusy, radiusz;

    @Parameter
    double voxFX, voxFY, voxFZ;

    @Parameter(type = ItemIO.OUTPUT)
    ImagePlus imp_out;

    public void run() {

        SourcesFromImagePlusGetter getter = new SourcesFromImagePlusGetter(imp_in);
        getter.run();
        List<SourceAndConverter> sacs = getter.getSources();

        Roi roi1 = rm.getRoi(0);
        Roi roi2 = rm.getRoi(1);

        if (!(roi1 instanceof PointRoi)) {
            System.err.println("First Roi is not a point!");
            return;
        }

        if (!(roi2 instanceof PointRoi)) {
            System.err.println("Second Roi is not a point!");
            return;
        }

        PointRoi pt1 = (PointRoi) roi1;
        PointRoi pt2 = (PointRoi) roi2;

        double voxIX = imp_in.getCalibration().pixelWidth;
        double voxIY = imp_in.getCalibration().pixelHeight;
        double voxIZ = imp_in.getCalibration().pixelDepth;

        double v1x = (pt2.getXBase()-pt1.getXBase())*voxIX;
        double v1y = (pt2.getYBase()-pt1.getYBase())*voxIY;
        double v1z = (pt2.getZPosition()-pt1.getZPosition())*voxIZ;

        double normv1 = Math.sqrt(v1x*v1x+v1y*v1y+v1z*v1z);

        v1x = v1x/normv1;
        v1y = v1y/normv1;
        v1z = v1z/normv1;

        double v2x = 0;
        double v2y = 0;
        double v2z = 1;

        double normv2 = Math.sqrt(v2x*v2x+v2y*v2y+v2z*v2z);

        v2x = v2x/normv2;
        v2y = v2y/normv2;
        v2z = v2z/normv2;

        double[] q1 = new double[4];

        q1[0] = 1 + v1x*v2x+v1y*v2y+v1z*v2z;
        q1[1] = v1y*v2z-v1z*v2y;
        q1[2] = v1z*v2x-v1x*v2z;
        q1[3] = v1x*v2y-v1y*v2x;

        double normq1 = Math.sqrt( q1[0]*q1[0]+q1[1]*q1[1]+q1[2]*q1[2]+q1[3]*q1[3] );

        q1[0]/=normq1;
        q1[1]/=normq1;
        q1[2]/=normq1;
        q1[3]/=normq1;

        double [][] m = new double[3][3];

        AffineTransform3D rotMatrix = new AffineTransform3D();

        LinAlgHelpers.quaternionToR(q1, m);

        rotMatrix.set( m[0][0], m[1][0], m[2][0], 0,
                m[0][1], m[1][1], m[2][1], 0,
                m[0][2], m[1][2], m[2][2], 0);

        double cx = ((pt1.getXBase()+pt2.getXBase())/2.0)*voxIX;
        double cy = ((pt1.getYBase()+pt2.getYBase())/2.0)*voxIY;
        double cz = ((pt1.getZPosition()+pt2.getZPosition())/2.0)*voxIZ;

        double nx =  (2*(radiusx/voxFX));
        double ny =  (2*(radiusy/voxFY));
        double nz =  (2*(radiusz/voxFZ));

        AffineTransform3D translateCenterBwd = new AffineTransform3D();
        translateCenterBwd.translate(-nx/2,-ny/2,-nz/2);

        AffineTransform3D translateCenterFwd = new AffineTransform3D();
        translateCenterFwd.translate(-cx,-cy,-cz);

        AffineTransform3D scaler = new AffineTransform3D();
        scaler.scale(voxFX, voxFY, voxFZ);

        AffineTransform3D at3D = new AffineTransform3D();

        at3D.concatenate(translateCenterFwd.inverse());

        at3D.concatenate(rotMatrix);

        at3D.concatenate(scaler);

        at3D.concatenate(translateCenterBwd);

        // Need to make the model

        ArrayImg rai = ArrayImgs.unsignedBytes((long) nx,(long) ny,(long) nz);

        Source src = new RandomAccessibleIntervalSource(rai, Util.getTypeFromInterval(rai), at3D, "Dummy");

        SourceAndConverter model;

        model = SourceAndConverterUtils.createSourceAndConverter(src);

        if (model == null) {
            System.out.println("model is nul");
        }
        SourceResampler sampler = new SourceResampler(null, model, false);

        if (sacs == null) {
            System.out.println("sacs is null");
        }
        sacs_out = sacs.stream().map(sampler::apply).collect(Collectors.toList())
                       .toArray(new SourceAndConverter[sacs.size()]);

        ImagePlus[] channels = new ImagePlus[sacs_out.length];


        IntStream.range(0,sacs_out.length).parallel().forEach(i ->
                //);
        //for (int i = 0;i<sacs_out.length;i++)
        {
            channels[i] = ImageJFunctions.wrap(sacs_out[i].getSpimSource().getSource(0,0),"");
            channels[i].setDimensions(1, channels[i].getNSlices(), 1); // Set 3 dimension as Z, not as Channel
        });

        imp_out = RGBStackMerge.mergeChannels(channels, false);
        imp_out.setTitle("Reoriented_"+imp_in.getTitle());

        // Calibration in the limit of what's possible to know and set
        Calibration calibration = new Calibration();
        calibration.setImage(imp_out);

        // Origin is in fact the center of the image
        calibration.xOrigin=cx;
        calibration.yOrigin=cy;
        calibration.zOrigin=cz;

        calibration.pixelWidth=voxFX;
        calibration.pixelHeight=voxFY;
        calibration.pixelDepth=voxFZ;

        calibration.setUnit(imp_in.getCalibration().getUnit());

        imp_out.setCalibration(calibration);
    }

}
