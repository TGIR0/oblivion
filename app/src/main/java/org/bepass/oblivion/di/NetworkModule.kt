package org.bepass.oblivion.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.bepass.oblivion.dns.AppDnsResolverFactory
import org.bepass.oblivion.logging.SecureLog as Log

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
  private const val TAG = "NetworkModule"
  private const val DEFAULT_TIMEOUT_SECONDS = 15L
  private const val CONNECTION_POOL_MAX_IDLE_CONNECTIONS = 5
  private const val CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5L

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient {
    val builder =
      OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionPool(
          ConnectionPool(
            CONNECTION_POOL_MAX_IDLE_CONNECTIONS,
            CONNECTION_POOL_KEEP_ALIVE_MINUTES,
            TimeUnit.MINUTES,
          )
        )
        .fastFallback(true) // Power feature: Happy Eyeballs v2

    try {
      builder.dns(AppDnsResolverFactory.createAppHttpDns())
    } catch (initializationFailure: Exception) {
      Log.w(
        TAG,
        "Failed to initialize configurable app DNS; falling back to system DNS",
        initializationFailure,
      )
    }

    return builder.build()
  }
}
