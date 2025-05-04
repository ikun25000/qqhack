package moe.ore.xposed.helper

import com.tencent.mmkv.MMKV
import android.content.Context

object MMKVConfigManager {
    private var mmkv: MMKV? = null

    fun initialize(context: Context) {
        if (mmkv == null) {
            try {
                MMKV.initialize(context)
                mmkv = MMKV.mmkvWithID("txhook_config", MMKV.MULTI_PROCESS_MODE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    const val KEY_PUSH_API = "push_api"
    const val KEY_DATA_PUBLIC = "ecdh_public"
    const val KEY_DATA_SHARE = "ecdh_share"

    operator fun get(name: String): String? = mmkv?.getString(name, null)

    operator fun set(key: String, value: String) {
        mmkv?.putString(key, value)
    }

    fun getData(key: String): ByteArray? = mmkv?.decodeBytes(key)

    fun setData(key: String, value: ByteArray) {
        mmkv?.encode(key, value)
    }
}
