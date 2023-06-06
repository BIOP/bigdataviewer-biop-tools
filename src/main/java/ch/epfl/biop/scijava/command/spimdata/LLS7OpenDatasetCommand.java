package ch.epfl.biop.scijava.command.spimdata;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class,
        menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [Zeiss LLS7]",
        description = "Open and live deskew a zeiss Lattice Light Sheet dataset with  Bio-Formats (BioFormats) and BigDataViewer")
public class LLS7OpenDatasetCommand implements
        Command
{

    //@Parameter(label = "Name of this dataset")
    //public String datasetname = "dataset";

    //@Parameter(required = false, label = "Physical units of the dataset",
    //        choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
    public String unit = "MICROMETER";

    @Parameter(label = "CZI LLS7 file")
    File czi_file;

    @Parameter(required = false,
            label = "Plane Origin Convention", choices = {"CENTER", "TOP LEFT"})
    String plane_origin_convention = "CENTER";

    @Parameter
    Context ctx;

    @Parameter
    SourceAndConverterService sac_service;

    public void run() {
        List<OpenerSettings> openerSettings = new ArrayList<>();
            int nSeries = BioFormatsHelper.getNSeries(czi_file);
            for (int i = 0; i < nSeries; i++) {
                openerSettings.add(
                        OpenerSettings.BioFormats()
                                .location(czi_file)
                                .setSerie(i)
                                .unit(unit)
                                .splitRGBChannels(false)
                                .positionConvention(plane_origin_convention)
                                .cornerPositionConvention()
                                .context(ctx));
            }
        AbstractSpimData<?> spimdata = OpenersToSpimData.getSpimData(openerSettings);
        sac_service.register(spimdata);
        sac_service.setSpimDataName(spimdata, FilenameUtils.removeExtension(czi_file.getName()));

        //SpimDataPostprocessor
        List<SourceAndConverter<?>> sources = sac_service.getSourceAndConverterFromSpimdata(spimdata);
        int nTimepoints = SourceAndConverterHelper.getMaxTimepoint(sources.get(0));

        double tx=0, ty=0, tz=0;
        double angle = 60.0/180*Math.PI;

        AffineTransform3D latticeTransform = new AffineTransform3D();
        latticeTransform.set(
                1,0,0,tx,
                0,Math.cos(angle),0,ty,
                0,+Math.sin(angle),1,tz
        );

        for (SourceAndConverter<?> source : sources) {
            SourceTransformHelper.append(latticeTransform, new SourceAndConverterAndTimeRange<>(source,0,nTimepoints));
        }
    }

}