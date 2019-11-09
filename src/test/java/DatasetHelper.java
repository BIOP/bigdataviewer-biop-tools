import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class DatasetHelper {

    // https://downloads.openmicroscopy.org/images/

    public static File cachedSampleDir = new File(System.getProperty("user.home"),"CachedSamples");

    final public static String JPG_RGB = "https://biop.epfl.ch/img/splash/physicsTemporal_byRGUIETcrop.jpg";
    final public static String OLYMPUS_OIR = "https://downloads.openmicroscopy.org/images/Olympus-OIR/etienne/venus%20stack.ome.tif";
    final public static String VSI ="https://github.com/NicoKiaru/TestImages/raw/master/VSI/Fluo3DFluoImage2Channels_01.vsi";
    final public static String LIF = "https://downloads.openmicroscopy.org/images/Leica-LIF/michael/PR2729_frameOrderCombinedScanTypes.lif";
    final public static String TIF_TIMELAPSE_3D = "https://github.com/NicoKiaru/TestImages/raw/master/CElegans/dub-0.5xy-TP1-22.tif";
    final public static String ND2_20X = "https://github.com/NicoKiaru/TestImages/raw/master/ND2/20x_g5_a1.nd2";
    final public static String ND2_60X = "https://github.com/NicoKiaru/TestImages/raw/master/ND2/60x_g5_a1.nd2";


    public static void main(String... args) {
        System.out.println("Downloading all sample datasets.");
        ASyncDL(JPG_RGB);
        ASyncDL(OLYMPUS_OIR);
        getSampleVSIDataset();
    }

    public static String getSampleVSIDataset() {
        Thread t0 =ASyncDL(VSI);
        Thread t1 = ASyncDL("https://github.com/NicoKiaru/TestImages/raw/master/VSI/_Fluo3DFluoImage2Channels_01_/stack1/frame_t_0.ets");
        Thread t2 = ASyncDL("https://github.com/NicoKiaru/TestImages/raw/master/VSI/_Fluo3DFluoImage2Channels_01_/stack10001/frame_t_0.ets");
        Thread t3 = ASyncDL("https://github.com/NicoKiaru/TestImages/raw/master/VSI/_Fluo3DFluoImage2Channels_01_/stack10004/frame_t_0.ets");
        try {
            t0.join();
            t1.join();
            t2.join();
            t3.join();
        } catch (Exception e) {

        }
        return getDataset(VSI).getAbsolutePath();
    }

    public static Thread ASyncDL(String str) {
        Thread thread = new Thread(() -> getDataset(str));
        thread.start();
        return thread;
    }

    public static File urlToFile(URL url) {
        try {
            File file_out = new File(cachedSampleDir,url.getFile());
            if (file_out.exists()) {
                return file_out;
            } else {
                System.out.println("Downloading and caching: "+url+" size = "+(int)(getFileSize(url)/1024)+" kb");
                FileUtils.copyURLToFile(url, file_out, 10000, 10000);
                System.out.println("Downloading and caching of "+url+" completed successfully ");
                if (FilenameUtils.getExtension(file_out.getAbsolutePath()).equals(".vsi")) {
                    // We need to download all the subfolders
                }
                return file_out;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File getDataset(String urlString) {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return urlToFile(url);
    }

    // https://stackoverflow.com/questions/12800588/how-to-calculate-a-file-size-from-url-in-java
    private static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).disconnect();
            }
        }
    }

}
