package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.imageloader.FileIndex;
import ij.IJ;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import ome.units.UNITS;
import ome.units.quantity.Length;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import spimdata.SpimDataHelper;
import spimdata.util.Displaysettings;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Open [Single file]")
public class FileToBigStitcherDatasetCommand implements Command {

    @Parameter(label="Bioformats compatible file", style = "open")
    File file;

    @Parameter(label="Xml Bdv Dataset output", style = "save")
    File xmlout;

    @Override
    public void run() {
        if (xmlout.exists()) {
            IJ.error("The output file already exist! Skipping execution");
            return;
        }

        try {
            BioFormatsBdvOpener opener = BioFormatsConvertFilesToSpimData.getDefaultOpener(file.getAbsolutePath()).micrometer();
            IFormatReader reader = opener.getNewReader();
            Length[] voxSizes = BioFormatsMetaDataHelper.getSeriesVoxelSizeAsLengths((IMetadata) reader.getMetadataStore(), 0);//.getSeriesRootTransform()
            double pixSizeXYMicrometer = voxSizes[0].value(UNITS.MICROMETER).doubleValue();
            double scalingForBigStitcher = 1 / pixSizeXYMicrometer;
            reader.close();

            AbstractSpimData<?> asd = BioFormatsConvertFilesToSpimData.getSpimData(
                    opener
                            .voxSizeReferenceFrameLength(new Length(1, UNITS.MICROMETER))
                            .positionReferenceFrameLength(new Length(1, UNITS.MICROMETER)));

            // Remove display settings attributes because this causes issues with BigStitcher
            SpimDataHelper.removeEntities(asd, Displaysettings.class, FileIndex.class);

            // Scaling such as size of one pixel = 1
            SpimDataHelper.scale(asd, "BigStitcher Scaling", scalingForBigStitcher);


            asd.setBasePath(new File(xmlout.getAbsolutePath()).getParentFile()); //TODO TOFIX
            new XmlIoSpimData().save((SpimData) asd, xmlout.getAbsolutePath());

            IJ.log("- Done! Dataset created - "+xmlout.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
