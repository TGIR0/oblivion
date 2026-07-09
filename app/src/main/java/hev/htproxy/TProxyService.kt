@file:Suppress("FunctionName")

package hev.htproxy

class TProxyService {
  external fun TProxyStartService(config: String, tunFd: Int)

  external fun TProxyStopService()

  external fun TProxyGetStats(): LongArray

  external fun TProxyIsRunning(): Boolean

  companion object {
    init {
      System.loadLibrary("hev-socks5-tunnel")
    }
  }
}
