package ch.epfl.biop.sourceandconverter.exporter;


import ij.process.ImageProcessor;

public class LocalizedImageProcessor {

    public ImageProcessor ip;

    public final int[] localizationIndex;

    public LocalizedImageProcessor(ImageProcessor ip, final int[] localizationIndex) {
        this.ip = ip;
        this.localizationIndex = localizationIndex;
    }

    public static LocalizedImageProcessor wrap(ImageProcessor ip) {
        return new LocalizedImageProcessor(ip, new int[]{1,1,1});
    }

}
