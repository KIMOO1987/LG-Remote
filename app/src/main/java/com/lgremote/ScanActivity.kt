package com.lgremote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lgremote.databinding.ActivityScanBinding
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var adapter: ArrayAdapter<TVDevice>
    private val foundDevices = mutableListOf<TVDevice>()
    private lateinit var discovery: TVDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "LG Smart TV Remote"

        discovery = TVDiscovery(this)
        setupList()

        binding.btnScan.setOnClickListener { startScan() }
        binding.btnManualIp.setOnClickListener { showManualIpDialog() }
    }

    private fun setupList() {
        adapter = object : ArrayAdapter<TVDevice>(this, R.layout.item_tv, foundDevices) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_tv, parent, false)
                val device = getItem(position)!!
                view.findViewById<TextView>(R.id.tvName).text = device.friendlyName
                view.findViewById<TextView>(R.id.tvIp).text = device.ip
                return view
            }
        }
        binding.deviceList.adapter = adapter
        binding.deviceList.setOnItemClickListener { _, _, position, _ ->
            openRemote(foundDevices[position])
        }
    }

    private fun startScan() {
        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = "Scanning for TVs..."
        foundDevices.clear()
        adapter.notifyDataSetChanged()

        lifecycleScope.launch {
            val results = discovery.scan()
            foundDevices.addAll(results)
            adapter.notifyDataSetChanged()
            binding.progressBar.visibility = View.GONE
            binding.statusText.text = if (results.isEmpty()) "No TVs found" else "${results.size} TVs found"
        }
    }

    private fun showManualIpDialog() {
        val input = EditText(this)
        input.hint = "192.168.1.x"
        AlertDialog.Builder(this)
            .setTitle("Manual IP Entry")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val ip = input.text.toString()
                if (ip.isNotEmpty()) {
                    openRemote(TVDevice(ip, "LG TV ($ip)"))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openRemote(device: TVDevice) {
        val prefs = getSharedPreferences("lg_remote_prefs", Context.MODE_PRIVATE)
        device.clientKey = prefs.getString("key_${device.ip}", null)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("device", device)
        }
        startActivity(intent)
    }
}
