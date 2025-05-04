package moe.ore.android

import android.annotation.SuppressLint
import android.content.Context
import com.tencent.mmkv.MMKV

@SuppressLint("StaticFieldLeak")
object AndroKtx {
    var isInit: Boolean = false
    lateinit var context: Context

    fun init(context: Context) {
        this.context = context
        // 初始化MMKV
        MMKV.initialize(context)
    }
}