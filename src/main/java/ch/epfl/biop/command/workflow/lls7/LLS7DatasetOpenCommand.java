/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.command.workflow.lls7;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Import>Dataset - Create [CZI LLS7]",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ImportMenu, weight = BdvPgMenus.ImportW),
                @Menu(label = "Dataset - Create [CZI LLS7]", weight = 4.1)
        },
        description = "Opens a Zeiss Lattice Light Sheet 7 dataset with live deskewing using Bio-Formats and BigDataViewer")
public class LLS7DatasetOpenCommand implements
        BdvPlaygroundActionCommand
{

    public String unit = "MICROMETER";

    @Parameter(label = "CZI LLS7 File",
            description = "The CZI file from a Zeiss LLS7 acquisition to open")
    File czi_file;

    //@Parameter(required = false,
    //        label = "Plane Origin Convention", choices = {"CENTER", "TOP LEFT"})
    String plane_origin_convention = "TOP LEFT";

    @Parameter
    Context ctx;

    @Parameter(label = "Use Legacy XY Mode",
            description = "When checked, uses legacy XY orientation for compatibility with older datasets")
    boolean legacy_xy_mode;

    @Parameter
    SourceService source_service;

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
        source_service.register(spimdata);
        source_service.setDatasetName(spimdata, FilenameUtils.removeExtension(czi_file.getName()));

        //SpimDataPostprocessor
        List<SourceAndConverter<?>> sources = source_service.getSourcesFromDataset(spimdata);
        int nTimepoints = SourceHelper.getMaxTimepoint(sources.get(0))+1;

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
            source_service.register(spimdata);
            source_service.setDatasetName(spimdata, FilenameUtils.removeExtension(mipFile.getName()));

            sources = source_service.getSourcesFromDataset(spimdata);

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

                    SourceTransformHelper.append(concatTr, new SourceAndTimeRange<>(source,0,nTimepoints));
                }
            }
        }
    }

}
