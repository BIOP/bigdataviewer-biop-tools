package ch.epfl.biop.scijava.command.source.register;

import ij.IJ;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleWrapped2DTransformAs3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Obsolete>AutoWarp Sources with Elastix and BigWarp (2D)",
        description =
                "Register two 2D sources by automatically registering small fields of view \n" +
                "surrounding the specified points of interests using elastix. The returned result \n" +
                "is a thin plate spline transform object that can be applied to other sources then \n" +
                "edited in BigWarp.")

public class Elastix2DSparsePointsRegisterCommand extends SelectSourcesForRegistrationCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(Elastix2DSparsePointsRegisterCommand.class);

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

    @Parameter(label = "Size in physical units of each fov used for the registration of each point", style = "format:0.#####E0")
    double sx,sy;

    @Parameter(label = "Location in z (default 0)", style = "format:0.#####E0")
    double z_location = 0;

    @Parameter(label = "Location of points of interest (px,py) used for registration", style = "text area")
    String pt_list_coordinates = "15,10,\n -30,-40,\n ";

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform tst;

    @Parameter(label = "Parallel registration of points of interests")
    boolean parallel;

    @Parameter(label = "Show point of interests registration in ImageJ1 (disables parallelisation)")
    boolean show_points;

    @Parameter
    boolean verbose = false;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int max_iteration_per_scale = 100;

    @Parameter(required = false)
    Task task;

    @Parameter
    TaskService taskService;

    @Parameter
    CommandService cs;

    public Consumer<String> log = s -> {};

    AtomicInteger counter = new AtomicInteger();

    @Override
    public void run() {

        if (verbose) log = logger::info;

        if (show_points) {
            parallel=false; // Cannot parallelize with IJ1 functions
            logger.warn("Cannot run parallel registrations if showPoints is true.");
        }

        ArrayList<RealPoint> pts_Fixed = new ArrayList<>();

        String[] coordsXY = pt_list_coordinates.split(",");

        for (int i = 0;i<coordsXY.length;i+=2) {
            pts_Fixed.add(new RealPoint(Double.valueOf(coordsXY[i]),Double.valueOf(coordsXY[i+1]), z_location));
        }

        boolean innerTask = false;

        if (task==null) {
            task = taskService.createTask("Registration "+sacs_moving[0].getSpimSource().getName()+" vs "+sacs_fixed[0].getSpimSource().getName());
            task.setProgressMaximum(pts_Fixed.size());
            innerTask = true;
        }

        try {

            Stream<RealPoint> streamOfPts;

            if (parallel) {
                streamOfPts = pts_Fixed.parallelStream();
            } else {
                streamOfPts = pts_Fixed.stream();
            }

            ConcurrentHashMap<RealPoint, RealPoint> correspondingPts = new ConcurrentHashMap<>();
            counter.set(0);
            streamOfPts.forEach(pt -> {
                if (!task.isCanceled()) {
                    try {
                        AffineTransform3D at = (AffineTransform3D) cs.run(Elastix2DAffineRegisterCommand.class, true,
                                "sacs_fixed", sacs_fixed,
                                "tp_fixed", tp_fixed,
                                "level_fixed_source", level_fixed_source,
                                "sacs_moving", sacs_moving,
                                "tp_moving", tp_moving,
                                "level_moving_source", level_moving_source,
                                "px", pt.getDoublePosition(0) - sx / 2.0,
                                "py", pt.getDoublePosition(1) - sy / 2.0,
                                "pz", pt.getDoublePosition(2),
                                "sx", sx,
                                "sy", sy,
                                "px_size_in_current_unit", px_size_in_current_unit,
                                "interpolate", interpolate,
                                "show_image_registration", show_points,
                                "automatic_transform_initialization", false,
                                "max_iteration_per_scale", max_iteration_per_scale,
                                "background_offset_value_moving", background_offset_value_moving,
                                "background_offset_value_fixed", background_offset_value_fixed,
                                "min_image_size_pix", 32,
                                "verbose", verbose
                        ).get().getOutput("at3d");
                        if (at != null) {
                            RealPoint ptCorr = new RealPoint(3);
                            at.apply(pt, ptCorr);
                            correspondingPts.put(pt, ptCorr);

                            String str = "xi =" + pt.getDoublePosition(0) + "\t xf =" + ptCorr.getDoublePosition(0) + "\n";
                            str += "yi =" + pt.getDoublePosition(1) + "\t yf =" + ptCorr.getDoublePosition(1);
                            log.accept("Registration point : " + str);
                        } else {
                            correspondingPts.put(pt, pt); // No movement
                            log.accept("Failed registration - maybe the image has low information content ?");
                        }
                        IJ.log("#Landmark:" + counter.addAndGet(1) + "/" + pts_Fixed.size());
                        synchronized (task) {
                            task.setProgressValue(task.getProgressValue() + 1);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        logger.error("Error during registration");
                        e.printStackTrace();
                    }
                }
            });

            if (task.isCanceled()) return;

            // Returns the Thin Plate Spline transform

            double[][] ptI = new double[2][pts_Fixed.size()];
            double[][] ptF = new double[2][pts_Fixed.size()];

            for (int i = 0; i < pts_Fixed.size(); i++) {
                ptF[0][i] = pts_Fixed.get(i).getDoublePosition(0);
                ptF[1][i] = pts_Fixed.get(i).getDoublePosition(1);

                ptI[0][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(0);
                ptI[1][i] = correspondingPts.get(pts_Fixed.get(i)).getDoublePosition(1);
            }

            tst = new InvertibleWrapped2DTransformAs3D(
                    new WrappedIterativeInvertibleRealTransform<>(
                            new ThinplateSplineTransform(ptI, ptF)
                    )
            );
        } finally {
            if (innerTask) {
                task.finish();
            }
        }

    }

}
