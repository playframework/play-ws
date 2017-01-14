/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package playwsclient;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.libs.ws.*;
import play.libs.ws.ahc.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

public class JavaIntegrationTest {

    private ActorSystem system;
    private StandaloneWSClient client;

    @Before
    public void before() {
        // Set up Akka materializer to handle streaming
        final String name = "wsclient";
        system = ActorSystem.create(name);
        final ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        final ActorMaterializer materializer = ActorMaterializer.create(settings, system, name);

        // Create the WS client
        client = StandaloneAhcWSClient.create(
                AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), this.getClass().getClassLoader()),
                materializer);
    }

    @After
    public void after() {
        try {
            client.close();
            system.terminate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testClientStatus() throws InterruptedException, ExecutionException, TimeoutException {
        // Make the call, but block until we can get a response back out (and throw a failure
        // back to the test thread)
        client.url("http://www.google.com").get().thenAccept(response -> {
            int status = response.getStatus();
            assertEquals(status, 200);
        }).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
