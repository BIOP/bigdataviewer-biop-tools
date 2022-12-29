package ch.epfl.biop.scijava.command.source;

import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import java.util.Arrays;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Fuse and Resample Sources Based on Model Source")
public class SourcesFuserAndResamplerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)")
    SourceAndConverter[] sacs;

    @Parameter
    SourceAndConverter model;

    @Parameter(label="Re-use MipMaps")
    boolean reusemipmaps;

    @Parameter(label="MipMap level if not re-used (0 = max resolution)")
    int defaultmipmaplevel;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean cache;

    @Parameter
    int cacheX = 64, cacheY=64, cacheZ=64;

    @Parameter(label = "Number of blocks kept in memory, negative values = no bounds")
    int cacheBounds = -1;

    @Parameter
    int nThreads = 4;

    @Parameter(label="Name of the fused resampled source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter sac_out;

    @Parameter(choices = {AlphaFusedResampledSource.SUM, AlphaFusedResampledSource.AVERAGE})
    String blendingMode;

    @Override
    public void run() {
        // Should not be parallel
        List<SourceAndConverter> sacs_list = Arrays.asList(sacs);
        sac_out = new SourceFuserAndResampler(sacs_list, blendingMode,  model, name, reusemipmaps, cache, interpolate, defaultmipmaplevel, cacheX, cacheY, cacheZ, cacheBounds, nThreads).get();
    }

}
