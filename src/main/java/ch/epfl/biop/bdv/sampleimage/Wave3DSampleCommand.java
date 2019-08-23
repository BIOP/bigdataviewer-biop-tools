package ch.epfl.biop.bdv.sampleimage;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import net.imagej.display.ColorTables;
import net.imagej.lut.LUTService;
import net.imglib2.*;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.ColorTable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@Plugin(type=Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Samples>Wave 3D")
public class Wave3DSampleCommand extends DynamicCommand {

    @Parameter(label = "Open in new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

    @Parameter
    private LUTService lutService;

    @Parameter(label = "LUT name", persist = false, callback = "nameChanged")
    private String choice = "Gray";

    @Parameter(required = false, label = "LUT", persist = false)
    private ColorTable table = ColorTables.GRAYS;

    @Parameter
    private ConvertService cs;

    // -- other fields --

    private Map<String, URL> luts = null;

    public void run() {
        Converter bvdLut = cs.convert(table, Converter.class);

        Interval interval = new FinalInterval(
                new long[]{ -100, -100, -100 },
                new long[]{ 100, 100, 100 });

        /*public class Wave3D extends Procedural3DImageShort {
            public int getValue(double x, double y, double z) {
                return (int) ((Math.sin(x/20)*Math.sin(y/40)*Math.sin(z/5)+1)*2000);
            }
        }*/

        RealRandomAccessible<UnsignedShortType> rra = new Procedural3DImageShort(
                p -> (int) ((Math.sin(p[0]/20)*Math.sin(p[1]/40)*Math.sin(p[2]/5)+1)*2000)
        ).getRRA();


        final RealRandomAccessible<ARGBType> convertedrra =
                Converters.convert(
                        rra,
                        bvdLut,
                        new ARGBType() );

        BdvOptions options = BdvOptions.options();

        if (!createNewWindow) {
            options.addTo(bdv_h);
        }

        bdv_h = BdvFunctions.show( convertedrra, interval, "Wave3D", options ).getBdvHandle();

    }

    // -- initializers --

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
