package alpha;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewTransformAffine;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.List;

public class BdvSampleDatasets {

    /**
     * Creates a single image, using the demo spim dataset
     * @param options bdv options used for creating the window
     * @return bdvHandle of the created window
     */
    public static BdvHandle oneImage(BdvOptions options) {
        // Display 2 sources shifted in space
        System.out.print("1\t");

        SpimDataMinimal spimData = getTestSpimData();

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(spimData, options);
        stackSources.get(0).setDisplayRange(0,255);

        BdvHandle bdvh = stackSources.get(0).getBdvHandle(); // Get current bdv window

        return bdvh;
    }

    /**
     * Creates two images, using the demo spim dataset
     * @param options bdv options used for creating the window
     * @return bdvHandle of the created window
     */
    public static BdvHandle twoImages(BdvOptions options) {
        // Display 2 sources shifted in space
        System.out.print("2\t");

        SpimDataMinimal spimData = getTestSpimData();

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(spimData, options);
        stackSources.get(0).setDisplayRange(0,255);

       BdvHandle bdvh = stackSources.get(0).getBdvHandle(); // Get current bdv window

        spimData = getTestSpimData();
        shiftSpimData(spimData, 50, 0);

        stackSources = BdvFunctions.show(spimData, BdvOptions.options().addTo(bdvh));
        stackSources.get(0).setDisplayRange(0,255);

        return bdvh;
    }

    /**
     * Creates 25 images (5x5) images in a grid, using the demo spim dataset
     * @param options bdv options used for creating the window
     * @return bdvHandle of the created window
     */
    public static BdvHandle twentyFiveImages(BdvOptions options) {
        // Display 2 sources shifted in space
        System.out.print("25\t");
        SpimDataMinimal spimData = getTestSpimData();

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(spimData, options);
        stackSources.get(0).setDisplayRange(0,255);

        BdvHandle bdvh = stackSources.get(0).getBdvHandle(); // Get current bdv window

        for (int x=0;x<5;x++) {
            for (int y=0;y<5;y++) {

                spimData = getTestSpimData();
                shiftSpimData(spimData, 50*x, 50*y);

                stackSources = BdvFunctions.show(spimData, BdvOptions.options().addTo(bdvh));
                stackSources.get(0).setDisplayRange(0,255);
            }
        }

        AffineTransform3D at3D = new AffineTransform3D();
        bdvh.getViewerPanel().state().getViewerTransform(at3D);
        at3D.scale(0.6);
        bdvh.getViewerPanel().state().setViewerTransform(at3D);

        return bdvh;
    }

    /**
     *
     * @return the demo spimdataset
     */
    public static SpimDataMinimal getTestSpimData() {
        String fn = "src/test/resources/mri-stack.xml";
        try {
            return new XmlIoSpimDataMinimal().load( fn );
        } catch (SpimDataException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Shift the input spimdata in space, used to create 'grid views'
     * @param spimData spimdata to shift in space
     * @param x shift in x
     * @param y shift in y
     */
    public static void shiftSpimData(SpimDataMinimal spimData, int x, int y) {
        AffineTransform3D transform = new AffineTransform3D();
        transform.translate(x,y,0);
        spimData.getViewRegistrations()
                .getViewRegistration(0,0)
                .concatenateTransform(new ViewTransformAffine("ShiftXY", transform));
    }
}
