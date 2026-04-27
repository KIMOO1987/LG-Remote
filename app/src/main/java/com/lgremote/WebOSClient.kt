package com.lgremote

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class WebOSClient(val ip: String) {

    enum class State { DISCONNECTED, CONNECTING, PAIRING, CONNECTED }

    interface Listener {
        fun onStateChange(state: State)
        fun onVolumeUpdate(volume: Int, muted: Boolean)
        fun onChannelUpdate(channelName: String)
        fun onPairingPrompt()
        fun onPinRequired()
        fun onError(message: String)
        fun onConnected()
    }

    var listener: Listener? = null
    var state = State.DISCONNECTED
        private set(value) {
            field = value
            handler.post { listener?.onStateChange(value) }
        }

    private val client = OkHttpClient()
    private var mainSocket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private val msgId = AtomicInteger(1)
    private val callbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var clientKey: String? = null

    fun connect(savedKey: String?) {
        clientKey = savedKey
        state = State.CONNECTING
        val request = Request.Builder().url("ws://$ip:3000").build()
        mainSocket = client.newWebSocket(request, mainSocketListener)
    }

    fun disconnect() {
        mainSocket?.close(1000, "User disconnect")
        pointerSocket?.close(1000, "User disconnect")
        state = State.DISCONNECTED
    }

    private val mainSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            register(clientKey)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = JSONObject(text)
            val type = json.optString("type")
            val id = json.optString("id")
            val payload = json.optJSONObject("payload")

            when (type) {
                "registered" -> {
                    val key = payload?.optString("client-key")
                    clientKey = key
                    state = State.CONNECTED
                    handler.post { listener?.onConnected() }
                    setupSubscriptions()
                    openPointerSocket()
                }
                "response" -> {
                    callbacks[id]?.invoke(json)
                    callbacks.remove(id)
                }
                "error" -> {
                    val error = json.optString("error")
                    if (error.contains("401") || error.contains("permission")) {
                        handler.post { listener?.onError("Insufficient Permission. Reset 'LG Connect Apps' on TV.") }
                    } else if (error.contains("500")) {
                        handler.post { listener?.onError("TV Internal Error (500). Please restart your TV.") }
                    } else {
                        handler.post { listener?.onError("TV Error: $error") }
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            state = State.DISCONNECTED
            handler.post { listener?.onError("Connection failed: ${t.message}") }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            state = State.DISCONNECTED
        }
    }

    private fun register(key: String?) {
        val id = "reg_${msgId.getAndIncrement()}"
        
        // Comprehensive permission list used by official apps
        val permissions = JSONArray().apply {
            put("LAUNCH")
            put("CONTROL_AUDIO")
            put("CONTROL_INPUT_TEXT")
            put("CONTROL_INPUT_JOYSTICK")
            put("READ_INSTALLED_APPS")
            put("CONTROL_POWER")
            put("READ_TV_CHANNEL_LIST")
            put("READ_CURRENT_CHANNEL")
            put("READ_RUNNING_APPS")
            put("READ_NETWORK_STATE")
            put("CONTROL_TV_SETTING")
            put("CONTROL_TV_SCREEN")
            put("READ_TV_STATE")
            put("READ_LGE_SDP_COMMON")
            put("CONTROL_TV_DISPLAY")
        }

        val manifest = JSONObject().apply {
            put("manifestVersion", 1.1)
            put("appId", "com.webos.app.remote") // Trusted App ID
            put("vendorId", "com.lge")
            put("localizedAppNames", JSONObject().put("", "LG Smart Remote"))
            put("permissions", permissions)
        }

        val msg = JSONObject().apply {
            put("type", "register")
            put("id", id)
            put("payload", JSONObject().apply {
                put("forcePairing", key == null) // Force new pairing if no key
                put("pairingType", "PIN")
                if (key != null) put("client-key", key)
                put("manifest", manifest)
            })
        }
        mainSocket?.send(msg.toString())
        if (key == null) {
            state = State.PAIRING
            handler.post { listener?.onPinRequired() }
        }
    }

    fun sendPin(pin: String) {
        val msg = JSONObject().apply {
            put("type", "request")
            put("uri", "ssap://pairing/setPin")
            put("payload", JSONObject().put("pin", pin))
        }
        mainSocket?.send(msg.toString())
    }

    private fun setupSubscriptions() {
        sendRequest("ssap://audio/getVolume", JSONObject(), subscribe = true) { resp ->
            val payload = resp.optJSONObject("payload")
            if (payload != null) {
                val vol = payload.optInt("volume")
                val muted = payload.optBoolean("muted")
                handler.post { listener?.onVolumeUpdate(vol, muted) }
            }
        }
        sendRequest("ssap://tv/getCurrentChannel", JSONObject(), subscribe = true) { resp ->
            val payload = resp.optJSONObject("payload")
            if (payload != null) {
                val name = payload.optString("channelName", "Unknown")
                handler.post { listener?.onChannelUpdate(name) }
            }
        }
    }

    private fun openPointerSocket() {
        sendRequest("ssap://com.webos.service.networkinput/getPointerInputSocket", JSONObject()) { resp ->
            val url = resp.optJSONObject("payload")?.optString("socketPath")
            if (url != null) {
                val request = Request.Builder().url(url).build()
                pointerSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("WebOS", "Pointer socket failed, retrying in 2s...")
                        handler.postDelayed({ openPointerSocket() }, 2000)
                    }
                })
            } else {
                Log.e("WebOS", "Pointer socket URL not returned. Possible permission issue.")
            }
        }
    }

    fun sendRequest(uri: String, payload: JSONObject, subscribe: Boolean = false, callback: ((JSONObject) -> Unit)? = null) {
        val id = "msg_${msgId.getAndIncrement()}"
        if (callback != null) callbacks[id] = callback
        val msg = JSONObject().apply {
            put("type", if (subscribe) "subscribe" else "request")
            put("id", id)
            put("uri", uri)
            put("payload", payload)
        }
        mainSocket?.send(msg.toString())
    }

    fun turnOff() = sendRequest("ssap://system/turnOff", JSONObject())
    fun volumeUp() = sendRequest("ssap://audio/volumeUp", JSONObject())
    fun volumeDown() = sendRequest("ssap://audio/volumeDown", JSONObject())
    fun setVolume(vol: Int) = sendRequest("ssap://audio/setVolume", JSONObject().put("volume", vol))
    fun setMute(mute: Boolean) = sendRequest("ssap://audio/setMute", JSONObject().put("mute", mute))
    fun channelUp() = sendRequest("ssap://tv/channelUp", JSONObject())
    fun channelDown() = sendRequest("ssap://tv/channelDown", JSONObject())
    fun listApps(callback: (JSONArray) -> Unit) {
        sendRequest("ssap://com.webos.applicationManager/listApps", JSONObject()) { resp ->
            val apps = resp.optJSONObject("payload")?.optJSONArray("apps") ?: JSONArray()
            handler.post { callback(apps) }
        }
    }
    fun launchApp(appId: String) = sendRequest("ssap://system.launcher/launch", JSONObject().put("id", appId))
    fun closeApp(appId: String) = sendRequest("ssap://system.launcher/close", JSONObject().put("id", appId))
    fun insertText(text: String, replace: Int = 0) = sendRequest("ssap://com.webos.service.ime/insertText", JSONObject().apply {
        put("text", text)
        put("replace", replace)
    })
    fun deleteChar(count: Int = 1) = sendRequest("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", count))
    
    // Remote Keys - Standard Button Names
    fun sendKey(name: String) {
        if (pointerSocket != null) {
            pointerSocket?.send("type:button\nname:$name\n\n")
        } else {
            // Fallback for some keys if pointer is not available
            when (name) {
                "HOME" -> sendRequest("ssap://system.launcher/open", JSONObject().put("id", "com.webos.app.home"))
                "BACK" -> sendRequest("ssap://system.launcher/close", JSONObject())
                else -> Log.w("WebOS", "Key $name ignored because pointer socket is null")
            }
        }
    }
    
    fun moveMouse(dx: Int, dy: Int) = pointerSocket?.send("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
    fun click() = pointerSocket?.send("type:click\n\n")
    fun scroll(dx: Int, dy: Int) = pointerSocket?.send("type:scroll\ndx:$dx\ndy:$dy\n\n")
    fun getClientKey(): String? = clientKey
}
