package com.lgremote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.lgremote.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : AppCompatActivity(), WebOSClient.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: WebOSClient
    private lateinit var device: TVDevice
    
    private var lastX = 0f
    private var lastY = 0f
    private var isMoved = false
    private val moveThreshold = 8f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        device = intent.getParcelableExtra("device") ?: return finish()
        client = WebOSClient(device.ip)
        client.listener = this

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = device.friendlyName
        supportActionBar?.subtitle = "Connecting..."

        setupTabs()
        setupRemoteButtons()
        setupTouchpad()
        setupAppsTab()

        client.connect(device.clientKey)
        
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_keyboard) {
                showKeyboard()
                true
            } else false
        }
    }

    private fun setupTabs() {
        binding.tabRemote.setOnClickListener { switchTab(0) }
        binding.tabTouchpad.setOnClickListener { switchTab(1) }
        binding.tabApps.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(index: Int) {
        binding.panelRemote.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelTouchpad.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.panelApps.visibility = if (index == 2) View.VISIBLE else View.GONE
        
        if (index == 2) refreshApps()
    }

    private fun setupRemoteButtons() {
        binding.btnPower.setOnClickListener { client.turnOff() }
        binding.btnVolUp.setOnClickListener { client.volumeUp() }
        binding.btnVolDown.setOnClickListener { client.volumeDown() }
        binding.btnChUp.setOnClickListener { client.channelUp() }
        binding.btnChDown.setOnClickListener { client.channelDown() }
        binding.btnUp.setOnClickListener { client.sendKey("UP") }
        binding.btnDown.setOnClickListener { client.sendKey("DOWN") }
        binding.btnLeft.setOnClickListener { client.sendKey("LEFT") }
        binding.btnRight.setOnClickListener { client.sendKey("RIGHT") }
        binding.btnOk.setOnClickListener { client.sendKey("ENTER") }
        binding.btnHome.setOnClickListener { client.sendKey("HOME") }
        binding.btnBack.setOnClickListener { client.sendKey("BACK") }
        binding.btnExit.setOnClickListener { client.sendKey("EXIT") }
        binding.btnRew.setOnClickListener { client.sendRequest("ssap://media.controls/rewind", JSONObject()) }
        binding.btnPlay.setOnClickListener { client.sendRequest("ssap://media.controls/play", JSONObject()) }
        binding.btnPause.setOnClickListener { client.sendRequest("ssap://media.controls/pause", JSONObject()) }
        binding.btnFF.setOnClickListener { client.sendRequest("ssap://media.controls/fastForward", JSONObject()) }
        binding.btnRed.setOnClickListener { client.sendKey("RED") }
        binding.btnGreen.setOnClickListener { client.sendKey("GREEN") }
        binding.btnYellow.setOnClickListener { client.sendKey("YELLOW") }
        binding.btnBlue.setOnClickListener { client.sendKey("BLUE") }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchpad() {
        binding.touchSurface.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    isMoved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX).toInt()
                    val dy = (event.y - lastY).toInt()
                    
                    if (abs(dx) > moveThreshold || abs(dy) > moveThreshold) {
                        isMoved = true
                        if (binding.switchMode.isChecked) {
                            client.scroll(dx / 3, dy / 3)
                        } else {
                            client.moveMouse(dx, dy)
                        }
                        lastX = event.x
                        lastY = event.y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) client.click()
                }
            }
            true
        }
        binding.btnTouchKeyboard.setOnClickListener { showKeyboard() }
    }

    private fun setupAppsTab() {
        binding.swipeRefresh.setOnRefreshListener { refreshApps() }
    }

    private fun refreshApps() {
        binding.swipeRefresh.isRefreshing = true
        client.listApps { apps ->
            binding.swipeRefresh.isRefreshing = false
            binding.appsGrid.adapter = AppAdapter(apps)
            binding.appsGrid.setOnItemClickListener { _, _, position, _ ->
                val appId = apps.getJSONObject(position).optString("id")
                client.launchApp(appId)
            }
            binding.appsGrid.setOnItemLongClickListener { _, _, position, _ ->
                val appId = apps.getJSONObject(position).optString("id")
                client.closeApp(appId)
                Toast.makeText(this, "Closing app...", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun showKeyboard() {
        KeyboardDialog(client).show(supportFragmentManager, "keyboard")
    }

    override fun onStateChange(state: WebOSClient.State) {
        supportActionBar?.subtitle = state.name
    }

    override fun onVolumeUpdate(volume: Int, muted: Boolean) {
        binding.tvVolume.text = "VOL $volume"
        binding.btnMute.text = if (muted) "UNMUTE" else "MUTE"
        binding.btnMute.setOnClickListener { client.setMute(!muted) }
    }

    override fun onChannelUpdate(channelName: String) {
        binding.tvChannel.text = channelName
    }

    override fun onPairingPrompt() {
        Snackbar.make(binding.root, "Accept the pairing request on your TV", Snackbar.LENGTH_INDEFINITE)
            .setAction("OK") {}
            .show()
    }

    override fun onPinRequired() {
        val input = EditText(this)
        input.hint = "3-digit code"
        AlertDialog.Builder(this)
            .setTitle("Enter PIN from TV")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                client.sendPin(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onConnected() {
        val key = client.getClientKey()
        if (key != null) {
            getSharedPreferences("lg_remote_prefs", Context.MODE_PRIVATE)
                .edit().putString("key_${device.ip}", key).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.disconnect()
    }

    private inner class AppAdapter(private val apps: JSONArray) : BaseAdapter() {
        override fun getCount(): Int = apps.length()
        override fun getItem(position: Int) = apps.getJSONObject(position)
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val app = getItem(position)
            view.findViewById<TextView>(R.id.appName).text = app.optString("title")
            Glide.with(this@MainActivity).load(app.optString("icon")).into(view.findViewById(R.id.appIcon))
            return view
        }
    }
}
