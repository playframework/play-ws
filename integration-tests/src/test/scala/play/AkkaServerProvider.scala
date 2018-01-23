/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play

import java.io.InputStream
import java.net.{ InetAddress, UnknownHostException }
import java.security.cert.CertificateFactory
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLParameters, TrustManagerFactory }

import akka.actor.ActorSystem
import akka.http.scaladsl.{ Http, HttpsConnectionContext }
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

trait AkkaServerProvider extends BeforeAfterAll {

  /**
   * @return Routes to be used by the test.
   */
  def routes: Route

  /**
   * The execution context environment.
   */
  def executionEnv: ExecutionEnv

  var testServerPort: Int = _
  var testServerPortHttps: Int = _
  val defaultTimeout: FiniteDuration = 5.seconds

  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem("AkkaServerProvider", ConfigFactory.parseString(
    s"""
       |akka.io.dns.inet-address.provider-object = ${classOf[akka.io.AkkaExampleOrgToLocalhostDnsProvider].getName}
     """.stripMargin).withFallback(ConfigFactory.load()))
  implicit val materializer = ActorMaterializer()

  lazy val futureServer: Future[Seq[Http.ServerBinding]] = {
    implicit val ec = executionEnv.executionContext

    // Using 0 (zero) means that a random free port will be used.
    // So our tests can run in parallel and won't mess with each other.
    val httpBinding = Http().bindAndHandle(routes, "localhost", 0)
      .map { b => testServerPort = b.localAddress.getPort; b }
    val httpsBinding = Http().bindAndHandle(routes, "localhost", 0, connectionContext = serverHttpContext())
      .map { b => testServerPortHttps = b.localAddress.getPort; b }

    Future.sequence(Seq(httpBinding, httpBinding))
  }

  override def beforeAll(): Unit = {
    implicit val ec = executionEnv.executionContext
    Await.ready(futureServer, defaultTimeout)
  }

  override def afterAll(): Unit = {
    futureServer.foreach(_.foreach(_.unbind()))(executionEnv.executionContext)
    val terminate = system.terminate()
    Await.ready(terminate, defaultTimeout)
  }

  private def serverHttpContext() = {
    // never put passwords into code!
    val password = "abcdef".toCharArray

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(resourceStream("server.p12"), password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)

    new HttpsConnectionContext(context)
  }

  def clientHttpsContext(akkaSslConfig: Option[AkkaSSLConfig] = None): HttpsConnectionContext = {
    val certStore = KeyStore.getInstance(KeyStore.getDefaultType)
    certStore.load(null, null)
    // only do this if you want to accept a custom root CA. Understand what you are doing!
    certStore.setCertificateEntry("ca", loadX509Certificate("rootCA.crt"))

    val certManagerFactory = TrustManagerFactory.getInstance("SunX509")
    certManagerFactory.init(certStore)

    val context = SSLContext.getInstance("TLS")
    context.init(null, certManagerFactory.getTrustManagers, new SecureRandom)

    val params = new SSLParameters()
    params.setEndpointIdentificationAlgorithm("https")
    new HttpsConnectionContext(
      context,
      sslConfig = akkaSslConfig,
      sslParameters = Some(params))
  }

  private def resourceStream(resourceName: String): InputStream = {
    val is = getClass.getClassLoader.getResourceAsStream(resourceName)
    require(is ne null, s"Resource $resourceName not found")
    is
  }

  private def loadX509Certificate(resourceName: String) =
    CertificateFactory.getInstance("X.509").generateCertificate(resourceStream(resourceName))
}
