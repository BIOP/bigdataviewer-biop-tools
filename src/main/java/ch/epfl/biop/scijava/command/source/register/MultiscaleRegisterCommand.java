package ch.epfl.biop.scijava.command.source.register;

import bdv.util.BigWarpHelper;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.InvertibleWrapped2DTransformAs3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.ThinPlateSplineTransformAdapter;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO test edge cases
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Multiscale Registration (2D)",
        headless = true, // User interface not required
        initializer = "updateInfo"
        )
public class MultiscaleRegisterCommand implements BdvPlaygroundActionCommand{

    private static Logger logger = LoggerFactory.getLogger(MultiscaleRegisterCommand.class);

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "<html><h2>Automated WSI registration using multiscale Warpy</h2><br/>"+
            "Automated registrations requires elastix.<br/>"+
            "</html>";

    @Parameter(label = "Number of registration scales (# registration x2 per scale)", style = "slider", min = "2", max="8", callback = "updateInfo")
    int n_scales = 4;

    @Parameter(visibility = ItemVisibility.MESSAGE, required = false)
    String infoRegistration = "";

    @Parameter(label = "Fixed reference sources", style = "sorted")
    SourceAndConverter<?>[] fixed;

    @Parameter(label = "Moving sources used for registration to the reference", style = "sorted")
    SourceAndConverter<?>[] moving;

    @Parameter(label = "Sources to transform, including the moving source if needed")
    SourceAndConverter<?>[] sources_to_transform;

    @Parameter(label = "Remove images z-offsets")
    boolean remove_z_offset = true;

    @Parameter(label = "Center moving image with fixed image")
    boolean center_moving_image = true;

    @Parameter(label = "Number of pixel for each block of image used for the registration (default 128)")
    int pixels_per_block = 128;

    @Parameter(label = "Number of iterations for each registration (default 100)")
    int max_iteration_number_per_scale = 100;

    @Parameter(label = "Show results of automated registrations (breaks parallelization)")
    boolean show_details = false;

