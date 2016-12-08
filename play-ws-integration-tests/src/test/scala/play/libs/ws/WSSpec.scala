/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.ws
import java.nio.file.Paths

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test._

class WSSpec extends PlaySpecification {

  sequential

  "patch" should {
    val uploadApp = GuiceApplicationBuilder().appRoutes { app =>
      {
        case ("PATCH", "/") =>
          app.injector.instanceOf[DefaultActionBuilder].apply { request =>
            request.body.asRaw.fold[Result](BadRequest) { raw =>
              val size = raw.size
              Ok(s"size=$size")
            }
          }
      }
    }.build()
//
//    "uploads the file" in new WithServer(app = uploadApp) with Injecting {
//      val wsClient = inject[WSClient]
//
//      val file = Paths.get("ws/play_full_color.png").toFile
//      val rep: WSResponse = wsClient.url(s"http://localhost:$testServerPort").patch(file).toCompletableFuture.get()
//
//      rep.getStatus must ===(200)
//      rep.getBody must ===("size=20039")
//    }

    "uploads the stream" in new WithServer(app = uploadApp) with Injecting {
      val wsClient = inject[WSClient]

      val input = this.getClass.getClassLoader.getResourceAsStream("ws/play_full_color.png")
      val rep: WSResponse = wsClient.url(s"http://localhost:$testServerPort").patch(input).toCompletableFuture.get()

      rep.getStatus must ===(200)
      rep.getBody must ===("size=20039")
    }
  }

  "post" should {
    val uploadApp = GuiceApplicationBuilder().appRoutes { app =>
    {
      case ("POST", "/") =>
        app.injector.instanceOf[DefaultActionBuilder].apply { request =>
          request.body.asRaw.fold[Result](BadRequest) { raw =>
            val size = raw.size
            Ok(s"size=$size")
          }
        }
    }
    }.build()
//
//    "uploads the file" in new WithServer(app = uploadApp) with Injecting {
//      val wsClient = inject[WSClient]
//
//      val file = Paths.get("ws/play_full_color.png").toFile
//      val rep: WSResponse = wsClient.url(s"http://localhost:$testServerPort").post(file).toCompletableFuture.get()
//
//      rep.getStatus must ===(200)
//      rep.getBody must ===("size=20039")
//    }

    "uploads the stream" in new WithServer(app = uploadApp) with Injecting {
      val wsClient = inject[WSClient]

      val input = this.getClass.getClassLoader.getResourceAsStream("ws/play_full_color.png")
      val rep: WSResponse = wsClient.url(s"http://localhost:$testServerPort").post(input).toCompletableFuture.get()

      rep.getStatus must ===(200)
      rep.getBody must ===("size=20039")
    }
  }

  "put" should {
    val uploadApp = GuiceApplicationBuilder().appRoutes { app =>
    {
      case ("PUT", "/") =>
        app.injector.instanceOf[DefaultActionBuilder].apply { request =>
          request.body.asRaw.fold[Result](BadRequest) { raw =>
            val size = raw.size
            Ok(s"size=$size")
          }
        }
    }
    }.build()

//    "uploads the file" in new WithServer(app = uploadApp) with Injecting {
//      val wsClient = inject[WSClient]
//
//      val file = Paths.get("ws/play_full_color.png").toFile
//      val rep: WSResponse = wsClient.url(s"http://localhost:$testServerPort").put(file).toCompletableFuture.get()
//
//      rep.getStatus must ===(200)
//      rep.getBody must ===("size=20039")
//    }

    "uploads the stream" in new WithServer(app = uploadApp) with Injecting {
      val wsClient = inject[WSClient]

      val input = this.getClass.getClassLoader.getResourceAsStream("ws/play_full_color.png")
      val rep: WSResponse = wsClient.url(s"http://localhost:$testServerPort").put(input).toCompletableFuture.get()

      rep.getStatus must ===(200)
      rep.getBody must ===("size=20039")
    }
  }


}
