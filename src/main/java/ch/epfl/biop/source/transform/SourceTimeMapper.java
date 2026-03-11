package ch.epfl.biop.source.transform;

import bdv.util.source.time.MappedTimeSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;

import java.util.function.Function;
import sc.fiji.bdvpg.source.SourceHelper;

public class SourceTimeMapper implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter source_in;
    Function<Integer, Integer> timeMapper;
    private String name;

    public SourceTimeMapper(SourceAndConverter source_in, Function<Integer, Integer> timeMapper, String name) {
        this.name = name;
        this.timeMapper = timeMapper;
        this.source_in = source_in;
    }

    public void run() {
    }

    public SourceAndConverter get() {
        return this.apply(this.source_in);
    }

    public SourceAndConverter apply(SourceAndConverter src) {
        Source srcRsampled = new MappedTimeSource(src.asVolatile().getSpimSource(), this.name, timeMapper);
        SourceAndConverter source;
        if (src.asVolatile() != null) {
            MappedTimeSource vsrcRsampled = new MappedTimeSource(src.asVolatile().getSpimSource(), this.name, timeMapper);
            SourceAndConverter vsource = new SourceAndConverter((Source)vsrcRsampled, SourceHelper.cloneConverter(src.asVolatile().getConverter(), src.asVolatile()));
            source = new SourceAndConverter(srcRsampled, SourceHelper.cloneConverter(src.getConverter(), src), vsource);
        } else {
            source = new SourceAndConverter(srcRsampled, SourceHelper.cloneConverter(src.getConverter(), src));
        }
        return source;
    }
}
