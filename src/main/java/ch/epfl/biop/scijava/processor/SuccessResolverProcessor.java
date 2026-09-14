/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
