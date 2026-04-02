package com.myvms.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class AddDeviceActivity : AppCompatActivity() {
    private lateinit var tabGroup: RadioGroup
    private lateinit var layoutIp: View
    private lateinit var layoutDid: View
    private lateinit var etIp: TextInputEditText
    private lateinit var etRtspPort: TextInputEditText
    private lateinit var etHttpPort: TextInputEditText
    private lateinit var etName: TextInputEditText
    private lateinit var etUser: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var etChannels: TextInputEditText
    private lateinit var btnTest: android.widget.Button
    private lateinit var tvTestResult: TextView
    private lateinit var etDid: TextInputEditText
    private lateinit var etDidName: TextInputEditText
    private lateinit var etDidUser: TextInputEditText
    private lateinit var etDidPass: TextInputEditText
    private lateinit var btnSave: android.widget.Button
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)
        supportActionBar?.title = "장치 추가"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tabGroup = findViewById(R.id.tabGroup)
        layoutIp = findViewById(R.id.layoutIp)
        layoutDid = findViewById(R.id.layoutDid)
        etIp = findViewById(R.id.etIp); etRtspPort = findViewById(R.id.etRtspPort)
        etHttpPort = findViewById(R.id.etHttpPort); etName = findViewById(R.id.etName)
        etUser = findViewById(R.id.etUser); etPass = findViewById(R.id.etPass)
        etChannels = findViewById(R.id.etChannels); btnTest = findViewById(R.id.btnTest)
        tvTestResult = findViewById(R.id.tvTestResult)
        etDid = findViewById(R.id.etDid); etDidName = findViewById(R.id.etDidName)
        etDidUser = findViewById(R.id.etDidUser); etDidPass = findViewById(R.id.etDidPass)
        btnSave = findViewById(R.id.btnSave)
        tabGroup.setOnCheckedChangeListener { _, id ->
            layoutIp.visibility = if (id == R.id.radioIp) View.VISIBLE else View.GONE
            layoutDid.visibility = if (id == R.id.radioDid) View.VISIBLE else View.GONE
        }
        btnTest.setOnClickListener { testConnection() }
        btnSave.setOnClickListener { saveDevice() }
    }

    private fun testConnection() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) { tvTestResult.text = "❌ IP를 입력하세요"; return }
        tvTestResult.text = "🔄 연결 확인 중..."
        tvTestResult.setTextColor(0xFF888888.toInt())
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val port = etRtspPort.text.toString().toIntOrNull() ?: 554
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ip, port), 3000)
                    socket.close()
                    "✅ RTSP 포트 $port 연결 성공!"
                } catch (e: Exception) { "❌ 연결 실패: ${e.message?.take(40)}" }
            }
            tvTestResult.text = result
            tvTestResult.setTextColor(if (result.startsWith("✅")) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
        }
    }

    private fun saveDevice() {
        val isIp = tabGroup.checkedRadioButtonId == R.id.radioIp
        val device = if (isIp) {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) { Toast.makeText(this, "IP를 입력하세요", Toast.LENGTH_SHORT).show(); return }
            Device(
                id = UUID.randomUUID().toString(),
                name = etName.text.toString().trim().ifEmpty { "$ip NVR" },
                type = Device.DeviceType.IP_P6S, ip = ip,
                port = etRtspPort.text.toString().toIntOrNull() ?: 554,
                httpPort = etHttpPort.text.toString().toIntOrNull() ?: 80,
                username = etUser.text.toString().trim().ifEmpty { "admin" },
                password = etPass.text.toString().trim(),
                channels = etChannels.text.toString().toIntOrNull() ?: 4
            )
        } else {
            val did = etDid.text.toString().trim().uppercase()
            if (did.isEmpty()) { Toast.makeText(this, "DID를 입력하세요", Toast.LENGTH_SHORT).show(); return }
            Device(
                id = UUID.randomUUID().toString(),
                name = etDidName.text.toString().trim().ifEmpty { "P2P 장치" },
                type = Device.DeviceType.DID_P2P, did = did,
                username = etDidUser.text.toString().trim().ifEmpty { "admin" },
                password = etDidPass.text.toString().trim(), channels = 4
            )
        }
        DeviceStorage.addDevice(this, device)
        Toast.makeText(this, "'${device.name}' 추가됨", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK); finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
