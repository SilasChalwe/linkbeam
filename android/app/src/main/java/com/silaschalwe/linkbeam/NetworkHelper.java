package com.silaschalwe.linkbeam;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkHelper extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "NetworkHelper";
    private ReactApplicationContext reactContext;

    public NetworkHelper(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void getWifiIpAddress(Promise promise) {
        try {
            String ipAddress = getWifiIpAddressInternal();
            if (ipAddress != null && !ipAddress.equals("0.0.0.0")) {
                promise.resolve(ipAddress);
            } else {
                promise.reject("NO_WIFI_IP", "Could not determine WiFi IP address");
            }
        } catch (Exception e) {
            promise.reject("WIFI_IP_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void isWifiConnected(Promise promise) {
        try {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) reactContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            boolean isConnected = wifiInfo != null && wifiInfo.isConnected();
            
            promise.resolve(isConnected);
        } catch (Exception e) {
            promise.reject("WIFI_CHECK_ERROR", e.getMessage());
        }
    }

    private String getWifiIpAddressInternal() {
        try {
            // Method 1: Try WifiManager (works on older Android versions)
            WifiManager wifiManager = (WifiManager) reactContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ipInt = wifiInfo.getIpAddress();
                    if (ipInt != 0) {
                        String ip = String.format("%d.%d.%d.%d",
                            (ipInt & 0xff),
                            (ipInt >> 8 & 0xff),
                            (ipInt >> 16 & 0xff),
                            (ipInt >> 24 & 0xff));
                        
                        // Check if it's a valid private IP
                        if (isPrivateIpAddress(ip)) {
                            return ip;
                        }
                    }
                }
            }

            // Method 2: Try NetworkInterface enumeration (more reliable on newer Android)
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); 
                 en.hasMoreElements();) {
                
                NetworkInterface networkInterface = en.nextElement();
                
                // Look for WiFi interface
                if (networkInterface.getName().toLowerCase().contains("wlan") || 
                    networkInterface.getName().toLowerCase().contains("wifi")) {
                    
                    for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); 
                         enumIpAddr.hasMoreElements();) {
                        
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        
                        if (!inetAddress.isLoopbackAddress() && 
                            !inetAddress.isLinkLocalAddress() &&
                            inetAddress.getHostAddress().indexOf(':') == -1) { // IPv4 only
                            
                            String ip = inetAddress.getHostAddress();
                            if (isPrivateIpAddress(ip)) {
                                return ip;
                            }
                        }
                    }
                }
            }

            // Method 3: Fallback - find any private IP
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); 
                 en.hasMoreElements();) {
                
                NetworkInterface networkInterface = en.nextElement();
                
                for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); 
                     enumIpAddr.hasMoreElements();) {
                    
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    
                    if (!inetAddress.isLoopbackAddress() && 
                        !inetAddress.isLinkLocalAddress() &&
                        inetAddress.getHostAddress().indexOf(':') == -1) { // IPv4 only
                        
                        String ip = inetAddress.getHostAddress();
                        if (isPrivateIpAddress(ip)) {
                            return ip;
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error getting WiFi IP: " + e.getMessage());
        }

        return null;
    }

    private boolean isPrivateIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // Check for private IP ranges:
            // 10.0.0.0/8 (10.0.0.0 – 10.255.255.255)
            // 172.16.0.0/12 (172.16.0.0 – 172.31.255.255)
            // 192.168.0.0/16 (192.168.0.0 – 192.168.255.255)
            
            if (first == 10) {
                return true;
            } else if (first == 172 && second >= 16 && second <= 31) {
                return true;
            } else if (first == 192 && second == 168) {
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        return false;
    }
}