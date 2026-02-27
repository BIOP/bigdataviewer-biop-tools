import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import org.scijava.util.VersionUtils;
import sc.fiji.bdvpg.scijava.services.SourcePopupMenu;

import java.io.IOException;

public class SimpleIJLaunch {

    static public void main(String... args) throws IOException {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();

        //dowloadBrainVSIDataset();

        System.out.println(VersionUtils.getVersion(CreateBdvDatasetBioFormatsCommand.class));

        System.out.println(ij.command().getCommand(CreateBdvDatasetBioFormatsCommand.class).getMenuPath());

        SourcePopupMenu s;
    }

}
