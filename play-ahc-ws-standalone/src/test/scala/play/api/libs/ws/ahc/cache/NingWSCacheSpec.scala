package play.api.libs.ws.ahc.cache

import com.typesafe.play.cachecontrol.HeaderName
import org.specs2.mutable.Specification

class NingWSCacheSpec extends Specification with NingBuilderMethods {

  "calculateSecondaryKeys" should {

    "calculate keys correctly" in {
      implicit val cache = generateCache
      val url = "http://localhost:9000"

      val request = generateRequest(url)(headers => headers.add("Accept-Encoding", "gzip"))
      val response = CacheableResponse(200, url).withHeaders("Vary" -> "Accept-Encoding")

      val actual = cache.calculateSecondaryKeys(request, response)

      actual must beSome.which { d =>
        d must haveKey(HeaderName("Accept-Encoding"))
        d(HeaderName("Accept-Encoding")) must be_==(Seq("gzip"))
      }
    }

  }

}
