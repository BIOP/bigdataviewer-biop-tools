package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Makes a model source spanning several sources")
public class SourcesMakeModelCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)")
    SourceAndConverter<?>[] sacs;

    @Parameter(label = "Voxel size (X)")
    double vox_size_x;

    @Parameter(label = "Voxel size (Y)")
    double vox_size_y;

    @Parameter(label = "Voxel size (Z)")
    double vox_size_z;

    @Parameter(label = "Model timepoint")
    int timepoint = 0;

    @Parameter(label = "Number of timepoint")
    int n_timepoints = 1;

    @Parameter(label = "Number of resolution levels")
    int n_resolution_levels = 1;

    @Parameter(label = "X Downscale Factor")
    int downscale_x = 1;

    @Parameter(label = "Y Downscale Factor")
    int downscale_y = 1;

    @Parameter(label = "Z Downscale Factor")
    int downscale_z = 1;

    @Parameter(label="Name of the model source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter<?> sac_out;

    @Override
    public void run() {
        sac_out = SourceHelper.getModelFusedMultiSources(sacs,
                timepoint, n_timepoints,
                vox_size_x, vox_size_y, vox_size_z,
                n_resolution_levels,
                downscale_x,downscale_y,downscale_z,name);
    }

}
