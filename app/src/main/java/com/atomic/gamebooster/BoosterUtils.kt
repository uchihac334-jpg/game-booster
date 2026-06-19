package com.atomic.gamebooster

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.RandomAccessFile

object BoosterUtils {

    /**
     * Best-effort: minta system kill cached/background process milik app lain.
     * CATATAN JUJUR: tanpa root ini cuma minta Android buat kill proses yang
     * statusnya udah "cached" (gak aktif). App yang lagi running/foreground/
     * persistent service GAK akan kena. Android sendiri sebenarnya udah
     * auto-manage ini, jadi efeknya minim — tapi at least gak placebo total
     * karena beneran manggil API resminya.
     */
    fun freeMemory(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningApps = am.runningAppProcesses ?: return 0
        var killed = 0
        for (proc in runningApps) {
            if (proc.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                for (pkg in proc.pkgList) {
                    if (pkg != context.packageName) {
                        am.killBackgroundProcesses(pkg)
                        killed++
                    }
                }
            }
        }
        return killed
    }

    fun getAvailableMemoryMB(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    fun getTotalMemoryMB(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    fun isDndAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun setGameMode(context: Context, enable: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Cari package yang lagi di foreground (butuh izin Usage Access dari Settings). */
    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usm.queryEvents(begin, end)
        var lastPkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    /**
     * Baca "Total frames rendered" cumulative dari dumpsys gfxinfo buat package tertentu.
     * Butuh android.permission.DUMP (digrant via ADB shell, lihat setup_adb.sh).
     * Return null kalau gagal (izin belum ada / command gak jalan).
     */
    fun readCumulativeFrameCount(packageName: String): Long? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys gfxinfo $packageName"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var result: Long? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.contains("Total frames rendered")) {
                    val num = Regex("\\d+").find(l)?.value
                    result = num?.toLongOrNull()
                    break
                }
            }
            reader.close()
            process.waitFor()
            result
        } catch (e: Exception) {
            null
        }
    }


    fun readCpuUsagePercent(): Float {
        return try {
            val r1 = readCpuLine()
            Thread.sleep(300)
            val r2 = readCpuLine()
            val idle1 = r1[3]; val total1 = r1.sum()
            val idle2 = r2[3]; val total2 = r2.sum()
            val totalDiff = (total2 - total1).toFloat()
            val idleDiff = (idle2 - idle1).toFloat()
            if (totalDiff <= 0) 0f else ((totalDiff - idleDiff) / totalDiff) * 100f
        } catch (e: Exception) {
            -1f
        }
    }

    private fun readCpuLine(): LongArray {
        RandomAccessFile("/proc/stat", "r").use { reader ->
            val line = reader.readLine()
            val parts = line.trim().split(Regex("\\s+")).drop(1)
            return parts.take(7).map { it.toLong() }.toLongArray()
        }
    }

    /** Baca suhu battery/CPU dari thermal zone. Gak ada standar resmi tanpa root,
     * jadi nyoba beberapa path umum dan ambil yang kebaca duluan. */
    fun readTemperatureCelsius(): Float {
        val candidates = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp"
        )
        for (path in candidates) {
            try {
                val raw = java.io.File(path).readText().trim().toFloat()
                // sebagian device report dalam milli-celsius, sebagian celsius langsung
                return if (raw > 1000) raw / 1000f else raw
            } catch (e: Exception) {
                continue
            }
        }
        return -1f
    }
}
