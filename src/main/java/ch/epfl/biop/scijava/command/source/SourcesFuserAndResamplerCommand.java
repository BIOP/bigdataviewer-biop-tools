package ch.epfl.biop.scijava.command.source;

import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceDistanceL1RAI;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ij.IJ;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import java.util.Arrays;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Fuse and Resample Sources Based on Model Source",
        description = "Fuses multiple sources into one, resampled to match a model source's grid")
public class SourcesFuserAndResamplerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources to fuse together")
    SourceAndConverter[] sacs;

    @Parameter(label = "Model Source",
            description = "The source whose grid defines the output resolution and dimensions")
    SourceAndConverter model;

    @Parameter(label = "Re-use MipMaps",
            description = "When checked, uses existing pyramid levels for efficiency")
    boolean reusemipmaps;

    @Parameter(label = "Default MipMap Level",
            description = "Pyramid level to use if not reusing mipmaps (0 = highest resolution)")
    int defaultmipmaplevel;

    @Parameter(label = "Interpolate",
            description = "When checked, uses interpolation when resampling")
    boolean interpolate;

    @Parameter(label = "Cache",
            description = "When checked, caches computed blocks in memory")
    boolean cache;

    @Parameter(label = "Cache Block X",
            description = "Cache block size in X dimension")
    int cache_x = 64;

    @Parameter(label = "Cache Block Y",
            description = "Cache block size in Y dimension")
    int cache_y = 64;

    @Parameter(label = "Cache Block Z",
            description = "Cache block size in Z dimension")
    int cache_z = 64;

    @Parameter(label = "Cache Size Limit",
            description = "Maximum number of blocks in cache (-1 = unlimited)")
    int cache_bounds = -1;

    @Parameter(label = "Number of Threads",
            description = "Number of parallel threads for computation")
    int n_threads = 4;

    @Parameter(label = "Output Name",
            description = "Name for the fused resampled source")
    String name;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Fused Source",
            description = "The resulting fused and resampled source")
    SourceAndConverter sac_out;

    @Parameter(label = "Blending Mode",
            description = "Method used to combine overlapping sources",
            choices = {AlphaFusedResampledSource.SUM, AlphaFusedResampledSource.AVERAGE,
            "SMOOTH "+AlphaFusedResampledSource.AVERAGE,
            AlphaFusedResampledSource.MAX})
    String blending_mode;

    @Override
    public void run() {

        // Should not be parallel
        List<SourceAndConverter> sacs_list = Arrays.asList(sacs);
        if (blending_mode.equals("SMOOTH "+AlphaFusedResampledSource.AVERAGE)) {

            VoxelDimensions voxelDimensions = model.getSpimSource().getVoxelDimensions();
            String unit = voxelDimensions.unit();
            IJ.log("Units is assumed to be micrometers, and it is "+unit);
            double vox_size_x_micrometer = voxelDimensions.dimension(0);
            double vox_size_y_micrometer = voxelDimensions.dimension(1);
            double vox_size_z_micrometer = voxelDimensions.dimension(2);

            for (SourceAndConverter<?> source: sacs) {
                AlphaSource alpha = new AlphaSourceDistanceL1RAI(source.getSpimSource(),
                        (float) vox_size_x_micrometer,
                        (float) vox_size_y_micrometer,
                        (float) vox_size_z_micrometer);
                AlphaSourceHelper.setAlphaSource(source, alpha);
            }
        }

        sac_out = new SourceFuserAndResampler(sacs_list, blending_mode,  model, name, reusemipmaps, cache, interpolate, defaultmipmaplevel, cache_x, cache_y, cache_z, cache_bounds, n_threads).get();
    }

}
