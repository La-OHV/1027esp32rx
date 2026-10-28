package com.example.a1027esp32rx

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class ControlState(
    val steer: Int = 0,
    val throttle: Int = 0,
    val buttons: Int = 0,
    val mode: Int = 0
)

class UdpSender(
    private val ip: String = "192.168.4.1",
    private val port: Int = 8888
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val state = AtomicReference(ControlState())

    private var socket: DatagramSocket? = null
    private lateinit var address: InetAddress
    private var sequence = 0

    fun start() {
        address = InetAddress.getByName(ip)
        socket = DatagramSocket()

        executor.scheduleAtFixedRate({
            try {
                val s = state.get()

                val data = ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(s.steer.coerceIn(-32768, 32767).toShort())
                    .putShort(s.throttle.coerceIn(-32768, 32767).toShort())
                    .putShort(s.buttons.coerceIn(0, 65535).toShort())
                    .put(s.mode.coerceIn(0, 255).toByte())
                    .put((sequence and 0xFF).toByte())
                    .array()

                val packet = DatagramPacket(
                    data,
                    data.size,
                    address,
                    port
                )

                socket?.send(packet)
                sequence = (sequence + 1) and 0xFF
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 0, 20, TimeUnit.MILLISECONDS)
    }

    fun setControl(
        steer: Int,
        throttle: Int,
        buttons: Int,
        mode: Int
    ) {
        state.set(ControlState(steer, throttle, buttons, mode))
    }

    fun close() {
        socket?.close()
        executor.shutdownNow()
    }
}