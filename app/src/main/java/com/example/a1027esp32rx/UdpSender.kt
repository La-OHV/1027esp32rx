package com.example.a1027esp32rx

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class UdpSender(
    private val ip: String = "192.168.4.1",
    private val port: Int = 8888,
    private val periodMs: Long = 20L,
    private val onStatusChanged: (String) -> Unit = {}
) {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var payloadProvider: (() -> ByteArray)? = null
    private var started = false
    private var lastSuccessReportMs = 0L

    @Synchronized
    fun start() {
        if (started) return
        started = true
        try {
            address = InetAddress.getByName(ip)
            socket = DatagramSocket()
            onStatusChanged("UDP已启动（目标 $ip:$port）")
        } catch (e: Exception) {
            onStatusChanged("发送失败：${e.message ?: e.javaClass.simpleName}")
        }
        executor.scheduleAtFixedRate({ sendOnce() }, 0L, periodMs, TimeUnit.MILLISECONDS)
    }

    fun setPayloadProvider(provider: () -> ByteArray) {
        payloadProvider = provider
    }

    private fun sendOnce() {
        val currentSocket = socket ?: return
        val currentAddress = address ?: return
        val provider = payloadProvider ?: return
        try {
            val data = provider()
            currentSocket.send(DatagramPacket(data, data.size, currentAddress, port))
            val now = System.currentTimeMillis()
            if (now - lastSuccessReportMs >= 1000L) {
                lastSuccessReportMs = now
                onStatusChanged("已发出（UDP无回执，未确认ESP在线）")
            }
        } catch (e: Exception) {
            onStatusChanged("发送失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Kept for compatibility with the earlier test page. */
    fun setControl(steer: Int, throttle: Int, buttons: Int, mode: Int) {
        payloadProvider = {
            byteArrayOf(
                steer.coerceIn(-128, 127).toByte(),
                throttle.coerceIn(-128, 127).toByte(),
                (buttons and 0xFF).toByte(),
                (mode and 0xFF).toByte()
            )
        }
    }

    @Synchronized
    fun close() {
        started = false
        socket?.close()
        socket = null
        executor.shutdownNow()
    }
}
