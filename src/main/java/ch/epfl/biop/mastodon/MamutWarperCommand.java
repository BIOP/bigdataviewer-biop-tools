package ch.epfl.biop.mastodon;

import bdv.util.Elliptical3DTransform;
import ch.epfl.biop.bdv.img.imageplus.ImagePlusHelper;
import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettingsIO;
import fiji.plugin.trackmate.io.TmXmlWriter;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.plugin.frame.RoiManager;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.graph.Edge;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.model.tag.TagSetStructure;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MamutWarperCommand implements Command {

    @SuppressWarnings( "unused" )
    @Parameter
    private MamutPluginAppModel appModel;

    @Parameter(label = "Select the image plus projected image")
    ImagePlus image;

    @Parameter(label = "Select the transformation file")
    File elliptical_transform_file;

    @Parameter(label = "Trackmate file to save", style = "save")
    File tm_file;

    @Parameter(label = "Output cell size (pixel)")
    double cell_size;

    @Parameter
    RoiManager roiManager;

    @Parameter(label="Create a combined tag, for instance 'A,Somite_6'")
    String combine_create_tag = "";

    @Parameter(type = ItemIO.OUTPUT)
    fiji.plugin.trackmate.Model tm_model;

    @Parameter
    Context context;

    @Override
    public void run() {

        //AffineTransform3D matrix = ImagePlusHelper.getMatrixFromImagePlus(image);
        //System.out.println("Not projected matrix = "+matrix);
        AffineTransform3D proj_matrix = ImagePlusHelper.getMatrixFromImagePlus(image);
        double angle_inc = proj_matrix.get(0,0);
        double phi_0 = -proj_matrix.get(0,3);
        double theta_0 = -proj_matrix.get(1,3);
        double r_inc = -proj_matrix.get(2,2);
        double r_0 = proj_matrix.get(2,3);

        double[] matrix_double = {0,0, r_inc, r_0, 0, angle_inc, 0, theta_0, angle_inc,0,0, phi_0};
        proj_matrix.set(matrix_double);

        System.out.println("Projected matrix = "+proj_matrix);

        String tag1 = null;
        String tag2 = null;
        if ((combine_create_tag!=null)&&(combine_create_tag!="")&&(combine_create_tag.split(",").length==2)) {
            tag1 = combine_create_tag.split(",")[0].trim();
            tag2 = combine_create_tag.split(",")[1].trim();
            IJ.log("Creating combined tag tag_"+tag1+" and "+tag2);
        } else {
            IJ.log("Do not create combined tags");
        }


        try {
            roiManager.reset();
            Elliptical3DTransform e3Dt = ScijavaGsonHelper.getGson(context).fromJson(new FileReader(elliptical_transform_file), Elliptical3DTransform.class);//getEllipticalTransformFromImagePlus(image);

            MamutAppModel am = appModel.getAppModel();
            Model model = am.getModel();
            ReentrantReadWriteLock.WriteLock lock = model.getGraph().getLock().writeLock();

            // For TrackMate export:

            tm_model = new fiji.plugin.trackmate.Model();
            tm_model.setLogger(Logger.IJ_LOGGER);
            Settings settings = new Settings(image);
            settings.addAllAnalyzers();
            tm_model.beginUpdate();



            List<TagSetStructure.Tag> allTags = new ArrayList<>();
            List<String> allTagsString = new ArrayList<>();
            Map<Integer, List<TagSetStructure.Tag>> idToTags = new HashMap<>(); // Stores all the model 'easily'

            try {
                lock.lock();
                RealPoint spotLocation = new RealPoint(3);


                Map<Integer, fiji.plugin.trackmate.Spot> mastodonToTM = new HashMap<>();

                model.getTagSetModel().getTagSetStructure().getTagSets().forEach(ts -> {
                    ts.getTags().forEach(tag -> {
                        allTags.add(tag);
                        allTagsString.add(tag.label());
                    });
                });

                if (tag1!=null) {
                    allTagsString.add(tag1+"_"+tag2);
                }

                declareFeatures(tm_model, allTagsString);


                allTags.forEach(tag -> {
                    model.getTagSetModel()
                            .getVertexTags()
                            .getTaggedWith(tag)
                            .forEach(spot -> {
                                int idx = spot.getInternalPoolIndex();
                                if (!idToTags.containsKey(spot.getInternalPoolIndex())) {
                                    idToTags.put(idx, new ArrayList<>());
                                }
                                idToTags.get(idx).add(tag);
                            });
                });



                // sauve features
                for (Spot spot : model.getGraph().vertices()) {
                    spot.localize(spotLocation);

                    int t = spot.getTimepoint();

                    e3Dt.inverse().apply(spotLocation, spotLocation); // Physical to r, theta, phi

                    // The source coordinate (r,θ,φ) is converted to the cartesian target coordinate (x,y,z)
                    proj_matrix.inverse().apply(spotLocation, spotLocation);
                    double x = spotLocation.getDoublePosition(0);
                    double y = spotLocation.getDoublePosition(1);
                    double z = spotLocation.getDoublePosition(2);

                    OvalRoi ovalRoi = new OvalRoi(x - cell_size / 2, y - cell_size / 2, cell_size, cell_size);

                    ovalRoi.setImage(image);
                    ovalRoi.setPosition(1, (int) (Math.round(z) + 1), t + 1);

                    fiji.plugin.trackmate.Spot tm_spot = new fiji.plugin.trackmate.Spot(x * image.getCalibration().pixelHeight, y * image.getCalibration().pixelWidth, z * image.getCalibration().pixelDepth * 0, cell_size * image.getCalibration().pixelHeight, -1);

                    allTags.forEach(tag -> tm_spot.putFeature("tag_"+tag.label(), 0.0));

                    if (idToTags.containsKey(spot.getInternalPoolIndex())) {
                        idToTags.get(spot.getInternalPoolIndex())
                                .forEach(tag -> {
                                    tm_spot.putFeature("tag_"+tag.label(), 1.0);
                                });
                    }

                    if (tag1!=null) {
                        if (idToTags.containsKey(spot.getInternalPoolIndex())) {
                            List<TagSetStructure.Tag> tags = idToTags.get(spot.getInternalPoolIndex());
                            Set<String> stringTags = tags.stream().map(tag -> tag.label()).collect(Collectors.toSet());
                            if ((stringTags.contains(tag1))&&(stringTags.contains(tag2))) {
                                tm_spot.putFeature("tag_"+tag1+"_"+tag2, 1.0);
                            } else {
                                tm_spot.putFeature("tag_"+tag1+"_"+tag2, 0.0);
                            }
                        } else {
                            tm_spot.putFeature("tag_"+tag1+"_"+tag2, 0.0);
                        }
                    }

                    tm_model.addSpotTo(tm_spot, t);

                    mastodonToTM.put(spot.getInternalPoolIndex(), tm_spot);
                    ovalRoi.setName(spot.getLabel() + "_T" + t);
                    roiManager.addRoi(ovalRoi);

                }

                for (Edge edge : model.getGraph().edges()) {
                    int source_idx = ((Spot) edge.getSource()).getInternalPoolIndex();
                    int target_idx = ((Spot) edge.getTarget()).getInternalPoolIndex();

                    if (mastodonToTM.containsKey(source_idx) && mastodonToTM.containsKey(target_idx)) {
                        tm_model.addEdge(mastodonToTM.get(source_idx), mastodonToTM.get(target_idx), -1);
                    } else {
                        IJ.log("Missing source or target");
                    }
                }

                tm_model.notifyFeaturesComputed();

                // model.getFeatureModel().getFeatureSpecs() // features spec to add -> look at how this is  - declared and discovered manually
                // restore features
                model.getGraph().notifyGraphChanged();
                model.setUndoPoint();

                // Trackmate output
                tm_model.endUpdate();
                SelectionModel tm_sm = new SelectionModel(tm_model);
                DisplaySettings ds = DisplaySettingsIO.readUserDefault();
                ds.setTrackColorBy(DisplaySettings.TrackMateObject.TRACKS, "TRACK_INDEX");
                ds.setSpotColorBy(DisplaySettings.TrackMateObject.TRACKS, "TRACK_INDEX");
                ds.setTrackDisplayMode(DisplaySettings.TrackDisplayMode.LOCAL_BACKWARD);
                ds.setLineThickness(3.);

                TmXmlWriter writer = new TmXmlWriter(tm_file);
                writer.appendDisplaySettings(ds);
                writer.appendModel(tm_model);
                writer.appendSettings(settings);
                writer.writeToFile();

                HyperStackDisplayer view = new HyperStackDisplayer(tm_model, tm_sm, image, ds);
                view.render();


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void declareFeatures(fiji.plugin.trackmate.Model tmModel, List<String> allTags) {
        List<String> features = new ArrayList<>();
        Map<String, String> featureNames = new HashMap<>();
        Map<String, String> featureShortNames = new HashMap<>();
        Map<String, Dimension> featureDimensions = new HashMap<>();
        Map<String, Boolean> isIntFeature = new HashMap<>();

        allTags.forEach(tag -> {
            System.out.println("Declaring feature "+tag);
            features.add("tag_"+tag);
            featureNames.put("tag_"+tag, "tag_"+tag);
            featureShortNames.put("tag_"+tag, "tag_"+tag);
            featureDimensions.put("tag_"+tag, Dimension.NONE);
            isIntFeature.put("tag_"+tag, Boolean.TRUE);
        });


        tmModel.getFeatureModel()
                .declareSpotFeatures(features, featureNames, featureShortNames, featureDimensions, isIntFeature);
    }

    private static Elliptical3DTransform getEllipticalTransformFromImagePlus(ImagePlus image) throws UnsupportedOperationException {

        Elliptical3DTransform e3Dt = new Elliptical3DTransform();

        if (image.getInfoProperty() == null) throw new UnsupportedOperationException("No elliptic transform found in image info");

        if (image.getInfoProperty() != null) {

            Pattern pattern = Pattern.compile("(\"ellipse_params\":)((?s).*)(})");
            Matcher matcher = pattern.matcher(image.getInfoProperty());
            if (matcher.find()) {

                String ellipticalTransformString = matcher.group(2).replace("{","").replace("}","");
                Map<String, Double> parameters = new HashMap<>();
                String[] linesParam = ellipticalTransformString.split(",");

                Set<String> params = new HashSet<>();

                params.addAll(e3Dt.getParameters().keySet());
                for (String line: linesParam) {
                    String[] args = line.split(":");
                    if (args.length!=2) {
                        throw new UnsupportedOperationException("Error in parsing elliptical transform. Invalid number of args in line "+line);
                    }
                    Double value = Double.parseDouble(args[1]);
                    Optional<String> key = params.stream().filter(k -> args[0].contains(k)).findFirst();
                    if (key.isPresent()) {
                        parameters.put(key.get(), value);
                    } else {
                        throw new UnsupportedOperationException("No matching elliptical parameter found with line "+args[0]);
                    }
                }
                e3Dt.setParameters(parameters);
            } else {
                throw new UnsupportedOperationException("No elliptic transform found in image info");
            }
        }

        return e3Dt;

    }

    private static void covarianceFromRadiusSquared( final double rsqu, final double[][] cov )
    {
        for( int row = 0; row < 3; ++row )
            for( int col = 0; col < 3; ++col )
                cov[ row ][ col ] = ( row == col ) ? rsqu : 0;
    }

    /*

    What's inside info:

    3d-affine: (2.1374118819131E-19, 0.0, -0.15, 1.0764, 0.0, 0.003490658503988659, 0.0, 1.1334866294151975, 0.003490658503988659, 0.0, 9.184850993605149E-18, -2.596212168926605)
    TimePoint: (0)

    {
      "name": "Elliptical3DTransform_569465546",
      "ellipse_params": {
        "radiusX": 407.3802778041126,
        "radiusY": 354.8133892335754,
        "radiusZ": 380.1893963205612,
        "rotationX": 1.6830276814529452,
        "rotationY": 0.010000000000000002,
        "rotationZ": -0.10232929922807542,
        "centerX": 370.83545190163085,
        "centerY": 370.83545190163085,
        "centerZ": 370.83545190163085
      }
    }
ellipse_params \{(.*)\}
    The regex to get the transform back:

    ("ellipse_params":)((?s).*)(})

     */
}
