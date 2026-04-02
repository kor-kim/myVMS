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
            Toast.makeText(this, "📷 스냅샷", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnPtz).setOnClickListener {
            layoutPtz.visibility = if (layoutPtz.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<ImageButton>(R.id.btnFullscreen).setOnClickListener { toggleFullscreen() }
        setupPtzButtons()
        if (device.type == Device.DeviceType.DID_P2P) showP2PNotice() else startStream()
    }

    private fun startStream() {
        val url = device.getRtspUrl(currentChannel, isSubStream)
        if (url.isEmpty()) { tvStatus.text = "P2P SDK 필요"; return }
        tvStatus.text = "연결 중..."; tvStatus.setTextColor(0xFFFFAA00.toInt())
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exo.prepare(); exo.play()
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            tvStatus.text = "● LIVE"; tvStatus.setTextColor(0xFF4CAF50.toInt())
                            scope.launch { while(isActive) {
                                delay(2000)
                                player?.videoFormat?.let { f ->
                                    if(f.width>0) tvBitrate.text = "${f.width}x${f.height}"
                                }
                            }}
                        }
                        Player.STATE_BUFFERING -> { tvStatus.text = "⏳ 버퍼링..."; tvStatus.setTextColor(0xFFFFAA00.toInt()) }
                        else -> { tvStatus.text = "연결 끊김"; tvStatus.setTextColor(0xFFE53935.toInt()) }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    tvStatus.text = "오류"; tvStatus.setTextColor(0xFFE53935.toInt())
                    AlertDialog.Builder(this@LiveViewActivity)
                        .setTitle("연결 실패").setMessage("URL: $url

오류: ${error.message}

• NVR과 같은 네트워크인지 확인
• IP, 포트, 계정 확인")
                        .setPositiveButton("재시도") { _, _ -> startStream() }
                        .setNegativeButton("닫기") { _, _ -> finish() }.show()
                }
            })
        }
    }

    private fun toggleQuality() {
        isSubStream = !isSubStream
        Toast.makeText(this, if (isSubStream) "서브스트림" else "메인스트림(HD)", Toast.LENGTH_SHORT).show()
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
        mapOf(R.id.btnPtzUp to "up", R.id.btnPtzDown to "down",
            R.id.btnPtzLeft to "left", R.id.btnPtzRight to "right",
            R.id.btnPtzZoomIn to "zoomin", R.id.btnPtzZoomOut to "zoomout"
        ).forEach { (id, cmd) ->
            findViewById<View>(id)?.setOnClickListener {
                if (device.ip.isNotEmpty()) scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val code = mapOf("up" to 0,"down" to 1,"left" to 2,"right" to 3,"zoomin" to 11,"zoomout" to 12)[cmd] ?: 0
                            httpClient.newCall(Request.Builder()
                                .url("${device.getApiBaseUrl()}/cgi-bin/ptz.cgi?action=start&channel=$currentChannel&code=$code&arg1=0&arg2=1&arg3=0")
                                .build()).execute()
                        } catch(e: Exception) {}
                    }
                }
                Toast.makeText(this, "PTZ: $cmd", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showP2PNotice() {
        tvStatus.text = "P2P SDK 필요"; tvStatus.setTextColor(0xFFFF6600.toInt())
        AlertDialog.Builder(this).setTitle("P2P 연결")
            .setMessage("DID: ${device.did}

P2P 방식은 P6SCore SDK가 필요합니다.
LAN 내 IP 방식으로 장치를 추가하면 즉시 영상을 볼 수 있습니다.")
            .setPositiveButton("확인", null).setNegativeButton("닫기") { _, _ -> finish() }.show()
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null; scope.cancel() }

    companion object { const val EXTRA_DEVICE_JSON = "device_json" }
                        }
