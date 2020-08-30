package ch.epfl.biop.qupathfiji;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

import java.util.List;
import java.net.URI;

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
        List<ChannelInfo> channels;

    }

    public static class ChannelInfo {
        int color;
        String name;
    }

    public static class ServerBuilderEntry {
        public String builderType; // "uri"
        public String providerClassName; // "qupath.lib.images.servers.bioformats.BioFormatsServerBuilder",
        public URI uri;
        public List<String> args;
        public ServerBuilderMetadata metadata;
    }

}
