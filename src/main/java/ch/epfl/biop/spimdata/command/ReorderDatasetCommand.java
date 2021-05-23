package ch.epfl.biop.spimdata.command;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.spimdata.reordered.LifReOrdered;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.ViewSetupAttributes;
import ome.units.UNITS;
import ome.units.quantity.Length;
import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import spimdata.util.Displaysettings;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Reorder BDV Dataset")
public class ReorderDatasetCommand implements Command {

    @Parameter(label="Lif file to reorder", style = "open")
    File file;

    @Parameter(label="Xml Bdv Dataset output", style = "save")
    File xmlout;

    @Parameter(label="Number of tiles")
    int nTiles;

    @Parameter(label="Number of channels")
    int nChannels;

    @Override
    public void run() {
        try {

            BioFormatsBdvOpener opener = BioFormatsConvertFilesToSpimData.getDefaultOpener(file.getAbsolutePath()).micrometer();

            AbstractSpimData<?> asd = BioFormatsConvertFilesToSpimData.getSpimData(
                    opener
                            .voxSizeReferenceFrameLength(new Length(1, UNITS.MICROMETER))
                            .positionReferenceFrameLength(new Length(1,UNITS.MICROMETER)));

            String intermediateXml = FilenameUtils.removeExtension(xmlout.getAbsolutePath())+"_nonreordered.xml";

            System.out.println(intermediateXml);
            // Remove display settings attributes because this causes issues with BigStitcher
            asd.getSequenceDescription().getViewSetups().forEach((id, vs) -> {
                if (vs.getAttribute(Displaysettings.class)!=null) {
                    vs.getAttributes().remove(ViewSetupAttributes.getNameForClass( Displaysettings.class ));
                }
            });

            // Save non reordered dataset
            asd.setBasePath((new File(intermediateXml)).getParentFile());

            if (asd instanceof SpimData) {
                (new XmlIoSpimData()).save((SpimData) asd, intermediateXml);
            } else if (asd instanceof SpimDataMinimal) {
                (new XmlIoSpimDataMinimal()).save((SpimDataMinimal) asd, FilenameUtils.getName(intermediateXml));
            }
            //new XmlIoSpimData().save((SpimData) asd, intermediateXml);

            // Creates reordered dataset
            LifReOrdered kd = new LifReOrdered(intermediateXml,nTiles,nChannels);
            kd.initialize();
            AbstractSpimData reshuffled = kd.constructSpimData();
            reshuffled.setBasePath(new File(xmlout.getAbsolutePath()).getParentFile()); //TODO TOFIX
            new XmlIoSpimData().save((SpimData) reshuffled, xmlout.getAbsolutePath());

            IJ.log("Done! Dataset file "+xmlout.getAbsolutePath()+" created.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
