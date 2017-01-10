package playwsclient;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.ahc.StandaloneAhcWSClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;

import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JavaIntegrationTest {

    @Test
    public void testClient() {
        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setMaxRequestRetry(0)
                .setShutdownQuietPeriod(0)
                .setShutdownTimeout(0).build();

        String name = "wsclient";
        ActorSystem system = ActorSystem.create(name);
        ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        ActorMaterializer materializer = ActorMaterializer.create(settings, system, name);

        DefaultAsyncHttpClient ahcClient = new DefaultAsyncHttpClient(config);
        StandaloneWSClient client = new StandaloneAhcWSClient(ahcClient, materializer);
        CompletionStage<StandaloneWSResponse> completionStage = client.url("http://www.google.com").get();

        try {
            final StandaloneWSResponse response = completionStage.toCompletableFuture().get();
            int status = response.getStatus();
            assertEquals(status, 200);
        } catch (Exception e) {
            fail(e.toString());
        } finally {
            try {
                client.close();
                system.terminate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
