package com.myvms.app

import org.json.JSONObject

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val ip: String = "",
    val port: Int = 554,
    val httpPort: Int = 80,
    val username: String = "admin",
    val password: String = "",
    val channels: Int = 4,
    val did: String = "",
    var online: Boolean = true
) {
    enum class DeviceType { RTSP, IP_P6S, DID_P2P }

    fun getRtspUrl(channel: Int = 1, subStream: Boolean = false): String {
        val ch = channel - 1
        return when (type) {
            DeviceType.RTSP -> "rtsp://$username:$password@$ip:$port/ch0${ch}${if (subStream) "sub" else ""}.264"
            DeviceType.IP_P6S -> "rtsp://$username:$password@$ip:554/ch0${ch}${if (subStream) "sub" else ""}.264"
            DeviceType.DID_P2P -> ""
        }
    }

    fun getApiBaseUrl(): String = "http://$ip:$httpPort"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name)
        put("ip", ip); put("port", port); put("httpPort", httpPort)
        put("username", username); put("password", password)
        put("channels", channels); put("did", did); put("online", online)
    }

    companion object {
        fun fromJson(json: JSONObject) = Device(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            name = json.optString("name", "NVR"),
            type = DeviceType.valueOf(json.optString("type", "IP_P6S")),
            ip = json.optString("ip", ""),
            port = json.optInt("port", 554),
            httpPort = json.optInt("httpPort", 80),
            username = json.optString("username", "admin"),
            password = json.optString("password", ""),
            channels = json.optInt("channels", 4),
            did = json.optString("did", ""),
            online = json.optBoolean("online", true)
        )
    }
}
