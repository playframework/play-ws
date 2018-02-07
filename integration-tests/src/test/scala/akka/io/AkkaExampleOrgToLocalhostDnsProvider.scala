package akka.io

import java.net.InetAddress

import scala.concurrent.duration._

class AkkaExampleOrgToLocalhostDnsProvider extends DnsProvider {
  override def cache: Dns = {
    val cache = new SimpleDnsCache()
    cache.put(Dns.Resolved("akka.example.org", Seq(InetAddress.getByName("127.0.0.1"))), 1.hour.toMillis)
    cache
  }
  override def actorClass = classOf[InetAddressDnsResolver]
  override def managerClass = classOf[SimpleDnsManager]
}
