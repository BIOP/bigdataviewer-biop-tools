package ch.epfl.biop.scijava.processor;

import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.ModuleItem;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Scijava postprocessor that looks for a boolean output named {@code success}.
 * If present and {@code true}, the output is resolved so the standard display
 * postprocessors do not render it. If {@code false}, an error is logged with
 * the module name (no further detail is available at this layer).
 * <p>
 * Runs before the default display postprocessor (priority {@code 0.1} &gt; default {@code 0}).
 */
@Plugin(type = PostprocessorPlugin.class, priority = 0.1)
public class SuccessResolverProcessor extends AbstractPostprocessorPlugin {

    private static final String SUCCESS_OUTPUT_NAME = "success";

    @Parameter
    LogService logger;

    @Override
    public void process(Module module) {

        if (module.getInfo() == null) {
            logger.warn("null getInfo for module " + module);
            return;
        }

        ModuleItem<?> outputKind = module.getInfo().getOutput(SUCCESS_OUTPUT_NAME);
        if (outputKind == null) return;

        Class<?> type = outputKind.getType();
        if (!type.equals(Boolean.class) && !type.equals(boolean.class)) return;

        Object output = module.getOutput(SUCCESS_OUTPUT_NAME);
        if (!(output instanceof Boolean)) return;

        boolean success = (Boolean) output;
        if (success) {
            module.resolveOutput(SUCCESS_OUTPUT_NAME);
        } else {
            logger.error("Module " + module.getInfo().getTitle() + " did not complete successfully.");
            module.resolveOutput(SUCCESS_OUTPUT_NAME);
        }
    }
}