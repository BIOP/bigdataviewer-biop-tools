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
package ch.epfl.biop.command.workspace;

import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.FilterNode;
import sc.fiji.bdvpg.scijava.service.tree.SourceTreeModel;


@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.WorkspaceMenu, weight = BdvPgMenus.WorkspaceW),
                @Menu(label = "Tree", weight = 1),
                @Menu(label = "Tree - Filter By Name", weight = -4.5)
        },
        description = "Adds a node in the tree view which selects the sources based on their name")

public class FilterNodeNameAddCommand implements BdvPlaygroundActionCommand {

        @Parameter(label = "Filter Name",
                description = "Name for this filter node in the source tree view")
        String filter_name;

        @Parameter(label = "Name Contains",
                description = "Text pattern that source names must contain to match")
        String string_filter;

        @Parameter(label = "Match Case",
                description = "When checked, the name matching is case-sensitive")
        boolean match_case;

        @Parameter(label = "Show Sources",
                description = "When checked, matching sources are displayed; when unchecked, non-matching sources are shown")
        boolean show_sources;

        @Parameter
        SourceService source_service;

        @Override
        public void run() {
            FilterNode filterNode = new FilterNode(filter_name, (source) -> {
                if (match_case) {
                    return source.getSpimSource().getName().contains(string_filter);
                } else {
                    return source.getSpimSource().getName().toUpperCase().contains(string_filter.toUpperCase());
                }
            }, show_sources);
            SourceTreeModel model = source_service.tree().getSourceTreeModel();
            model.addNode(model.getRoot(), filterNode);
        }
}
