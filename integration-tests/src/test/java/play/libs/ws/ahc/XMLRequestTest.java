/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws.ahc;

import static org.assertj.core.api.Assertions.*;

import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import play.libs.ws.XML;
import play.libs.ws.XMLBodyWritables;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaderNames;
import play.shaded.ahc.org.asynchttpclient.Request;

public class XMLRequestTest implements XMLBodyWritables {

      @Test
      public void setXML() {
          Document document = XML.fromString("<?xml version='1.0' encoding='UTF-8'?>" +
                  "<note>" +
                  "<from>hello</from>" +
                  "<to>world</to>" +
                  "</note>");

          StandaloneAhcWSClient client = StandaloneAhcWSClient.create(
                  AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass().getClassLoader()), /*materializer*/ null);
          StandaloneAhcWSRequest ahcWSRequest = new StandaloneAhcWSRequest(client, "http://playframework.com/", null)
                  .setBody(body(document));

          Request req = ahcWSRequest.buildRequest();

          assertThat(req.getHeaders().get(HttpHeaderNames.CONTENT_TYPE)).isEqualTo("application/xml");

          Document responseXml = XML.fromString(req.getStringData());
          responseXml.normalizeDocument();
          assertThat(responseXml.isEqualNode(document)).isTrue();
        }

}
