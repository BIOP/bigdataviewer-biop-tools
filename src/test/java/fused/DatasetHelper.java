package fused;


import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    public static String dowloadBrainVSIDataset() throws IOException {
        // URL = https://zenodo.org/records/6553641
        String targetDir = cachedSampleDir.getAbsolutePath()+File.separator+"records"+File.separator+"6553641"+File.separator+"files"+File.separator;
        String urlBrainSlices = "https://zenodo.org/records/6553641/files/";
        if (!new File(targetDir+"Slide_00.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_00.zip", targetDir);
        if (!new File(targetDir+"Slide_01.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_01.zip", targetDir);
        if (!new File(targetDir+"Slide_02.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_02.zip", targetDir);
        if (!new File(targetDir+"Slide_03.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_03.zip", targetDir);
        if (!new File(targetDir+"Slide_04.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_04.zip", targetDir);
        if (!new File(targetDir+"Slide_05.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_05.zip", targetDir);
        if (!new File(targetDir+"Slide_06.vsi").exists()) dlUnzip(urlBrainSlices+"Slide_06.zip", targetDir);
        return targetDir;
    }

    private static void dlUnzip(String url, String targetDir) throws IOException{
        File tempZip = getDataset(url);
        unpack(tempZip.getAbsolutePath(), targetDir);
        tempZip.delete();


    }

    private static void unpack(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdirs();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    private static final int BUFFER_SIZE = 4096;

    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

}
