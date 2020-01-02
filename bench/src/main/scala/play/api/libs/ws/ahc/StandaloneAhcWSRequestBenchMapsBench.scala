/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.TimeUnit

import akka.stream.Materializer
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import play.api.libs.ws.StandaloneWSRequest

/**
 * Tests Map-backed features of [[StandaloneAhcWSRequest]].
 *
 * ==Quick Run from sbt==
 *
 * > bench/jmh:run .*StandaloneAhcWSRequestBenchMapsBench
 *
 * ==Using Oracle Flight Recorder==
 *
 * To record a Flight Recorder file from a JMH run, run it using the jmh.extras.JFR profiler:
 * > bench/jmh:run -prof jmh.extras.JFR .*StandaloneAhcWSRequestBenchMapsBench
 *
 * Compare your results before/after on your machine. Don't trust the ones in scaladoc.
 *
 * Sample benchmark results:
 *
 * {{{
 * // not compilable
 * > bench/jmh:run .*StandaloneAhcWSRequestBenchMapsBench
 * [info] Benchmark                                             (size)  Mode  Cnt    Score   Error  Units
 * [info] StandaloneAhcWSRequestBenchMapsBench.addHeaders            1  avgt       162.673          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addHeaders           10  avgt       195.672          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addHeaders          100  avgt       278.829          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addHeaders         1000  avgt       356.446          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addHeaders        10000  avgt       308.384          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addQueryParams        1  avgt        42.123          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addQueryParams       10  avgt        82.650          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addQueryParams      100  avgt        90.095          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addQueryParams     1000  avgt       123.221          ns/op
 * [info] StandaloneAhcWSRequestBenchMapsBench.addQueryParams    10000  avgt       141.556          ns/op
 * }}}
 *
 * @see https://github.com/ktoso/sbt-jmh
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(jvmArgsAppend = Array("-Xmx350m", "-XX:+HeapDumpOnOutOfMemoryError"), value = 1)
@State(Scope.Benchmark)
class StandaloneAhcWSRequestBenchMapsBench {

  private implicit val materializer: Materializer = null // we're not actually going to execute anything.
  private var exampleRequest: StandaloneWSRequest = _

  @Param(Array("1", "10", "100", "1000", "10000"))
  private var size: Int = _

  @Setup def setup(): Unit = {
    val params = (1 to size)
      .map(_.toString)
      .map(s => s -> s)

    exampleRequest = StandaloneAhcWSRequest(new StandaloneAhcWSClient(null), "https://www.example.com")
      .addQueryStringParameters(params: _*)
      .addHttpHeaders(params: _*)
  }

  @Benchmark
  def addQueryParams(bh: Blackhole): Unit = {
    bh.consume(exampleRequest.addQueryStringParameters("nthParam" -> "nthParam"))
  }

  @Benchmark
  def addHeaders(bh: Blackhole): Unit = {
    bh.consume(exampleRequest.addHttpHeaders("nthHeader" -> "nthHeader"))
  }
}
