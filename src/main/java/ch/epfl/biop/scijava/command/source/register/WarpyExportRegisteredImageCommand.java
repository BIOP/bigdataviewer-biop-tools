package ch.epfl.biop.scijava.command.source.register;

import bdv.util.EmptySource;
import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.legacy.qupath.entity.QuPathEntryEntity;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import com.google.gson.stream.JsonReader;
import ij.IJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformHelper;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.epfl.biop.scijava.command.source.register.WarpyEditRegistrationCommand.removeZOffsets;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>QuPath - Export Warpy Registered Image")
public class WarpyExportRegisteredImageCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String message = "<html><h1>QuPath registration exporter</h1>Please select a moving and a fixed source<br></html>";

    @Parameter(label = "Remove Z offsets")
    boolean remove_z_offsets = true;

    @Parameter(label = "Pre-compute transformation before export (faster for n landmarks ~> 40)")
    boolean pre_compute_transform;

    @Parameter(label = "Transformation pre-computation downsampling", style = "slider", min = "10", max = "200")
    int pre_compute_downsample_xy = 10;

    @Parameter(label = "Fixed source", callback = "updateMessage", style ="sorted")
    SourceAndConverter<?>[] fixed_sources;

    @Parameter(label = "Moving sources", callback = "updateMessage", style ="sorted")
    SourceAndConverter<?>[] moving_sources;

    @Parameter(label = "Include fixed sources in exported image")
    boolean includeFixedSources;

    @Parameter(label = "Interpolate pixels values")
    boolean interpolate;

    @Parameter
    Context scijavaCtx;

    @Override
    public void run() {
        try {
            if (remove_z_offsets) fixed_sources = removeZOffsets(fixed_sources);
            if (remove_z_offsets) moving_sources = removeZOffsets(moving_sources);
            //QuPathEntryEntity fixedEntity = QuPathBdvHelper.getQuPathEntityFromDerivedSource(fixed_sources[0]);
            int fixed_series_index = QuPathBdvHelper.getEntryId(fixed_sources[0]);//fixedEntity.getId();
            Map<SourceAndConverter, RealTransform> sourceToTransformation =new HashMap<>();
            double downsampleXYTransformField = pre_compute_downsample_xy;
            double downsampleZTransformField = 1;
            EmptySource model = null;
            if (pre_compute_transform) {
                EmptySource.EmptySourceParams params = new EmptySource.EmptySourceParams();
                // Assert all fixed sources have the same size
                long nPixX = fixed_sources[0].getSpimSource().getSource(0, 0).max(0) + 1;
                long nPixY = fixed_sources[0].getSpimSource().getSource(0, 0).max(1) + 1;
                long nPixZ = fixed_sources[0].getSpimSource().getSource(0, 0).max(2) + 1;
                params.nx = (long) (nPixX / downsampleXYTransformField);
                params.ny = (long) (nPixY / downsampleXYTransformField);
                params.nz = (long) (nPixZ / downsampleZTransformField);
                AffineTransform3D transform = new AffineTransform3D();
                fixed_sources[0].getSpimSource().getSourceTransform(0, 0, transform);
                params.at3D = transform.copy();
                double posX = params.at3D.get(0, 3);
                double posY = params.at3D.get(1, 3);
                double posZ = params.at3D.get(2, 3);
                params.at3D.translate(-posX, -posY, -posZ);
                params.at3D.scale(downsampleXYTransformField, downsampleXYTransformField, downsampleZTransformField);
                params.at3D.translate(posX, posY, posZ);
                model = new EmptySource(params);
            }
            if (pre_compute_transform) {
                IJ.log("Computing deformation fields, please wait...");
            }
            Map<File, RealTransform> alreadyOpenedTransforms = new HashMap<>();
            for (SourceAndConverter source: moving_sources) {
                File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(source);
                //QuPathEntryEntity movingEntity = QuPathBdvHelper.getQuPathEntityFromDerivedSource(source);
                int moving_series_index = QuPathBdvHelper.getEntryId(source);//movingEntity.getId();
                String movingToFixedLandmarkName = "transform_"+moving_series_index+"_"+fixed_series_index+".json";
                File result = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                if (!result.exists()) {
                    IJ.error("Registration file "+result.getAbsolutePath()+" not found");
                    return;
                }
                // Avoids computing two times the same transformation
                if (alreadyOpenedTransforms.containsKey(result)) {
                    sourceToTransformation.put(source, alreadyOpenedTransforms.get(result).copy());
                } else {
                    JsonReader reader = new JsonReader(new FileReader(result));
                    InvertibleRealTransformSequence irts = ScijavaGsonHelper.getGson(scijavaCtx).fromJson(reader, RealTransform.class);
                    RealTransform transformation = RealTransformHelper.getTransformSequence(irts).get(1);
                    if (pre_compute_transform) {
                        transformation = bdv.util.RealTransformHelper.resampleTransform(transformation, model);
                    }
                    sourceToTransformation.put(source, transformation);
                    alreadyOpenedTransforms.put(result, transformation);
                }
            }
            if (pre_compute_transform) {
                IJ.log("Computation done!");
            }


            List<SourceAndConverter> movingSacs = Arrays.stream(moving_sources).collect(Collectors.toList());


            List<SourceAndConverter> fixedSacs = Arrays.stream(fixed_sources).collect(Collectors.toList());

            List<SourceAndConverter> transformedSources = new ArrayList<>();

            Class<?> pixelType;
            pixelType = movingSacs.get(0).getSpimSource().getType().getClass();
            for (SourceAndConverter source: movingSacs) {
                if (!source.getSpimSource().getType().getClass().equals(pixelType)) {
                    IJ.log("ERROR - combining images with different pixel types is not supported: ");
                    IJ.log(movingSacs.get(0).getSpimSource().getName()+" pixel type = "+pixelType.getSimpleName());
                    IJ.log(source.getSpimSource().getName()+" pixel type = "+source.getSpimSource().getType().getClass().getSimpleName());
                    IJ.log("You may try to re-open your QuPath project with the option 'split RGB channel'");
                    return;
                }
                transformedSources.add(new SourceRealTransformer(sourceToTransformation.get(source).copy()).apply(source));
            }
            List<SourceAndConverter> exportedSources = new ArrayList<>();

            if (includeFixedSources) {
                exportedSources.addAll(fixedSacs);
                for (SourceAndConverter source: fixedSacs) {
                    if (!source.getSpimSource().getType().getClass().equals(pixelType)) {
                        IJ.log("ERROR - combining images with different pixel types is not supported: ");
                        IJ.log(movingSacs.get(0).getSpimSource().getName()+" pixel type = "+pixelType.getSimpleName());
                        IJ.log(source.getSpimSource().getName()+" pixel type = "+source.getSpimSource().getType().getClass().getSimpleName());
                        IJ.log("You may try to re-open your QuPath project with the option 'split RGB channel'");
                        return;
                    }
                }
            }
            for (SourceAndConverter source: transformedSources) {
                exportedSources.add(
                        new SourceResampler(source,
                                fixedSacs.get(0),
                                source.getSpimSource().getName()+"_Warpy",
                                false, false,
                                interpolate, 0).get());
            }
            scijavaCtx.getService(CommandService.class)
                    .run(KheopsExportSourcesCommand.class, true,
                            "sacs",  exportedSources.toArray(new SourceAndConverter[0]),
                            "range_channels", "",
                            "unit","millimeter",
                            "override_voxel_size",false,
                            "vox_size_xy",-1, //ignored
                            "vox_size_z",-1//ignored
                    ).get();

            IJ.log("Export warpy registered image done.");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void updateMessage() {

        String message = "<html><h1>Warpy registration exporter</h1>";

        if ((fixed_sources ==null)||(fixed_sources.length==0)) {
            message+="Please select a fixed source <br>";
        } else {
            if (!QuPathBdvHelper.isSourceLinkedToQuPath(fixed_sources[0])) {
                message+="The fixed source is not originating from a QuPath project! <br>";
            } else {
                if ((moving_sources == null)||(moving_sources.length==0)) {
                    message += "Please select at least one moving source <br>";
                } else {
                    for (SourceAndConverter testSource: moving_sources) {
                        if (!QuPathBdvHelper.isSourceLinkedToQuPath(testSource)) {
                            message += testSource.getSpimSource().getName()+" is not originating from a QuPath project! <br>";
                            message+="</html>";
                            this.message = message;
                            return;
                        } else {
                            try {
                                String qupathProjectMoving = QuPathBdvHelper.getProjectFile(testSource).getAbsolutePath();
                                String qupathProjectFixed = QuPathBdvHelper.getProjectFile(fixed_sources[0]).getAbsolutePath();
                                if (!qupathProjectMoving.equals(qupathProjectFixed)) {
                                    message += "Error : the moving source ("+testSource.getSpimSource().getName()+") and the fixed source are not from the same qupath project";
                                    message+="</html>";
                                    this.message = message;
                                    return;
                                } else {
                                    // - Are they different entries ?
                                    File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(testSource);
                                    File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_sources[0]);
                                    if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                                        message += "Error : moving and fixed source should belong to different qupath entries. <br>";
                                        message += "<ul>";
                                        message += "<li>Fixed: " + fixed_sources[0].getSpimSource().getName() + "</li>";
                                        message += "<li>Moving: " + testSource.getSpimSource().getName() + "</li>";
                                        message += "<ul>";
                                        message+="</html>";
                                        this.message = message;
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                message += "Could not fetch the QuPath project error: " + e.getMessage() + "<br>";
                                message+="</html>";
                                this.message = message;
                                return;
                            }
                        }
                    }
                }
            }
        }

        message += "Export task properly set: <br>";
        message+="</html>";
        this.message = message;

    }

}
