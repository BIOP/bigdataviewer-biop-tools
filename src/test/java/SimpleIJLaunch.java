import ij.ImagePlus;
import loci.common.DebugTools;
import net.imagej.ImageJ;

public class SimpleIJLaunch {

    static public void main(String... args) {
        // create the ImageJ application context with all available services

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

    }

    /*
    #@ ObjectService os
#@ ImagePlus imp

import ch.epfl.biop.bdv.process.Procedural3DImageShort
import bdv.util.BdvFunctions
import bdv.util.BdvOptions
import bdv.util.BdvHandle

bdv_h = os.getObjects(BdvHandle.class).get(0)


/*
s = (new Procedural3DImageShort(
            { p ->


            	(int) ((Math.sin(p[0]*4.0)*Math.cos(p[1]*20.0)+1)*100)

            }
      )).getSource("Wave");

BdvFunctions.show( s, BdvOptions.options().addTo(bdv_h) );

     */
}
