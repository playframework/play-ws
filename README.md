# Play WS Standalone

Play WS is a powerful HTTP Client library, originally developed by the Play team for use with Play Framework. It uses AsyncHttpClient for HTTP client functionality and has no Play dependencies.

We've provided some documentation here on how to use Play WS in your app (without Play). For more information on how to use Play WS in Play, please refer to the Play documentation.

## Getting Started

To get started, you can add `play-ahc-ws-standalone` as a dependency in SBT:

```scala
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0-M1"
```

This adds the standalone version of Play WS, backed by AsyncHttpClient.  This library contains both the Scala and Java APIs, under `play.api.libs.ws` and `play.libs.ws`.

## Shading

Play WS uses shaded versions of AsyncHttpClient and OAuth Signpost, repackaged under the `play.shaded.ahc` and `play.shaded.oauth` package names, respectively.  Shading AsyncHttpClient means that the version of Netty used behind AsyncHttpClient is completely independent of the application and Play as a whole.

Specifically, shading AsyncHttpClient means that there are no version conflicts introduced between Netty 4.0 and Netty 4.1 using Play WS.

> **NOTE**: If you are developing play-ws and publishing `shaded-asynchttpclient` and `shaded-oauth` using `sbt publishLocal`, you need to be aware that updating `~/.ivy2/local` does not overwrite `~/.ivy2/cache` and so you will not see your updated shaded code until you remove it from cache.  See http://eed3si9n.com/field-test for more details.  This bug has been filed as https://github.com/sbt/sbt/issues/2687.

### Shaded AHC Defaults 

Because Play WS shades AsyncHttpClient, the default settings are also shaded and so do not adhere to the AHC documentation.  This means that the settings in `ahc-default.properties` and the AsyncHttpClient system properties are prepended with `play.shaded.ahc`, for example the `usePooledMemory` setting in the shaded version of AsyncHttpClient is defined like this:

```properties
play.shaded.ahc.org.asynchttpclient.usePooledMemory=true
```

## Instantiating a standalone client

The standalone client needs Akka to handle streaming data internally:

### Scala

In Scala, the way to call out to a web service and close down the client:

```scala
package playwsclient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import play.api.libs.ws._
import play.api.libs.ws.ahc._

import scala.concurrent.Future

object ScalaClient {
  import scala.concurrent.ExecutionContext.Implicits._

  def main(args: Array[String]): Unit = {
    // Create Akka system for thread and streaming management
    implicit val system = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }
    implicit val materializer = ActorMaterializer()

    // Create the standalone WS client
    // no argument defaults to a AhcWSClientConfig created from
    // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"
    val wsClient = StandaloneAhcWSClient()

    call(wsClient)
      .andThen { case _ => wsClient.close() }
      .andThen { case _ => system.terminate() }
  }

  def call(wsClient: StandaloneWSClient): Future[Unit] = {
    wsClient.url("http://www.google.com").get().map { response ⇒
      val statusText: String = response.statusText
      println(s"Got a response $statusText")
    }
  }
}
```

### Java

In Java the API is much the same, except that an instance of AsyncHttpClient has to be passed in explicitly:

```java
package playwsclient;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import com.typesafe.config.ConfigFactory;

import play.libs.ws.*;
import play.libs.ws.ahc.*;

import java.util.concurrent.CompletionStage;

public class JavaClient {

    public static void main(String[] args) {
        // Set up Akka materializer to handle streaming
        final String name = "wsclient";
        ActorSystem system = ActorSystem.create(name);
        system.registerOnTermination(System.exit(0));
        final ActorMaterializerSettings settings = ActorMaterializerSettings.create(system);
        final ActorMaterializer materializer = ActorMaterializer.create(settings, system, name);

        // Create the WS client from the `application.conf` file, the current classloader and materializer.
        StandaloneAhcWSClient client = StandaloneAhcWSClient.create(
                AhcWSClientConfigFactory.forConfig(ConfigFactory.load(), system.getClass().getClassLoader()),
                materializer);

        CompletionStage<StandaloneWSResponse> completionStage = client.url("http://www.google.com").get();

        completionStage.whenComplete((response, throwable) -> {
            String statusText = response.getStatusText();
            System.out.println("Got a response " + statusText);
        }).thenRun(() -> {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).thenRun(system::terminate);

    }
}
```

## Releasing

This project uses `sbt-release` to push to Sonatype and Maven.  You will need Lightbend Sonatype credentials and a GPG key that is available on one of the public keyservers to release this project.

To release cleanly, you should clone this project fresh into a directory with writable credentials (i.e. you have ssh key to github):

```bash
mkdir releases
cd releases
git clone git@github.com:playframework/play-ws.git
```

and from there you can release:

```bash
cd play-ws
sbt release
```

The script will walk you through integration tests and publishing.

## License

Play WS is licensed under the Apache license, version 2. See the LICENSE file for more information.
