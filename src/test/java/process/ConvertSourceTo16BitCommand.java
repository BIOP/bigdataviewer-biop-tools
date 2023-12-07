package process;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Convert>To 16 bits")
public class ConvertSourceTo16BitCommand implements Command {

    @Parameter
    SourceAndConverter<?>[] sources;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter<?>[] converted_sources;

    @Parameter
    int nThreads;
    @Override
    public void run() {
        converted_sources = new SourceAndConverter[sources.length];
        SharedQueue queue = new SharedQueue(nThreads,6);
        for (int i = 0; i< sources.length; i++) {
            converted_sources[i] = DemoConvertSourcePixelType.cvt((SourceAndConverter<UnsignedByteType>) sources[i], queue);
        }
    }
}
