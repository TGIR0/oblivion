package org.bepass.oblivion.logging

import timber.log.Timber

object SecureLog {
  fun d(tag: String, message: String) {
    Timber.tag(normalizeTag(tag)).d("event=%s", redact(message))
  }

  fun i(tag: String, message: String) {
    Timber.tag(normalizeTag(tag)).i("event=%s", redact(message))
  }

  fun w(tag: String, message: String) {
    Timber.tag(normalizeTag(tag)).w("event=%s", redact(message))
  }

  fun w(tag: String, message: String, cause: Throwable) {
    Timber.tag(normalizeTag(tag)).w("event=%s cause=%s", redact(message), redactedCause(cause))
  }

  fun e(tag: String, message: String) {
    Timber.tag(normalizeTag(tag)).e("event=%s", redact(message))
  }

  fun e(tag: String, message: String, cause: Throwable) {
    Timber.tag(normalizeTag(tag)).e("event=%s cause=%s", redact(message), redactedCause(cause))
  }

  internal fun redact(value: String): String {
    var redacted = value.replace(CONTROL_CHARACTERS, " ")
    redacted = SECRET_VALUE.replace(redacted) { "${it.groupValues[1]}=[REDACTED]" }
    redacted = URL.replace(redacted, "[URL_REDACTED]")
    redacted = IPV4.replace(redacted, "[IP_REDACTED]")
    redacted = IPV6.replace(redacted, "[IP_REDACTED]")
    redacted = HOSTNAME.replace(redacted, "[HOST_REDACTED]")
    return redacted.take(MAX_MESSAGE_LENGTH)
  }

  private fun redactedCause(cause: Throwable): String {
    val type = cause::class.java.simpleName.ifBlank { "Throwable" }
    val message = cause.message?.let(::redact).orEmpty()
    return if (message.isEmpty()) type else "$type:$message"
  }

  private fun normalizeTag(tag: String): String =
    tag
      .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
      .take(MAX_TAG_LENGTH)
      .ifBlank { "Oblivion" }

  private const val MAX_TAG_LENGTH = 64
  private const val MAX_MESSAGE_LENGTH = 512
  private val CONTROL_CHARACTERS = Regex("[\\r\\n\\t]+")
  private val SECRET_VALUE =
    Regex(
      """(?i)\b(license|token|access[_-]?token|private[_-]?key|password|secret)\s*[=:]\s*[^\s,;}\]]+"""
    )
  private val URL = Regex("""(?i)\b(?:https?|wss?)://[^\s]+""")
  private val IPV4 = Regex("""(?<![A-Za-z0-9])(?:\d{1,3}\.){3}\d{1,3}(?![A-Za-z0-9])""")
  private val IPV6 =
    Regex("""(?i)(?<![A-Za-z0-9])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![A-Za-z0-9])""")
  private val HOSTNAME = Regex("""(?i)\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}\b""")
}
