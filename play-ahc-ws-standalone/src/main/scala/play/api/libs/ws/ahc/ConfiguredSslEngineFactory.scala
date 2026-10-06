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
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolConfig.Protocol
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior
import play.shaded.ahc.io.netty.handler.ssl.ApplicationProtocolNames
import play.shaded.ahc.io.netty.handler.ssl.ClientAuth
import play.shaded.ahc.io.netty.handler.ssl.IdentityCipherSuiteFilter
import play.shaded.ahc.io.netty.handler.ssl.JdkSslContext
import play.shaded.ahc.io.netty.handler.ssl.SslContext
import play.shaded.ahc.io.netty.util.ReferenceCountUtil
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig
import play.shaded.ahc.org.asynchttpclient.netty.ssl.SslEngineFactoryBase

import scala.jdk.CollectionConverters._

/**
 * Creates the client `SSLEngine`s from Play WS's configured JSSE `SSLContext`.
 *
 * The protocols and cipher suites come from the AHC configuration when AHC initializes the factory, so both the
 * `play.ws.ssl` settings and changes made through `AhcConfigBuilder.modifyUnderlying` apply. HTTPS hostname
 * verification is enabled unless this is the loose factory or the AHC configuration disables it.
 *
 * ALPN follows AHC's own `DefaultSslEngineFactory`, because AHC switches a connection to HTTP/2 whenever ALPN selects
 * h2:
 *  - HTTP/2 enabled and allowed for the connection: offer h2, then http/1.1;
 *  - HTTP/2 enabled but not allowed, for example for WebSocket or connection-bound authentication such as NTLM:
 *    offer only http/1.1;
 *  - HTTP/2 disabled: no ALPN extension.
 *
 * A Netty `SslContext` set on the AHC configuration, for example through `modifyUnderlying`, is used as is by the
 * loose factory, as Play WS did before it created its own engines on that path. AHC's own factory treats such a
 * context the same way. The context then decides protocols, cipher suites, certificates and ALPN itself. Otherwise the
 * context is ignored with a warning, because using it would bypass the hostname verification and ALPN set up here.
 *
 * @param sslContext the configured context, which supplies the key and trust managers
 * @param loose true for `play.ws.ssl.loose.acceptAnyCertificate`, which accepts any certificate for any host
 * @param logger receives the warning about an ignored `SslContext`
 */
private[ahc] final class ConfiguredSslEngineFactory(
    sslContext: SSLContext,
    loose: Boolean,
    logger: Logger = LoggerFactory.getLogger(classOf[ConfiguredSslEngineFactory])
) extends SslEngineFactoryBase {

  @volatile private var customContext: SslContext   = _
  @volatile private var http2Context: JdkSslContext = _
  @volatile private var http1Context: JdkSslContext = _

  override def init(config: AsyncHttpClientConfig): Unit = {
    if (config.getSslContext != null && !loose) {
      logger.warn(
        "The SslContext set on the AsyncHttpClient configuration is ignored, because Play WS creates its TLS engines " +
          "from the play.ws.ssl settings; only play.ws.ssl.loose.acceptAnyCertificate = true uses it. To take over " +
          "TLS, set an SslEngineFactory instead, which is then responsible for hostname verification and ALPN."
      )
    }
    customContext = if (loose) config.getSslContext else null
    if (config.isHttp2Enabled) {
      http2Context = newContext(config, alpn(ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1))
      http1Context = newContext(config, alpn(ApplicationProtocolNames.HTTP_1_1))
    } else {
      http2Context = newContext(config, ApplicationProtocolConfig.DISABLED)
      http1Context = http2Context
    }
  }

  override def newSslEngine(config: AsyncHttpClientConfig, peerHost: String, peerPort: Int): SSLEngine =
    newSslEngine(config, peerHost, peerPort, true)

  override def newSslEngine(
      config: AsyncHttpClientConfig,
      peerHost: String,
      peerPort: Int,
      http2Allowed: Boolean
  ): SSLEngine = {
    val engine =
      if (customContext != null) {
        // As AHC's own factory uses a configured context.
        if (config.isDisableHttpsEndpointIdentificationAlgorithm) customContext.newEngine(ByteBufAllocator.DEFAULT)
        else customContext.newEngine(ByteBufAllocator.DEFAULT, domain(peerHost), peerPort)
      } else {
        val context = if (http2Allowed) http2Context else http1Context
        val engine  = context.newEngine(ByteBufAllocator.DEFAULT, domain(peerHost), peerPort)
        if (!loose && !config.isDisableHttpsEndpointIdentificationAlgorithm) {
          val parameters = engine.getSSLParameters
          parameters.setEndpointIdentificationAlgorithm("HTTPS")
          engine.setSSLParameters(parameters)
        }
        engine
      }
    configureSslEngine(engine, config)
    engine
  }

  override def destroy(): Unit = {
    // AHC's own factory releases a configured context too.
    ReferenceCountUtil.release(customContext)
  }

  private def alpn(protocols: String*): ApplicationProtocolConfig =
    new ApplicationProtocolConfig(
      Protocol.ALPN,
      SelectorFailureBehavior.NO_ADVERTISE,
      SelectedListenerFailureBehavior.ACCEPT,
      protocols.asJava
    )

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
