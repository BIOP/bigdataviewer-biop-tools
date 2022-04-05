package ch.epfl.biop.sourceandconverter.transform;

import bdv.util.source.time.MappedTimeSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;

import java.util.function.Function;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

public class SourceTimeMapper implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter sac_in;
    Function<Integer, Integer> timeMapper;
    private String name;

    public SourceTimeMapper(SourceAndConverter sac_in, Function<Integer, Integer> timeMapper, String name) {
        this.name = name;
        this.timeMapper = timeMapper;
        this.sac_in = sac_in;
    }

    public void run() {
    }

    public SourceAndConverter get() {
        return this.apply(this.sac_in);
    }

    public SourceAndConverter apply(SourceAndConverter src) {
        Source srcRsampled = new MappedTimeSource(src.asVolatile().getSpimSource(), this.name, timeMapper);
        SourceAndConverter sac;
        if (src.asVolatile() != null) {
            MappedTimeSource vsrcRsampled = new MappedTimeSource(src.asVolatile().getSpimSource(), this.name, timeMapper);
            SourceAndConverter vsac = new SourceAndConverter((Source)vsrcRsampled, SourceAndConverterHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            sac = new SourceAndConverter(srcRsampled, SourceAndConverterHelper.cloneConverter(src.getConverter(), src), vsac);
        } else {
            sac = new SourceAndConverter(srcRsampled, SourceAndConverterHelper.cloneConverter(src.getConverter(), src));
        }
        return sac;
    }
}
