package com.myvms.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveViewActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBitrate: TextView
    private lateinit var layoutPtz: View
    private lateinit var spinnerChannel: Spinner
    private var player: ExoPlayer? = null
    private lateinit var device: Device
    private var currentChannel = 1
    private var isSubStream = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_live_view)
        val json = intent.getStringExtra(EXTRA_DEVICE_JSON) ?: run { finish(); return }
        device = Device.fromJson(JSONObject(json))
        playerView = findViewById(R.id.playerView)
        tvTitle = findViewById(R.id.tvTitle); tvStatus = findViewById(R.id.tvStatus)
        tvBitrate = findViewById(R.id.tvBitrate); layoutPtz = findViewById(R.id.layoutPtz)
        spinnerChannel = findViewById(R.id.spinnerChannel)
        tvTitle.text = device.name
        val channels = (1..device.channels).map { "CH $it" }
        spinnerChannel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, channels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerChannel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentChannel = pos + 1; startStream()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnQuality).setOnClickListener { toggleQuality() }
        findViewById<ImageButton>(R.id.btnSnapshot).setOnClickListener {
            Toast.makeText(this, "\uD83D\uDCF7 \uc2a4\ub0c5\uc0f7", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnPtz).setOnClickListener {
            layoutPtz.visibility = if (layoutPtz.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<ImageButton>(R.id.btnFullscreen).setOnClickListener { toggleFullscreen() }
        setupPtzButtons()
        if (device.type == Device.DeviceType.DID_P2P) showP2PNotice() else startStream()
    }

    private fun startStream() {
        val rtspUrl = device.getRtspUrl(currentChannel, isSubStream)
        if (rtspUrl.isEmpty()) { tvStatus.text = "P2P SDK \ud544\uc694"; return }
        tvStatus.text = "\uc5f0\uacb0 \uc911..."; tvStatus.setTextColor(0xFFFFAA00.toInt())
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(rtspUrl)))
            exo.prepare(); exo.play()
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            tvStatus.text = "\u25cf LIVE"; tvStatus.setTextColor(0xFF4CAF50.toInt())
                            scope.launch {
                                while(isActive) {
                                    delay(2000)
                                    player?.videoFormat?.let { f ->
                                        if (f.width > 0) tvBitrate.text = "${f.width}x${f.height}"
                                    }
                                }
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            tvStatus.text = "\u23f3 \ubc84\ud37c\ub9c1..."
                            tvStatus.setTextColor(0xFFFFAA00.toInt())
                        }
                        else -> {
                            tvStatus.text = "\uc5f0\uacb0 \ub05d\uae40"
                            tvStatus.setTextColor(0xFFE53935.toInt())
                        }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    tvStatus.text = "\uc624\ub958"; tvStatus.setTextColor(0xFFE53935.toInt())
                    AlertDialog.Builder(this@LiveViewActivity)
                        .setTitle("\uc5f0\uacb0 \uc2e4\ud328")
                        .setMessage("URL: " + rtspUrl + "\n\n\uc624\ub958: " + error.message)
                        .setPositiveButton("\uc7ac\uc2dc\ub3c4") { _, _ -> startStream() }
                        .setNegativeButton("\ub2eb\uae30") { _, _ -> finish() }.show()
                }
            })
        }
    }

    private fun toggleQuality() {
        isSubStream = !isSubStream
        val msg = if (isSubStream) "\uc11c\ube0c\uc2a4\ud2b8\ub9bc" else "\uba54\uc778\uc2a4\ud2b8\ub9bc(HD)"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        startStream()
    }

    private fun toggleFullscreen() {
        val full = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
        if (full) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            supportActionBar?.show()
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            supportActionBar?.hide()
        }
    }

    private fun setupPtzButtons() {
        val ptzMap = mapOf(
            R.id.btnPtzUp to "up", R.id.btnPtzDown to "down",
            R.id.btnPtzLeft to "left", R.id.btnPtzRight to "right",
            R.id.btnPtzZoomIn to "zoomin", R.id.btnPtzZoomOut to "zoomout"
        )
        ptzMap.forEach { (btnId, cmd) ->
            findViewById<View>(btnId)?.setOnClickListener {
                if (device.ip.isNotEmpty()) scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val code = mapOf("up" to 0, "down" to 1, "left" to 2, "right" to 3,
                                "zoomin" to 11, "zoomout" to 12)[cmd] ?: 0
                            val ptzUrl = "${device.getApiBaseUrl()}/cgi-bin/ptz.cgi" +
                                "?action=start&channel=$currentChannel&code=$code&arg1=0&arg2=1&arg3=0"
                            httpClient.newCall(Request.Builder().url(ptzUrl).build()).execute()
                        } catch (e: Exception) {}
                    }
                }
                Toast.makeText(this, "PTZ: $cmd", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showP2PNotice() {
        tvStatus.text = "P2P SDK \ud544\uc694"; tvStatus.setTextColor(0xFFFF6600.toInt())
        AlertDialog.Builder(this).setTitle("P2P \uc5f0\uacb0")
            .setMessage("DID: " + device.did + "\n\nP2P \ubc29\uc2dd\uc740 P6SCore SDK\uac00 \ud544\uc694\ud569\ub2c8\ub2e4.\n" +
                "LAN IP \ubc29\uc2dd\uc73c\ub85c \uc7a5\uce58\ub97c \ucd94\uac00\ud558\uba74 \uc989\uc2dc \uc601\uc0c1\uc744 \ubcfc \uc218 \uc788\uc2b5\ub2c8\ub2e4.")
            .setPositiveButton("\ud655\uc778", null)
            .setNegativeButton("\ub2eb\uae30") { _, _ -> finish() }.show()
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() {
        super.onDestroy(); player?.release(); player = null; scope.cancel()
    }

    companion object { const val EXTRA_DEVICE_JSON = "device_json" }
}
