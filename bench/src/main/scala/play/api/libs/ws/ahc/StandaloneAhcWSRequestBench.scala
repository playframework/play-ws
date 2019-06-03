/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.ws.ahc

import java.util.concurrent.TimeUnit

import akka.stream.Materializer
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/**
 * ==Quick Run from sbt==
 *
 * > bench/jmh:run .*StandaloneAhcWSRequestBench
 *
 * ==Using Oracle Flight Recorder==
 *
 * To record a Flight Recorder file from a JMH run, run it using the jmh.extras.JFR profiler:
 * > bench/jmh:run -prof jmh.extras.JFR .*StandaloneAhcWSRequestBench
 *
 * Compare your results before/after on your machine. Don't trust the ones in scaladoc.
 *
 * Sample benchmark results:
 * {{{
 * > bench/jmh:run .*StandaloneAhcWSRequestBench
 * [info] Benchmark                                  Mode  Cnt     Score    Error  Units
 * [info] StandaloneAhcWSRequestBench.urlNoParams    avgt    5   326.443 ±  3.712  ns/op
 * [info] StandaloneAhcWSRequestBench.urlWithParams  avgt    5  1562.871 ± 16.736  ns/op
 * }}}
 *
 * @see https://github.com/ktoso/sbt-jmh
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(jvmArgsAppend = Array("-Xmx350m", "-XX:+HeapDumpOnOutOfMemoryError"), value = 1)
@State(Scope.Benchmark)
class StandaloneAhcWSRequestBench {

  private implicit val materializer: Materializer = null // we're not actually going to execute anything.
  private val wsClient = StandaloneAhcWSClient()

  @Benchmark
  def urlNoParams(bh: Blackhole): Unit = {
    bh.consume(wsClient.url("https://www.example.com/foo/bar/a/b"))
  }

  @Benchmark
  def urlWithParams(bh: Blackhole): Unit = {
    bh.consume(wsClient.url("https://www.example.com?foo=bar& = "))
  }

  @TearDown
  def teardown(): Unit = wsClient.close()
}
