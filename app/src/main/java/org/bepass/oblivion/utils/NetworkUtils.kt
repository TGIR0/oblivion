package org.bepass.oblivion.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address

object NetworkUtils {
  @JvmStatic
  fun getLocalIpAddress(context: Context): String? {
    val connectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null

    return connectivityManager.activeNetwork?.let { network ->
      val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@let null
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@let null

      val linkProperties = connectivityManager.getLinkProperties(network) ?: return@let null

      linkProperties.linkAddresses
        .asSequence()
        .map(LinkAddress::getAddress)
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
    }
  }
}
