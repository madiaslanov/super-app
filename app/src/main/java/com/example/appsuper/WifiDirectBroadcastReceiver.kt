package com.example.appsuper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: YourActivityInterface
) : BroadcastReceiver() {

    interface YourActivityInterface {
        fun onPeersAvailable(peers: List<android.net.wifi.p2p.WifiP2pDevice>)
        fun onConnectionInfoAvailable(info: android.net.wifi.p2p.WifiP2pInfo)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                Log.d("WifiDirect", "P2P state changed to ${if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "Enabled" else "Disabled"}")
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d("WifiDirect", "P2P peers changed")
                manager.requestPeers(channel) { peers ->
                    activity.onPeersAvailable(peers.deviceList.toList())
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Log.d("WifiDirect", "P2P connection changed")
                manager.requestConnectionInfo(channel) { info ->
                    if (info != null && info.groupFormed) {
                        activity.onConnectionInfoAvailable(info)
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Статус этого устройства изменился
            }
        }
    }
}