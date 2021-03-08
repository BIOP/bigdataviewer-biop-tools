package ch.epfl.biop.bdv.command;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceFilterNode;


@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Filter Sources Based on Name",
        description = "Adds a node in the tree view which selects the sources based on their name")

public class FilterSourcesByNameCommand implements BdvPlaygroundActionCommand {

        @Parameter(label = "The source name should contain:")
        String stringFilter;

        @Parameter(label = "Match case")
        boolean matchCase;

        @Parameter
        SourceAndConverterService sac_service;

        @Override
        public void run() {
            SourceFilterNode sfn = new SourceFilterNode(sac_service.getUI().getTreeModel(),
                    stringFilter,
                    (sac) -> {
                        if (matchCase) {
                            return sac.getSpimSource().getName().contains(stringFilter);
                        } else {
                            return sac.getSpimSource().getName().toUpperCase().contains(stringFilter.toUpperCase());
                        }
                    },
                    false);
            sac_service.getUI().addNode(sfn);
        }
}
