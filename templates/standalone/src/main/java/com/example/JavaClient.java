package com.example;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;
import play.libs.ws.*;
import play.libs.ws.ahc.*;

import java.util.Optional;

public class Standalone {

    public static void main(String[] args) {
        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setMaxRequestRetry(0)
                .setShutdownQuietPeriod(0)
                .setShutdownTimeout(0).build();

        String name = "wsclient";
        ActorSystem system = ActorSystem.create(name);
        ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        ActorMaterializer materializer = ActorMaterializer.create(settings, system, name);

        DefaultAsyncHttpClient ahcClient = new DefaultAsyncHttpClient(config);
        WSClient client = new AhcWSClient(ahcClient, materializer);
        client.url("http://www.google.com").get().whenComplete((r, e) -> {
            Optional.ofNullable(r).ifPresent(response -> {
                String statusText = response.getStatusText();
                System.out.println("Got a response " + statusText);
            });
        }).thenRun(() -> {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).thenRun(system::terminate);

    }
}
