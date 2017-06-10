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
 * A WS Cookie.  This is a trait so that we are not tied to a specific client.
 */
trait WSCookie {

  /**
   * The underlying "native" cookie object for the client.
   */
  def underlying[T]: T

  /**
   * The domain.
   */
  def domain: String

  /**
   * The cookie name.
   */
  def name: Option[String]

  /**
   * The cookie value.
   */
  def value: Option[String]

  /**
   * The path.
   */
  def path: String

  /**
   * The maximum age.
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

  nonProxyHosts: Option[Seq[String]] = None
) extends WSProxyServer

/**
 * Sign a WS call with OAuth.
 */
trait WSSignatureCalculator
