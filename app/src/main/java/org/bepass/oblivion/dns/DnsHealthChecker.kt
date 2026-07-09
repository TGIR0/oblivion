package org.bepass.oblivion.dns

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object DnsHealthChecker {
  private const val DEFAULT_TIMEOUT_MS = 3_500
  private const val MAX_CONCURRENCY = 6
  private const val PROBE_HOST = "example.com"
  private val dnsMessageMediaType = "application/dns-message".toMediaType()

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
      .readTimeout(DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
      .callTimeout((DEFAULT_TIMEOUT_MS * 2).toLong(), TimeUnit.MILLISECONDS)
      .build()

  suspend fun scan(
    providers: List<DnsProvider>,
    transport: DnsTransport,
    onResult: (DnsHealthResult) -> Unit = {},
  ): List<DnsHealthResult> = coroutineScope {
    val semaphore = Semaphore(max(1, minOf(MAX_CONCURRENCY, providers.size)))
    providers
      .map { provider ->
        async(Dispatchers.IO) {
          semaphore.withPermit {
            probe(provider, transport).also(onResult)
          }
        }
      }
      .awaitAll()
  }

  suspend fun probe(provider: DnsProvider, transport: DnsTransport): DnsHealthResult =
    withContext(Dispatchers.IO) {
      val transactionId = ThreadLocalRandom.current().nextInt(0, 65_536)
      val query = buildDnsQuery(PROBE_HOST, transactionId)
      val startedAt = System.nanoTime()

      try {
        when (transport) {
          DnsTransport.PLAIN,
          DnsTransport.UDP -> probePlainUdp(provider, query, transactionId)
          DnsTransport.TCP -> probePlainTcp(provider, query, transactionId)
          DnsTransport.DOH -> probeDoh(provider, query, transactionId)
          DnsTransport.DOT -> probeDot(provider, query, transactionId)
          else -> error("${transport.name} health checks are not supported")
        }
        DnsHealthResult(
          providerId = provider.providerId,
          transport = transport,
          isAvailable = true,
          latencyMs = elapsedMs(startedAt),
        )
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (probeFailure: Exception) {
        DnsHealthResult(
          providerId = provider.providerId,
          transport = transport,
          isAvailable = false,
          errorMessage =
            probeFailure.message?.take(MAX_ERROR_MESSAGE_LENGTH)
              ?: probeFailure::class.java.simpleName,
        )
      }
    }

  private fun probePlainUdp(provider: DnsProvider, query: ByteArray, transactionId: Int) {
    val addresses = provider.plainIps.ifEmpty { error("No Plain DNS address") }
    var lastError: Throwable? = null
    addresses.forEach { address ->
      try {
        DatagramSocket().use { socket ->
          socket.soTimeout = DEFAULT_TIMEOUT_MS
          val target = InetSocketAddress(InetAddress.getByName(address), provider.portFor(DNS_PORT))
          socket.send(DatagramPacket(query, query.size, target))
          val response = ByteArray(4_096)
          val packet = DatagramPacket(response, response.size)
          socket.receive(packet)
          requireValidResponse(response.copyOf(packet.length), transactionId)
          return
        }
      } catch (probeFailure: Exception) {
        lastError = probeFailure
      }
    }
    throw lastError ?: IllegalStateException("Plain DNS probe failed")
  }

  private fun probePlainTcp(provider: DnsProvider, query: ByteArray, transactionId: Int) {
    val addresses = provider.plainIps.ifEmpty { error("No Plain DNS address") }
    var lastError: Throwable? = null
    addresses.forEach { address ->
      try {
        Socket().use { socket ->
          socket.connect(
            InetSocketAddress(address, provider.portFor(DNS_PORT)),
            DEFAULT_TIMEOUT_MS,
          )
          socket.soTimeout = DEFAULT_TIMEOUT_MS
          writeFramedQuery(socket.getOutputStream().let(::DataOutputStream), query)
          val response = readFramedResponse(DataInputStream(socket.getInputStream()))
          requireValidResponse(response, transactionId)
          return
        }
      } catch (probeFailure: Exception) {
        lastError = probeFailure
      }
    }
    throw lastError ?: IllegalStateException("TCP DNS probe failed")
  }

  private fun probeDoh(provider: DnsProvider, query: ByteArray, transactionId: Int) {
    val url = provider.dohUrl ?: error("No DoH endpoint")
    val request =
      Request.Builder()
        .url(url)
        .header("Accept", dnsMessageMediaType.toString())
        .post(query.toRequestBody(dnsMessageMediaType))
        .build()
    httpClient.newCall(request).execute().use { response ->
      check(response.isSuccessful) { "DoH HTTP ${response.code}" }
      requireValidResponse(response.body.bytes(), transactionId)
    }
  }

  private fun probeDot(provider: DnsProvider, query: ByteArray, transactionId: Int) {
    val host = provider.dotHost ?: error("No DoT endpoint")
    val port = provider.ports[DnsTransport.DOT] ?: DNS_TLS_PORT
    val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
    socket.use {
      it.connect(InetSocketAddress(host, port), DEFAULT_TIMEOUT_MS)
      it.soTimeout = DEFAULT_TIMEOUT_MS
      it.sslParameters =
        it.sslParameters.apply {
          endpointIdentificationAlgorithm = "HTTPS"
        }
      it.startHandshake()
      writeFramedQuery(DataOutputStream(it.outputStream), query)
      val response = readFramedResponse(DataInputStream(it.inputStream))
      requireValidResponse(response, transactionId)
    }
  }

  private fun DnsProvider.portFor(defaultPort: Int): Int =
    ports[DnsTransport.PLAIN] ?: ports[DnsTransport.UDP] ?: ports[DnsTransport.TCP] ?: defaultPort

  private fun writeFramedQuery(output: DataOutputStream, query: ByteArray) {
    output.writeShort(query.size)
    output.write(query)
    output.flush()
  }

  private fun readFramedResponse(input: DataInputStream): ByteArray {
    val length = input.readUnsignedShort()
    check(length in DNS_HEADER_BYTES..DNS_MAX_MESSAGE_BYTES) { "Invalid DNS response length" }
    return ByteArray(length).also(input::readFully)
  }

  internal fun buildDnsQuery(host: String, transactionId: Int): ByteArray {
    require(transactionId in 0..DNS_TRANSACTION_ID_MAX)
    val output = ByteArrayOutputStream()
    DataOutputStream(output).use { data ->
      data.writeShort(transactionId)
      data.writeShort(DNS_RECURSION_DESIRED_FLAG)
      data.writeShort(1)
      data.writeShort(0)
      data.writeShort(0)
      data.writeShort(0)
      host.trimEnd('.').split('.').forEach { label ->
        val bytes = label.toByteArray(Charsets.US_ASCII)
        require(bytes.size in 1..DNS_MAX_LABEL_BYTES) { "Invalid DNS label" }
        data.writeByte(bytes.size)
        data.write(bytes)
      }
      data.writeByte(0)
      data.writeShort(1)
      data.writeShort(1)
    }
    return output.toByteArray()
  }

  internal fun isValidDnsResponse(response: ByteArray, transactionId: Int): Boolean {
    if (response.size < DNS_HEADER_BYTES) return false
    val responseId =
      ((response[0].toInt() and UNSIGNED_BYTE_MASK) shl Byte.SIZE_BITS) or
        (response[1].toInt() and UNSIGNED_BYTE_MASK)
    val flags =
      ((response[2].toInt() and UNSIGNED_BYTE_MASK) shl Byte.SIZE_BITS) or
        (response[3].toInt() and UNSIGNED_BYTE_MASK)
    val responseCode = flags and DNS_RESPONSE_CODE_MASK
    return responseId == transactionId &&
      flags and DNS_RESPONSE_FLAG != 0 &&
      responseCode in 0..DNS_MAX_VALID_RESPONSE_CODE
  }

  private fun requireValidResponse(response: ByteArray, transactionId: Int) {
    check(isValidDnsResponse(response, transactionId)) { "Invalid DNS response" }
  }

  private fun elapsedMs(startedAt: Long): Long =
    max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))

  private const val MAX_ERROR_MESSAGE_LENGTH = 120
}
