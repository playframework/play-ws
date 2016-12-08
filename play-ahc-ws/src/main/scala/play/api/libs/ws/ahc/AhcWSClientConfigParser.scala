package play.api.libs.ws.ahc

import javax.inject.{Inject, Provider, Singleton}

import play.api.{ConfigLoader, Configuration, Environment}
import play.api.libs.ws.WSClientConfig

import scala.concurrent.duration.Duration

/**
 * This class creates a DefaultWSClientConfig object from the play.api.Configuration.
 */
@Singleton
class AhcWSClientConfigParser @Inject() (
                                          wsClientConfig: WSClientConfig,
                                          configuration: Configuration,
                                          environment: Environment) extends Provider[AhcWSClientConfig] {

  def get = parse()

  def parse(): AhcWSClientConfig = {

    def get[A: ConfigLoader](name: String): A =
      configuration.getDeprecated[A](s"play.ws.ahc.$name", s"play.ws.ning.$name")

    val maximumConnectionsPerHost = get[Int]("maxConnectionsPerHost")
    val maximumConnectionsTotal = get[Int]("maxConnectionsTotal")
    val maxConnectionLifetime = get[Duration]("maxConnectionLifetime")
    val idleConnectionInPoolTimeout = get[Duration]("idleConnectionInPoolTimeout")
    val maximumNumberOfRedirects = get[Int]("maxNumberOfRedirects")
    val maxRequestRetry = get[Int]("maxRequestRetry")
    val disableUrlEncoding = get[Boolean]("disableUrlEncoding")
    val keepAlive = get[Boolean]("keepAlive")

    // allowPoolingConnection and allowSslConnectionPool were merged into keepAlive in AHC 2.0
    // We want one value, keepAlive, and we don't want to confuse anyone who has to migrate.
    // keepAlive
    if (configuration.underlying.hasPath("play.ws.ahc.keepAlive")) {
      val msg = "Both allowPoolingConnection and allowSslConnectionPool have been replaced by keepAlive!"
      Seq("play.ws.ning.allowPoolingConnection", "play.ws.ning.allowSslConnectionPool").foreach { s =>
        if (configuration.underlying.hasPath(s)) {
          throw configuration.reportError(s, msg)
        }
      }
    }
    AhcWSClientConfig(
      wsClientConfig = wsClientConfig,
      maxConnectionsPerHost = maximumConnectionsPerHost,
      maxConnectionsTotal = maximumConnectionsTotal,
      maxConnectionLifetime = maxConnectionLifetime,
      idleConnectionInPoolTimeout = idleConnectionInPoolTimeout,
      maxNumberOfRedirects = maximumNumberOfRedirects,
      maxRequestRetry = maxRequestRetry,
      disableUrlEncoding = disableUrlEncoding,
      keepAlive = keepAlive
    )
  }
}
