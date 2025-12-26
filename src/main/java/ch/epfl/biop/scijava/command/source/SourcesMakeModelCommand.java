package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceHelper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Makes a model source spanning several sources",
        description = "Creates an empty model source that spans the bounding box of multiple sources with custom voxel size")
public class SourcesMakeModelCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources whose combined bounding box defines the model extent")
    SourceAndConverter<?>[] sacs;

    @Parameter(label = "Voxel Size X",
            description = "Output voxel size in X dimension")
    double vox_size_x;

    @Parameter(label = "Voxel Size Y",
            description = "Output voxel size in Y dimension")
    double vox_size_y;

    @Parameter(label = "Voxel Size Z",
            description = "Output voxel size in Z dimension")
    double vox_size_z;

    @Parameter(label = "Model Timepoint",
            description = "Reference timepoint used to compute the bounding box")
    int timepoint = 0;

    @Parameter(label = "Number of Timepoints",
            description = "Number of timepoints in the model source")
    int n_timepoints = 1;

    @Parameter(label = "Resolution Levels",
            description = "Number of pyramid resolution levels to create")
    int n_resolution_levels = 1;

    @Parameter(label = "X Downscale Factor",
            description = "Downscaling factor between resolution levels in X")
    int downscale_x = 1;

    @Parameter(label = "Y Downscale Factor",
            description = "Downscaling factor between resolution levels in Y")
    int downscale_y = 1;

    @Parameter(label = "Z Downscale Factor",
            description = "Downscaling factor between resolution levels in Z")
    int downscale_z = 1;

    @Parameter(label = "Model Name",
            description = "Name for the model source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT,
            description = "The resulting model source spanning all input sources")
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
