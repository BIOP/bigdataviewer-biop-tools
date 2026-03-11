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
