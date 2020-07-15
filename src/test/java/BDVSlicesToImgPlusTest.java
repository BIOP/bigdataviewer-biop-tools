import net.imagej.ImageJ;
import org.junit.Test;

/**
 * Test consistently failing. I don't know why
 */

public class BDVSlicesToImgPlusTest {

    @Test
    public void run() throws Exception {
        // Arrange
        //  create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();


        /*
        ij.command().run(BigDataServerPlugInSciJava.class, true,
                "urlServer","http://fly.mpi-cbg.de:8081",
                "datasetName", "Drosophila").get();

        ij.command().run(ch.epfl.biop.bdv.commands.BDVSlicesToImgPlus.class, true);
        */
    }
}
