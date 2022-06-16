/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc.cache

import play.api.libs.ws.ahc.AhcUtilities
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient._

/**
 * Debugging trait.
 */
private[ahc] trait Debug extends AhcUtilities {

  def debug(cfg: AsyncHttpClientConfig): String = {
    s"AsyncHttpClientConfig(requestFilters = ${cfg.getRequestFilters})"
  }

  def debug(request: Request): String = {
    Option(request)
      .map { r =>
        s"Request(${r.getMethod} ${r.getUrl})"
      }
      .getOrElse("null")
  }

  def debug(response: Response): String = {
    Option(response)
      .map {
        case cr: CacheableResponse =>
          cr.toString
        case r =>
          s"Response(${r.getStatusCode} ${r.getStatusText})"
      }
      .getOrElse("null")
  }

  def debug(responseStatus: HttpResponseStatus): String = {
    Option(responseStatus)
      .map {
        case cs: CacheableHttpResponseStatus =>
          cs.toString
        case s =>
          s"HttpResponseStatus(${s.getProtocolName} ${s.getStatusCode} ${s.getStatusText})"
      }
      .getOrElse("null")
  }

  def debug(responseHeaders: HttpHeaders): String = {
    Option(responseHeaders)
      .map { rh =>
        s"HttpResponseHeaders(${headersToMap(rh)})"
      }
      .getOrElse("null")
  }

  def debug(bodyParts: java.util.List[HttpResponseBodyPart]): String = {
    import scala.jdk.CollectionConverters._
    bodyParts.asScala.map(debug).toString()
  }

  def debug[T](handler: AsyncHandler[T]): String = {
    s"AsyncHandler($handler)"
  }

  def debug[T](ctx: play.shaded.ahc.org.asynchttpclient.filter.FilterContext[T]): String = {
    s"FilterContext(request = ${debug(ctx.getRequest)}}, responseStatus = ${debug(ctx.getResponseStatus)}}, responseHeaders = ${debug(ctx.getResponseHeaders)}})"
  }

  def debug(bodyPart: HttpResponseBodyPart): String = {
    bodyPart match {
      case cbp: CacheableHttpResponseBodyPart =>
        cbp.toString
      case otherBodyPart =>
        s"HttpResponseBodyPart(length = ${otherBodyPart.length()}})"
    }
  }
}
