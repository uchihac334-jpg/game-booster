package com.atomic.gamebooster

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.atomic.gamebooster.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var overlayActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClearRam.setOnClickListener { onClearRam() }
        binding.btnDnd.setOnClickListener { onToggleDnd() }
        binding.btnOverlay.setOnClickListener { onToggleOverlay() }
        binding.btnUsageAccess.setOnClickListener { onRequestUsageAccess() }

        setupOverlayToggles()
        refreshRamLabel()
    }

    private fun setupOverlayToggles() {
        val prefs = getSharedPreferences("gb_prefs", Context.MODE_PRIVATE)

        binding.chkShowFps.isChecked = prefs.getBoolean("show_fps", true)
        binding.chkShowCpu.isChecked = prefs.getBoolean("show_cpu", true)
        binding.chkShowTemp.isChecked = prefs.getBoolean("show_temp", true)
        binding.chkShowRam.isChecked = prefs.getBoolean("show_ram", true)

        binding.chkShowFps.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_fps", checked).apply()
        }
        binding.chkShowCpu.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_cpu", checked).apply()
        }
        binding.chkShowTemp.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_temp", checked).apply()
        }
        binding.chkShowRam.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_ram", checked).apply()
        }
    }

    private fun onClearRam() {
        val killed = BoosterUtils.freeMemory(this)
        Toast.makeText(this, "Diminta kill $killed proses background", Toast.LENGTH_SHORT).show()
        refreshRamLabel()
    }

    private fun refreshRamLabel() {
        val avail = BoosterUtils.getAvailableMemoryMB(this)
        val total = BoosterUtils.getTotalMemoryMB(this)
        binding.txtRam.text = "RAM tersedia: ${avail}MB / ${total}MB"
    }

    private fun onRequestUsageAccess() {
        if (BoosterUtils.isUsageAccessGranted(this)) {
            Toast.makeText(this, "Usage access udah aktif", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cari 'Game Booster' di list, aktifkan", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun onToggleDnd() {
        if (!BoosterUtils.isDndAccessGranted(this)) {
            Toast.makeText(this, "Kasih izin DND dulu di setting", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val turningOn = binding.btnDnd.text == "Aktifkan Game Mode"
        BoosterUtils.setGameMode(this, turningOn)
        binding.btnDnd.text = if (turningOn) "Matikan Game Mode" else "Aktifkan Game Mode"
        Toast.makeText(
            this,
            if (turningOn) "Notif diblok selama main" else "Notif normal lagi",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onToggleOverlay() {
        if (!overlayActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Kasih izin overlay dulu", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            }
            startService(Intent(this, OverlayService::class.java))
            overlayActive = true
            binding.btnOverlay.text = "Stop Monitor"
        } else {
            stopService(Intent(this, OverlayService::class.java))
            overlayActive = false
            binding.btnOverlay.text = "Start Monitor (CPU/Suhu/RAM)"
        }
    }
}
