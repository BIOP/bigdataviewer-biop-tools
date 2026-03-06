import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.workflow.lls7.LLS7DatasetOpenCommand;
import ch.epfl.biop.command.workflow.lls7.LLS7ZDriftCompensateCommand;
import net.imagej.ImageJ;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.service.SourceService;

import java.io.File;

public class DemoZDriftCompensate {

    public static void main(String... args) throws Exception {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        File f = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14903188/files/RBC_full_time_series.czi");

        ij.command().run(LLS7DatasetOpenCommand.class, true,
                "czi_file", f,
                "legacy_xy_mode", false).get();

        String datasetNameLattice = FilenameUtils.removeExtension(f.getName());

        SourceAndConverter[] sources = ij.context().getService(SourceService.class).tree().getSources(datasetNameLattice)
                .toArray(new SourceAndConverter[0]);

        ij.command().run(LLS7ZDriftCompensateCommand.class, true,
                "model_source", sources[0],
                        "sources_to_correct", sources,
                        "threshold", 225,
                        "mode", "Append",
                        "debug", true
                ).get();
    }

}

