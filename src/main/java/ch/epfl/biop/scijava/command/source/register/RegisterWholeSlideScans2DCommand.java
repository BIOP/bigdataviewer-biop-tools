package ch.epfl.biop.scijava.command.source.register;

import bdv.util.BigWarpHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.qupath.command.CreateBdvDatasetQuPathCommand;
import ij.IJ;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.command.bdv.BdvSourcesShowCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

@Plugin(type = BdvPlaygroundActionCommand.class/*,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Align Slides (2D)"*/)
public class RegisterWholeSlideScans2DCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(RegisterWholeSlideScans2DCommand.class);

    @Parameter(label = "Global reference image (fixed, usually, first dapi channel)")
    SourceAndConverter global_ref_source;

    @Parameter(label = "Index of current reference image (moving, dapi channel of scan i)")
    SourceAndConverter current_ref_source;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

    @Parameter(label = "Locations of interest for warping registration", style = "text area")
    String pt_list_coordinates = "15,10,\n -30,-40,\n ";

    @Parameter(label = "Make a first affine registration on defined regions")
    boolean perform_first_coarse_affine_registration;

    @Parameter(label = "Make a second spline registration with landmarks")
    boolean perform_second_spline_registration;

    @Parameter(style = "format:0.#####E0")
    double top_left_x;

    @Parameter(style = "format:0.#####E0")
    double top_left_y;

    @Parameter(style = "format:0.#####E0")
    double bottom_right_x;

    @Parameter(style = "format:0.#####E0")
    double bottom_right_y;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int max_iteration_per_scale = 100;

    @Parameter(label = "Pixel size for coarse registration in mm (default 0.01)", style = "format:0.000", persist = false)
    double coarse_pixel_size_mm = 0.01;

    @Parameter(label = "Patch size for registration in mm (default 0.5)", style = "format:0.000", persist = false)
    double patch_size_mm = 0.5;

    @Parameter(label = "Pixel size for precise patch registration in mm (default 0.001)", style = "format:0.000", persist = false)
    double precise_pixel_size_mm = 0.001;

    @Parameter
    CommandService cs;

    @Parameter
    boolean show_details;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform tst;

    @Parameter
    boolean verbose;

    @Override
    public void run() {

        // Approximate rigid registration
        try {
            AffineTransform3D at1 = new AffineTransform3D();
            SourceAndConverter firstRegSrc = current_ref_source;

            if (perform_first_coarse_affine_registration) {
                logger.info("----------- First registration - Coarse Affine");
                IJ.log("- Coarse Affine Registration");

                CommandModule cm = cs.run(Elastix2DAffineRegisterCommand.class, true,
                        "sacs_fixed", new SourceAndConverter[]{global_ref_source},
                        "tp_fixed", 0,
                        "level_fixed_source", SourceAndConverterHelper.bestLevel(global_ref_source, 0, coarse_pixel_size_mm),
                        "sacs_moving", new SourceAndConverter[]{current_ref_source},
                        "tp_moving", 0,
                        "level_moving_source", SourceAndConverterHelper.bestLevel(current_ref_source, 0, coarse_pixel_size_mm),
                        "px", top_left_x,
                        "py", top_left_y,
                        "pz", 0,
                        "sx", bottom_right_x - top_left_x,
                        "sy", bottom_right_y - top_left_y,
                        "px_size_in_current_unit", coarse_pixel_size_mm, // in mm
                        "interpolate", true,
                        "show_image_registration", show_details,
                        "automatic_transform_initialization", false,
                        "max_iteration_per_scale", max_iteration_per_scale,
                        "min_image_size_pix", 32,
                        "background_offset_value_moving", background_offset_value_moving,
                        "background_offset_value_fixed", background_offset_value_fixed,
                        "verbose", verbose
                ).get();
                at1 = (AffineTransform3D) cm.getOutput("at3d");
                //firstRegSrc = (SourceAndConverter) cm.getOutput("registeredSource");
                firstRegSrc = new SourceAffineTransformer(at1).apply(current_ref_source);
            }

            logger.info("----------- Precise Warping based on particular locations");
            RealTransform tst_temp = new AffineTransform3D(); // Identity transform applied if no warping
            if (perform_second_spline_registration) {

                IJ.log("- Landmarks registration");
                tst_temp =
                        (RealTransform) cs.run(Elastix2DSparsePointsRegisterCommand.class, true,
                                "sacs_fixed", new SourceAndConverter[]{global_ref_source},
                                "sacs_moving", new SourceAndConverter[]{firstRegSrc},
                                "tp_fixed", 0,
                                "level_fixed_source", SourceAndConverterHelper.bestLevel(global_ref_source, 0, precise_pixel_size_mm), //TODO does this make sense ? Shouldn't it be self pixel precise size ?
                                "tp_moving", 0,
                                "level_moving_source", SourceAndConverterHelper.bestLevel(firstRegSrc, 0, precise_pixel_size_mm),
                                "pt_list_coordinates", pt_list_coordinates,
                                "z_location", 0,
                                "sx", patch_size_mm, // 500 microns
                                "sy", patch_size_mm, // 500 microns
                                "px_size_in_current_unit", precise_pixel_size_mm, //1 micron per pixel
                                "interpolate", true,
                                "show_points", show_details,//true,
                                "parallel", !show_details,//false,
                                "verbose", verbose,
                                "max_iteration_per_scale", max_iteration_per_scale,
                                "background_offset_value_moving", background_offset_value_moving,
                                "background_offset_value_fixed", background_offset_value_fixed,
                                "verbose", verbose
                        ).get().getOutput("tst");
            } else {
                // Let's put landmarks on each corner in case the user wants to edit the registration later
                pt_list_coordinates = top_left_x +","+ top_left_y +",";
                pt_list_coordinates += bottom_right_x +","+ top_left_y +",";
                pt_list_coordinates += bottom_right_x +","+ bottom_right_y +",";
                pt_list_coordinates += top_left_x +","+ bottom_right_y;
            }

            logger.info("----------- Computing global transformation");

            RealTransformSequence rts = new RealTransformSequence();
            AffineTransform3D at2 = new AffineTransform3D();
            rts.add(at1.concatenate(at2).inverse());
            rts.add(tst_temp);

            ArrayList<RealPoint> pts_Fixed = new ArrayList<>();
            ArrayList<RealPoint> pts_Moving = new ArrayList<>();

            String[] coordsXY = pt_list_coordinates.split(",");

            for (int i = 0;i<coordsXY.length;i+=2) {
                RealPoint pt_fixed = new RealPoint(Double.valueOf(coordsXY[i]),Double.valueOf(coordsXY[i+1]),0);
                pts_Fixed.add(pt_fixed);
                RealPoint pt_moving = new RealPoint(3);
                rts.apply(pt_fixed, pt_moving);
                pts_Moving.add(pt_moving);
            }

            tst = new InvertibleWrapped2DTransformAs3D(
                    new WrappedIterativeInvertibleRealTransform<>(
                            BigWarpHelper.getTransform(pts_Moving, pts_Fixed,true)
                    )
            );

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

    public static void main(String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        CommandService cs = ij.command();

        cs.run(CreateBdvDatasetQuPathCommand.class,true,
                "unit","MILLIMETER",
                "split_rgb_channels",false).get();

        cs.run(BdvSourcesShowCommand.class,true,
                "autoContrast",true,
                "adjustViewOnSource",true,
                "is2D",true,
                "windowTitle","Test Registration",
                "interpolate",false,
                "nTimepoints",1,
                "projector","Sum Projector",
                "sacs","SpimData 0>Channel>1").get();
    }
}
