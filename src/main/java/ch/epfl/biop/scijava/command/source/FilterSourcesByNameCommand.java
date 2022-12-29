package ch.epfl.biop.scijava.command.source;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceFilterNode;


@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Filter Sources Based on Name",
        description = "Adds a node in the tree view which selects the sources based on their name")

public class FilterSourcesByNameCommand implements BdvPlaygroundActionCommand {

        @Parameter(label = "Filter name")
        String filter_name;

        @Parameter(label = "The source name should contain:")
        String string_filter;

        @Parameter(label = "Match case")
        boolean match_case;

        @Parameter(label = "Show sources")
        boolean show_sources;

        @Parameter
        SourceAndConverterService sac_service;

        @Override
        public void run() {
            SourceFilterNode sfn = new SourceFilterNode(sac_service.getUI().getTreeModel(),
                    filter_name,
                    (sac) -> {
                        if (match_case) {
                            return sac.getSpimSource().getName().contains(string_filter);
                        } else {
                            return sac.getSpimSource().getName().toUpperCase().contains(string_filter.toUpperCase());
                        }
                    },
                    show_sources);
            sac_service.getUI().addNode(sfn);
        }
}
