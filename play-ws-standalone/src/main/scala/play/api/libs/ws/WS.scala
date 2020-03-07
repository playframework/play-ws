/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws

/**
 *
 */
trait WSAuthScheme {
  // Purposely not sealed in case clients want to add their own auth schemes.
}

object WSAuthScheme {

  case object DIGEST extends WSAuthScheme

  case object BASIC extends WSAuthScheme

  case object NTLM extends WSAuthScheme

  case object SPNEGO extends WSAuthScheme

  case object KERBEROS extends WSAuthScheme

}

/**
 * A WS Cookie.
 */
trait WSCookie {

  /**
   * The cookie name.
   */
  def name: String

  /**
   * The cookie value.
   */
  def value: String

  /**
   * The domain.
   */
  def domain: Option[String]

  /**
   * The path.
   */
  def path: Option[String]

  /**
   * The maximum age.  If negative, then returns None.
   */
  def maxAge: Option[Long]

  /**
   * If the cookie is secure.
   */
  def secure: Boolean

  /**
   * If the cookie is HTTPOnly.
   */
  def httpOnly: Boolean
}

case class DefaultWSCookie(
    name: String,
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Long] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false
) extends WSCookie

/**
 * A WS proxy.
 */
trait WSProxyServer {

  /** The hostname of the proxy server. */
  def host: String

  /** The port of the proxy server. */
  def port: Int

  /** The protocol of the proxy server: "kerberos", "ntlm", "https" etc.  Defaults to "http" if not specified. */
  def protocol: Option[String]

  /** The proxy type, "http", "socksv4", or "socksv5".  Defaults to "http" if not specified. */
  def proxyType: Option[String]

  /** The principal (aka username) of the credentials for the proxy server. */
  def principal: Option[String]

  /** The password for the credentials for the proxy server. */
  def password: Option[String]

  def ntlmDomain: Option[String]

  /** The realm's charset. */
  def encoding: Option[String]

  def nonProxyHosts: Option[Seq[String]]
}

/**
 * A WS proxy.
 */
case class DefaultWSProxyServer(
    host: String,
    port: Int,
    protocol: Option[String] = None,
    proxyType: Option[String] = None,
    principal: Option[String] = None,
    password: Option[String] = None,
    ntlmDomain: Option[String] = None,
    encoding: Option[String] = None,
    nonProxyHosts: Option[Seq[String]] = None
) extends WSProxyServer

/**
 * Sign a WS call with OAuth.
 */
trait WSSignatureCalculator
