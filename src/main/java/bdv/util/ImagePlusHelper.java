package bdv.util;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class that facilitate compatibility for going forth and back between bdv and imageplus
 * The affine transform located an ImagePlus in 3D cannot be properly defined using the ij.measure.Calibration class
 * - Inner trick used : store and retrieve the affine transform from within the ImagePlus "info" property
 * - This allows to support saving the image as tiff and being able to retrieve its location when loading it
 * - As well, cropping and scaling the image is allowed because the xOrigin, yOrigin (and maybe zOrigin, not tested)
 * allows to compute the offset relative to the original dataset
 * - Calibration is still used in order to store all the useful information that can be contained within it
 * (and is useful for the proper scaling retrieval)
 * @author Nicolas Chiaruttini, EPFL, 2020
 */

public class ImagePlusHelper {

    /**
     * Regex matching the toString function of AffineTransform3D
     */
    final public static String regexAffineTransform3D = "(3d-affine: \\()(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.*)\\)";

    public static void storeMatrixToImagePlus(ImagePlus imp, AffineTransform3D at3D) {
        Calibration cal = new Calibration();

        double[] m = at3D.getRowPackedCopy();
        double voxX = Math.sqrt(m[0]*m[0]+m[4]*m[4]+m[8]*m[8]);
        double voxY = Math.sqrt(m[1]*m[1]+m[5]*m[5]+m[9]*m[9]);
        double voxZ = Math.sqrt(m[2]*m[2]+m[6]*m[6]+m[10]*m[10]);

        cal.pixelWidth = voxX;
        cal.pixelHeight = voxY;
        cal.pixelDepth = voxZ;

        cal.xOrigin = 1; // Ignored if set to zero
        cal.yOrigin = 1;
        cal.zOrigin = 1;

        imp.setCalibration(cal);

        // Calibration is not enough Inner trick : use ImagePlus info property to store matrix of transformation
        if (imp.getInfoProperty() == null) {
            imp.setProperty("Info", " "); // One character should be present
        }
        String info = imp.getInfoProperty();

        // Removes any previously existing stored affine transform
        info = info.replaceAll(regexAffineTransform3D, "");

        // Appends matrix data
        info += at3D.toString() + "\n";

        imp.setProperty("Info", info);

    }

    public static AffineTransform3D getMatrixFromImagePlus(ImagePlus imp) {
        AffineTransform3D at3D = new AffineTransform3D();

        if (imp.getInfoProperty()!=null) {
            Pattern pattern = Pattern.compile(regexAffineTransform3D);
            Matcher matcher = pattern.matcher(imp.getInfoProperty());
            if (matcher.find()) {
                // Looks good, we have something that looks like an affine transform
                double[] m = new double[12];
                for (int i=0;i<12;i++) {
                    m[i] = Double.valueOf(matcher.group(i+2));
                }
                at3D.set(m);

                double[] offsetLocalCoordinates =
                        {imp.getCalibration().xOrigin-1,
                         imp.getCalibration().yOrigin-1,
                         imp.getCalibration().zOrigin-1};

                double[] offsetGlobalCoordinates = new double[3];

                double m03 = at3D.get(0,3);
                double m13 = at3D.get(1,3);
                double m23 = at3D.get(2,3);

                at3D.translate(-m03, -m13, -m23);

                at3D.apply(offsetLocalCoordinates, offsetGlobalCoordinates);

                at3D.translate(
                        m03 - offsetGlobalCoordinates[0],
                        m13 - offsetGlobalCoordinates[1],
                        m23 - offsetGlobalCoordinates[2]
                        );

                m03 = at3D.get(0,3);
                m13 = at3D.get(1,3);
                m23 = at3D.get(2,3);


                // Size
                double voxX = Math.sqrt(m[0]*m[0]+m[4]*m[4]+m[8]*m[8]);
                double voxY = Math.sqrt(m[1]*m[1]+m[5]*m[5]+m[9]*m[9]);
                double voxZ = Math.sqrt(m[2]*m[2]+m[6]*m[6]+m[10]*m[10]);

                double scaleX = imp.getCalibration().pixelWidth / voxX;
                double scaleY = imp.getCalibration().pixelHeight / voxY ;
                double scaleZ = imp.getCalibration().pixelDepth / voxZ;

                m[0]*=scaleX;m[4]*=scaleX;m[8]*=scaleX;
                m[1]*=scaleY;m[5]*=scaleY;m[9]*=scaleY;
                m[2]*=scaleZ;m[6]*=scaleZ;m[10]*=scaleZ;

                m[3] = m03;
                m[7] = m13;
                m[11] = m23;

                at3D.set(m);

                return at3D;
            } else {
               // Affine transform not found in ImagePlus Info
            }
        }

        if (imp.getCalibration()!=null) {
            //Matrix built from calibration
            at3D.scale(imp.getCalibration().pixelWidth,
                       imp.getCalibration().pixelHeight,
                       imp.getCalibration().pixelDepth );
            at3D.translate(imp.getCalibration().xOrigin * imp.getCalibration().pixelWidth,
                    imp.getCalibration().yOrigin * imp.getCalibration().pixelHeight,
                    imp.getCalibration().zOrigin * imp.getCalibration().pixelDepth
                    );
        }

        return at3D;
    }

}
