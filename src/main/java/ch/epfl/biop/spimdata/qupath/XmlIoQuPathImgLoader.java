/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2021 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.spimdata.qupath;

import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.imageloader.BioFormatsImageLoader;
import ch.epfl.biop.bdv.bioformats.imageloader.XmlIoBioFormatsImgLoader;
import ch.epfl.biop.ij2command.OmeroTools;
import ch.epfl.biop.omero.imageloader.XmlIoOmeroImgLoader;
import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import org.jdom2.Element;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;

import static ch.epfl.biop.ij2command.OmeroTools.getSecurityContext;
import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo( format = "spimreconstruction.biop_qupathimageloader", type = QuPathImageLoader.class )
public class XmlIoQuPathImgLoader implements XmlIoBasicImgLoader< QuPathImageLoader > {

    public static final String OPENER_CLASS_TAG = "opener_class";
    public static final String CACHE_NUM_FETCHER = "num_fetcher_threads";
    public static final String CACHE_NUM_PRIORITIES = "num_priorities";
    public static final String QUPATH_PROJECT_TAG = "qupath_project";
    public static final String OPENER_MODEL_TAG = "opener_model";
    public static final String DATASET_NUMBER_TAG = "dataset_number";

    Map<String, XmlIoQuPathImgLoader.GatewaySecurityContext> hostToGatewayCtx = new HashMap<>();

    @Override
    public Element toXml(QuPathImageLoader imgLoader, File basePath) {
        final Element elem = new Element( "ImageLoader" );
        elem.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass().getAnnotation( ImgLoaderIo.class ).format() );
        // For potential extensibility
        elem.addContent(XmlHelpers.intElement( CACHE_NUM_FETCHER, imgLoader.numFetcherThreads));
        elem.addContent(XmlHelpers.intElement( CACHE_NUM_PRIORITIES, imgLoader.numPriorities));
        elem.addContent(XmlHelpers.textElement( QUPATH_PROJECT_TAG, (new Gson()).toJson(imgLoader.getProjectURI(), URI.class)));
        elem.addContent(XmlHelpers.textElement( OPENER_CLASS_TAG, QuPathImageOpener.class.getName()));
        elem.addContent(XmlHelpers.intElement( DATASET_NUMBER_TAG, imgLoader.getModelOpener().size()));

        Gson gson = new Gson();
        for (int i=0;i<imgLoader.getModelOpener().size();i++) {
            // Opener serialization
            elem.addContent(XmlHelpers.textElement(OPENER_MODEL_TAG+"_"+i, gson.toJson(imgLoader.getModelOpener().get(i))));
        }

        //elem.addContent(XmlHelpers.textElement( OPENER_MODEL_TAG, (new Gson()).toJson(imgLoader.getModelOpener())));
        return elem;
    }

    @Override
    public QuPathImageLoader fromXml(Element elem, File basePath, AbstractSequenceDescription<?, ?, ?> sequenceDescription) {
        try
        {
            final int number_of_datasets = XmlHelpers.getInt( elem, DATASET_NUMBER_TAG );
            final int numFetcherThreads = XmlHelpers.getInt(elem, CACHE_NUM_FETCHER);
            final int numPriorities = XmlHelpers.getInt(elem, CACHE_NUM_PRIORITIES);

            List<QuPathImageOpener> openers = new ArrayList<>();

            String openerClassName = XmlHelpers.getText( elem, OPENER_CLASS_TAG );

            if (!openerClassName.equals(QuPathImageOpener.class.getName())) {
                throw new UnsupportedOperationException("Error class "+openerClassName+" not recognized.");
            }

            Gson gson = new Gson();
            for (int i=0;i<number_of_datasets;i++) {
                // Opener de-serialization
                String jsonInString = XmlHelpers.getText( elem, OPENER_MODEL_TAG+"_"+i );
                QuPathImageOpener opener = (gson.fromJson(jsonInString, QuPathImageOpener.class));

                if(opener.getImage().serverBuilder.providerClassName.equals("qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder")) {
                    if (!hostToGatewayCtx.containsKey(opener.getImage().serverBuilder.providerClassName)) {
                        // No : add it in the channel hashmap
                        //Get username
                        String username = (String) JOptionPane.showInputDialog(null, "Enter Your OMERO Username: ", null);

                        // get password
                        JPasswordField jpf = new JPasswordField(24);
                        Box box = Box.createHorizontalBox();
                        box.add(jpf);
                        JOptionPane.showConfirmDialog(null, box, "Enter Your OMERO Password: ", JOptionPane.OK_CANCEL_OPTION);
                        char[] chArray = jpf.getPassword();
                        String password = new String(chArray);
                        Arrays.fill(chArray, (char) 0);

                        String host = "omero-server.epfl.ch";
                        Gateway gateway = OmeroTools.omeroConnect(host, 4064, username, password);
                        System.out.println("Gateway : "+gateway);

                        SecurityContext ctx = OmeroTools.getSecurityContext(gateway);
                        System.out.println("ctx : "+ctx);
                        GatewaySecurityContext gtCtx = new GatewaySecurityContext(gateway, ctx);
                        ctx.setServerInformation(new ServerInformation(host));
                        hostToGatewayCtx.put(opener.getImage().serverBuilder.providerClassName, gtCtx);
                    }

                    GatewaySecurityContext gtCtx = hostToGatewayCtx.get(opener.getImage().serverBuilder.providerClassName);
                    opener.create(gtCtx.gateway,gtCtx.ctx).loadMetadata();

                } else opener.create(null,null).loadMetadata();

                openers.add(opener);
            }


           /* String openerClassName = XmlHelpers.getText( elem, OPENER_CLASS_TAG );

            if (!openerClassName.equals(BioFormatsBdvOpener.class.getName())) {
                throw new UnsupportedOperationException("Error class "+openerClassName+" not recognized.");
            }

            Gson gson = new Gson();
            String jsonInString = XmlHelpers.getText( elem, OPENER_MODEL_TAG );
            BioFormatsBdvOpener modelOpener = gson.fromJson(jsonInString, BioFormatsBdvOpener.class);*/

            String qupathProjectUri = XmlHelpers.getText( elem, QUPATH_PROJECT_TAG);//, Paths.get(imgLoader.getProjectURI()).toString());

            URI qpProjURI = (new Gson()).fromJson(qupathProjectUri, URI.class);

            return new QuPathImageLoader(qpProjURI, openers/*Collections.singletonList(new QuPathImageOpener(modelOpener))*/, sequenceDescription, numFetcherThreads, numPriorities);
        }
        catch ( final Exception e )
        {
            e.printStackTrace();
            throw new RuntimeException( e );
        }
    }

    public static class GatewaySecurityContext {
        public Gateway gateway;
        public SecurityContext ctx;

        public GatewaySecurityContext(Gateway gateway,SecurityContext ctx) {
            this.gateway = gateway;
            this.ctx = ctx;
        }
    }
}

