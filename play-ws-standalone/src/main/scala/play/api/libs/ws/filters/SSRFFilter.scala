/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.libs.ws.filters

import javax.inject.{Inject, Provider}

import com.typesafe.config.Config
import play.api.libs.ws.{WSRequestExecutor, WSRequestFilter}

import scala.concurrent.Future

/**
 * Only allows hosts matching configuration.
 *
 * Useful for preventing <a href="https://cwe.mitre.org/data/definitions/918.html">Server Side Request Forgery (SSRF)</a> attacks.
 */
@Singleton
class SSRFFilter @Inject()(configuration: SSRFFilterConfiguration) extends WSRequestFilter {

  private val safeUrl = new SafeURL(configuration)

  override def apply(executor: WSRequestExecutor): WSRequestExecutor = {
    WSRequestExecutor { r =>
      if (safeUrl.allowed(r.url)) {
        executor.apply(r)
      } else {
        Future.failed(new IllegalArgumentException(s"URL ${r.url} is not safe!"))
      }
    }
  }

}

/**
 * This class parses a SSRFFilterConfiguration from Typesafe Config.
 */
@Singleton
class SSRFFilterConfigurationProvider(config: Config) extends Provider[SSRFFilterConfiguration] {
  import SSRFFilterConfiguration._
  import scala.collection.JavaConverters._

  lazy val get: SSRFFilterConfiguration = {
    val safeURLConfig = config.getConfig("play.ws.requestFilters.safeURL")
    val secureRedirects = safeURLConfig.getBoolean("secureRedirects")
    val maxRedirects = safeURLConfig.getInt("maxRedirects")
    val pinDNS = safeURLConfig.getBoolean("pinDNS")
    val allowDefaultPort = safeURLConfig.getBoolean("allowDefaultPort")

    def accessList(list: String) = {
      val config = safeURLConfig.getConfig(list)
      AccessList(
        whitelist = config.getStringList("whitelist").asScala.toList,
        blacklist = config.getStringList("blacklist").asScala.toList
      )
    }

    SSRFFilterConfiguration(
      secureRedirects = secureRedirects,
      maxRedirects = maxRedirects,
      pinDNS = pinDNS,
      allowDefaultPort = allowDefaultPort,
      lists = ListContainer(
        protocol = accessList("protocol"),
        port = accessList("port"),
        ip = accessList("ip"),
        domain = accessList("domain")
      )
    )
  }
}

/**
 *  SSRFFilter configuration.
 *
 * Stores the black- and whitelists used by SafeURL as well
 * as some other configuration properties.
 *
 * Has secure defaults.
 */
case class SSRFFilterConfiguration(
  /** Do secure redirects, revalidate each redirect location first. */
  secureRedirects: Boolean = true,

  /** The maximum number of redirects SaveCurl will follow. */
  maxRedirects: Int = 20,

  supportIPv6: Boolean = false,

  /** Determines whether SafeURL will pin DNS entries, preventing DNS rebinding attacks. */
  pinDNS: Boolean = true,

  /** When a protocol is allowed also allow its default port. */
  allowDefaultPort: Boolean = true,

  /** Access lists for the various parts of a URL. */
  lists: SSRFFilterConfiguration.ListContainer = SSRFFilterConfiguration.defaultAccessLists
)

object SSRFFilterConfiguration {

  /** Access lists for the various parts of a URL. */
  case class AccessList(
                         whitelist: List[String] = Nil,
                         blacklist: List[String] = Nil
                       )

  /** Contains an AccessList for each part of the URL that is validated. */
  case class ListContainer(
                            ip: AccessList = new AccessList,
                            port: AccessList = new AccessList,
                            domain: AccessList = new AccessList,
                            protocol: AccessList = new AccessList
                          )

  def defaultAccessLists: ListContainer = {
    ListContainer(
      ip = AccessList(blacklist = "0.0.0.0/8" ::
        "10.0.0.0/8" ::
        "100.64.0.0/10" ::
        "127.0.0.0/8" ::
        "169.254.0.0/16" ::
        "172.16.0.0/12" ::
        "192.0.0.0/29" ::
        "192.0.2.0/24" ::
        "192.88.99.0/24" ::
        "192.168.0.0/16" ::
        "198.18.0.0/15" ::
        "198.51.100.0/24" ::
        "203.0.113.0/24" ::
        "224.0.0.0/4" ::
        "240.0.0.0/4" ::
        Nil),
      port = AccessList(whitelist = "80" :: "8080" :: "443" :: Nil),
      protocol = AccessList(whitelist = "http" :: "https" :: Nil)
    )
  }
}