package org.bepass.oblivion.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.bepass.oblivion.enums.ConnectionState
import org.bepass.oblivion.model.IPDetails
import org.bepass.oblivion.platform.VpnServiceConnector
import org.bepass.oblivion.utils.PublicIPUtils

@HiltViewModel
class MainViewModel
@Inject
constructor(
  private val vpnServiceConnector: VpnServiceConnector,
  private val publicIPUtils: PublicIPUtils,
) : ViewModel() {
  val connectionState: StateFlow<ConnectionState> = vpnServiceConnector.connectionState

  private val _publicIpDetails = MutableStateFlow<IPDetails?>(null)
  val publicIpDetails: StateFlow<IPDetails?> = _publicIpDetails.asStateFlow()

  private val _isFetchingPublicIp = MutableStateFlow(false)
  val isFetchingPublicIp: StateFlow<Boolean> = _isFetchingPublicIp.asStateFlow()

  private var fetchPublicIpJob: Job? = null

  init {
    vpnServiceConnector.ensureBound()
    viewModelScope.launch {
      connectionState.collectLatest { state ->
        if (state == ConnectionState.CONNECTED) {
          refreshPublicIp()
        } else {
          fetchPublicIpJob?.cancel()
          fetchPublicIpJob = null
          _publicIpDetails.value = null
          _isFetchingPublicIp.value = false
        }
      }
    }
  }

  fun refreshPublicIp() {
    fetchPublicIpJob?.cancel()
    fetchPublicIpJob =
      viewModelScope.launch {
        _isFetchingPublicIp.value = true
        try {
          _publicIpDetails.value = publicIPUtils.fetchIpDetails()
        } finally {
          _isFetchingPublicIp.value = false
        }
      }
  }
}
