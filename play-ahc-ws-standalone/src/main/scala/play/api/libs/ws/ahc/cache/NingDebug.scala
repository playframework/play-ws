package play.api.libs.ws.ahc.cache

import play.api.libs.ws.ahc.CaseInsensitiveOrdered
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import play.shaded.ahc.org.asynchttpclient._

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap

/**
 * Useful Ning header mapping.
 */
trait NingUtilities {

  def headersToMap(headers: HttpHeaders): TreeMap[String, Seq[String]] = {
    val mutableMap = scala.collection.mutable.HashMap[String, Seq[String]]()
    headers.names().asScala.foreach { name =>
      mutableMap.put(name, headers.getAll(name).asScala)
    }
    TreeMap[String, Seq[String]]()(CaseInsensitiveOrdered) ++ mutableMap
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
        s"HttpResponseHeaders(${headersToMap(rh.getHeaders)})"
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
