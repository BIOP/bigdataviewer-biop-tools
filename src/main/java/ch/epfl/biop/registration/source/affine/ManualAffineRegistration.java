package ch.epfl.biop.registration.source.affine;

import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import ch.epfl.biop.registration.plugin.RegistrationTypeProperties;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.plugin.Plugin;

/**
 * Interactive affine registration: the user moves the moving sources onto the fixed ones with a gizmo, in a
 * dedicated BigDataViewer window (see {@link AffineEditor}), and the resulting in-plane affine transform is stored
 * as the transform of this registration step.
 * <p>
 * This registration is editable: editing it reopens the same window with the transform already applied.
 */
@Plugin(type = IRegistrationPlugin.class)
@RegistrationTypeProperties(
        isManual = true,
        isEditable = true)
public class ManualAffineRegistration extends AffineTransformSourceRegistration {

    @Override
    public boolean register() {
        // Nothing has been defined yet: the moving sources are displayed as they are
        if (editInteractively(new AffineTransform3D(), "Manual Affine Registration")) return true;
        errorMessage = "Manual affine registration cancelled by the user.";
        return false;
    }

    @Override
    public void abort() {

    }

    String errorMessage = "Unspecified error";

    @Override
    public String getExceptionMessage() {
        return errorMessage;
    }

    String name = "Manual Affine";

    @Override
    public void setRegistrationName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

}
