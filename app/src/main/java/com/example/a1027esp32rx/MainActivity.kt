package com.example.a1027esp32rx

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var udpSender: UdpSender
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val sendButton: Button = findViewById(R.id.sendButton)
        val stopButton: Button = findViewById(R.id.stopButton)

        // 1. 创建 UdpSender 实例并启动（连接发报机）
        udpSender = UdpSender(
            ip = "192.168.4.1",
            port = 8888
        )
        udpSender.start()

        // 2. 给“发送测试值”绑定点击事件
        sendButton.setOnClickListener {
            udpSender.setControl(
                steer = 0,
                throttle = 300, // 油门 300
                buttons = 0,
                mode = 0
            )
            statusText.text = "正在发送：throttle = 300"
        }

        // 3. 给“急停”绑定点击事件
        stopButton.setOnClickListener {
            udpSender.setControl(
                steer = 0,
                throttle = 0,
                buttons = 1, // 急停标志
                mode = 0
            )
            statusText.text = "急停"
        }
    }

    override fun onDestroy() {
        // 退出 App 时关闭发报机，释放资源
        udpSender.close()
        super.onDestroy()
    }
}