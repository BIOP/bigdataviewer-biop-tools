package ch.epfl.biop.registration.plugin;

import ch.epfl.biop.registration.Registration;

// Facilitates accessing annotation values
public class RegistrationPluginHelper {

    /**
     * Does the registration required an user input ?
     * @param reg the registration to check
     * @return true if user input is required
     */
    public static boolean isManual(Registration<?> reg) {
        if (reg.getClass().isAnnotationPresent(RegistrationTypeProperties.class)) {
            final RegistrationTypeProperties annotation = reg.getClass()
                    .getAnnotation(RegistrationTypeProperties.class);
            return annotation.isManual();
        } else {
            if (reg instanceof ExternalRegistrationPlugin) {
                return ((ExternalRegistrationPlugin) reg).isManual();
            } else {
                return false; // Default value if no annotation is present
            }
        }
    }

    /**
     * Can the registration be edited after it has run ?
     * Considered a manual task by default
     * @param reg the registration to check
     * @return true if the registration can be edited after execution
     */
    public static boolean isEditable(Registration<?> reg) {
        if (reg.getClass().isAnnotationPresent(RegistrationTypeProperties.class)) {
            final RegistrationTypeProperties annotation = reg.getClass()
                    .getAnnotation(RegistrationTypeProperties.class);
            return annotation.isEditable();
        } else {
            if (reg instanceof ExternalRegistrationPlugin) {
                return ((ExternalRegistrationPlugin) reg).isEditable();
            } else {
                return false; // Default value if no annotation is present
            }
        }
    }
}
