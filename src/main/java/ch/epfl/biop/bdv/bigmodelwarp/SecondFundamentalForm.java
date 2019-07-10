package ch.epfl.biop.bdv.bigmodelwarp;

import org.scijava.vecmath.Vector3d;

public class SecondFundamentalForm {

    // Center of the plate
    Vector3d o = new Vector3d();

    // First vector of the referential
    Vector3d u = new Vector3d();;

    // Second vector of the referential
    Vector3d v = new Vector3d();;

    public double angle_z = 0;

    // Third vector of the referential (normal vector)
    Vector3d n = new Vector3d();

    // Radius of curvature along u and v
    public double cu = 0, cv = 0;

    public void setNormal(double phi, double theta) {
        n.x = Math.sin(theta)*Math.cos(phi);
        n.y = Math.sin(theta)*Math.sin(phi);
        n.z = Math.cos(theta);
        setuv();
    }

    public double getPhi() {
        return Math.atan2(n.y,n.x);
    }

    public double getTheta() {
        return Math.acos(n.z);
    }

    private void setuv() {

        Vector3d a = new Vector3d(0,n.z, -n.y);
        Vector3d b = new Vector3d(0,0,0);

        a.normalize();
        b.cross(n,a);

        double sz = Math.sin(angle_z);
        double cz = Math.cos(angle_z);

        // u = cz U + sz V
        // v = cz V - sz U

        u.x = cz * u.x + sz * v.x;
        u.y = cz * u.y + sz * v.y;
        u.z = cz * u.z + sz * v.z;

        v.x = cz * v.x - sz * u.x;
        v.y = cz * v.y - sz * u.y;
        v.z = cz * v.z - sz * u.z;

    }

}
