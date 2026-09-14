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

import org.scijava.ItemVisibility;
import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.ModuleItem;
import org.scijava.module.process.AbstractPreprocessorPlugin;
import org.scijava.module.process.PreprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.InputHarvester;

/**
 * Scijava processor that resolves all inputs with MESSAGE {@link ItemVisibility},
 * if they are the only ones remaining, before the InputHarvester kicks in
 */
@Plugin(type = PreprocessorPlugin.class, priority = InputHarvester.PRIORITY+1) // We want it to kick in before the swing input harvester
public class MessageResolverProcessor extends AbstractPreprocessorPlugin {

    int unresolvedInputsExceptMessageCount = 0;
    int messagesCount = 0;
    int buttonCount = 0;

    @Parameter
    LogService logger;

    @Override
    public void process(Module module) {

        if (module.getInfo()==null) {
            logger.warn("null getInfo for module "+module);
            return;
        }

        module.getInputs().forEach((name, input) -> {
            ModuleItem<?> inputKind = module.getInfo().getInput(name);
            if (inputKind == null) {
                logger.warn("null input "+name+" for module "+module);
                return; // avoid doing anything
            }

            ItemVisibility visibility = inputKind.getVisibility();

            if (visibility==null) {
                logger.warn("null visibility for input "+name+" for module "+module);
                return; // avoid doing anything
            }

            if (visibility.equals(ItemVisibility.MESSAGE)) {
                messagesCount++;
            } else {
                if (!module.isInputResolved(name)) unresolvedInputsExceptMessageCount++;
            }
        });

        module.getInputs().forEach((name, input) -> {
            ModuleItem<?> inputKind = module.getInfo().getInput(name);
            if (inputKind == null) {
                logger.warn("null input "+name+" for module "+module);
                return; // avoid doing anything
            }
            if (inputKind.getType().equals(Button.class)) buttonCount++;
        });

        if (messagesCount > 0) {
            if ((unresolvedInputsExceptMessageCount == 0)||(unresolvedInputsExceptMessageCount == buttonCount)) {
                // No need for null check, it's been done before
                module.getInputs().forEach((name, input) -> {
                    if (module.getInfo().getInput(name).getVisibility().equals(ItemVisibility.MESSAGE)||(module.getInfo().getInput(name).getType().equals(Button.class))) {
                        logger.debug("Resolving parameter "+name+" in module "+module+" in MessageResolverProcessor.");
                        module.resolveInput(name);
                    }
                });
            }
        }

    }
}
