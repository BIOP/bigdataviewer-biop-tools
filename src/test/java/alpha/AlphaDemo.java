/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2021 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
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
package alpha;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import fused.TestHelper;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.ARGBType;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.display.ColorChanger;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class AlphaDemo
{
	static ImageJ ij;

	public static void main( String[] args ) throws ExecutionException, InterruptedException
	{
		// Create the ImageJ application context with all available services; necessary for SourceAndConverterServices creation
		ij = new ImageJ();
		ij.ui().showUI();
		demo();
	}

	@Test
	public void demoRunOk() throws ExecutionException, InterruptedException
	{
		main(new String[]{""});
	}

	@After
	public void closeFiji() {
		TestHelper.closeFijiAndBdvs(ij);
	}

	public static void demo() {
		IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());

		SourceAndConverterServices.getBdvDisplayService().setDefaultBdvSupplier(bdvSupplier);

		BdvHandle bdv = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

		// Import SpimData
		new SpimDataFromXmlImporter( "src/test/resources/mri-stack.xml" ).run();
		new SpimDataFromXmlImporter("src/test/resources/mri-stack-shiftedX.xml").run();
		new SpimDataFromXmlImporter( "src/test/resources/mri-stack-shiftedY.xml" ).run();

		// Get a handle on the sacs
		final List< SourceAndConverter > sacs = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverters();

		// Show all three sacs
		sacs.forEach( sac -> {
			SourceAndConverterServices.getBdvDisplayService().show(bdv, sac);
			new ViewerTransformAdjuster(bdv, sac).run();
			new BrightnessAutoAdjuster(sac, 0).run();
		});

		// Change color of third one
		new ColorChanger( sacs.get( 2 ), new ARGBType( ARGBType.rgba( 0, 255, 0, 255 ) ) ).run();
	}
}
