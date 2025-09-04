package com.silaschalwe.linkbeam

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import java.net.InetAddress
import java.net.NetworkInterface

class NetworkHelper(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val MODULE_NAME = "NetworkHelper"
    }

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun getWifiIpAddress(promise: Promise) {
        try {
            val ipAddress = wifiIpAddressInternal
            if (!ipAddress.isNullOrEmpty() && ipAddress != "0.0.0.0") {
                promise.resolve(ipAddress)
            } else {
                promise.reject("NO_WIFI_IP", "Could not determine WiFi IP address")
            }
        } catch (e: Exception) {
            promise.reject("WIFI_IP_ERROR", e.message)
        }
    }

    @ReactMethod
    fun isWifiConnected(promise: Promise) {
        try {
            val connectivityManager = reactContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            
            if (connectivityManager == null) {
                promise.resolve(false)
                return
            }

            val wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            val isConnected = wifiInfo?.isConnected == true
            
            promise.resolve(isConnected)
        } catch (e: Exception) {
            promise.reject("WIFI_CHECK_ERROR", e.message)
        }
    }

    @ReactMethod
    fun getNetworkInfo(promise: Promise) {
        try {
            val networkInfo = Arguments.createMap().apply {
                // Get WiFi IP
                putString("wifiIp", wifiIpAddressInternal)
                
                // Get all available IPs
                val allIps = getAllNetworkIps()
                putString("allIps", allIps.joinToString(", "))
                
                // Check WiFi connection status
                putBoolean("isWifiConnected", isWifiConnectedInternal())
                
                // Get WiFi SSID if available
                putString("wifiSSID", wifiSSID)
                
                // Device info
                putString("deviceModel", Build.MODEL)
                putString("androidVersion", Build.VERSION.RELEASE)
            }
            
            promise.resolve(networkInfo)
        } catch (e: Exception) {
            promise.reject("NETWORK_INFO_ERROR", e.message)
        }
    }

    // Make this property public so SimpleHttpServer can use it
    val wifiIpAddressInternal: String?
        get() {
            return try {
                // Method 1: Try WifiManager (works on older Android versions)
                getIpFromWifiManager()
                    // Method 2: Try NetworkInterface enumeration (more reliable on newer Android)
                    ?: getIpFromNetworkInterface()
                    // Method 3: Fallback - find any private IP
                    ?: getFallbackPrivateIp()
            } catch (e: Exception) {
                System.err.println("Error getting WiFi IP: ${e.message}")
                null
            }
        }

    private fun getIpFromWifiManager(): String? {
        return try {
            val wifiManager = reactContext.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            
            if (wifiManager?.isWifiEnabled == true) {
                val wifiInfo = wifiManager.connectionInfo
                wifiInfo?.let {
                    val ipInt = it.ipAddress
                    if (ipInt != 0) {
                        val ip = String.format(
                            "%d.%d.%d.%d",
                            ipInt and 0xff,
                            ipInt shr 8 and 0xff,
                            ipInt shr 16 and 0xff,
                            ipInt shr 24 and 0xff
                        )
                        
                        // Check if it's a valid private IP
                        if (isPrivateIpAddress(ip)) ip else null
                    } else null
                }
            } else null
        } catch (e: Exception) {
            System.err.println("Error getting IP from WifiManager: ${e.message}")
            null
        }
    }

    private fun getIpFromNetworkInterface(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstNotNullOfOrNull { networkInterface ->
                    val interfaceName = networkInterface.name.lowercase()
                    
                    // Look for WiFi interface (more comprehensive check)
                    if (interfaceName.contains("wlan") || 
                        interfaceName.contains("wifi") ||
                        interfaceName.contains("wl")) {
                        
                        networkInterface.inetAddresses?.toList()
                            ?.firstNotNullOfOrNull { inetAddress ->
                                if (!inetAddress.isLoopbackAddress && 
                                    !inetAddress.isLinkLocalAddress &&
                                    ':' !in inetAddress.hostAddress) { // IPv4 only
                                    
                                    val ip = inetAddress.hostAddress
                                    if (isPrivateIpAddress(ip)) ip else null
                                } else null
                            }
                    } else null
                }
        } catch (e: Exception) {
            System.err.println("Error getting IP from NetworkInterface: ${e.message}")
            null
        }
    }

    private fun getFallbackPrivateIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filterNot { it.isLoopback }
                ?.firstNotNullOfOrNull { networkInterface ->
                    networkInterface.inetAddresses?.toList()
                        ?.firstNotNullOfOrNull { inetAddress ->
                            if (!inetAddress.isLoopbackAddress && 
                                !inetAddress.isLinkLocalAddress &&
                                ':' !in inetAddress.hostAddress) { // IPv4 only
                                
                                val ip = inetAddress.hostAddress
                                if (isPrivateIpAddress(ip)) ip else null
                            } else null
                        }
                }
        } catch (e: Exception) {
            System.err.println("Error getting fallback IP: ${e.message}")
            null
        }
    }

    private fun getAllNetworkIps(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { networkInterface ->
                    networkInterface.inetAddresses?.toList()
                        ?.mapNotNull { inetAddress ->
                            if (!inetAddress.isLoopbackAddress && 
                                ':' !in inetAddress.hostAddress) { // IPv4 only
                                
                                val ip = inetAddress.hostAddress
                                if (isPrivateIpAddress(ip)) {
                                    "$ip (${networkInterface.name})"
                                } else null
                            } else null
                        } ?: emptyList()
                } ?: emptyList()
        } catch (e: Exception) {
            System.err.println("Error getting all IPs: ${e.message}")
            emptyList()
        }
    }

    private fun isWifiConnectedInternal(): Boolean {
        return try {
            val connectivityManager = reactContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiInfo = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            wifiInfo?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    private val wifiSSID: String?
        get() = try {
            val wifiManager = reactContext.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            
            if (wifiManager?.isWifiEnabled == true) {
                val wifiInfo = wifiManager.connectionInfo
                wifiInfo?.ssid?.let { ssid ->
                    if (ssid != "<unknown ssid>") {
                        // Remove quotes if present
                        ssid.replace("\"", "")
                    } else null
                }
            } else null
        } catch (e: Exception) {
            System.err.println("Error getting WiFi SSID: ${e.message}")
            null
        }

    private fun isPrivateIpAddress(ip: String?): Boolean {
        if (ip.isNullOrEmpty()) return false

        val parts = ip.split(".")
        if (parts.size != 4) return false

        return try {
            val first = parts[0].toInt()
            val second = parts[1].toInt()

            // Check for private IP ranges:
            // 10.0.0.0/8 (10.0.0.0 – 10.255.255.255)
            // 172.16.0.0/12 (172.16.0.0 – 172.31.255.255)
            // 192.168.0.0/16 (192.168.0.0 – 192.168.255.255)
            
            when (first) {
                10 -> true
                172 -> second in 16..31
                192 -> second == 168
                else -> false
            }
        } catch (e: NumberFormatException) {
            false
        }
    }
}