package play.api.libs.ws

trait WSRequestFilter {
  def apply(next: WSRequestExecutor): WSRequestExecutor
}
