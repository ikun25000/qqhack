package moe.ore.xposed.helper

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
object ConfigPusher {
    const val KEY_PUSH_API = "push_api"

    const val KEY_DATA_PUBLIC = "ecdh_public"
    const val KEY_DATA_SHARE = "ecdh_share"

    const val ALLOW_SOURCE = "find_source"

    private val config = Config()

    fun initForOnce() {
        this[ALLOW_SOURCE] = "yes"
    }

    operator fun get(name: String): String? {
        return config.cfg[name]
    }

    operator fun set(key: String, value: String) {
        config.cfg[key] = value
    }

    fun getData(key: String): ByteArray? {
        return config.data[key]
    }

    fun setData(key: String, value: ByteArray) {
        config.data[key] = value
    }

    @Serializable
    private class Config(
        @ProtoNumber(1) val cfg: HashMap<String, String> = hashMapOf(),
        @ProtoNumber(2) val data: HashMap<String, ByteArray> = hashMapOf()
    )
}
