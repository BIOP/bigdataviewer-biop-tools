package ch.epfl.biop.scijava.command.source.register;

import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.ExportToOMETiffBuildPyramidCommand;
import ch.epfl.biop.bdv.bioformats.export.ometiff.OMETiffPyramidizerExporter;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
import com.google.gson.stream.JsonReader;
import ij.IJ;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
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

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>QuPath - Export Warpy Registered Image")
public class WarpyExportRegisteredImageCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String message = "<html><h1>QuPath registration exporter</h1>Please select a moving and a fixed source<br></html>";

    @Parameter(label = "Fixed source", callback = "updateMessage", style ="sorted")
    SourceAndConverter[] fixed_source;

    @Parameter(label = "Moving sources", callback = "updateMessage", style ="sorted")
    SourceAndConverter[] moving_source;

    @Parameter(label = "Include fixed sources in exported image")
    boolean includeFixedSources;

    @Parameter(label = "Interpolate pixels values")
    boolean interpolate;

    @Parameter
    Context scijavaCtx;

    @Override
    public void run() {
        try {
            QuPathEntryEntity fixedEntity = QuPathBdvHelper.getQuPathEntityFromSource(fixed_source[0]);
            int fixed_series_index = fixedEntity.getId();
            Map<SourceAndConverter, RealTransform> sourceToTransformation =new HashMap<>();

            for (SourceAndConverter source: moving_source) {
                File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(source);
                QuPathEntryEntity movingEntity = QuPathBdvHelper.getQuPathEntityFromSource(source);
                int moving_series_index = movingEntity.getId();
                String movingToFixedLandmarkName = "transform_"+moving_series_index+"_"+fixed_series_index+".json";
                File result = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                if (!result.exists()) {
                    IJ.error("Registration file "+result.getAbsolutePath()+" not found");
                    return;
                }
                JsonReader reader = new JsonReader(new FileReader(result));
                InvertibleRealTransformSequence irts = ScijavaGsonHelper.getGson(scijavaCtx).fromJson(reader, RealTransform.class);
                RealTransform transformation = RealTransformHelper.getTransformSequence(irts).get(1);
                sourceToTransformation.put(source, transformation);
            }

            List<SourceAndConverter> movingSacs = Arrays.stream(moving_source).collect(Collectors.toList());

            List<SourceAndConverter> fixedSacs = Arrays.stream(fixed_source).collect(Collectors.toList());

            List<SourceAndConverter> transformedSources = new ArrayList<>();

            for (SourceAndConverter source: movingSacs) {
                transformedSources.add(new SourceRealTransformer(sourceToTransformation.get(source).copy()).apply(source));
            }

            List<SourceAndConverter> exportedSources = new ArrayList<>();

            if (includeFixedSources) {
                exportedSources.addAll(fixedSacs);
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
                    .run(ExportToOMETiffBuildPyramidCommand.class, true,
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

        if ((fixed_source==null)||(fixed_source.length==0)) {
            message+="Please select a fixed source <br>";
        } else {
            if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(fixed_source[0])) {
                message+="The fixed source is not originating from a QuPath project! <br>";
            } else {
                if ((moving_source == null)||(moving_source.length==0)) {
                    message += "Please select at least one moving source <br>";
                } else {
                    for (SourceAndConverter testSource:moving_source) {
                        if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(testSource)) {
                            message += testSource.getSpimSource().getName()+" is not originating from a QuPath project! <br>";
                            message+="</html>";
                            this.message = message;
                            return;
                        } else {
                            try {
                                String qupathProjectMoving = QuPathBdvHelper.getQuPathProjectFile(testSource).getAbsolutePath();
                                String qupathProjectFixed = QuPathBdvHelper.getQuPathProjectFile(fixed_source[0]).getAbsolutePath();
                                if (!qupathProjectMoving.equals(qupathProjectFixed)) {
                                    message += "Error : the moving source ("+testSource.getSpimSource().getName()+") and the fixed source are not from the same qupath project";
                                    message+="</html>";
                                    this.message = message;
                                    return;
                                } else {
                                    // - Are they different entries ?
                                    File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(testSource);
                                    File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_source[0]);
                                    if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                                        message += "Error : moving and fixed source should belong to different qupath entries. <br>";
                                        message += "<ul>";
                                        message += "<li>Fixed: " + fixed_source[0].getSpimSource().getName() + "</li>";
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
