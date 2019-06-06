package ch.epfl.biop.bdv.bioformats;

import loci.formats.ImageReader;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

import java.util.concurrent.ConcurrentHashMap;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

public class BioFormatsBdvUnsignedByteSource extends BioFormatsBdvSource<UnsignedByteType> {
    public BioFormatsBdvUnsignedByteSource(ImageReader reader, int image_index, int channel_index, boolean sw) {
        super(reader, image_index, channel_index, sw);
    }

    @Override
    public RandomAccessibleInterval<UnsignedByteType> createSource(int t, int level) {
        assert is8bit;
        synchronized(reader) {
            //System.out.println("Building level "+level);
            if (!raiMap.containsKey(t)) {
                raiMap.put(t, new ConcurrentHashMap<>());
            }

            reader.setResolution(level);
            int sx = reader.getSizeX();
            int sy = reader.getSizeY();
            int sz = numDimensions==2?1:reader.getSizeZ();


            final int[] cellDimensions = new int[] { 512, 512,  numDimensions==2?1:64 };

            // Cached Image Factory Options
            final DiskCachedCellImgOptions factoryOptions = options()
                    .cellDimensions( cellDimensions )
                    .cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
                    .maxCacheSize( 1000 );


            // Creates cached image factory of Type Byte
            final DiskCachedCellImgFactory<UnsignedByteType> factory = new DiskCachedCellImgFactory<>( new UnsignedByteType() , factoryOptions );

            //final Random random = new Random( 10 );

            int xc = cellDimensions[0];
            int yc = cellDimensions[1];

            // Creates border image, with cell Consumer method, which creates the image

            final Img<UnsignedByteType> rai = factory.create(new FinalInterval(new long[]{sx, sy, sz}),
                    cell -> {
                        synchronized(reader) {
                            reader.setResolution(level);
                            Cursor<UnsignedByteType> out = Views.flatIterable(cell).cursor();
                            int minX = (int) cell.min(0);
                            int maxX = Math.min(minX + xc, reader.getSizeX());

                            int minY = (int) cell.min(1);
                            int maxY = Math.min(minY + yc, reader.getSizeY());

                            int w = maxX - minX;
                            int h = maxY - minY;

                            byte[] bytes = reader.openBytes(switchZandC?reader.getIndex(cChannel,0,t):reader.getIndex(0,cChannel,t), minX, minY, w, h);

                            int idxPx = 0;

                            int totBytes = (w * h);
                            while ((out.hasNext()) && (idxPx < totBytes)) {
                                //int v = ((bytes[idxPx] & 0xff) << 16 ) | ((bytes[idxPx + 1] & 0xff) << 8) | (bytes[idxPx+2] & 0xff);
                                out.next().set(bytes[idxPx]);
                                idxPx ++;
                            }
                        }
                    }, options().initializeCellsAsDirty(true));

            raiMap.get(t).put(level, rai);

            //System.out.println("Building level "+level+" done!");
            return raiMap.get(t).get(level);
        }
    }
}
