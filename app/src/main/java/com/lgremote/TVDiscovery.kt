package com.lgremote

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.regex.Pattern

class TVDiscovery(private val context: Context) {

    suspend fun scan(timeoutMs: Int = 4000): List<TVDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<TVDevice>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifiManager.createMulticastLock("SSDP_SCAN")
        
        try {
            lock.acquire()
            val group = InetAddress.getByName("239.255.255.250")
            val port = 1900
            val query = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"

            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs
            
            val sendPacket = DatagramPacket(query.toByteArray(), query.length, group, port)
            socket.send(sendPacket)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val buffer = ByteArray(1024)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val device = parseResponse(response)
                    if (device != null && !devices.any { it.ip == device.ip }) {
                        devices.add(device)
                    }
                } catch (e: Exception) {
                    // Timeout or error
                }
            }
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (lock.isHeld) lock.release()
        }
        devices
    }

    private fun parseResponse(response: String): TVDevice? {
        val locationPattern = Pattern.compile("LOCATION: http://([^:/]+):", Pattern.CASE_INSENSITIVE)
        val serverPattern = Pattern.compile("DLNADeviceName.lge.com: ([^\\r\\n]+)", Pattern.CASE_INSENSITIVE)
        
        // Some devices might use friendly name in USN or other headers
        val friendlyNamePattern = Pattern.compile("SERVER: .*LG WebOS TV.*", Pattern.CASE_INSENSITIVE)

        val matcherLocation = locationPattern.matcher(response)
        if (matcherLocation.find()) {
            val ip = matcherLocation.group(1) ?: return null
            
            // Extract friendly name if possible, fallback to IP
            val nameMatcher = serverPattern.matcher(response)
            val friendlyName = if (nameMatcher.find()) nameMatcher.group(1) else "LG TV ($ip)"
            
            return TVDevice(ip, friendlyName!!)
        }
        return null
    }
}
