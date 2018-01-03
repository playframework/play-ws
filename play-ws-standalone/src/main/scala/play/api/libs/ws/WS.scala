/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
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
  httpOnly: Boolean = false) extends WSCookie

/**
 * A WS proxy.
 */
trait WSProxyServer {
  /** The hostname of the proxy server. */
  def host: String

  /** The port of the proxy server. */
  def port: Int

  /** The protocol of the proxy server.  Use "http" or "https".  Defaults to "http" if not specified. */
  def protocol: Option[String]

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
  /* The hostname of the proxy server. */
  host: String,

  /* The port of the proxy server. */
  port: Int,

  /* The protocol of the proxy server.  Use "http" or "https".  Defaults to "http" if not specified. */
  protocol: Option[String] = None,

  /* The principal (aka username) of the credentials for the proxy server. */
  principal: Option[String] = None,

  /* The password for the credentials for the proxy server. */
  password: Option[String] = None,

  ntlmDomain: Option[String] = None,

  /* The realm's charset. */
  encoding: Option[String] = None,

  nonProxyHosts: Option[Seq[String]] = None) extends WSProxyServer

/**
 * Sign a WS call with OAuth.
 */
trait WSSignatureCalculator
