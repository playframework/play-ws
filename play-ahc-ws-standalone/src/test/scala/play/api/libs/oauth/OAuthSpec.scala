/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.oauth

import org.scalatest.wordspec.AnyWordSpec

class OAuthSpec extends AnyWordSpec {
  "OAuth" should {
    "be able to use signpost OAuth" in {
      Class.forName("play.shaded.oauth.oauth.signpost.OAuth")
    }
  }
}
