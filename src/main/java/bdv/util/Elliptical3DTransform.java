package bdv.util;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.*;
import net.imglib2.util.LinAlgHelpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A transform sequence which transform a 3D ellipse into a flat projection map.
 * <p>
 * This transform is a concatenation of
 * - a {@link SphericalToCartesianTransform3D} transform
 * - a {@link Scale3D} transform
 * - a {@link AffineTransform3D}, but only for rotation in 3D (quaternions)
 * - a {@link Translation3D},  for moving the ellipse in space
 *
 * There are 9 degrees of freedom:
 * - r1 : radius of first axis of the ellipse
 * - r2 : radius of first axis of the ellipse
 * - rx : rotation (rad) of the ellipse around the X axis
 * - ry : rotation (rad) of the ellipse around the Y axis
 * - rz : rotation (rad) of the ellipse around the Z axis
 * - tx : translation of the center of the ellipse along the X axis
 * - ty : translation of the center of the ellipse along the Y axis
 * - tz : translation of the center of the ellipse along the Z axis
 *
 * This transformation is invertible, but only in a certain fraction of the space
 */
public class Elliptical3DTransform implements InvertibleRealTransform {

    // Transform 1 : spherical to cartesian

    public SphericalToCartesianTransform3D s2c = SphericalToCartesianTransform3D.getInstance();

    // Transform 2 : scale elliptical axes

    public Scale3D s3d = new Scale3D(1f, 1f, 1f);

    // Transform 3 : rotate axes

    public AffineTransform3D rot3D = new AffineTransform3D();

    // Transform 4 : translate center

    public Translation3D tr3D = new Translation3D();

    public RealTransformSequence rts = new RealTransformSequence();

    public RealTransformSequence rtsi = new RealTransformSequence();

    public Elliptical3DTransform() {

        rts.add(s2c);
        rts.add(s3d);
        rts.add(rot3D);
        rts.add(tr3D);

        rtsi.add(tr3D.inverse());
        rtsi.add(rot3D.inverse());
        rtsi.add(s3d.inverse());
        rtsi.add(s2c.inverse());

        updateNotifiers = new ArrayList<>();
        this.updateTransformsFromParameters();

        inverse = new InverseRealTransform(this);
    }

    static public ArrayList<String> getParamsName() {
        ArrayList<String> names = new ArrayList<>();
        names.add("r1");
        names.add("r2");
        names.add("r3");
        names.add("rx");
        names.add("ry");
        names.add("rz");
        names.add("tx");
        names.add("ty");
        names.add("tz");
        return names;
    }

    public Map<String, Double> getParameters() {

        Map<String, Double> map = new LinkedHashMap<>();
        map.put("r1", r1);
        map.put("r2", r2);
        map.put("r3", r3);

        map.put("rx", rx);
        map.put("ry", ry);
        map.put("rz", rz);

        map.put("tx", tx);
        map.put("ty", ty);
        map.put("tz", tz);
        return map;
    }

    public void setParameters(Map<String, Double> parameters) {
        parameters.entrySet().forEach(entry -> {
            setParameters(entry.getKey(), entry.getValue());
        });
    }

    public void setParameters(Object... kv) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            map.put((String) kv[i],
                    (Double) kv[i + 1]);
        map.keySet().forEach(k -> {
            switch (k) {
                case "r1":
                    r1 = map.get(k);
                    break;
                case "r2":
                    r2 = map.get(k);
                    break;
                case "r3":
                    r3 = map.get(k);
                    break;
                case "rx":
                    rx = map.get(k);
                    break;
                case "ry":
                    ry = map.get(k);
                    break;
                case "rz":
                    rz = map.get(k);
                    break;
                case "tx":
                    tx = map.get(k);
                    break;
                case "ty":
                    ty = map.get(k);
                    break;
                case "tz":
                    tz = map.get(k);
                    break;
            }
        });

