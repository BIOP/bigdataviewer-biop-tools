package fused;

import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.scijava.command.spimdata.CreateCZIDatasetCommand;
import ch.epfl.biop.scijava.command.spimdata.FuseBigStitcherDatasetIntoOMETiffCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;

import java.io.File;

import static fiji.util.TicToc.tic;
import static fiji.util.TicToc.toc;

public class FusePerfMeasure {
    static ImageJ ij;

    static {
        LegacyInjector.preinit();
    }
    public static void main( String[] args ) throws Exception {

        /*Context ctx = new Context(CommandService.class,
                TaskService.class,
                SourceAndConverterService.class,
                ConvertService.class
        );*/

        ij = new ImageJ();
        ij.ui().showUI();

        File cziTest = DatasetHelper.getDataset("https://zenodo.org/records/8303129/files/Demo%20LISH%204x8%2015pct%20647.czi");

        DebugTools.enableLogging ("OFF");

        // Get rid of xml dataset and bfmemo
        File xmlOut = new File (FilenameUtils.removeExtension(cziTest.getAbsolutePath())+".xml");
        System.out.println(xmlOut.getAbsolutePath());
        if (xmlOut.exists()) xmlOut.delete();
        File xmlOutBfMemo = new File (cziTest.getAbsolutePath()+".bfmemo");
        if (xmlOutBfMemo.exists()) xmlOutBfMemo.delete();
        System.out.println(xmlOutBfMemo);
        ij.command()
        //ctx.getService(CommandService.class)
                .run(CreateCZIDatasetCommand.class, true,
                        "czi_file", cziTest,
                        "xml_out", xmlOut.getAbsolutePath(),
                        "erase_if_file_already_exists", true).get();

        tic();
        ij.command()
        //ctx.getService(CommandService.class)
                .run(FuseBigStitcherDatasetIntoOMETiffCommand.class,
                true, "xml_bigstitcher_file", xmlOut,
                "output_path_directory", xmlOut.getParent(),
                "n_resolution_levels", 1,
                "split_slices", false,
                "split_channels", false,
                "split_frames", false,
                "override_z_ratio", false,
                "range_channels", "",
                "range_slices", "",
                "range_frames", "",
                "use_lzw_compression", false,
                "z_ratio", 1.0, // ignored
                "use_interpolation", false,
                "fusion_method", "SMOOTH AVERAGE"
        ).get();
        toc();
    }
}
