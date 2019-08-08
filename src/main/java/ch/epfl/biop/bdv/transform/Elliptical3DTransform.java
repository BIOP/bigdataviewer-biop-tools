package ch.epfl.biop.bdv.transform;

import net.imglib2.realtransform.*;
import org.scijava.vecmath.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class Elliptical3DTransform extends RealTransformSequence {

    // Transform 1 : spherical to cartesian

    SphericalToCartesianTransform3D s2c = SphericalToCartesianTransform3D.getInstance();

    // Transform 2 : scale elliptical axes

    Scale3D s3d = new Scale3D(1f,1f,1f);

    // Transform 3 : rotate axes

    AffineTransform3D rot3D = new AffineTransform3D();

    // Transform 4 : translate center

    Translation3D tr3D = new Translation3D();

    public Elliptical3DTransform() {
        nSource=3;
        nTarget=3;
        this.add(s2c);
        this.add(s3d);
        this.add(rot3D);
        this.add(tr3D);

        updateNotifiers = new ArrayList<>();
        this.updateTransformsFromParameters();

    }

    public Map<String, Double> getParameters() {

        Map<String, Double> map = new LinkedHashMap<>();
        map.put("r1", r1);
        map.put("r2", r2);
        map.put("r3", r3);

        map.put("theta", theta);
        map.put("phi", phi);
        map.put("angle_en", angle_en);

        map.put("tx", tx);
        map.put("ty", ty);
        map.put("tz", tz);
        return map;
    }

    public void setParameters(Object... kv) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            map.put((String) kv[i],
                    (Double) kv[i + 1]);
        map.keySet().forEach(k -> {
            switch (k) {
                case "r1":
                    r1=map.get(k);
                    break;
                case "r2":
                    r2=map.get(k);
                    break;
                case "r3":
                    r3=map.get(k);
                    break;
                case "theta":
                    theta=map.get(k);
                    break;
                case "phi":
                    phi=map.get(k);
                    break;
                case "angle_en":
                    angle_en=map.get(k);
                    break;
                case "tx":
                    tx=map.get(k);
                    break;
                case "ty":
                    ty=map.get(k);
                    break;
                case "tz":
                    tz=map.get(k);
                    break;
            }
        });

        this.updateTransformsFromParameters();

    }

    public ArrayList<Runnable> updateNotifiers;

    double r1=1, r2=1, r3=1, //radius of axes 1 2 3 of ellipse
           theta=0, phi=0, angle_en=0, // 3D rotation euler angles maybe not the best parametrization
           tx=0, ty=0, tz=0; // ellipse center


    void updateTransformsFromParameters() {
        // Easy
        s3d.set(r1, r2, r3);
        tr3D.set(tx,ty,tz);

        // Pfou

        Vector3d en = new Vector3d(0,0,0);
        Vector3d eu = new Vector3d(0,0,0);
        Vector3d ev = new Vector3d(0,0,0);

        en.x = Math.sin(theta)*Math.cos(phi);
        en.y = Math.sin(theta)*Math.sin(phi);
        en.z = Math.cos(theta);

        Vector3d a,b;
        if ((en.z==0)&&(en.y==0)) {
            a = new Vector3d(0, en.x, 0); // TODO : is this good ?
        } else {
            a = new Vector3d(0, en.z, -en.y);
        }

        b = new Vector3d(0, 0, 0);

        a.normalize();

        b.cross(en,a);

        double sz = Math.sin(angle_en);
        double cz = Math.cos(angle_en);

        // eu = cz A + sz B
        // ev = cz B - sz A

        eu.x = cz * a.x + sz * b.x;
        eu.y = cz * a.y + sz * b.y;
        eu.z = cz * a.z + sz * b.z;

        ev.x = cz * b.x - sz * a.x;
        ev.y = cz * b.y - sz * a.y;
        ev.z = cz * b.z - sz * a.z;

        rot3D.set(
                  eu.x, eu.y, eu.z, 0,
                  ev.x, ev.y, ev.z, 0,
                  en.x, en.y, en.z, 0,
                     0,    0,    0, 1
                );

        //rot3D.identity();

        updateNotifiers.forEach(c -> {
            c.run();
        });

    }

}
