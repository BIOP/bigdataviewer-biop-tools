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

    public String unit = "MICROMETER";

    @Parameter(label = "CZI LLS7 file")
    File czi_file;

    //@Parameter(required = false,
    //        label = "Plane Origin Convention", choices = {"CENTER", "TOP LEFT"})
    String plane_origin_convention = "TOP LEFT";

    @Parameter
    Context ctx;
    @Parameter
    boolean legacy_xy_mode;

    @Parameter
    SourceAndConverterService sac_service;

    public void run() {
        String bfOptions = "--bfOptions zeissczi.autostitch=false";
        List<OpenerSettings> openerSettings = new ArrayList<>();
        int nSeries = BioFormatsHelper.getNSeries(czi_file, bfOptions);
        for (int i = 0; i < nSeries; i++) {
            openerSettings.add(
                    OpenerSettings.BioFormats()
                            .location(czi_file)
                            .setSerie(i)
                            .unit(unit)
                            .splitRGBChannels(false)
                            .positionConvention(plane_origin_convention)
                            .cornerPositionConvention()
                            .addOptions(bfOptions)
                            .context(ctx));
        }
        AbstractSpimData<?> spimdata = OpenersToSpimData.getSpimData(openerSettings);
        sac_service.register(spimdata);
        sac_service.setSpimDataName(spimdata, FilenameUtils.removeExtension(czi_file.getName()));

        //SpimDataPostprocessor
        List<SourceAndConverter<?>> sources = sac_service.getSourceAndConverterFromSpimdata(spimdata);
        int nTimepoints = SourceAndConverterHelper.getMaxTimepoint(sources.get(0))+1;

        // Now let's try to open the max proj, if it exists

        String mipFileName = FilenameUtils.removeExtension(czi_file.getName())+"_MIP.czi";
        File mipFile = new File(czi_file.getParent(), mipFileName);
        if (mipFile.exists()) {
            openerSettings = new ArrayList<>();
            nSeries = BioFormatsHelper.getNSeries(czi_file, bfOptions);
            AffineTransform3D scaleVoxZUp = new AffineTransform3D();
            scaleVoxZUp.scale(1,1,1000);
            for (int i = 0; i < nSeries; i++) {
                openerSettings.add(
                        OpenerSettings.BioFormats()
                                .location(mipFile)
                                .setSerie(i)
                                .unit(unit)
                                .splitRGBChannels(false)
                                .positionConvention(plane_origin_convention)
                                .cornerPositionConvention()
                                .setPositionPreTransform(scaleVoxZUp)
                                .addOptions(bfOptions)
                                .context(ctx));
            }
            spimdata = OpenersToSpimData.getSpimData(openerSettings);
            sac_service.register(spimdata);
            sac_service.setSpimDataName(spimdata, FilenameUtils.removeExtension(mipFile.getName()));

            sources = sac_service.getSourceAndConverterFromSpimdata(spimdata);

            if (!legacy_xy_mode) {
                for (SourceAndConverter<?> source : sources) {
                    AffineTransform3D ori = new AffineTransform3D();
                    source.getSpimSource().getSourceTransform(0,0, ori);
                    double ox = ori.get(0,3);
                    double oy = ori.get(1,3);
                    double oz = ori.get(2,3);

                    AffineTransform3D addOffset = new AffineTransform3D();
                    addOffset.identity();
                    addOffset.set(ox,0,3);
                    addOffset.set(oy,1,3);
                    addOffset.set(oz,2,3);

                    AffineTransform3D concatTr = new AffineTransform3D();
                    // swap x and y
                    concatTr.set(ori.get(0,0),0,1);
                    concatTr.set(-ori.get(1,1),1,0);
                    concatTr.set(0,0,0);
                    concatTr.set(0,1,1);
                    concatTr.set(ori.get(2,2),2,2);
                    // source.getSpimSource().getSource(0,0).max(1)*ori.get(1,1)

                    AffineTransform3D shifty = new AffineTransform3D();
                    shifty.translate(0,source.getSpimSource().getSource(0,0).max(0)*ori.get(1,1),0);
                    concatTr.preConcatenate(shifty);
                    concatTr.preConcatenate(addOffset);
                    concatTr.concatenate(ori.inverse());

                    SourceTransformHelper.append(concatTr, new SourceAndConverterAndTimeRange<>(source,0,nTimepoints));
                }
            }
        }
    }

}