package play.api.libs.oauth

import org.specs2.mutable.Specification

class OAuthSpec extends Specification {
  "OAuth" should {
    "be able to use signpost OAuth" in {
      Class.forName("play.shaded.oauth.oauth.signpost.OAuth") must not(throwA[ClassNotFoundException])
    }
  }
}
