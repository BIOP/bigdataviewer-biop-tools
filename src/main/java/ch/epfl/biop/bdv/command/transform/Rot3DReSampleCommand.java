package ch.epfl.biop.bdv.command.transform;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import spimdata.imageplus.ImagePlusHelper;
import spimdata.imageplus.SpimDataFromImagePlusGetter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reorients an ImagePlus in 3D
 */

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = "Image>Stacks>Rotation 3D Resample",
description = "Required : two point roi in the ROI Manager and an ImagePlus. " +
        "This command reorients a 3D IJ1 image (ImagePlus) by creating another IJ1 image where" +
        "the two points Roi are aligned in Z. Hyperstakcs are supporteda and metadata (original pixel size)" +
        "matters.")
public class Rot3DReSampleCommand implements BdvPlaygroundActionCommand {

    @Parameter
    ImagePlus imp_in;

    @Parameter
    RoiManager rm;

    @Parameter(label = "Final Image Size X (physical unit)")
    double radiusx;

    @Parameter(label = "Final Image Size Y (physical unit)")
    double radiusy;

    @Parameter(label = "Final Image Size Z (physical unit)")
    double radiusz;

    @Parameter(label = "Final Voxel Size X (physical unit)")
    double voxFX;

    @Parameter(label = "Final Voxel Size Y (physical unit)")
    double voxFY;

    @Parameter(label = "Final Voxel Size Z (physical unit)")
    double voxFZ;

    @Parameter(label = "Align X axis (default true)")
    boolean alignX = true;

    @Parameter(type = ItemIO.OUTPUT)
    ImagePlus imp_out;

    @Parameter(label = "Interpolate pixel values during resampling.")
    boolean interpolate;

    @Parameter
    SourceAndConverterService sac_service;

    public void run() {

        AbstractSpimData asd = (new SpimDataFromImagePlusGetter()).apply(imp_in);
        sac_service.register(asd);
        sac_service.setSpimDataName(asd, imp_in.getTitle());
        List<SourceAndConverter> sacs = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverterFromSpimdata(asd);

        if (rm.getCount()<2) {
            System.err.println("Error : 2 point Rois should be present in the Roi Manager to reorient a stack");
            return;
        }

        if (rm.getCount()>2) {
            System.out.println("Ignoring rois with index > 1");
        }

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

        q1[0] = 1 + v2x*v1x+v2y*v1y+v2z*v1z;
        q1[1] = v2y*v1z-v2z*v1y;
        q1[2] = v2z*v1x-v2x*v1z;
        q1[3] = v2x*v1y-v2y*v1x;

        double normq1 = Math.sqrt( q1[0]*q1[0]+q1[1]*q1[1]+q1[2]*q1[2]+q1[3]*q1[3] );

        q1[0]/=normq1;
        q1[1]/=normq1;
        q1[2]/=normq1;
        q1[3]/=normq1;

        double [][] m = new double[3][3];

        AffineTransform3D rotMatrix = new AffineTransform3D();

        if (alignX) {
            double[] q2 = new double[4];

            double alpha = (Math.atan2(v1y,v1x)+Math.PI/2.0)/2.0;

            q2[0] = Math.cos(alpha);
            q2[3] = Math.sin(alpha);

            double[] q1q2 = new double[4];

            LinAlgHelpers.quaternionMultiply(q1,q2,q1q2);

            LinAlgHelpers.quaternionToR(q1q2, m);
        } else {
            LinAlgHelpers.quaternionToR(q1, m);
        }

        rotMatrix.set( m[0][0], m[0][1], m[0][2], 0,
                m[1][0], m[1][1], m[1][2], 0,
                m[2][0], m[2][1], m[2][2], 0);

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

        SourceAndConverter model;

        model = new EmptySourceAndConverterCreator("dummy", at3D, (long) nx,(long) ny,(long) nz).get();//SourceAndConverterHelper.createSourceAndConverter(src);

        if (model == null) {
            System.out.println("model is null");
        }

        SourceResampler sampler = new SourceResampler(null, model, "Sampler", false, false, interpolate,0);

        List<SourceAndConverter> reoriented_sources = sacs.stream().map(sampler::apply).collect(Collectors.toList());

        Map<SourceAndConverter, ConverterSetup> mapCS = new HashMap<>();
        reoriented_sources.forEach(sac -> mapCS.put(sac,
                    SourceAndConverterServices
                        .getBdvDisplayService()
                        .getConverterSetup(sac)
                ));

        Map<SourceAndConverter, Integer> mapMipmap = new HashMap<>();
        reoriented_sources.forEach(sac -> mapMipmap.put(sac, 0)); // Only one resolution exists

        imp_out = ImagePlusHelper.wrap(
                reoriented_sources,
                //mapCS,
                mapMipmap,
                0,
                imp_in.getNFrames(),
                1);

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

        imp_out.setCalibration(calibration);/**/
    }

}
