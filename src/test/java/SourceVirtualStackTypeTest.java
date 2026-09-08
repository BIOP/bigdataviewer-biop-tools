import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.exporter.CZTRange;
import ch.epfl.biop.source.exporter.SourceVirtualStack;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.junit.Assert;
import org.junit.Test;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An ImageJ stack has a single bit depth, so all the channels handed to a
 * {@link SourceVirtualStack} have to share a pixel type. A mismatch used to be discovered only when
 * the pixels of the offending channel were read, as a {@link ClassCastException} thrown from deep
 * inside the reading - typically while running a registration command, which gave no clue about
 * which source was at fault.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class SourceVirtualStackTypeTest {

    @Test
    public void sameTypeChannelsAreAccepted() throws Exception {
        SourceVirtualStack stack = stackOf(
                source("c0", new UnsignedShortType()),
                source("c1", new UnsignedShortType()));
        Assert.assertEquals(16, stack.getBitDepth());
        Assert.assertNotNull(stack.getProcessor(1));
        Assert.assertNotNull(stack.getProcessor(2));
    }

    @Test
    public void mixedTypeChannelsAreRejectedWithTheNamesOfTheChannels() throws Exception {
        try {
            stackOf(source("16 bits channel", new UnsignedShortType()),
                    source("8 bits channel", new UnsignedByteType()));
            Assert.fail("A stack mixing 8 and 16 bits channels should not be built.");
        } catch (UnsupportedOperationException e) {
            String message = e.getMessage();
            Assert.assertTrue(message, message.contains("16 bits channel"));
            Assert.assertTrue(message, message.contains("8 bits channel"));
            Assert.assertTrue(message, message.contains(UnsignedShortType.class.getSimpleName()));
            Assert.assertTrue(message, message.contains(UnsignedByteType.class.getSimpleName()));
        }
    }

    private static SourceVirtualStack stackOf(SourceAndConverter... channels) throws Exception {
        List<SourceAndConverter> sources = Arrays.asList(channels);
        List<Integer> channelIndices = new ArrayList<>();
        for (int iC = 0; iC < sources.size(); iC++) channelIndices.add(iC);
        CZTRange range = new CZTRange(channelIndices, Arrays.asList(0), Arrays.asList(0));
        return new SourceVirtualStack(sources, 0, range, new AtomicLong(), false, null);
    }

    /** A 4 x 3 x 1 source of the requested type, filled with zeroes. */
    private static SourceAndConverter source(String name, Object type) {
        RandomAccessibleIntervalSource rais = type instanceof UnsignedByteType
                ? new RandomAccessibleIntervalSource(ArrayImgs.unsignedBytes(4, 3, 1),
                    new UnsignedByteType(), new AffineTransform3D(), name)
                : new RandomAccessibleIntervalSource(ArrayImgs.unsignedShorts(4, 3, 1),
                    new UnsignedShortType(), new AffineTransform3D(), name);
        return SourceHelper.createSourceAndConverter(rais);
    }
}
