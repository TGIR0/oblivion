package org.bepass.oblivion.dns

internal const val DNS_PORT = 53
internal const val DNS_TLS_PORT = 853
internal const val DNS_HTTPS_PORT = 443
internal const val DNS_HEADER_BYTES = 12
internal const val DNS_MAX_MESSAGE_BYTES = 65_535
internal const val DNS_MAX_LABEL_BYTES = 63
internal const val DNS_TRANSACTION_ID_MAX = 65_535
internal const val DNS_RECURSION_DESIRED_FLAG = 0x0100
internal const val DNS_RESPONSE_FLAG = 0x8000
internal const val DNS_RESPONSE_CODE_MASK = 0x000F
internal const val DNS_MAX_VALID_RESPONSE_CODE = 5
internal const val UNSIGNED_BYTE_MASK = 0xFF
