package play.api.libs.ws.ahc

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

class AhcWSRequestSpec extends Specification with Mockito {

  "give the full URL with the query string" in {
    implicit val materializer = mock[akka.stream.Materializer]
    val client = mock[StandaloneAhcWSClient]
    val request = StandaloneAhcWSRequest(client, "http://example.com")

    request.uri.toString must equalTo("http://example.com")
    request.withQueryString("bar" -> "baz").uri.toString must equalTo("http://example.com?bar=baz")
    request.withQueryString("bar" -> "baz", "bar" -> "bah").uri.toString must equalTo("http://example.com?bar=bah&bar=baz")

  }

  "correctly URL-encode the query string part" in {
    implicit val materializer = mock[akka.stream.Materializer]
    val client = mock[StandaloneAhcWSClient]
    val request = StandaloneAhcWSRequest(client, "http://example.com")

    request.withQueryString("&" -> "=").uri.toString must equalTo("http://example.com?%26=%3D")

  }
}
