package ch.epfl.biop.bdv.uberdataset;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.Volatile;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.*;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class UberImgLoader implements ViewerImgLoader, MultiResolutionImgLoader {

    Map<File, SpimDataMinimal> spimDataFromFiles = new HashMap<>();

    HashMap<Integer, UberSetupLoader> setupLoaders = new HashMap<>();

    //HashMap<Integer, Supplier<NumericType>> tGetter = new HashMap<>();

    //HashMap<Integer, Supplier<Volatile>> vGetter = new HashMap<>();

    LinkedList<Integer> viewSetupIndexStart = new LinkedList<>(); //

    public File getFileFromViewSetup(int iSetup) {
        int indexFile = 0;
        while (iSetup<viewSetupIndexStart.get(indexFile)) {
            indexFile++;
        }
        return files.get(indexFile);
    }

    public UberImgLoader (List<File> files, final AbstractSequenceDescription<?, ?, ?> sequenceDescription) {
        // Stores fields
        this.files = files;
        this.sequenceDescription = sequenceDescription;
        viewSetupIndexStart.add(0); // first file starts at 0
        // Opens each linked dataset
        files.stream().forEach(f -> {
            try {
                // Fetch spimdata minimal
                SpimDataMinimal sdm = new XmlIoSpimDataMinimal().load(f.getAbsolutePath());
                spimDataFromFiles.put(f, sdm);
                // How many viewsetups ?
                int nViewSetup = sdm.getSequenceDescription().getViewSetups().size();
                // Stores index of first one
                viewSetupIndexStart.add(viewSetupIndexStart.getLast()+nViewSetup);
            } catch (SpimDataException e) {
                e.printStackTrace();
            }
        });

        // Q : should we ignore  the sequence description ?

        // NOT CORRECTLY IMPLEMENTED YET
        final BlockingFetchQueues<Callable<?>> queue = new BlockingFetchQueues<>(1);
        cache = new VolatileGlobalCellCache(queue);
    }

    public List<File> files;

    protected VolatileGlobalCellCache cache;

    final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

    public UberSetupLoader<?,?> getSetupImgLoader(int setupId) {
        if (setupLoaders.containsKey(setupId)) {
            return setupLoaders.get(setupId);
        } else {
            File f = getFileFromViewSetup(setupId);
            int setupIndexInOriginalFile = setupId - viewSetupIndexStart.get(files.indexOf(f));

            BasicSetupImgLoader bsil = spimDataFromFiles
                    .get(f)
                    .getSequenceDescription()
                    .getImgLoader()
                    .getSetupImgLoader(setupIndexInOriginalFile);

            Type t = (Type) ((BasicSetupImgLoader)bsil).getImageType();

            if (t instanceof NumericType) {

                NumericType numType = (NumericType) t.copy();
                Volatile volType = getVolatileFromNumeric(numType);
                UberSetupLoader usl = new UberSetupLoader(numType,volType,bsil);
                setupLoaders.put(setupId,usl);
                return usl;
            } else {
                System.err.println("Impossible to cast Type "+t.getClass()+" to "+NumericType.class);
                return null;
            }
        }
    }

    private Volatile getVolatileFromNumeric(NumericType numType) {
        if (numType instanceof ARGBType) {
            return new VolatileARGBType();
        }

        if (numType instanceof UnsignedByteType) {
            return new VolatileUnsignedByteType();
        }

        if (numType instanceof UnsignedShortType) {
            return new VolatileUnsignedShortType();
        }

        if (numType instanceof UnsignedIntType) {
            return new VolatileUnsignedIntType();
        }

        if (numType instanceof FloatType) {
            return new VolatileFloatType();
        }

        return null;
    }

    @Override
    public CacheControl getCacheControl() {
        return cache;
    }
}
