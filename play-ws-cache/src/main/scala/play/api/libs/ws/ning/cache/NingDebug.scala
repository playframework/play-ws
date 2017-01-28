package play.api.libs.ws.ning.cache

import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient._

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Useful Ning header mapping.
 */
trait NingUtilities {

  def ningHeadersToMap(headers: java.util.Map[String, java.util.Collection[String]]): Map[String, Seq[String]] =
    mapAsScalaMapConverter(headers).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap

  // XXX This is repeated in the AHC module, should remove
  def ningHeadersToMap(headers: HttpHeaders): Map[String, Seq[String]] = {
    val res: mutable.Seq[(String, String)] = headers.entries().asScala.map(e => e.getKey -> e.getValue)
    val map = res.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
    map // XXX expects CaseInsensitiveOrdered?
  }

}

/**
 * Ning header debugging trait.
 */
trait NingDebug extends NingUtilities {

  def debug(cfg: AsyncHttpClientConfig): String = {
    s"AsyncHttpClientConfig(requestFilters = ${cfg.getRequestFilters})"
  }

  def debug(request: Request): String = {
    Option(request).map { r =>
      s"Request(${r.getMethod} ${r.getUrl})"
    }.getOrElse("null")
  }

  def debug(response: Response): String = {
    Option(response).map {
      case cr: CacheableResponse =>
        cr.toString
      case r =>
        s"Response(${r.getStatusCode} ${r.getStatusText})"
    }.getOrElse("null")
  }

  def debug(responseStatus: HttpResponseStatus): String = {
    Option(responseStatus).map {
      case cs: CacheableHttpResponseStatus =>
        cs.toString
      case s =>
        s"HttpResponseStatus(${s.getProtocolName} ${s.getStatusCode} ${s.getStatusText})"
    }.getOrElse("null")
  }

  def debug(responseHeaders: HttpResponseHeaders): String = {
    Option(responseHeaders).map {
      case crh: CacheableHttpResponseHeaders =>
        crh.toString
      case rh =>
        s"HttpResponseHeaders(${ningHeadersToMap(rh.getHeaders)})"
    }.getOrElse("null")
  }

  def debug(bodyParts: java.util.List[HttpResponseBodyPart]): String = {
    import collection.JavaConverters._
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
