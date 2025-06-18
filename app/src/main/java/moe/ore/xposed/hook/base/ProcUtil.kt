package moe.ore.xposed.hook.base

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import moe.ore.xposed.utils.runRetry

object ProcUtil {
    const val UNKNOW = 0
    const val MAIN = 1
    const val MSF = 1 shl 1
    const val PEAK = 1 shl 2
    const val TOOL = 1 shl 3
    const val QZONE = 1 shl 4
    const val VIDEO = 1 shl 5
    const val MINI = 1 shl 6
    const val PLUGIN = 1 shl 7
    const val QQFAV = 1 shl 8
    const val TROOP = 1 shl 9
    const val UNITY = 1 shl 10

    const val OTHER = 1 shl 31
    const val ANY = (1 shl 32) - 1

    val mPid: Int by lazy { Process.myPid() }
    val procName: String by lazy {
        val activityManager = hostApp.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return@lazy runRetry(3) {
            activityManager.runningAppProcesses.forEach {
                if (it.pid == mPid) {
                    return@runRetry it.processName
                }
            }
            return@runRetry null
        } ?: "unknown"
    }
    val procType: Int by lazy {
        val parts = procName.split(":")
        if (parts.size == 1) {
            if (procName == "com.tencent.ilink.ServiceProcess") {
                return@lazy OTHER
            } else if (parts.last() == "unknown") {
                return@lazy UNKNOW
            } else return@lazy MAIN
        }
        val tail = parts.last()
        return@lazy when {
            tail == "MSF" -> MSF
            tail == "peak" -> PEAK
            tail == "tool" -> TOOL
            tail.startsWith("qzone") -> QZONE
            tail == "video" -> VIDEO
            tail.startsWith("mini") -> MINI
            tail.startsWith("plugin") -> PLUGIN
            tail.startsWith("troop") -> TROOP
            tail.startsWith("unity") -> UNITY
            tail.startsWith("qqfav") -> QQFAV
            else -> OTHER
        }
    }

    fun getProcessName(): String {
        val parts = procName.split(":")
        return if (parts.size == 1) {
            if (procName == "com.tencent.ilink.ServiceProcess") {
                "other"
            } else if (parts.last() == "unknown") {
                "unknown"
            } else "main"
        } else {
            parts.last()
        }
    }

    fun inProcess(flag: Int): Boolean = (procType and flag) != 0

    val isMain: Boolean = inProcess(MAIN)
    val isMsf: Boolean = inProcess(MSF)
    val isPeak: Boolean = inProcess(PEAK)
    val isTool: Boolean = inProcess(TOOL)
    val isQzone: Boolean = inProcess(QZONE)
    val isVideo: Boolean = inProcess(VIDEO)
    val isMini: Boolean = inProcess(MINI)
    val isPlugin: Boolean = inProcess(PLUGIN)
    val isQQFav: Boolean = inProcess(QQFAV)
    val isTroop: Boolean = inProcess(TROOP)
    val isUnity: Boolean = inProcess(UNITY)
}
