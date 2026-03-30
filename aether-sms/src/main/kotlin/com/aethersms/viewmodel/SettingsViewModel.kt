package com.aethersms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aethersms.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs  = PreferencesRepository(app)
    private val wifiMgr = WifiMessagingManager(app)

    val settings: StateFlow<AppSettings> = prefs.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val sims: StateFlow<List<WifiMessagingManager.SimInfo>> = flow {
        emit(wifiMgr.getAvailableSims())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkStatus: StateFlow<NetworkStatus> = flow {
        emit(wifiMgr.getNetworkStatus())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkStatus.CELL_ONLY)

    fun setDarkTheme(v: Boolean)         = viewModelScope.launch { prefs.setDarkTheme(v) }
    fun setUseSystemTheme(v: Boolean)    = viewModelScope.launch { prefs.setUseSystemTheme(v) }
    fun setVoiceLanguage(lang: String)   = viewModelScope.launch { prefs.setVoiceLanguage(lang) }
    fun setFontSize(size: Int)           = viewModelScope.launch { prefs.setFontSize(size) }
    fun setDefaultSim(subId: Int)        = viewModelScope.launch { prefs.setDefaultSim(subId) }
    fun setSmsGateway(gw: String)        = viewModelScope.launch { prefs.setSmsGateway(gw) }
    fun setSmsGatewayEmail(email: String)= viewModelScope.launch { prefs.setSmsGatewayEmail(email) }
    fun setLockApp(v: Boolean)           = viewModelScope.launch { prefs.setLockApp(v) }
    fun setNotifSound(v: Boolean)        = viewModelScope.launch { prefs.setNotifSound(v) }
    fun setNotifVibrate(v: Boolean)      = viewModelScope.launch { prefs.setNotifVibrate(v) }
    fun setAutoDeleteDays(d: Int)        = viewModelScope.launch { prefs.setAutoDeleteDays(d) }
    fun blockNumber(n: String)           = viewModelScope.launch { prefs.blockNumber(n) }
    fun unblockNumber(n: String)         = viewModelScope.launch { prefs.unblockNumber(n) }
}
