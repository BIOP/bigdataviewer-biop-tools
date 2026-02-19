package ch.epfl.biop.scijava.command.source;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceService;
import sc.fiji.bdvpg.scijava.services.tree.FilterNode;
import sc.fiji.bdvpg.scijava.services.tree.SourceTreeModel;


@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Filter Sources Based on Name",
        description = "Adds a node in the tree view which selects the sources based on their name")

public class FilterSourcesByNameCommand implements BdvPlaygroundActionCommand {

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
            FilterNode filterNode = new FilterNode(filter_name, (sac) -> {
                if (match_case) {
                    return sac.getSpimSource().getName().contains(string_filter);
                } else {
                    return sac.getSpimSource().getName().toUpperCase().contains(string_filter.toUpperCase());
                }
            }, show_sources);
            SourceTreeModel model = source_service.tree().getSourceTreeModel();
            model.addNode(model.getRoot(), filterNode);
        }
}
