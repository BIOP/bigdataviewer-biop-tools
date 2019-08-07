package ch.epfl.biop.bdv.bigmodelwarp;

import org.scijava.vecmath.Vector3d;

public class SecondFundamentalForm {

    // Center of the plate
    Vector3d o = new Vector3d();

    // First vector of the referential
    Vector3d eu = new Vector3d(); // Elementary u vector

    // Second vector of the referential
    Vector3d ev = new Vector3d(); // Elementary v vector

    // Third vector of the referential (normal vector)
    Vector3d en = new Vector3d(); // Elementary normal vector

    public double angle_z = 0;

    // Radius of curvature along eu and ev
    public double cu = 0, cv = 0;

    public void setNormal(double phi, double theta) {
        en.x = Math.sin(theta)*Math.cos(phi);
        en.y = Math.sin(theta)*Math.sin(phi);
        en.z = Math.cos(theta);
        //System.out.println("en=["+en.x+" "+en.y+" "+en.z+"]");
        setuv();
    }

    public double getPhi() {
        return Math.atan2(en.y, en.x);
    }

    public double getTheta() {
        return Math.acos(en.z);
    }

    private void setuv() {

        Vector3d a = new Vector3d(0, en.z, -en.y);
        Vector3d b = new Vector3d(0,0,0);

        a.normalize();

        //System.out.println("a=["+a.x+" "+a.y+" "+a.z+"]");

        b.cross(en,a);

        //System.out.println("b=["+b.x+" "+b.y+" "+b.z+"]");

        double sz = Math.sin(angle_z);
        double cz = Math.cos(angle_z);

        // eu = cz A + sz B
        // ev = cz B - sz A

        eu.x = cz * a.x + sz * b.x;
        eu.y = cz * a.y + sz * b.y;
        eu.z = cz * a.z + sz * b.z;


        //System.out.println("eu=["+eu.x+" "+eu.y+" "+eu.z+"]");

        ev.x = cz * b.x - sz * a.x;
        ev.y = cz * b.y - sz * a.y;
        ev.z = cz * b.z - sz * a.z;


        //System.out.println("ev=["+ev.x+" "+ev.y+" "+ev.z+"]");

    }

    public void setParameters(double ox, double oy, double oz,
                              double theta, double phi, double normal_angle,
                              double cu, double cv) {
        this.o.x = ox;
        this.o.y = oy;
        this.o.z = oz;
        this.cu = cu;
        this.cv = cv;
        this.angle_z = normal_angle;
        this.setNormal(phi, theta);
    }

    public double[] getParametersArray() {
        return new double[] {o.x, o.y, o.z,
                            this.getTheta(), this.getPhi(), angle_z,
                            cu,cv};
    }

    public void setParametersArray(double[] params) {
        assert params.length==8;
        setParameters(params[0], params[1], params[2],
                      params[3], params[4], params[5],
                      params[6], params[7]);

    }

    public Vector3d getPosAt(double u, double v, double n) {
        double h = 1d/2d*cu*u*u+1d/2d*cv*v*v;
        return new Vector3d(
                o.x + u*eu.x + v*ev.x + (h+n)*en.x,
                o.y + u*eu.y + v*ev.y + (h+n)*en.y,
                o.z + u*eu.z + v*ev.z + (h+n)*en.z
        );
    }

}
