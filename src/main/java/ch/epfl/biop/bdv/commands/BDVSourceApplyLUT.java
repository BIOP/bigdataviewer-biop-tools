package ch.epfl.biop.bdv.commands;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.scijava.command.BDVSourceAndConverterFunctionalInterfaceCommand;
import net.imagej.display.ColorTables;
import net.imagej.lut.LUTService;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorTable;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, initializer = "init", menuPath = ScijavaBdvRootMenu+"Apply LUT to Sources")
public class BDVSourceApplyLUT extends BDVSourceAndConverterFunctionalInterfaceCommand {

    @Parameter
    private LUTService lutService;

    @Parameter(label = "LUT name", persist = false, callback = "nameChanged")
    private String choice = "Gray";

    @Parameter(required = false, label = "LUT", persist = false)
    private ColorTable table = ColorTables.GRAYS;
    // -- initializers --

    @Parameter
    double min;

    @Parameter
    double max;

    @Parameter
    private ConvertService cs;

    public BDVSourceApplyLUT() {
        this.f = src -> {
            /*ConvertedSource convSource = new ConvertedSource<>(src, () -> new ARGBType(), cs.convert(table, Converter.class), src.getName()+"_"+choice);
            convSource.getLinearRange().setMin(min);
            convSource.getLinearRange().setMax(max);*/

            // TODO : volatile stuff
            return new SourceAndConverter<>(src.getSpimSource(), cs.convert(table, Converter.class));
        };
    }

    // -- other fields --

    private Map<String, URL> luts = null;

    protected void init() {
        luts = lutService.findLUTs();
        final ArrayList<String> choices = new ArrayList<>();
        for (final Map.Entry<String, URL> entry : luts.entrySet()) {
            choices.add(entry.getKey());
        }
        Collections.sort(choices);
        final MutableModuleItem<String> input =
                getInfo().getMutableInput("choice", String.class);
        input.setChoices(choices);
        input.setValue(this, choices.get(0));
        nameChanged();
    }

    // -- callbacks --

    protected void nameChanged() {
        try {
            table = lutService.loadLUT(luts.get(choice));
        }
        catch (final Exception e) {
            // nada
        }
    }

}