        this.updateTransformsFromParameters();

    }

    public ArrayList<Runnable> updateNotifiers;

    double r1 = 1, r2 = 1, r3 = 1, //radius of axes 1 2 3 of ellipse
            rx = 0, ry = 0, rz = 0, // 3D rotation euler angles maybe not the best parametrization
            tx = 0, ty = 0, tz = 0; // ellipse center


    void updateTransformsFromParameters() {
        // Easy
        s3d.set(r1, r2, r3);
        tr3D.set(tx, ty, tz);

        // Pfou

        /*Vector3d en = new Vector3d(0,0,0);
        Vector3d eu = new Vector3d(0,0,0);
        Vector3d ev = new Vector3d(0,0,0);

        en.x = Math.sin(theta)*Math.cos(phi);
        en.y = Math.sin(theta)*Math.sin(phi);
        en.z = Math.cos(theta);

        Vector3d a,b;
        if ((en.x==0)&&(en.y==0)) {
            a = new Vector3d(1, 0, 0); // TODO : is this good ?
        } else {
            a = new Vector3d(en.y, -en.x, 0);
        }

        b = new Vector3d(0, 0, 0);

        a.normalize();

        b.cross(en,a);

        double sz = Math.sin(angle_en);
        double cz = Math.cos(angle_en);

        eu.x = cz * a.x + sz * b.x;
        eu.y = cz * a.y + sz * b.y;
        eu.z = cz * a.z + sz * b.z;

        ev.x = cz * b.x - sz * a.x;
        ev.y = cz * b.y - sz * a.y;
        ev.z = cz * b.z - sz * a.z;

        rot3D.set(
                  en.x, eu.x, ev.x, 0,
                  en.y, eu.y, ev.y, 0,
                  en.z, eu.z, ev.z, 0,
                     0,    0,    0, 1
                );*/

        double rxRad = rx;// Math.PI * rx/360.0; // factor 2 because quaternions
        double ryRad = ry;// Math.PI * ry/360.0; // factor 2 because quaternions
        double rzRad = rz;// Math.PI * rz/360.0; // factor 2 because quaternions

        double[] qx = new double[4];

        qx[0] = Math.cos(rxRad);
        qx[1] = Math.sin(rxRad);
        qx[2] = 0;
        qx[3] = 0;

        double[] qy = new double[4];

        qy[0] = Math.cos(ryRad);
        qy[1] = 0;
        qy[2] = Math.sin(ryRad);
        qy[3] = 0;

        double[] qz = new double[4];

        qz[0] = Math.cos(rzRad);
        qz[1] = 0;
        qz[2] = 0;
        qz[3] = Math.sin(rzRad);

        double[] qXY = new double[4];

        LinAlgHelpers.quaternionMultiply(qy, qx, qXY);

        double[] qRes = new double[4];

        LinAlgHelpers.quaternionMultiply(qz, qXY, qRes);

        double[][] m = new double[3][3];

        AffineTransform3D rotMatrix = new AffineTransform3D();

        LinAlgHelpers.quaternionToR(qRes, m);

        rot3D.set(m[0][0], m[0][1], m[0][2], 0,
                m[1][0], m[1][1], m[1][2], 0,
                m[2][0], m[2][1], m[2][2], 0);


        updateNotifiers.forEach(c -> {
            c.run();
        });

    }

    @Override
    public void applyInverse(double[] source, double[] target) {
        rtsi.apply(source, target);
    }

    @Override
    public void applyInverse(RealPositionable source, RealLocalizable target) {
        rtsi.apply(target, source);
    }

    private final InverseRealTransform inverse;

    @Override
    public InvertibleRealTransform inverse() {
        return inverse;
    }

    @Override
    public int numSourceDimensions() {
        return 3;
    }

    @Override
    public int numTargetDimensions() {
        return 3;
    }

    @Override
    public void apply(double[] source, double[] target) {
        rts.apply(source, target);
    }

    @Override
    public void apply(RealLocalizable source, RealPositionable target) {
        rts.apply(source, target);
    }

    @Override
    public Elliptical3DTransform copy() {
        final Elliptical3DTransform copy = new Elliptical3DTransform();
        copy.rts = rts.copy();
        copy.rtsi = rtsi.copy();
        return copy;
    }

}
