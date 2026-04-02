package com.myvms.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var fab: FloatingActionButton
    private val devices = mutableListOf<Device>()
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recycler = findViewById(R.id.recyclerDevices)
        emptyView = findViewById(R.id.emptyView)
        fab = findViewById(R.id.fab)
        adapter = DeviceAdapter(devices,
            onLiveClick = { device -> openLiveView(device) },
            onSettingsClick = { device -> showDeviceOptions(device) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        fab.setOnClickListener {
            startActivityForResult(Intent(this, AddDeviceActivity::class.java), REQ_ADD)
        }
        loadDevices()
    }

    override fun onResume() { super.onResume(); loadDevices() }

    private fun loadDevices() {
        devices.clear()
        devices.addAll(DeviceStorage.loadDevices(this))
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openLiveView(device: Device) {
        startActivity(Intent(this, LiveViewActivity::class.java).apply {
            putExtra(LiveViewActivity.EXTRA_DEVICE_JSON, device.toJson().toString())
        })
    }

    private fun showDeviceOptions(device: Device) {
        AlertDialog.Builder(this).setTitle(device.name)
            .setItems(arrayOf("라이브 보기", "정보 보기", "삭제")) { _, which ->
                when (which) {
                    0 -> openLiveView(device)
                    1 -> showDeviceInfo(device)
                    2 -> confirmDelete(device)
                }
            }.show()
    }

    private fun showDeviceInfo(device: Device) {
        val info = buildString {
            appendLine("이름: ${device.name}")
            appendLine("타입: ${device.type.name}")
            if (device.ip.isNotEmpty()) appendLine("IP: ${device.ip}:${device.port}")
            if (device.did.isNotEmpty()) appendLine("DID: ${device.did}")
            appendLine("채널 수: ${device.channels}")
            appendLine("RTSP: ${device.getRtspUrl(1)}")
        }
        AlertDialog.Builder(this).setTitle("장치 정보").setMessage(info)
            .setPositiveButton("확인", null).show()
    }

    private fun confirmDelete(device: Device) {
        AlertDialog.Builder(this).setTitle("장치 삭제")
            .setMessage("'${device.name}'을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                DeviceStorage.removeDevice(this, device.id)
                loadDevices()
                Snackbar.make(recycler, "삭제되었습니다", Snackbar.LENGTH_SHORT).show()
            }.setNegativeButton("취소", null).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADD && resultCode == RESULT_OK) loadDevices()
    }

    companion object { const val REQ_ADD = 1001 }
}

class DeviceAdapter(
    private val devices: List<Device>,
    private val onLiveClick: (Device) -> Unit,
    private val onSettingsClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgDeviceIcon)
        val name: TextView = view.findViewById(R.id.tvDeviceName)
        val info: TextView = view.findViewById(R.id.tvDeviceInfo)
        val status: TextView = view.findViewById(R.id.tvStatus)
        val btnLive: View = view.findViewById(R.id.btnLive)
        val btnSettings: ImageButton = view.findViewById(R.id.btnSettings)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false))
    override fun getItemCount() = devices.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val dev = devices[position]
        holder.name.text = dev.name
        holder.info.text = when (dev.type) {
            Device.DeviceType.DID_P2P -> "DID: ${dev.did}"
            else -> "${dev.ip}:${dev.port} · ${dev.channels}CH"
        }
        holder.status.text = "● 온라인"
        holder.status.setTextColor(0xFF4CAF50.toInt())
        holder.btnLive.setOnClickListener { onLiveClick(dev) }
        holder.btnSettings.setOnClickListener { onSettingsClick(dev) }
        holder.itemView.setOnClickListener { onLiveClick(dev) }
    }
}
