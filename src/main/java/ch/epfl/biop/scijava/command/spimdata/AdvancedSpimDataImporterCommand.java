/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
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
package ch.epfl.biop.scijava.command.spimdata;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.n5.N5ImageLoader;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import net.imglib2.cache.LoaderCache;
import net.imglib2.cache.ref.BoundedSoftRefLoaderCache;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.io.File;
import java.lang.reflect.Field;

@Plugin( type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Open XML BDV Dataset (bound RAM)" )
public class AdvancedSpimDataImporterCommand implements BdvPlaygroundActionCommand {

    @Parameter(style="extensions:xml")
    public File file;

    @Parameter
    int numberOfPriorities = 4;

    @Parameter
    int numberOfFetcherThreads = 1;

    @Parameter
    int maxNumberOfCells = -1;

    public void run() {
        AbstractSpimData asd = new SpimDataFromXmlImporter(file).get();
        BasicImgLoader imageLoader = asd.getSequenceDescription().getImgLoader();
        /*
        if the PR https://github.com/bigdataviewer/bigdataviewer-core/pull/137 is accepted:
        if (imageLoader instanceof CacheOverrider) {
            CacheOverrider co = (CacheOverrider) imageLoader;
            SharedQueue queue = new SharedQueue(numberOfFetcherThreads, numberOfPriorities);
            LoaderCache loaderCache;
            if (maxNumberOfCells>0) {
                loaderCache = new BoundedSoftRefLoaderCache<>(maxNumberOfCells);
            } else {
                loaderCache = new SoftRefLoaderCache();
            }
            VolatileGlobalCellCache cache = new VolatileGlobalCellCache(queue, loaderCache);
            co.setCache(cache);
        } else {
            IJ.log("Can't override cache with image loader type: "+imageLoader.getClass().getName());
        }*/
        // ---------------------- Hacking through reflection while waiting for the PR
        LoaderCache loaderCache;
        if (maxNumberOfCells>0) {
            loaderCache = new BoundedSoftRefLoaderCache<>(maxNumberOfCells);
        } else {
            loaderCache = new SoftRefLoaderCache();
        }
        VolatileGlobalCellCache cache = new VolatileGlobalCellCache(numberOfFetcherThreads, numberOfPriorities);
        // Now override the backingCache field of the VolatileGlobalCellCache
        try {
            Field backingCacheField = VolatileGlobalCellCache.class.getDeclaredField("backingCache");
            backingCacheField.setAccessible(true);
            backingCacheField.set(cache,loaderCache);
            // Now overrides the cache in the ImageLoader
            if (imageLoader instanceof Hdf5ImageLoader) {
                Field cacheField = Hdf5ImageLoader.class.getDeclaredField("cache");
                cacheField.setAccessible(true);
                cacheField.set(imageLoader,cache);
            } else if (imageLoader instanceof N5ImageLoader) {
                Field cacheField = N5ImageLoader.class.getDeclaredField("cache");
                cacheField.setAccessible(true);
                cacheField.set(imageLoader,cache);
            } else {
                IJ.log("Can't override cache with image loader type: "+imageLoader.getClass().getName());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

}
