package org.bepass.oblivion.model

data class IPDetails
@JvmOverloads
constructor(
  @JvmField var ip: String? = null,
  @JvmField var country: String? = null,
  @JvmField var flag: String? = null,
)
