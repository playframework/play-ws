/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import com.typesafe.sslconfig.util.LoggerFactory
import com.typesafe.sslconfig.util.NoDepsLogger
import org.slf4j.ILoggerFactory
import org.slf4j.{ LoggerFactory => SLF4JLoggerFactory }

class AhcLoggerFactory(lf: ILoggerFactory = SLF4JLoggerFactory.getILoggerFactory) extends LoggerFactory {

  private[ahc] def createLogger(name: String) = {
    new NoDepsLogger {
      private[ahc] val logger = lf.getLogger(name)

      def warn(msg: String): Unit                        = logger.warn(msg)
      def isDebugEnabled: Boolean                        = logger.isDebugEnabled
      def error(msg: String): Unit                       = logger.error(msg)
      def error(msg: String, throwable: Throwable): Unit = logger.error(msg, throwable)
      def debug(msg: String): Unit                       = logger.debug(msg)
      def info(msg: String): Unit                        = logger.info(msg)
    }
  }

  def apply(clazz: Class[?]): NoDepsLogger = createLogger(clazz.getName)
  def apply(name: String): NoDepsLogger    = createLogger(name)

}
