package com.example.playwsclient

import play.api.libs.ws.ahc._
import play.api.libs.ws._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object Main {
  import scala.concurrent.ExecutionContext.Implicits._

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val wsClient = StandaloneAhcWSClient()

    call(wsClient)
      .andThen { case _ ⇒ wsClient.close() }
      .andThen { case _ ⇒ system.terminate() }
  }

  def call(wsClient: StandaloneWSClient): Future[Unit] = {
    wsClient.url("http://www.google.com").get().map { response ⇒
      val statusText: String = response.statusText
      println(s"Got a response $statusText")
    }
  }
}
