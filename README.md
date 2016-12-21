# Play WS

Play WS is a powerful HTTP Client library, originally developed by the Play team for use with Play Framework. It uses AsyncHttpClient for HTTP client functionality and has no Play dependencies.

We've provided some documentation here on how to use Play WS in your app (without Play). For more information on how to use Play WS in Play, please refer to the Play documentation.

## Getting Started

To get started, you can add `play-ahc-ws-standalone` as a dependency in SBT:

```scala
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.6.0-M1"
```

This adds the standalone version of Play WS, backed by AsyncHttpClient.  This library contains both the Scala and Java APIs, under `play.api.libs.ws` and `play.libs.ws`.

You can also add the Play WS library that depends on Play directly:

```scala
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws" % "2.6.0-M1"
```

The `play-ahc-ws` library has a richer API than the standalone version, since it has access to Play's `Writable` and `MultipartFormData` classes.  However, `play-ahc-ws` does have a dependency on Play's core library as a result, which adds roughly 4 megabytes of JAR file to the application. 

## Shading

Play WS uses shaded versions of AsyncHttpClient and OAuth Signpost, repackaged under the `play.shaded.ahc` and `play.shaded.oauth` package names, respectively.  Shading AsyncHttpClient means that the version of Netty used behind AsyncHttpClient is completely independent of the application and Play as a whole.

Specifically, shading AsyncHttpClient means that there are no version conflicts introduced between Netty 4.0 and Netty 4.1 using Play WS.

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
TODO
```

### Java

In Java the API is much the same:

```java
TODO
```

## Missing Features

This version of Play WS does not include Request Filters.  AsyncHttpClient request filter can still be added directly.

## License

Play WS is licensed under the Apache license, version 2. See the LICENSE file for more information.
