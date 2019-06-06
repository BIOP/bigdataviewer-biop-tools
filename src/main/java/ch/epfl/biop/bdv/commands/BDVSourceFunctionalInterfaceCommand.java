package ch.epfl.biop.bdv.commands;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.converter.Converter;
import org.scijava.ItemIO;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract public class BDVSourceFunctionalInterfaceCommand extends DynamicCommand {

    @Parameter(label="Source indexes ('2,3-5'), starts at 0")
    public String sourceIndexString = "0";

    protected Function<Source<?>, Source<?>> f;

    @Parameter(type = ItemIO.BOTH)
    BdvHandle bdv_h;

    @Parameter(choices = {"Replace In Bdv", "Add To Bdv", "Output List as Output Item"})
    String mode;

    @Parameter
    boolean transferConverters = true;

    @Parameter(type = ItemIO.OUTPUT)
    List<Source<?>> srcs_out;

    @Override
    public void run() {
        List<Source<?>> srcs_in = CommandHelper.commaSeparatedListToArray(sourceIndexString)
                        .stream()
                        .map(idx -> bdv_h.getViewerPanel().getState().getSources().get(idx).getSpimSource())
                        .collect(Collectors.toList());

        BdvOptions opts = BdvOptions.options().addTo(bdv_h);

        srcs_out = srcs_in.stream().map(s -> {
                Source<?> src_out = f.apply(s);
                if (mode.equals("Replace In Bdv")||mode.equals("Add To Bdv")) {
                    if (transferConverters) {
                        Converter cvt = bdv_h.getViewerPanel()
                                             .getState()
                                             .getSources().stream()
                                             .filter(stest -> stest.getSpimSource().equals(s))
                                             .findFirst().get().getConverter();
                        // TODO clone converter instead of passing reference
                        bdv_h.getViewerPanel().addSource(new SourceAndConverter<>(src_out, cvt));
                    } else {
                        BdvFunctions.show(src_out, opts);
                    }
                }
                if (mode.equals("Replace In Bdv")) {
                    bdv_h.getViewerPanel().removeSource(s);
                }
                return src_out;
            }
        ).collect(Collectors.toList());
    }

}