    @Parameter
    boolean debug;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter<?>[] transformed_sources;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform transformation;
    @Parameter
    CommandService cs;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {
        //TODO: task service and QuPath export and warning with too many points and cancellation and less registrations

        InvertibleRealTransformSequence transformSequence = new InvertibleRealTransformSequence();

        // These transforms are removed at the end in the wizard
        AffineTransform3D preTransformFixed = new AffineTransform3D();
        AffineTransform3D preTransformMoving = new AffineTransform3D();

        // Gather pre transformations
        if (remove_z_offset) {
            AffineTransform3D at3D = new AffineTransform3D();
            moving[0].getSpimSource().getSourceTransform(0,0,at3D);
            preTransformMoving.translate(0,0,-at3D.get(2,3)); // Removes z offset

            fixed[0].getSpimSource().getSourceTransform(0,0,at3D);
            preTransformFixed.translate(0,0,-at3D.get(2,3)); // Removes z offset
        }

        if (center_moving_image) {
            RealPoint centerMoving = SourceAndConverterHelper.getSourceAndConverterCenterPoint(moving[0],0);
            RealPoint centerFixed = SourceAndConverterHelper.getSourceAndConverterCenterPoint(fixed[0],0);
            preTransformMoving.translate(
                    centerFixed.getDoublePosition(0)-centerMoving.getDoublePosition(0),
                    centerFixed.getDoublePosition(1)-centerMoving.getDoublePosition(1),
                    0 // Use removeZOffset for that!
                    );
        }

        // Apply these pre transformations to all sources involved in the registration
        for (int i = 0; i< moving.length; i++) {
            SourceAndConverter newFixed = SourceTransformHelper.createNewTransformedSourceAndConverter(preTransformFixed, new SourceAndConverterAndTimeRange(fixed[i],0));
            SourceAndConverter newMoving = SourceTransformHelper.createNewTransformedSourceAndConverter(preTransformMoving, new SourceAndConverterAndTimeRange(moving[i],0));
            moving[i] = newMoving;
            fixed[i] = newFixed;
        }

        Task task = null;

        try {
            // General
            task = taskService.createTask("Warpy Reg. "+moving[0].getSpimSource().getName()+"  / "+fixed[0].getSpimSource().getName());

            // Let's take the bounding box
            RealInterval box = getBoundingBox();
            List<RealPoint> corners = new ArrayList<>();
            corners.add(box.minAsRealPoint());
            corners.add(box.maxAsRealPoint());

            double topLeftX = Math.min(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
            double topLeftY = Math.min(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );
            double bottomRightX = Math.max(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
            double bottomRightY = Math.max(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );

            // Let's start with an affine registration with double the block size

            // TODO

            // Now we want to do a registration for blocks that become smaller and smaller and cover the whole image
            transformation = new RealTransformSequence();

            task.setStatusMessage("Registration started...");
            Map<Integer, List<RealPoint>> landmarksPerScale = new HashMap<>();
            Map<Integer, Double> blockSizeXmmPerScale = new HashMap<>();
            Map<Integer, Double> blockSizeYmmPerScale = new HashMap<>();

            int nScales = 0;

            // First we compute the number and size of blocks to register per scale
            // Initialisation
            double sizeBlockXmm = bottomRightX-topLeftX; // Should all be positive
            double sizeBlockYmm = bottomRightY-topLeftY;
            //double maxSizeBlockmm = Math.max(sizeBlockXmm, sizeBlockYmm);

            int nRegistrations = 0;

            int iScale = 0;
            // Subdivide blocks until we reach the minimal block size
            while (iScale<n_scales) {
                // Let's split in 2 according to the biggest dimension
                if (sizeBlockXmm>sizeBlockYmm) sizeBlockXmm/=2.; else sizeBlockYmm/=2.;

                // And update the block size for end condition
                List<RealPoint> landmarksAtCurrentLevel = new ArrayList<>();
                for (double xp = topLeftX + sizeBlockXmm/2; xp < bottomRightX; xp += sizeBlockXmm) {
                    for (double yp = topLeftY + sizeBlockYmm/2; yp < bottomRightY; yp += sizeBlockYmm) {
                        landmarksAtCurrentLevel.add(new RealPoint(xp, yp));
                    }
                }

                if (landmarksAtCurrentLevel.size()>=4) {
                    landmarksPerScale.put(nScales, landmarksAtCurrentLevel);
                    blockSizeXmmPerScale.put(nScales, sizeBlockXmm);
                    blockSizeYmmPerScale.put(nScales, sizeBlockYmm);
                    nScales++;
                    nRegistrations+= landmarksAtCurrentLevel.size();
                }
                iScale++;
            }

            task.setProgressMaximum(nRegistrations);

            for (int scale = 0; scale<nScales; scale++) {
                // Perform the registration at scale 'scale'
                task.setStatusMessage("Registration level "+(scale+1)+" / "+nScales);

                double pixelSizeBlockmm = Math.max(blockSizeXmmPerScale.get(scale), blockSizeYmmPerScale.get(scale)) / pixels_per_block;
                SourceAndConverter<?>[] transformedMoving;
                // Convert to string representation
                String ptListCoordinates = "";
                for (RealPoint pt : landmarksPerScale.get(scale)) {
                    ptListCoordinates += pt.getDoublePosition(0)+","+pt.getDoublePosition(1)+",";
                }

                if (scale!=0) {
                    // Let's compute the current BigWarp transform, by avoiding the concatenation of all transforms
                    // -> unnecessary optimisation ?

                    ArrayList<RealPoint> ptsSource = new ArrayList<>();
                    ArrayList<RealPoint> ptsTarget = new ArrayList<>();

                    List<RealPoint> landmarksLastScale = landmarksPerScale.get(nScales-1);

                    for (RealPoint source : landmarksLastScale) {
                        ptsSource.add(source);
                        RealPoint dest = new RealPoint(3);
                        transformSequence.apply(source, dest);
                        ptsTarget.add(dest);
                    }

                    InvertibleRealTransform currentTransformation = new InvertibleWrapped2DTransformAs3D(
                            new WrappedIterativeInvertibleRealTransform<>(
                                    BigWarpHelper.getTransform(ptsTarget, ptsSource,true)
                            )
                    );

                    transformedMoving = Arrays.stream(moving)
                            .map(source -> new SourceRealTransformer(currentTransformation).apply(source))
                            .toArray(SourceAndConverter<?>[]::new);
                } else {
                    transformedMoving = moving;
                }

                // Let's run the registration
                RealTransform currentLevelTransform =
                        (RealTransform) cs.run(Elastix2DSparsePointsRegisterCommand.class, true,
                                "sacs_fixed", fixed,
                                "sacs_moving", transformedMoving,
                                "tpFixed", 0,
                                "levelFixedSource", SourceAndConverterHelper.bestLevel(fixed[0], 0, pixelSizeBlockmm),
                                "tpMoving", 0,
                                "levelMovingSource", SourceAndConverterHelper.bestLevel(moving[0], 0, pixelSizeBlockmm),
                                "ptListCoordinates", ptListCoordinates, // landmarksAtCurrentLevel
                                "zLocation", 0,
                                "sx", blockSizeXmmPerScale.get(scale),
                                "sy", blockSizeYmmPerScale.get(scale),
                                "pxSizeInCurrentUnit", pixelSizeBlockmm,
                                "interpolate", true,
                                "showPoints", show_details,
                                "parallel", !show_details,
                                "verbose", debug,
                                "maxIterationNumberPerScale", max_iteration_number_per_scale,
                                "minPixSize", 32,
                                "background_offset_value_moving", 0,
                                "background_offset_value_fixed", 0,
                                "task", task
                        ).get().getOutput("tst");

                transformSequence.add(((InvertibleWrapped2DTransformAs3D)currentLevelTransform).getTransform());

                // For debugging

                if (debug) {
                    ArrayList<RealPoint> ptsSource = new ArrayList<>();
                    ArrayList<RealPoint> ptsTarget = new ArrayList<>();

                    List<RealPoint> landmarksLastScale = landmarksPerScale.get(nScales-1);

                    for (RealPoint source : landmarksLastScale) {
                        ptsSource.add(source);
                        RealPoint dest = new RealPoint(3);
                        transformSequence.apply(source, dest);
                        ptsTarget.add(dest);
                    }

                    InvertibleRealTransform currentTransformation = new InvertibleWrapped2DTransformAs3D(
                            new WrappedIterativeInvertibleRealTransform<>(
                                    BigWarpHelper.getTransform(ptsTarget, ptsSource,true)
                            )
                    );

                    SourceAndConverter<?>[] currentScaleSources = Arrays.stream(moving)
                            .map(source -> new SourceRealTransformer(currentTransformation).apply(source))
                            .toArray(SourceAndConverter<?>[]::new);
                    for (SourceAndConverter<?> source : currentScaleSources ) {
                        SourceAndConverterServices.getSourceAndConverterService().register(new RenamedSourceAndConverterAdapter(source, source.getSpimSource().getName()+"_Scale_"+scale));
                    }
                }

                // task.setProgressValue(task.getProgressValue()+landmarksPerScale.get(scale).size()); done inside the inner task

                if (task.isCanceled()) break;
            }

            if (!task.isCanceled()) {

                ArrayList<RealPoint> ptsSource = new ArrayList<>();
                ArrayList<RealPoint> ptsTarget = new ArrayList<>();

                List<RealPoint> landmarksLastScale = landmarksPerScale.get(nScales - 1);

                for (int i = 0; i < landmarksLastScale.size(); i++) {
                    RealPoint source = landmarksLastScale.get(i);
                    ptsSource.add(source);
                    RealPoint dest = new RealPoint(3);
                    transformSequence.apply(source, dest);
                    ptsTarget.add(dest);
                }

                transformation = new InvertibleWrapped2DTransformAs3D(
                        new WrappedIterativeInvertibleRealTransform<>(
                                BigWarpHelper.getTransform(ptsTarget, ptsSource, true)
                        )
                );

                // Adds the pretransformation!
                preTransformLandmarks(preTransformMoving);
                task.setStatusMessage("Registration DONE.");

                transformed_sources = Arrays.stream(sources_to_transform)
                        .map(source -> new SourceRealTransformer(transformation).apply(source))
                        .toArray(SourceAndConverter<?>[]::new);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            task.finish();
        }

    }

    private void preTransformLandmarks(AffineTransform3D centeringTransform) {
        // I know, it's complicated
        ThinplateSplineTransform tst = (ThinplateSplineTransform)
                ((WrappedIterativeInvertibleRealTransform)
                        ((InvertibleWrapped2DTransformAs3D)transformation).getTransform())
                        .getTransform();
        ThinPlateR2LogRSplineKernelTransform kernel = ThinPlateSplineTransformAdapter.getKernel(tst);
        double[][] pts_src = ThinPlateSplineTransformAdapter.getSrcPts(kernel);
        double[][] pts_tgt = ThinPlateSplineTransformAdapter.getTgtPts(kernel);

        List<RealPoint> movingPts = new ArrayList<>();
        List<RealPoint> fixedPts = new ArrayList<>();
        for (int i = 0; i<kernel.getNumLandmarks(); i++) {
            RealPoint moving = new RealPoint(3);
            RealPoint fixed = new RealPoint(3);
            for (int d = 0; d<kernel.getNumDims();d++) {
                // num dims should be 2!
                moving.setPosition(pts_tgt[d][i], d);
                fixed.setPosition(pts_src[d][i], d);
            }
            //manualTransform.inverse().apply(moving, moving);
            centeringTransform.inverse().apply(moving, moving);
            movingPts.add(moving);
            fixedPts.add(fixed);
        }
        transformation = new InvertibleWrapped2DTransformAs3D(
                new WrappedIterativeInvertibleRealTransform<>(BigWarpHelper.getTransform(movingPts, fixedPts, true))
        );
    }

    RealInterval getBoundingBox() {
        SourceAndConverter[] sources = new SourceAndConverter[]{moving[0], fixed[0]};
        List<RealInterval> intervalList = Arrays.stream(sources).map((sourceAndConverter) -> {
            Interval interval = sourceAndConverter.getSpimSource().getSource(0, 0);
            AffineTransform3D sourceTransform = new AffineTransform3D();
            sourceAndConverter.getSpimSource().getSourceTransform(0, 0, sourceTransform);
            RealPoint corner0 = new RealPoint(new float[]{(float)interval.min(0), (float)interval.min(1), (float)interval.min(2)});
            RealPoint corner1 = new RealPoint(new float[]{(float)interval.max(0), (float)interval.max(1), (float)interval.max(2)});
            sourceTransform.apply(corner0, corner0);
            sourceTransform.apply(corner1, corner1);
            return new FinalRealInterval(new double[]{Math.min(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.min(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.min(corner0.getDoublePosition(2), corner1.getDoublePosition(2))}, new double[]{Math.max(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.max(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.max(corner0.getDoublePosition(2), corner1.getDoublePosition(2))});
        }).collect(Collectors.toList());
        RealInterval maxInterval = intervalList.stream().reduce((i1, i2) -> {
            return new FinalRealInterval(
                    new double[]{
                            Math.max(i1.realMin(0), i2.realMin(0)),
                            Math.max(i1.realMin(1), i2.realMin(1)),
                            Math.max(i1.realMin(2), i2.realMin(2))},
                    new double[]{
                            Math.min(i1.realMax(0), i2.realMax(0)),
                            Math.min(i1.realMax(1), i2.realMax(1)),
                            Math.min(i1.realMax(2), i2.realMax(2))});
        }).get();
        return maxInterval;
    }

    static class RenamedSourceAndConverterAdapter<T> extends SourceAndConverter<T> {
        final String name;
        final SourceAndConverter<T> origin;
        protected RenamedSourceAndConverterAdapter(SourceAndConverter<T> soc, String name) {
            super(new RenamedSourceAdapter<>(soc.getSpimSource(), name), soc.getConverter(), soc.asVolatile());
            origin = soc;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class RenamedSourceAdapter<T> implements Source<T> {
        final Source<T> origin;
        final String name;
        public RenamedSourceAdapter(Source<T> origin, String name) {
            this.origin = origin;
            this.name = name;
        }

        @Override
        public boolean isPresent(int t) {
            return origin.isPresent(t);
        }

        @Override
        public RandomAccessibleInterval<T> getSource(int t, int level) {
            return origin.getSource(t, level);
        }

        @Override
        public boolean doBoundingBoxCulling() {
            return origin.doBoundingBoxCulling();
        }

        @Override
        public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
            return origin.getInterpolatedSource(t, level, method);
        }

        @Override
        public void getSourceTransform(int t, int level, AffineTransform3D transform) {
            origin.getSourceTransform(t, level, transform);
        }

        @Override
        public T getType() {
            return origin.getType();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public VoxelDimensions getVoxelDimensions() {
            return origin.getVoxelDimensions();
        }

        @Override
        public int getNumMipmapLevels() {
            return origin.getNumMipmapLevels();
        }
    }

    void updateInfo() {
        int nReg = 4*((int) (Math.pow(2, n_scales-1)-1));
        infoRegistration = nReg+" registrations will be computed, resulting in "+(int) Math.pow(2, n_scales)+" control points";
    }
}