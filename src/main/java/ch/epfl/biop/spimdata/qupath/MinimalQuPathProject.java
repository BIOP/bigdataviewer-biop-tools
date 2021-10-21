package ch.epfl.biop.spimdata.qupath;

import java.util.List;
import java.net.URI;
import java.util.Map;

public class MinimalQuPathProject {

    public String version;

    public URI uri;

    public int lastID;

    public List<MinimalQuPathProject.ImageEntry> images;

    public static class ImageEntry {
        public ServerBuilderEntry serverBuilder;
        public int entryID;
        public String randomizedName;
        public String imageName;
    }

    public static class ServerBuilderMetadata {
        public String name;
        public int width;
        public int height;
        public int sizeZ;
        public int sizeT;
        public String channelType;
        public boolean isRGB;
        public String pixelType;
        // "levels": (ignored)
        public List<ChannelInfo> channels;
        public PixelCalibrations pixelCalibration;

    }

    public static class ChannelInfo {
        int color;
        String name;
    }

    public static class ServerBuilderEntry {
        public String builderType; // "uri" or "rotated"
        public ServerBuilderEntry builder;
        public String rotation; // for "rotated builder"
        public String providerClassName; // "qupath.lib.images.servers.bioformats.BioFormatsServerBuilder",
        public URI uri;
        public List<String> args;
        public ServerBuilderMetadata metadata;
    }

    public static class PixelCalibrations {
        public PixelCalibration pixelWidth;
        public PixelCalibration pixelHeight;
        public PixelCalibration zSpacing;
    }

    public static class PixelCalibration {
        public double value;
        public String unit;
    }

}
