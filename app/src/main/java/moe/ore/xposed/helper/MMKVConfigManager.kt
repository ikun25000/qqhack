
package moe.ore.xposed.helper

import com.tencent.mmkv.MMKV

object MMKVConfigManager {
    private val mmkv: MMKV by lazy {
        MMKV.mmkvWithID("txhook_config", MMKV.MULTI_PROCESS_MODE)
    }

    const val KEY_FORBID_HTTP = "forbid_http"
    const val KEY_PUSH_API = "push_api"
    const val ALLOW_SOURCE = "find_source"

    operator fun get(name: String): String? = mmkv.getString(name, null)

    operator fun set(key: String, value: String) {
        mmkv.putString(key, value)
    }

    fun getData(key: String): ByteArray? = mmkv.decodeBytes(key)

    fun setData(key: String, value: ByteArray) {
        mmkv.encode(key, value)
    }
}

