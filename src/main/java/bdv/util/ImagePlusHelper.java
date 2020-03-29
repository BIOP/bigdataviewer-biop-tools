package bdv.util;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

public class ImagePlusHelper {

    // TODO : improve storage of transform:
    // Assume orthogonality and store rotation matrix as quaternion
    public static void storeMatrixToImagePlus(ImagePlus imp, AffineTransform3D at3D) {
        Calibration cal = new Calibration();

        double[] m = at3D.getRowPackedCopy();
        double voxX = Math.sqrt(m[0]*m[0]+m[4]*m[4]+m[8]*m[8]);
        double voxY = Math.sqrt(m[1]*m[1]+m[5]*m[5]+m[9]*m[9]);
        double voxZ = Math.sqrt(m[2]*m[2]+m[6]*m[6]+m[10]*m[10]);

        cal.pixelWidth = voxX;
        cal.pixelHeight = voxY;
        cal.pixelDepth = voxZ;

        cal.xOrigin = m[3];
        cal.yOrigin = m[7];
        cal.zOrigin = m[11];

        // Inner trick : use cal.info to store matrix of transformation
        cal.info = ImagePlusHelper.matrixToStringIn64Characters(at3D); // Otherwise it cannot be stored in cal info

        imp.setCalibration(cal);
    }

    public static AffineTransform3D getMatrixFromImagePlus(ImagePlus imp) {
        AffineTransform3D at3D = new AffineTransform3D();

        if ((imp.getCalibration()!=null)&&(imp.getCalibration().info!=null)&&(imp.getCalibration().info.length()==63)) {

            at3D = ImagePlusHelper.stringIn64CharactersToMatrix(imp.getCalibration().info);

            at3D.set(imp.getCalibration().xOrigin, 0, 3);
            at3D.set(imp.getCalibration().yOrigin, 1, 3);
            at3D.set(imp.getCalibration().zOrigin, 2, 3);
        } else {
            at3D.set(imp.getCalibration().pixelWidth, 0, 0);
            at3D.set(imp.getCalibration().pixelHeight, 1, 1);
            at3D.set(imp.getCalibration().pixelDepth, 2, 2);


            at3D.set(imp.getCalibration().xOrigin, 0, 3);
            at3D.set(imp.getCalibration().yOrigin, 1, 3);
            at3D.set(imp.getCalibration().zOrigin, 2, 3);
        }
        return at3D;
    }

    public static String matrixToStringIn64Characters(AffineTransform3D at3D) {
        // 9 components (xOrigin, yOrigin and zOrigin and already stored
        // 64 / 9 = 7 characters per number + 1 :
        // 1 float is 32 bits, 4 bytes : 4 characters,
        // but some characters are not allowed...
        // 9 x 32 bits = 288 bits
        // one character = 5 bits
        // Starts ASCII table with offset at 65 = A character (http://www.asciitable.com/)
        // 5*64 = 320 bits. Enough!

        float[] m = new float[9];

        m[0] = (float) at3D.get(0,0);
        m[1] = (float) at3D.get(0,1);
        m[2] = (float) at3D.get(0,2);

        m[3] = (float) at3D.get(1,0);
        m[4] = (float) at3D.get(1,1);
        m[5] = (float) at3D.get(1,2);

        m[6] = (float) at3D.get(2,0);
        m[7] = (float) at3D.get(2,1);
        m[8] = (float) at3D.get(2,2);

        String str = "";

        for (int i=0; i<9; i++) {
            str+= floatToCompactString(m[i]);
        }

        return str;
    }

    public static AffineTransform3D stringIn64CharactersToMatrix(String str) {
        // 9 components (xOrigin, yOrigin and zOrigin and already stored
        // 64 / 9 = 7 characters per number + 1 :
        // 1 float is 32 bits, 4 bytes : 4 characters,
        // but some characters are not allowed...
        // 9 x 32 bits = 288 bits
        // one character = 5 bits
        // Starts ASCII table with offset at 65 = A character (http://www.asciitable.com/)
        // 5*64 = 320 bits. Enough!

        float[] m = new float[9];

        for (int i=0; i<9; i++) {
            m[i] = compactStringToFloat(str.substring(7*i, 7*(i+1)));
        }
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.set(m[0],0,0);
        at3D.set(m[1],0,1);
        at3D.set(m[2],0,2);

        at3D.set(m[3],1,0);
        at3D.set(m[4],1,1);
        at3D.set(m[5],1,2);

        at3D.set(m[6],2,0);
        at3D.set(m[7],2,1);
        at3D.set(m[8],2,2);

        return at3D;
    }

    public static float compactStringToFloat(String str) {
        int f1 = str.charAt(0);
        int f2 = str.charAt(1);
        int f3 = str.charAt(2);
        int f4 = str.charAt(3);
        int f5 = str.charAt(4);
        int f6 = str.charAt(5);
        int f7 = str.charAt(6);

        int offsetChar = 65;
        int reconstruct = 0;
        reconstruct+=f1-offsetChar;
        reconstruct+=(f2-offsetChar) << 5 ;
        reconstruct+=(f3-offsetChar) << 10 ;
        reconstruct+=(f4-offsetChar) << 15 ;
        reconstruct+=(f5-offsetChar) << 20 ;
        reconstruct+=(f6-offsetChar) << 25 ;
        reconstruct+=(f7-offsetChar) << 30 ;

        return Float.intBitsToFloat(reconstruct);
    }

    public static String floatToCompactString(float f) {

        int raw = Float.floatToRawIntBits(f);
        // One float will need 7 characters
        int offsetChar = 65;
        int f1 = (raw & 31) + offsetChar;
        int f2 = ((raw & (31 << 5)) >>> 5) + offsetChar;
        int f3 = ((raw & (31 << 10)) >>> 10) + offsetChar;
        int f4 = ((raw & (31 << 15)) >>> 15) + offsetChar;
        int f5 = ((raw & (31 << 20)) >>> 20) + offsetChar;
        int f6 = ((raw & (31 << 25)) >>> 25) + offsetChar;
        int f7 = ((raw & (31 << 30)) >>> 30) + offsetChar;

        return "" + ((char) f1) + ((char) f2) + ((char) f3) + ((char) f4) + ((char) f5) + ((char) f6) + ((char) f7);
    }

    public static void main(String... args) {
        boolean ok = true;
        for (int i=0;i<2000;i++) {
            float test = (float) Math.random();
            String str = floatToCompactString(test);
            float r = compactStringToFloat(str);
            if (r != test) {
                ok = false;
            }
        }
        if (ok) {
            System.out.println("ok");
        } else {
            System.out.println("not ok");
        }
    }
}
