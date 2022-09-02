
package qupath;

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.img.legacy.qupath.QuPathToSpimData;
import ch.epfl.biop.bdv.img.qupath.command.GuiParams;
import mpicbg.spim.data.generic.AbstractSpimData;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Warning : a qupath project may have its source reordered and or removed : -
 * not all entries will be present in the qupath project Limitations : only
 * images
 */

@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Open [QuPath Project (legacy)]")
public class QuPathProjectToBDVDatasetCommand extends
	BioformatsBigdataviewerBridgeDatasetCommand
{

	private static final Logger logger = LoggerFactory.getLogger(
		QuPathProjectToBDVDatasetCommand.class);

	@Parameter
	File quPathProject;

	@Parameter(
		label = "Dataset name (leave empty to name it like the QuPath project)",
		persist = false)
	public String datasetname = ""; // Cheat to allow dataset renaming

	@Parameter
	public boolean show = false;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimData;

	@Override
	public void run() {

		try {
			spimData = (new QuPathToSpimData()).getSpimDataInstance(quPathProject
				.toURI(), getOpener(""));
			if (datasetname.equals("")) {
				datasetname = quPathProject.getParentFile().getName();// FilenameUtils.removeExtension(FilenameUtils.getName(quPathProject.getAbsolutePath()))
																															// + ".xml";
			}

			if (show) BdvFunctions.show(spimData);

			// BdvFunctions.show(spimData);

			// Directly registers it to prevent memory leak...
			/*SourceAndConverterServices
			        .getSourceAndConverterService()
			        .register(spimData);
			SourceAndConverterServices
			        .getSourceAndConverterService()
			        .setSpimDataName(spimData, datasetname);*/

			// End of session
			// Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			// fail
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}


	public BioFormatsBdvOpener getOpener(String datalocation) {
		Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(this.unit);
		Length positionReferenceFrameLength = new Length(this.refframesizeinunitlocation, bfUnit);
		Length voxSizeReferenceFrameLength = new Length(this.refframesizeinunitvoxsize, bfUnit);
		BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
				.location(datalocation).unit(this.unit)
				//.auto()
				.ignoreMetadata();
		if (!this.switchzandc.equals("AUTO")) {
			opener = opener.switchZandC(this.switchzandc.equals("TRUE"));
		}

		if (!this.usebioformatscacheblocksize) {
			opener = opener.cacheBlockSize(this.cachesizex, this.cachesizey, this.cachesizez);
		}

		if (!this.positioniscenter.equals("AUTO")) {
			if (this.positioniscenter.equals("TRUE")) {
				opener = opener.centerPositionConvention();
			} else {
				opener = opener.cornerPositionConvention();
			}
		}

		if (!this.flippositionx.equals("AUTO") && this.flippositionx.equals("TRUE")) {
			opener = opener.flipPositionX();
		}

		if (!this.flippositiony.equals("AUTO") && this.flippositiony.equals("TRUE")) {
			opener = opener.flipPositionY();
		}

		if (!this.flippositionz.equals("AUTO") && this.flippositionz.equals("TRUE")) {
			opener = opener.flipPositionZ();
		}

		opener = opener.unit(this.unit);
		opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
		opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);
		if (this.splitrgbchannels) {
			opener = opener.splitRGBChannels();
		}

		return opener;
	}
}
