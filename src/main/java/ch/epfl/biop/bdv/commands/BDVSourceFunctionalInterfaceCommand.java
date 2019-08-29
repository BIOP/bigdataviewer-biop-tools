package ch.epfl.biop.bdv.commands;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.VolatileBdvSource;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.*;
import org.scijava.ItemIO;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract public class BDVSourceFunctionalInterfaceCommand extends DynamicCommand {

    @Parameter(label = "Bdv Frame Containing the sources to process", type = ItemIO.BOTH)
    BdvHandle bdv_h;

    @Parameter(label="Indexes ('0,3-5'), of the sources to process")
    public String sourceIndexString = "0";

    protected Function<Source<?>, Source<?>> f;

    @Parameter(label="New BDV Window for output")
    boolean newBDV=false;

    @Parameter(label = "Bdv Frame containing the output sources", type = ItemIO.BOTH)
    BdvHandle bdv_out;

    @Parameter(choices = {"Replace In Bdv", "Add To Bdv", "New Bdv Window (Unsupported)", "Output List as Output Item"})
    String mode;

    @Parameter
    boolean transferConverters = true;

    @Parameter(type = ItemIO.OUTPUT)
    List<Source<?>> srcs_out;

    @Parameter
    boolean makeOutputVolatile;

    @Override
    public void run() {
        initCommand();
        List<Source<?>> srcs_in = CommandHelper.commaSeparatedListToArray(sourceIndexString)
                        .stream()
                        .map(idx -> bdv_h.getViewerPanel().getState().getSources().get(idx).getSpimSource())
                        .collect(Collectors.toList());

        // To remove
        Source<?> srcTest = bdv_h.getViewerPanel().getState().getSources().get(0).getSpimSource();

        //bdv_h.getViewerPanel().getState().getSources().get(0).getSpimSource()


        BdvOptions opts = BdvOptions.options().addTo(bdv_out);

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
                        bdv_out.getViewerPanel().addSource(new SourceAndConverter<>(src_out, cvt));
                    } else {
                        System.out.println("5");
                        if (makeOutputVolatile) {
                            Source<?> src_out_volatile = BDVSourceFunctionalInterfaceCommand.wrapAsVolatile(src_out);
                            if (src_out_volatile!=null) {
                                src_out = src_out_volatile;
                            }
                        }
                        BdvFunctions.show(src_out, opts);
                        System.out.println("6");
                    }
                }
                if (mode.equals("Replace In Bdv")) {
                    bdv_h.getViewerPanel().removeSource(s);
                }
                if (makeOutputVolatile) {
                    return src_out;
                } else {
                    return src_out;
                }
            }
        ).collect(Collectors.toList());
    }

    public void initCommand() {

    }

    public static Source<?> wrapAsVolatile(Source<?> src) {
        if (src.getType() instanceof Volatile) {
            return src;
        } else {
            VolatileBdvSource<?,?> vSrc = null;
            System.out.println("Wrapping Source as Volatile:");
            System.out.println("\tSource class = "+src.getClass());
            System.out.println("\tSource type class = "+src.getType().getClass());
            if (src.getSource(0,0)!=null) {
                System.out.println("\tRAI type (non interpolated) = " + src.getSource(0, 0).getClass());
                System.out.println("\tRAI type (interpolated) = " + src.getInterpolatedSource(0,0, Interpolation.NEARESTNEIGHBOR).getClass());
            }
            if (src.getType() instanceof UnsignedByteType) {
                vSrc = new VolatileBdvSource<>((Source<UnsignedByteType>)src, new VolatileUnsignedByteType(), new SharedQueue(1));
            }
            if (src.getType() instanceof FloatType) {
                vSrc = new VolatileBdvSource<>((Source<FloatType>)src, new VolatileFloatType(), new SharedQueue(1));
            }
            if (src.getType() instanceof UnsignedShortType) {
                vSrc = new VolatileBdvSource<>((Source<UnsignedShortType>)src, new VolatileUnsignedShortType(), new SharedQueue(1));
            }
            if (src.getType() instanceof UnsignedIntType) {
                vSrc = new VolatileBdvSource<>((Source<UnsignedIntType>)src, new VolatileUnsignedIntType(), new SharedQueue(1));
            }
            if (src.getType() instanceof ARGBType) {
                vSrc = new VolatileBdvSource<>((Source<ARGBType>)src, new VolatileARGBType(), new SharedQueue(1));
            }
            if (vSrc== null) {
                System.err.println("Couldn't wrap Source as Volatile, Source class = "+src.getClass());
                System.err.println("Couldn't wrap Source as Volatile, Source type class = "+src.getType().getClass());
                return src;
            }
            return vSrc;
        }

    }

}
