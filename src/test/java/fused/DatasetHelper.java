package fused;


import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.function.Function;

public class DatasetHelper {

    // https://downloads.openmicroscopy.org/images/

    public static final File cachedSampleDir = new File(System.getProperty("user.home"),"CachedSamples");

    public static File urlToFile(URL url, Function<String, String> decoder) {
        try {
            File file_out = new File(cachedSampleDir,decoder.apply(url.getFile()));
            if (file_out.exists()) {
                return file_out;
            } else {
                System.out.println("Downloading and caching: "+url+" size = "+ (getFileSize(url)/1024) +" kb");
                FileUtils.copyURLToFile(url, file_out, 10000, 10000);
                System.out.println("Downloading and caching of "+url+" completed successfully ");
                return file_out;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static Function<String, String> decoder = (str) -> {
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch(Exception e){
            e.printStackTrace();
            return str;
        }
    };

    public static File getDataset(String urlString) {
        return getDataset(urlString, decoder);
    }

    public static File getDataset(String urlString, Function<String, String> decoder) {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return urlToFile(url, decoder);
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
