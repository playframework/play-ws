/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.shaded.ahc.io.netty.buffer.ByteBufAllocator
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolConfig
import play.shaded.ahc.io.netty.handler.ssl.ClientAuth
import play.shaded.ahc.io.netty.handler.ssl.IdentityCipherSuiteFilter
import play.shaded.ahc.io.netty.handler.ssl.JdkSslContext
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.netty.ssl.SslEngineFactoryBase

import scala.jdk.CollectionConverters._

/**
 * Creates the client `SSLEngine`s from Play WS's configured JSSE `SSLContext`.
 *
 * The protocols and cipher suites come from the AHC configuration when AHC initializes the factory, so both the
 * `play.ws.ssl` settings and changes made through `AhcConfigBuilder.modifyUnderlying` apply. HTTPS hostname
 * verification is enabled unless the AHC configuration disables it.
 *
 * A Netty `SslContext` set on the AHC configuration, for example through `modifyUnderlying`, is ignored with a warning,
 * because using it would bypass the hostname verification set up here.
 *
 * @param sslContext the configured context, which supplies the key and trust managers
 * @param logger receives the warning about an ignored `SslContext`
 */
private[ahc] final class ConfiguredSslEngineFactory(
    sslContext: SSLContext,
    logger: Logger = LoggerFactory.getLogger(classOf[ConfiguredSslEngineFactory])
) extends SslEngineFactoryBase {

  @volatile private var context: JdkSslContext = _

  override def init(config: AsyncHttpClientConfig): Unit = {
    if (config.getSslContext != null) {
      logger.warn(
        "The SslContext set on the AsyncHttpClient configuration is ignored, because Play WS creates its TLS engines " +
          "from the play.ws.ssl settings; only play.ws.ssl.loose.acceptAnyCertificate = true uses it. To take over " +
          "TLS, set an SslEngineFactory instead, which is then responsible for hostname verification and ALPN."
      )
    }
    context = newContext(config, ApplicationProtocolConfig.DISABLED)
  }

  override def newSslEngine(config: AsyncHttpClientConfig, peerHost: String, peerPort: Int): SSLEngine = {
    val engine = context.newEngine(ByteBufAllocator.DEFAULT, domain(peerHost), peerPort)
    if (!config.isDisableHttpsEndpointIdentificationAlgorithm) {
      val parameters = engine.getSSLParameters
      parameters.setEndpointIdentificationAlgorithm("HTTPS")
      engine.setSSLParameters(parameters)
    }
    configureSslEngine(engine, config)
    engine
  }

  private def newContext(
      config: AsyncHttpClientConfig,
      applicationProtocolConfig: ApplicationProtocolConfig
  ): JdkSslContext = {
    // As in AHC's own factory, an empty list means the context's defaults.
    val protocols    = Option(config.getEnabledProtocols).filter(_.nonEmpty).orNull
    val cipherSuites = Option(config.getEnabledCipherSuites).filter(_.nonEmpty).map(_.toSeq.asJava).orNull
    new JdkSslContext(
      sslContext,
      true, // client
      cipherSuites,
      IdentityCipherSuiteFilter.INSTANCE,
      applicationProtocolConfig,
      ClientAuth.NONE,
      protocols,
      false // startTls
    )
  }
}
