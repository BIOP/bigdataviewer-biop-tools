package bdv.util.source.fused;

import net.imglib2.RandomAccess;

public interface SubSetFusedRandomAccess<T> extends RandomAccess<T> {

    RandomAccess<T> copy(boolean[] subset);

}
