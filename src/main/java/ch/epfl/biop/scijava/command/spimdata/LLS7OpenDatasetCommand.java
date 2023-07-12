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

        //double tx=0, ty=0, tz=0;
        double angle = -60.0/180*Math.PI;

        for (SourceAndConverter<?> source : sources) {

            AffineTransform3D latticeTransform = new AffineTransform3D();
            latticeTransform.set(
                    1,0,0,0,
                    0,Math.cos(angle),0,0,
                    0,+Math.sin(angle),-1,0
            );

            AffineTransform3D rotateX = new AffineTransform3D();
            rotateX.rotate(0, Math.PI/2.0);

            latticeTransform.preConcatenate(rotateX);
            AffineTransform3D addOffset = new AffineTransform3D();
            source.getSpimSource().getSourceTransform(0,0, addOffset);
            double ox = addOffset.get(0,3);
            double oy = addOffset.get(1,3);
            double oz = addOffset.get(2,3);

            //System.out.println("ox = "+ox+" oy = "+oy+" oz = "+oz);
            addOffset.identity();
            addOffset.set(ox,0,3);
            addOffset.set(oy,1,3);
            addOffset.set(oz,2,3);

            AffineTransform3D rmOffset = addOffset.inverse();

            latticeTransform.preConcatenate(addOffset);
            latticeTransform.concatenate(rmOffset);

            SourceTransformHelper.append(latticeTransform, new SourceAndConverterAndTimeRange<>(source,0,nTimepoints));
        }

        // Now let's try to open the max proj, if it exists

        String mipFileName = FilenameUtils.removeExtension(czi_file.getName())+"_MIP.czi";
        File mipFile = new File(czi_file.getParent(), mipFileName);
        if (mipFile.exists()) {
            openerSettings = new ArrayList<>();
            nSeries = BioFormatsHelper.getNSeries(czi_file);
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
                                .context(ctx));
            }
            spimdata = OpenersToSpimData.getSpimData(openerSettings);
            sac_service.register(spimdata);
            sac_service.setSpimDataName(spimdata, FilenameUtils.removeExtension(mipFile.getName()));

            AffineTransform3D offset = new AffineTransform3D();

            sources = sac_service.getSourceAndConverterFromSpimdata(spimdata);

            sources.get(0).getSpimSource().getSourceTransform(0,0, offset);
            double ox = offset.get(0,3);
            double oy = offset.get(1,3);
            double oz = offset.get(2,3);

            //System.out.println("mipox = "+ox+" mipoy = "+oy+" mipoz = "+oz);
        }
    }

}