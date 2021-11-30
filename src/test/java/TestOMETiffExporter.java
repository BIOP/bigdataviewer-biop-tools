import ch.epfl.biop.ImagePlusToOMETiff;
import ij.ImagePlus;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.ImageJ;

import java.io.File;

public class TestOMETiffExporter {
    public static void main(String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        //Dataset dataset = ij.context().getService(DatasetIOService.class).open("src/test/resources/blobs.tif");
        Dataset dataset = ij.context().getService(DatasetIOService.class).open("src/test/resources/mitosis.tif");
        //ij.ui().show(dataset);
        ImagePlus image = ij.convert().convert(dataset, ImagePlus.class);
        image.show();

        //File testExport = new File("src/test/resources/blobs.ome.tiff");
        File testExport = new File("src/test/resources/mitosis4.ome.tiff");

        ImagePlusToOMETiff.writeToOMETiff(image, testExport, 2,2);
    }

}
