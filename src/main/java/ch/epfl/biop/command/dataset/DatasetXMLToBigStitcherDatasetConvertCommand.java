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
package ch.epfl.biop.command.dataset;

import ch.epfl.biop.bdv.img.bioformats.entity.SeriesIndex;
import spimdata.util.ImageName;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsSetupLoader;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.FileIndex;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.SeriesNumber;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import spimdata.SpimDataHelper;
import spimdata.util.Displaysettings;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Dataset>Dataset - Make BigStitcher Compatible",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DatasetMenu, weight = BdvPgMenus.DatasetW),
                @Menu(label = "Dataset - Make BigStitcher Compatible", weight = 3)
        },
        description = "Converts a BDV dataset to BigStitcher format by removing incompatible attributes and rescaling")
public class DatasetXMLToBigStitcherDatasetConvertCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Input XML File",
            description = "The BDV XML dataset file to convert",
            style = "open")
    File xmlin;

    @Parameter(label = "View Setup Reference",
            description = "View setup index for rescaling reference (-1 to list all and use first)",
            persist = false)
    int viewsetupreference = -1;

    @Parameter(label = "Output XML File",
            description = "The XML file where the BigStitcher-compatible dataset will be saved",
            style = "save")
    File xmlout;

    @Override
    public void run() {
        if (xmlout.exists()) {
            IJ.error("The output file already exist! Skipping execution");
            return;
        }

        try {

            AbstractSpimData<?> asd = new XmlIoSpimData().load(xmlin.getAbsolutePath());

            // We assume all pixel sizes are equal
            BasicImgLoader imageLoader =  asd.getSequenceDescription().getImgLoader();
            double scalingForBigStitcher = 1;
            int nSetups = asd.getSequenceDescription().getViewSetupsOrdered().size();
            if (viewsetupreference ==-1) {
                for (int i = 0; i < nSetups; i++) {
                    MultiResolutionSetupImgLoader setupLoader = (MultiResolutionSetupImgLoader) imageLoader.getSetupImgLoader(i);
                    BioFormatsSetupLoader l;
                    VoxelDimensions voxelDimensions = setupLoader.getVoxelSize(0);
                    voxelDimensions.dimension(0);
                    IJ.log("VS["+i+"] = "+voxelDimensions);
                }
                viewsetupreference = 0;
            }
            MultiResolutionSetupImgLoader setupLoader = (MultiResolutionSetupImgLoader) imageLoader.getSetupImgLoader(viewsetupreference);
            scalingForBigStitcher = 1./setupLoader.getVoxelSize(0).dimension(0);

            // Remove display settings attributes because this causes issues with BigStitcher
            SpimDataHelper.removeEntities(asd, Displaysettings.class, FileIndex.class, SeriesIndex.class, SeriesNumber.class, ImageName.class);

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
