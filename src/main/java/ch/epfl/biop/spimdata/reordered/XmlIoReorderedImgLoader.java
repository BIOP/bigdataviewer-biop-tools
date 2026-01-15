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
package ch.epfl.biop.spimdata.reordered;

import com.google.gson.Gson;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import org.jdom2.Element;

import java.io.File;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo( format = "spimreconstruction.biop_reorderedimageloader", type = ReorderedImageLoader.class )
public class XmlIoReorderedImgLoader implements XmlIoBasicImgLoader< ReorderedImageLoader > {

    public static final String CACHE_NUM_FETCHER = "num_fetcher_threads";
    public static final String CACHE_NUM_PRIORITIES = "num_priorities";
    public static final String ORDER_CLASS = "order_class";
    public static final String ORDER_TAG = "order";

    @Override
    public Element toXml(ReorderedImageLoader imgLoader, File basePath) {
        final Element elem = new Element( "ImageLoader" );
        elem.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass().getAnnotation( ImgLoaderIo.class ).format() );
        // For potential extensibility
        elem.addContent(XmlHelpers.intElement( CACHE_NUM_FETCHER, imgLoader.numFetcherThreads));
        elem.addContent(XmlHelpers.intElement( CACHE_NUM_PRIORITIES, imgLoader.numPriorities));
        Gson gson = new Gson();
        System.out.println(imgLoader.getOrder().getClass().getSimpleName());
        elem.addContent(XmlHelpers.textElement(ORDER_CLASS, gson.toJson(imgLoader.getOrder().getClass().getSimpleName())));
        elem.addContent(XmlHelpers.textElement(ORDER_TAG, gson.toJson(imgLoader.getOrder())));
        return elem;
    }

    @Override
    public ReorderedImageLoader fromXml(Element elem, File basePath, AbstractSequenceDescription<?, ?, ?> sequenceDescription) {
        try
        {
            final int numFetcherThreads = XmlHelpers.getInt(elem, CACHE_NUM_FETCHER);
            final int numPriorities = XmlHelpers.getInt(elem, CACHE_NUM_PRIORITIES);
            String orderClassName = XmlHelpers.getText( elem, ORDER_CLASS );
            Gson gson = new Gson();
            String orderString = XmlHelpers.getText( elem, ORDER_TAG );

            // Deserialize based on the order class name
            orderClassName = gson.fromJson(orderClassName, String.class);

            ISetupOrder order;
            if (orderClassName.equals(LifReOrdered.class.getSimpleName())) {
                order = gson.fromJson(orderString, LifReOrdered.class);
            } else if (orderClassName.equals(ch.epfl.biop.spimdata.combined.CombinedOrder.class.getSimpleName())) {
                order = gson.fromJson(orderString, ch.epfl.biop.spimdata.combined.CombinedOrder.class);
            } else {
                throw new UnsupportedOperationException("Unknown ISetupOrder class: " + orderClassName +
                    ". Supported types: " + LifReOrdered.class.getSimpleName() + ", " +
                    ch.epfl.biop.spimdata.combined.CombinedOrder.class.getSimpleName());
            }

            order.initialize();
            return new ReorderedImageLoader( order, sequenceDescription, numFetcherThreads, numPriorities);

        }
        catch ( final Exception e )
        {
            throw new RuntimeException( e );
        }
    }
}
