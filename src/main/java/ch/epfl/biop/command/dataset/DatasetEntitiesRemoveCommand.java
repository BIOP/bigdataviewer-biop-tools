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

import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import spimdata.SpimDataHelper;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Dataset>Dataset - Remove Entities",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DatasetMenu, weight = BdvPgMenus.DatasetW),
                @Menu(label = "Dataset - Remove Entities", weight = 4)
        },
        description = "Removes specified entity types from a BDV dataset for compatibility with other tools")
public class DatasetEntitiesRemoveCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Input XML File",
            description = "The BDV XML dataset file to modify",
            style = "open")
    File xmlin;

    @Parameter(label = "Output XML File",
            description = "The XML file where the modified dataset will be saved",
            style = "save")
    File xmlout;

    @Parameter(label = "Entities to Remove",
            description = "Comma-separated list of entity types to remove (e.g., 'displaysettings, fileindex')")
    String entitiestoremove = "displaysettings, fileindex";

    @Override
    public void run() {

        if (xmlout.exists()) {
            IJ.error("The output file already exist! Skipping execution");
            return;
        }

        try {
            String[] entities = entitiestoremove.split(",");
            AbstractSpimData<?> asd = new XmlIoSpimData().load(xmlin.getAbsolutePath());
            SpimDataHelper.removeEntities(asd, entities);
            new XmlIoSpimData().save((SpimData) asd, xmlout.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
