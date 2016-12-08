package play.api.libs.ws.ahc

import org.asynchttpclient.AsyncHttpClient
import play.api.inject.SimpleModule
import play.api.inject.bind
import play.api.libs.ws._

/**
 *
 */
class AhcWSModule extends SimpleModule(
  bind[AhcWSClientConfig].toProvider[AhcWSClientConfigParser],
  bind[WSClientConfig].toProvider[WSConfigParser],
  bind[AsyncHttpClient].toProvider[AsyncHttpClientProvider],
  bind[StandaloneAhcWSClient].toProvider[PlainAhcWSClientProvider],
  bind[WSClient].toProvider[WSClientProvider]
)
