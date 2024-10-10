package ch.epfl.biop.scijava.command.source;

import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceDistanceL1RAI;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Set L1 Alpha Source")
public class SourceSetAlphaCommand implements Command {

    @Parameter
    SourceAndConverter<?>[] sources;

    @Override
    public void run() {
        for (SourceAndConverter<?> source: sources) {

            double vox_size_x_micrometer, vox_size_y_micrometer, vox_size_z_micrometer;

            VoxelDimensions voxDim = source.getSpimSource().getVoxelDimensions();

            if (voxDim != null) {
                vox_size_x_micrometer = voxDim.dimension(0);
                vox_size_y_micrometer = voxDim.dimension(1);
                vox_size_z_micrometer = voxDim.dimension(2);
            } else {
                AffineTransform3D sourceTransform = new AffineTransform3D();
                source.getSpimSource().getSourceTransform(0,0, sourceTransform); // Assumption: same voxel size for all timepoints, and timepoint 0 exists

                double v1x = sourceTransform.get(0, 0);
                double v1y = sourceTransform.get(0, 1);
                double v1z = sourceTransform.get(0, 2);

                double v2x = sourceTransform.get(1, 0);
                double v2y = sourceTransform.get(1, 1);
                double v2z = sourceTransform.get(1,2);

                double v3x = sourceTransform.get(2, 0);
                double v3y = sourceTransform.get(2, 1);
                double v3z = sourceTransform.get(2,2);

                double a = Math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z);
                double b = Math.sqrt(v2x * v2x + v2y * v2y + v2z * v2z);
                double c = Math.sqrt(v3x * v3x + v3y * v3y + v3z * v3z);

                vox_size_x_micrometer = a;
                vox_size_y_micrometer = b;
                vox_size_z_micrometer = c;
            }

            AlphaSource alpha = new AlphaSourceDistanceL1RAI(source.getSpimSource(), (float) vox_size_x_micrometer,(float) vox_size_y_micrometer, (float) vox_size_z_micrometer);
            AlphaSourceHelper.setAlphaSource(source, alpha);
        }
    }
}
