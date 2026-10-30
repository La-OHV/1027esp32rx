package com.example.a1027esp32rx

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class MainActivity : AppCompatActivity() {
    @Volatile var left_x = 0
    @Volatile var left_y = 0
    @Volatile var right_x = 0
    @Volatile var right_y = 0
    @Volatile var left_gear = 1
    @Volatile var right_gear = 1
    @Volatile var power_on = false

    private lateinit var udpSender: UdpSender
    private lateinit var statusText: TextView
    private lateinit var leftJoystick: JoystickView
    private lateinit var rightJoystick: JoystickView
    private lateinit var powerButton: Button
    private lateinit var leftGear1Button: Button
    private lateinit var leftGear2Button: Button
    private lateinit var rightGear1Button: Button
    private lateinit var rightGear2Button: Button
    private lateinit var leftCoordinates: TextView
    private lateinit var rightCoordinates: TextView
    private val sequence = AtomicReference(0)
    @Volatile private var latestUdpStatus = "等待发送"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContentView(R.layout.activity_main)
        hideSystemBars()

        statusText = findViewById(R.id.statusText)
        leftJoystick = findViewById(R.id.leftJoystick)
        rightJoystick = findViewById(R.id.rightJoystick)
        powerButton = findViewById(R.id.powerButton)
        leftGear1Button = findViewById(R.id.leftGear1Button)
        leftGear2Button = findViewById(R.id.leftGear2Button)
        rightGear1Button = findViewById(R.id.rightGear1Button)
        rightGear2Button = findViewById(R.id.rightGear2Button)
        leftCoordinates = findViewById(R.id.leftCoordinates)
        rightCoordinates = findViewById(R.id.rightCoordinates)

        leftJoystick.isEnabled = false
        rightJoystick.isEnabled = false
        updateUi()

        leftJoystick.onPositionChanged = { x, y ->
            if (power_on) {
                left_x = x
                left_y = y
                updateUi()
            }
        }
        rightJoystick.onPositionChanged = { x, y ->
            if (power_on) {
                right_x = x
                right_y = y
                updateUi()
            }
        }

        powerButton.setOnClickListener {
            power_on = !power_on
            if (!power_on) resetControls()
            leftJoystick.isEnabled = power_on
            rightJoystick.isEnabled = power_on
            updateUi()
        }
        leftGear1Button.setOnClickListener {
            left_gear = 1
            updateUi()
        }
        leftGear2Button.setOnClickListener {
            left_gear = 2
            updateUi()
        }
        rightGear1Button.setOnClickListener {
            right_gear = 1
            updateUi()
        }
        rightGear2Button.setOnClickListener {
            right_gear = 2
            updateUi()
        }

        udpSender = UdpSender(
            ip = "192.168.4.1",
            port = 8888,
            periodMs = 20L,
            onStatusChanged = { message ->
                latestUdpStatus = message
                runOnUiThread { updateStatus(message) }
            }
        )
        udpSender.setPayloadProvider {
            val currentSequence = sequence.getAndUpdate { (it + 1) and 0xFF }
            packRemoteControlData(
                RemoteControlData(
                    sequence = currentSequence,
                    powerOn = power_on,
                    leftX = if (power_on) left_x else 0,
                    leftY = if (power_on) left_y else 0,
                    rightX = if (power_on) right_x else 0,
                    rightY = if (power_on) right_y else 0,
                    leftGear = left_gear,
                    rightGear = right_gear
                )
            )
        }
        udpSender.start()
    }

    /** Hide the status/navigation bars and keep the app in immersive mode. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Legacy flags are needed on some devices/ROMs even when using AndroidX.
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun resetControls() {
        left_x = 0
        left_y = 0
        right_x = 0
        right_y = 0
        leftJoystick.resetPosition()
        rightJoystick.resetPosition()
    }

    private fun updateUi() {
        leftCoordinates.text = "左摇杆  X ${formatAxis(left_x)}  Y ${formatAxis(left_y)}"
        rightCoordinates.text = "右摇杆  X ${formatAxis(right_x)}  Y ${formatAxis(right_y)}"
        updateGearButtons(
            leftGear1Button,
            leftGear2Button,
            left_gear,
            Color.rgb(82, 177, 224)
        )
        updateGearButtons(
            rightGear1Button,
            rightGear2Button,
            right_gear,
            Color.rgb(236, 160, 82)
        )

        powerButton.text = if (power_on) "ON" else "OFF"
        powerButton.backgroundTintList = ColorStateList.valueOf(
            if (power_on) Color.rgb(157, 218, 241) else Color.rgb(174, 184, 190)
        )
        powerButton.alpha = 1f

        statusText.text = "控制 ${if (power_on) "ON" else "OFF"}  |  UDP $latestUdpStatus  |  50 Hz"
    }

    private fun updateGearButtons(
        gear1Button: Button,
        gear2Button: Button,
        selectedGear: Int,
        selectedColor: Int
    ) {
        val disabledColor = ColorStateList.valueOf(Color.rgb(185, 194, 199))
        val unselectedColor = if (selectedColor == Color.rgb(82, 177, 224)) {
            ColorStateList.valueOf(Color.rgb(234, 248, 253))
        } else {
            ColorStateList.valueOf(Color.rgb(255, 244, 231))
        }

        gear1Button.isEnabled = power_on
        gear2Button.isEnabled = power_on
        gear1Button.backgroundTintList = when {
            !power_on -> disabledColor
            selectedGear == 1 -> ColorStateList.valueOf(selectedColor)
            else -> unselectedColor
        }
        gear2Button.backgroundTintList = when {
            !power_on -> disabledColor
            selectedGear == 2 -> ColorStateList.valueOf(selectedColor)
            else -> unselectedColor
        }
        val textColor = if (power_on) Color.rgb(45, 65, 75) else Color.rgb(112, 123, 130)
        gear1Button.setTextColor(textColor)
        gear2Button.setTextColor(textColor)
    }

    private fun updateStatus(message: String) {
        statusText.text = "控制 ${if (power_on) "ON" else "OFF"}  |  UDP $message  |  50 Hz"
    }

    private fun formatAxis(value: Int): String {
        return if (value >= 0) String.format(Locale.US, "+%03d", value)
        else String.format(Locale.US, "-%03d", -value)
    }

    override fun onDestroy() {
        if (::udpSender.isInitialized) udpSender.close()
        super.onDestroy()
    }
}
