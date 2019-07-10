package ch.epfl.biop.bdv.bigmodelwarp;

import bdv.util.BWBdvHandle;
import bdv.util.BdvHandle;
import bigwarp.BigWarp;
import net.imglib2.RealPoint;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Big Warp>Curvature Transform")
public class BMWCommand implements Command {
    @Parameter(label = "Bdv Frame from BigWarp")
    BdvHandle bdv_warp;

    // Center of the plate
    @Parameter
    double ox;
    @Parameter
    double oy;
    @Parameter
    double oz;

    // First vector of the referential
    @Parameter
    double ux;
    @Parameter
    double uy;
    @Parameter
    double uz;

    // Second vector of the referential
    @Parameter
    double vx;
    @Parameter
    double vy;
    @Parameter
    double vz;

    // Radius of curvature
    @Parameter
    double cu;
    @Parameter
    double cv;

    @Parameter
    double du;

    @Parameter
    double dv;

    @Parameter
    int nPtu;

    @Parameter
    int nPtv;

    // Thickness of the thin plate
    @Parameter
    double thickness;

    //ArrayList<RealPoint> pts_Image;
    //ArrayList<RealPoint> pts_Warp;

    @Override
    public void run() {

        int nPts = nPtu*nPtv;


        BigWarp bdvH = ((BWBdvHandle) bdv_warp).getBW();
        //pts_Image = new ArrayList<>();
        //pts_Warp = new ArrayList<>();

        double nx = uy*vz-uz*vy;
        double ny = uz*vx-ux*vz;
        double nz = ux*vy-uy*vx;


        for (int u=-nPtu;u<=nPtu;u++) {
            for (int v=-nPtv;v<=nPtv;v++) {
                double pu = u*du;
                double pv = v*dv;
                double pn = 1d/2d*cu*pu*pu+1d/2d*cv*pv*pv;

                double px = ox + pu*ux+pv*vx+pn*nx;
                double py = oy + pu*uy+pv*vy+pn*ny;
                double pz = oz + pu*uz+pv*vz+pn*nz;

                RealPoint ptI = new RealPoint(px,py,pz);
                RealPoint ptW = new RealPoint(pu,pv,0d);

                bdvH.addPoint(new double[]{pu,pv,thickness/20d},false);
                bdvH.addPoint(new double[]{px+thickness*nx/2d,py+thickness*ny/2d,pz+thickness*nz/2d},true);

                bdvH.addPoint(new double[]{pu,pv,-thickness/20d},false);
                bdvH.addPoint(new double[]{px-thickness*nx/2d,py-thickness*ny/2d,pz-thickness*nz/2d},true);
                //pts_Image.add(ptI);
                //pts_Warp.add(ptW);
            }
        }

    }
}
