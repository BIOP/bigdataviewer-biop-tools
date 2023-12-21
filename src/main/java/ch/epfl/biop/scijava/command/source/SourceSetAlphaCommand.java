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
import org.scijava.vecmath.Point3d;
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
                Point3d v1 = new Point3d(sourceTransform.get(0, 0), sourceTransform.get(0, 1), sourceTransform.get(0, 2));
                Point3d v2 = new Point3d(sourceTransform.get(1, 0), sourceTransform.get(1, 1), sourceTransform.get(1, 2));
                Point3d v3 = new Point3d(sourceTransform.get(2, 0), sourceTransform.get(2, 1), sourceTransform.get(2, 2));
                double a = Math.sqrt(v1.x * v1.x + v1.y * v1.y + v1.z * v1.z);
                double b = Math.sqrt(v2.x * v2.x + v2.y * v2.y + v2.z * v2.z);
                double c = Math.sqrt(v3.x * v3.x + v3.y * v3.y + v3.z * v3.z);
                vox_size_x_micrometer = a;
                vox_size_y_micrometer = b;
                vox_size_z_micrometer = c;
            }

            AlphaSource alpha = new AlphaSourceDistanceL1RAI(source.getSpimSource(), (float) vox_size_x_micrometer,(float) vox_size_y_micrometer, (float) vox_size_z_micrometer);
            AlphaSourceHelper.setAlphaSource(source, alpha);
        }
    }
}
