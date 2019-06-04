package ch.epfl.biop.bdv.process;

import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.RandomAccessibleInterval;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * NOT WORKING YET!
 */

@Plugin(type = Op.class, name = "rollingball2D")
public class OpRollingBall extends AbstractOp {

    @Parameter(type = ItemIO.INPUT)
    RandomAccessibleInterval imgIn;

    @Parameter(type = ItemIO.INPUT)
    int ballRadius;

    @Parameter(type = ItemIO.INPUT)
    boolean lightBackground;

    @Parameter(type = ItemIO.OUTPUT)
    RandomAccessibleInterval lblImgBorder;

    @Override
    public void run() {
        assert imgIn.numDimensions()==2;

        RollingBall ball = new RollingBall(ballRadius);

    }

}

class RollingBallNew {

    byte[] data;
    int patchwidth;
    int shrinkfactor;

    RollingBallNew(int radius) {
        int arcTrimPer;
        if (radius<=10) {
            shrinkfactor = 1;
            arcTrimPer = 12; // trim 24% in x and y
        } else if (radius<=30) {
            shrinkfactor = 2;
            arcTrimPer = 12; // trim 24% in x and y
        } else if (radius<=100) {
            shrinkfactor = 4;
            arcTrimPer = 16; // trim 32% in x and y
        } else {
            shrinkfactor = 8;
            arcTrimPer = 20; // trim 40% in x and y
        }
        buildRollingBall(radius, arcTrimPer);
    }

    /** Computes the location of each point on the rolling ball patch relative to the
     center of the sphere containing it.  The patch is located in the top half
     of this sphere.  The vertical axis of the sphere passes through the center of
     the patch.  The projection of the patch in the xy-plane below is a square.
     */
    void buildRollingBall(int ballradius, int arcTrimPer) {
        int rsquare;        // rolling ball radius squared
        int xtrim;          // # of pixels trimmed off each end of ball to make patch
        int xval, yval;     // x,y-values on patch relative to center of rolling ball
        int smallballradius, diam; // radius and diameter of rolling ball
        int temp;           // value must be >=0 to take square root
        int halfpatchwidth; // distance in x or y from center of patch to any edge
        int ballsize;       // size of rolling ball array

        this.shrinkfactor = shrinkfactor;
        smallballradius = ballradius/shrinkfactor;
        if (smallballradius<1)
            smallballradius = 1;
        rsquare = smallballradius*smallballradius;
        diam = smallballradius*2;
        xtrim = (arcTrimPer*diam)/100; // only use a patch of the rolling ball
        patchwidth = diam - xtrim - xtrim;
        halfpatchwidth = smallballradius - xtrim;
        ballsize = (patchwidth+1)*(patchwidth+1);
        data = new byte[ballsize];

        for (int i=0; i<ballsize; i++) {
            xval = i % (patchwidth+1) - halfpatchwidth;
            yval = i / (patchwidth+1) - halfpatchwidth;
            temp = rsquare - (xval*xval) - (yval*yval);
            if (temp >= 0)
                data[i] = (byte)Math.round(Math.sqrt(temp));
            else
                data[i] = 0;
        }
    }

}