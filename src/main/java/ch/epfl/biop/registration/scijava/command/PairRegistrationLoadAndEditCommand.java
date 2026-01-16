package ch.epfl.biop.registration.scijava.command;

import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.RegistrationPair;
import ch.epfl.biop.registration.sourceandconverter.affine.AffineRegistration;
import ch.epfl.biop.registration.sourceandconverter.spline.PrecomputedSplineRegistration;
import ch.epfl.biop.sourceandconverter.processor.SourcesIdentity;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import ij.IJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.imglib2.realtransform.RealTransformHelper.getTransformSequence;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Load And Edit An Existing Registration",
        description = "Reconstructs a registration pair sequence from an existing QuPath registration file")
public class PairRegistrationLoadAndEditCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String message = "<html><h1>Load QuPath registration</h1>Please select moving and fixed sources<br></html>";

    @Parameter(label = "Fixed Source(s)",
            callback = "updateMessage",
            description = "Reference source(s) from the QuPath project")
    SourceAndConverter<?>[] fixed_sources;

    @Parameter(label = "Moving Source(s)",
            callback = "updateMessage",
            description = "Source(s) that were registered to the fixed reference")
    SourceAndConverter<?>[] moving_sources;

    @Parameter(label = "Registration Name",
            description = "Name for the reconstructed registration pair")
    String registration_name = "Loaded Registration";

    @Parameter(label = "Remove Z Offsets",
            description = "When checked, removes Z position offsets from sources")
    boolean remove_z_offsets = true;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Registration Pair",
            description = "The reconstructed registration pair object")
    RegistrationPair registration_pair;

    @Parameter
    Context scijavaCtx;

    @Parameter
    ObjectService objectService;

    @Parameter
    CommandService commandService;

    @Parameter(label = "Open GUI",
            description = "When checked, automatically opens the registration GUI after loading")
    boolean open_gui = true;

    @Override
    public void run() {
        try {
            // Validate QuPath sources
            if (!QuPathBdvHelper.isSourceLinkedToQuPath(fixed_sources[0])) {
                IJ.error("The fixed source is not originating from a QuPath project!");
                return;
            }

            if (!QuPathBdvHelper.isSourceLinkedToQuPath(moving_sources[0])) {
                IJ.error("The moving source is not originating from a QuPath project!");
                return;
            }

            File movingProjectFile = QuPathBdvHelper.getProjectFile(moving_sources[0]);
            File fixedProjectFile = QuPathBdvHelper.getProjectFile(fixed_sources[0]);

            if (!movingProjectFile.equals(fixedProjectFile)) {
                IJ.error("Moving and fixed sources do not belong to the same QuPath project.");
                return;
            }

            File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(moving_sources[0]);
            File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_sources[0]);

            if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                IJ.error("Moving and fixed sources should belong to different QuPath entries.");
                return;
            }

            int moving_series_index = QuPathBdvHelper.getEntryId(moving_sources[0]);
            int fixed_series_index = QuPathBdvHelper.getEntryId(fixed_sources[0]);

            String movingToFixedLandmarkName = "transform_"+moving_series_index+"_"+fixed_series_index+".json";

            File registrationFile = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);

            if (!registrationFile.exists()) {
                IJ.error("Registration file not found: " + registrationFile.getAbsolutePath());
                return;
            }

            // Check if registration pair already exists
            List<RegistrationPair> allRegistrations = objectService.getObjects(RegistrationPair.class);
            if (allRegistrations.stream().anyMatch(rps -> rps.getName().equals(registration_name))) {
                IJ.error("A registration sequence named "+registration_name+" already exists! Please name it differently or delete the existing one.");
                return;
            }

            // Load and deserialize the transform sequence
            JsonReader reader = new JsonReader(new FileReader(registrationFile));
            InvertibleRealTransformSequence irts = ScijavaGsonHelper.getGson(scijavaCtx).fromJson(reader, RealTransform.class);

            List<InvertibleRealTransform> transforms = getTransformSequence(irts);

            System.out.println("=== DEBUG: Transform Structure ===");
            System.out.println("Total transforms in sequence: " + transforms.size());
            for (int i = 0; i < transforms.size(); i++) {
                InvertibleRealTransform t = transforms.get(i);
                System.out.println("Transform " + i + ": " + t.getClass().getName());
                System.out.println("  Simple name: " + t.getClass().getSimpleName());

                // Check if it's a nested sequence
                if (t instanceof InvertibleRealTransformSequence) {
                    InvertibleRealTransformSequence nested = (InvertibleRealTransformSequence) t;
                    List<InvertibleRealTransform> nestedTransforms = getTransformSequence(nested);
                    System.out.println("  -> Nested sequence with " + nestedTransforms.size() + " transforms:");
                    for (int j = 0; j < nestedTransforms.size(); j++) {
                        System.out.println("     Transform " + j + ": " + nestedTransforms.get(j).getClass().getSimpleName());
                    }
                }
            }
            System.out.println("=================================");

            if (transforms.size() < 3) {
                IJ.error("Invalid transform sequence: expected at least 3 transforms (coordinate conversions + registration)");
                return;
            }

            // Extract inner transforms (skip first and last which are coordinate conversions)
            // The middle element should be an InvertibleRealTransformSequence containing all registration steps
            List<InvertibleRealTransform> registrationTransforms;

            if (transforms.size() == 3 && transforms.get(1) instanceof InvertibleRealTransformSequence) {
                // Nested structure: affine1, sequence, affine2
                InvertibleRealTransformSequence middleSequence = (InvertibleRealTransformSequence) transforms.get(1);
                registrationTransforms = getTransformSequence(middleSequence);
                System.out.println("DEBUG: Found nested sequence with " + registrationTransforms.size() + " registration steps");
            } else {
                // Flat structure: affine1, reg1, reg2, ..., affine2
                registrationTransforms = transforms.subList(1, transforms.size() - 1);
                System.out.println("DEBUG: Found flat sequence with " + registrationTransforms.size() + " registration steps");
            }

            if (registrationTransforms.isEmpty()) {
                IJ.error("No registration transforms found in the file");
                return;
            }

            // Create the registration pair
            registration_pair = new RegistrationPair(fixed_sources, 0, moving_sources, 0, registration_name, remove_z_offsets);
            objectService.addObject(registration_pair);

            // Reconstruct each registration step
            int stepNumber = 1;

            Collections.reverse(registrationTransforms);
            for (InvertibleRealTransform transform : registrationTransforms) {
                System.out.println("DEBUG: Processing step " + stepNumber);
                System.out.println("  Transform class: " + transform.getClass().getSimpleName());

                RegistrationStepInfo stepInfo = createRegistrationFromTransform(transform, stepNumber);

                if (stepInfo == null) {
                    IJ.error("Unsupported transform type at step " + stepNumber + ": " + transform.getClass().getName());
                    // Clean up
                    objectService.removeObject(registration_pair);
                    registration_pair = null;
                    return;
                }

                System.out.println("  Registration type: " + stepInfo.registration.getClass().getSimpleName());
                System.out.println("  Registration name: " + stepInfo.registration.toString());

                stepInfo.registration.setScijavaContext(scijavaCtx);
                stepInfo.registration.setTimePoint(0);

                boolean success = registration_pair.executeRegistration(
                    stepInfo.registration,
                    stepInfo.parameters,
                    new SourcesIdentity(),
                    new SourcesIdentity()
                );

                if (!success) {
                    IJ.error("Failed to apply registration step " + stepNumber + ": " + registration_pair.getLastErrorMessage());
                    // Clean up
                    objectService.removeObject(registration_pair);
                    registration_pair = null;
                    return;
                }

                System.out.println("  Step " + stepNumber + " executed successfully");

                stepNumber++;
            }

            IJ.log("Successfully loaded registration from QuPath:");
            IJ.log("  Fixed: " + fixed_sources[0].getSpimSource().getName());
            IJ.log("  Moving: " + moving_sources[0].getSpimSource().getName());
            IJ.log("  Steps reconstructed: " + registrationTransforms.size());

            // Automatically open the GUI if requested
            if (open_gui) {
                commandService.run(PairRegistrationAddGUICommand.class, true,
                        "registration_pair", registration_pair);
            }

        } catch (Exception e) {
            IJ.error("Error loading registration: " + e.getMessage());
            e.printStackTrace();
            if (registration_pair != null) {
                objectService.removeObject(registration_pair);
                registration_pair = null;
            }
        }
    }

    private static class RegistrationStepInfo {
        Registration<SourceAndConverter<?>[]> registration;
        Map<String, String> parameters;

        RegistrationStepInfo(Registration<SourceAndConverter<?>[]> registration, Map<String, String> parameters) {
            this.registration = registration;
            this.parameters = parameters;
        }
    }

    private RegistrationStepInfo createRegistrationFromTransform(InvertibleRealTransform transform, int stepNumber) {
        // Unwrap the transform to determine its type
        RealTransform innerTransform = unwrapTransform(transform);
        System.out.println("    Unwrapped to: " + innerTransform.getClass().getSimpleName());

        Map<String, String> parameters = new HashMap<>();

        if (innerTransform instanceof AffineTransform3D) {
            // It's an affine transformation
            System.out.println("    Detected as: AFFINE");
            AffineRegistration registration = new AffineRegistration();
            registration.setRegistrationName("Affine Transform #" + stepNumber);

            // Serialize the transform and put it in parameters
            //String serializedTransform = ScijavaGsonHelper.getGson(scijavaCtx).toJson(transform, RealTransform.class);
            parameters.put(AffineRegistration.TRANSFORM_KEY, new Gson().toJson(((AffineTransform3D) ((AffineTransform3D) innerTransform).inverse()).getRowPackedCopy()));

            return new RegistrationStepInfo(registration, parameters);
        } else {
            // Assume it's a spline/deformable transformation
            System.out.println("    Detected as: SPLINE");
            // Create a generic spline registration that accepts a pre-computed transform
            PrecomputedSplineRegistration registration = new PrecomputedSplineRegistration();
            registration.setRegistrationName("Spline Transform #" + stepNumber);

            // Serialize the transform and store it
            String serializedTransform = ScijavaGsonHelper.getGson(scijavaCtx).toJson(transform, RealTransform.class);
            parameters.put("transform", serializedTransform);

            return new RegistrationStepInfo(registration, parameters);
        }
    }

    private RealTransform unwrapTransform(RealTransform transform) {
        // Unwrap common wrapper types to get to the actual transform
        RealTransform current = transform;
        System.out.println("      Unwrapping: " + current.getClass().getSimpleName());

        // Check for InvertibleWrapped2DTransformAs3D
        if (current.getClass().getSimpleName().equals("InvertibleWrapped2DTransformAs3D")) {
            try {
                java.lang.reflect.Method getTransform = current.getClass().getMethod("getTransform");
                current = (RealTransform) getTransform.invoke(current);
                System.out.println("      -> Unwrapped InvertibleWrapped2DTransformAs3D to: " + current.getClass().getSimpleName());
            } catch (Exception e) {
                // If reflection fails, return as-is
                System.out.println("      -> Failed to unwrap InvertibleWrapped2DTransformAs3D");
                return current;
            }
        }

        // Check for WrappedIterativeInvertibleRealTransform
        if (current.getClass().getSimpleName().equals("WrappedIterativeInvertibleRealTransform")) {
            try {
                java.lang.reflect.Method getTransform = current.getClass().getMethod("getTransform");
                current = (RealTransform) getTransform.invoke(current);
                System.out.println("      -> Unwrapped WrappedIterativeInvertibleRealTransform to: " + current.getClass().getSimpleName());
            } catch (Exception e) {
                // If reflection fails, return as-is
                System.out.println("      -> Failed to unwrap WrappedIterativeInvertibleRealTransform");
                return current;
            }
        }

        return current;
    }

    public void updateMessage() {
        String message = "<html><h1>Load QuPath registration</h1>";

        if (fixed_sources == null) {
            message += "Please select a fixed source <br>";
        } else {
            if (!QuPathBdvHelper.isSourceLinkedToQuPath(fixed_sources[0])) {
                message += "The fixed source is not originating from a QuPath project! <br>";
            } else {
                if (moving_sources == null) {
                    message += "Please select a moving source <br>";
                } else {
                    if (!QuPathBdvHelper.isSourceLinkedToQuPath(moving_sources[0])) {
                        message += "The moving source is not originating from a QuPath project! <br>";
                    } else {
                        try {
                            String qupathProjectMoving = QuPathBdvHelper.getProjectFile(moving_sources[0]).getAbsolutePath();
                            String qupathProjectFixed = QuPathBdvHelper.getProjectFile(fixed_sources[0]).getAbsolutePath();
                            if (!qupathProjectMoving.equals(qupathProjectFixed)) {
                                message += "Error: the moving source and the fixed source are not from the same QuPath project";
                            } else {
                                File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(moving_sources[0]);
                                File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_sources[0]);
                                if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                                    message += "Error: moving and fixed source should belong to different QuPath entries. <br>";
                                    message += "You can't move two channels of the same image. <br>";
                                    message += "<ul>";
                                    message += "<li>Fixed: " + fixed_sources[0].getSpimSource().getName() + "</li>";
                                    message += "<li>Moving: " + moving_sources[0].getSpimSource().getName() + "</li>";
                                    message += "</ul>";
                                } else {
                                    message += "Registration load task properly set: <br>";
                                    message += "<ul>";
                                    message += "<li>Fixed: " + fixed_sources[0].getSpimSource().getName() + "</li>";
                                    message += "<li>Moving: " + moving_sources[0].getSpimSource().getName() + "</li>";
                                    message += "</ul>";

                                    int moving_series_index = QuPathBdvHelper.getEntryId(moving_sources[0]);
                                    int fixed_series_index = QuPathBdvHelper.getEntryId(fixed_sources[0]);

                                    String movingToFixedLandmarkName = "transform_" + moving_series_index + "_" + fixed_series_index + ".json";

                                    File result = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                                    if (!result.exists()) {
                                        message += "WARNING! REGISTRATION FILE NOT FOUND!<br>";
                                    } else {
                                        message += "Registration file found: ready to load.<br>";
                                    }

                                    movingToFixedLandmarkName = "transform_" + fixed_series_index + "_" + moving_series_index + ".json";

                                    result = new File(fixed_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                                    if (result.exists()) {
                                        message += "WARNING! AN <b>INVERSE</b> REGISTRATION FILE ALSO EXISTS! <br>";
                                        message += "Switch your fixed and moving selected source to load it instead!<br>";
                                    }
                                }
                            }
                        } catch (Exception e) {
                            message += "Could not fetch the QuPath project error: " + e.getMessage() + "<br>";
                        }
                    }
                }
            }
        }

        message += "</html>";
        this.message = message;
    }
}
