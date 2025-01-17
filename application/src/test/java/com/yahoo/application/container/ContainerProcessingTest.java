// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Networking;
import com.yahoo.application.container.processors.Rot13Processor;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.Container;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
public class ContainerProcessingTest {

    private static String getXML(String chainName, String... processorIds) {
        String xml =
                "<container version=\"1.0\">\n" +
                "  <processing>\n" +
                "    <chain id=\"" + chainName + "\">\n";
        for (String processorId : processorIds) {
            xml += "      <processor id=\"" + processorId + "\"/>\n";
        }
        xml +=
                "    </chain>\n" +
                "  </processing>\n" +
                "  <accesslog type=\"disabled\" />" +
                "</container>\n";
        return xml;
    }

    @Before
    public void resetContainer() {
        Container.resetInstance();
    }

    @Test
    public void requireThatBasicProcessingWorks() {
        try (JDisc container = getContainerWithRot13()) {
            Processing processing = container.processing();

            Request req = new Request();
            req.properties().set("title", "Good day!");
            Response response = processing.process(ComponentSpecification.fromString("foo"), req);

            assertEquals("Tbbq qnl!", response.data().get(0).toString());
        }
    }

    @Test
    public void requireThatBasicProcessingDoesNotTruncateBigResponse() {
        int SIZE = 50*1000;
        StringBuilder foo = new StringBuilder();
        for (int j = 0 ; j < SIZE ; j++) {
            foo.append('b');
        }

        try (JDisc container = getContainerWithRot13()) {
            int NUM_TIMES = 100;
            for (int i = 0; i < NUM_TIMES; i++) {
                com.yahoo.application.container.handler.Response response =
                        container.handleRequest(
                                new com.yahoo.application.container.handler.Request("http://foo/processing/?chain=foo&title=" + foo.toString()));

                assertEquals(SIZE+26, response.getBody().length);
            }
        }
    }

    @Test
    public void processing_and_rendering_works() throws Exception {
        try (JDisc container = getContainerWithRot13()) {
            Processing processing = container.processing();

            Request req = new Request();
            req.properties().set("title", "Good day!");

            byte[] rendered = processing.processAndRender(ComponentSpecification.fromString("foo"),
                    ComponentSpecification.fromString("default"), req);
            String renderedAsString = new String(rendered, StandardCharsets.UTF_8);

            assertTrue(renderedAsString.contains("Tbbq qnl!"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatUnknownChainThrows() {
        try (JDisc container = getContainerWithRot13()) {
            container.processing().process(ComponentSpecification.fromString("unknown"), new Request());
        }

    }

    @Test(expected = UnsupportedOperationException.class)
    public void requireThatDocprocFails() {
        try (JDisc container = getContainerWithRot13()) {
            container.documentProcessing();
        }

    }

    @Test(expected = UnsupportedOperationException.class)
    public void requireThatSearchFails() {
        try (JDisc container = getContainerWithRot13()) {
            container.search();
        }

    }

    private JDisc getContainerWithRot13() {
        return JDisc.fromServicesXml(
                getXML("foo", Rot13Processor.class.getCanonicalName()),
                Networking.disable);
    }

}
