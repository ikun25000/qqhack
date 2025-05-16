@file:Suppress("LocalVariableName", "SpellCheckingInspection")
@file:OptIn(ExperimentalSerializationApi::class)

package moe.ore.xposed.main

import android.content.ContentValues
import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers
import kotlinx.serialization.ExperimentalSerializationApi
import moe.ore.txhook.app.CatchProvider
import moe.ore.txhook.helper.EMPTY_BYTE_ARRAY
import moe.ore.txhook.helper.hex2ByteArray
import moe.ore.txhook.helper.toHexString
import moe.ore.xposed.util.GlobalData
import moe.ore.xposed.util.HookUtil
import moe.ore.xposed.util.XPClassloader.load
import moe.ore.xposed.util.hookMethod
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

object MainHook {
    private const val DEFAULT_URI = "content://${CatchProvider.MY_URI}"
    private const val MODE_BDH_SESSION = "bdh.session"
    private const val MODE_BDH_SESSION_KEY = "bdh.sessionkey"
    private const val MODE_MD5 = "md5"
    private const val MODE_TLV_GET_BUF = "tlv.get_buf"
    private const val MODE_TLV_SET_BUF = "tlv.set_buf"
    private const val MODE_TEA = "tea"
    private const val MODE_RECE_DATA = "receData"
    private const val MODE_SEND = "send"
    private const val TYPE_FLY = "fly"
    private const val TYPE_GETSIGN = "getsign"
    private const val TYPE_GET_FE_KIT_ATTACH = "getFeKitAttach"
    private const val TYPE_NATIVE_SET_ACCOUNT_KEY = "nativeSetAccountKey"

    private val defaultUri = DEFAULT_URI.toUri()
    private var isInit: Boolean = false
    private var source = 0
    private val global = GlobalData()
    private val CodecWarpper = load("com.tencent.qphone.base.util.CodecWarpper")!!
    private val cryptor = load("oicq.wlogin_sdk.tools.cryptor")!!
    private val tlv_t = load("oicq.wlogin_sdk.tlv_type.tlv_t")!!
    private val MD5 = load("oicq.wlogin_sdk.tools.MD5")!!
    private val HighwaySessionData = load("com.tencent.mobileqq.highway.openup.SessionInfo")!!

    operator fun invoke(source: Int, ctx: Context) {
        HookUtil.contentResolver = ctx.contentResolver
        HookUtil.contextWeakReference = WeakReference(ctx)
        this.source = source

        hookCodecWarpperInit()
        hookMD5()
        hookTlv()
        hookTea()
        hookSendPacket()
        hookReceData()
        hookBDH()
        hookParams()
    }

    private fun hookCodecWarpperInit() {
        CodecWarpper.hookMethod("init")?.before {
            if (it.args.size >= 2) {
                it.args[1] = true // 强制打开调试模式
                if (!isInit) {
                    val thisClass = it.thisObject.javaClass
                    hookReceive(thisClass)
                }
            }
        }?.after {
            isInit = true
        }
    }

    private fun hookBDH() {
        hookForceUseHttp()
        hookGetSession()
        hookGetSessionKey()
    }

    private fun hookForceUseHttp() {
        val connMng = load("com.tencent.mobileqq.highway.config.ConfigManager")
        connMng.hookMethod("getNextSrvAddr")?.after {
            XposedHelpers.setIntField(it.result, "protoType", 2)
        }

        val pointClz = load("com.tencent.mobileqq.highway.utils.EndPoint")
        val tcpConn = load("com.tencent.mobileqq.highway.conn.TcpConnection")
        XposedBridge.hookAllConstructors(tcpConn, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args.filter { it.javaClass == pointClz }.forEach {
                    XposedHelpers.setIntField(it, "protoType", 2)
                }
            }
        })
    }

    private fun hookGetSession() {
        HighwaySessionData.hookMethod("getHttpconn_sig_session")?.after {
            val result = it.result as ByteArray
            sendDataToDefaultUri(MODE_BDH_SESSION, result)
        }
    }

    private fun hookGetSessionKey() {
        HighwaySessionData.hookMethod("getSessionKey")?.after {
            val result = it.result as ByteArray
            sendDataToDefaultUri(MODE_BDH_SESSION_KEY, result)
        }
    }

    private fun hookParams() {
        hookByteDataGetSign()
        hookDandelionFly()
        hookQQSecuritySignGetSign()
        hookQSecGetFeKitAttach()
        hookD2Key()
    }

    private fun hookByteDataGetSign() {
        val bytedataClz = load("com.tencent.secprotocol.ByteData")
        bytedataClz?.hookMethod("getSign")?.after {
            val result = it.result as ByteArray
            val data = it.args[1] as String
            val salt = it.args[2] as ByteArray
            postCallToken(TYPE_FLY, data, salt, result)
        }
    }

    private fun hookDandelionFly() {
        val dandelionClz = load("com.tencent.mobileqq.qsec.qsecdandelionsdk.Dandelion")
        dandelionClz?.hookMethod("fly")?.after {
            val result = it.result as ByteArray
            val data = it.args[0] as String
            val salt = it.args[1] as ByteArray
            postCallToken(TYPE_FLY, data, salt, result)
        }
    }

    private fun hookQQSecuritySignGetSign() {
        val qqsecuritysignClz = load("com.tencent.mobileqq.sign.QQSecuritySign")
        qqsecuritysignClz?.declaredMethods?.firstOrNull {
            it.name == "getSign" && it.parameterTypes.size == 5 &&
                    it.parameterTypes[1] == String::class.java &&
                    it.parameterTypes[2] == ByteArray::class.java &&
                    it.parameterTypes[3] == ByteArray::class.java &&
                    it.parameterTypes[4] == String::class.java
        }?.let { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val cmd = param.args[1] as String
                    val buffer = param.args[2] as ByteArray
                    val seq = param.args[3] as ByteArray
                    val uin = param.args[4] as String
                    val result = param.result
                    val resultData = getQQSecuritySignResultData(result)
                    val checkData = getCheckDataAndRemoveFromGlobal()
                    val wrapper = Wrapper(
                        type = TYPE_GETSIGN,
                        cmd = cmd,
                        buffer = buffer.toHexString(),
                        seq = ByteBuffer.wrap(seq).int,
                        uin = uin,
                        result = resultData,
                        source = source,
                        bit = checkData
                    )
                    val json = Gson().toJson(wrapper)
                    HookUtil.postTo("callToken", json)
                }
            })
        }
    }

    private fun getQQSecuritySignResultData(result: Any?): Result {
        var extra: ByteArray? = null
        var sign: ByteArray? = null
        var token: ByteArray? = null
        result?.let {
            if (it.javaClass.name == "com.tencent.mobileqq.sign.QQSecuritySign\$SignResult") {
                extra = it.javaClass.getField("extra").get(it) as? ByteArray
                sign = it.javaClass.getField("sign").get(it) as? ByteArray
                token = it.javaClass.getField("token").get(it) as? ByteArray
            }
        }
        return Result(extra!!.toHexString(), sign!!.toHexString(), token!!.toHexString())
    }

    private fun getCheckDataAndRemoveFromGlobal(): String {
        var checkData = ""
        if ("checkData" in global) {
            checkData = global["checkData"] as String
            global.remove("checkData")
        }
        return checkData
    }

    private fun hookQSecGetFeKitAttach() {
        val qsecClz = load("com.tencent.mobileqq.qsec.qsecurity.QSec")
        qsecClz?.hookMethod("getFeKitAttach")?.after {
            val uin = it.args[1] as String
            val cmd = it.args[2] as String
            val subcmd = it.args[3] as String
            val result = it.result as ByteArray
            postCallTokenWithExtraInfo(TYPE_GET_FE_KIT_ATTACH, uin, cmd, subcmd, result)
        }
    }

    private fun hookD2Key() {
        when (source) {
            0, 1, 2, 8 -> hookCodecWarpperNativeSetAccountKey()
            3, 4 -> hookCodecWarpperSetAccountKey()
        }
    }

    private fun hookCodecWarpperNativeSetAccountKey() {
        CodecWarpper.hookMethod("nativeSetAccountKey")?.after {
            val uin = it.args[0] as String
            val d2key = it.args[7] as ByteArray
            postCallTokenWithD2Key(uin, d2key)
        }
    }

    private fun hookCodecWarpperSetAccountKey() {
        CodecWarpper.hookMethod("setAccountKey")?.after {
            val uin = it.args[0] as String
            val d2key = it.args[7] as ByteArray
            postCallTokenWithD2Key(uin, d2key)
        }
    }

    private fun hookMD5() {
        hookMD5ToMD5ByteByteArray()
        hookMD5ToMD5ByteString()
        hookMD5ToMD5String()
        hookMD5ToMD5ByteArray()
    }

    private fun hookMD5ToMD5ByteByteArray() {
        XposedHelpers.findAndHookMethod(MD5, "toMD5Byte", ByteArray::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = param.args[0] as ByteArray
                val result = param.result as ByteArray? ?: EMPTY_BYTE_ARRAY
                submitMd5(data, result)
            }
        })
    }

    private fun hookMD5ToMD5ByteString() {
        XposedHelpers.findAndHookMethod(MD5, "toMD5Byte", String::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = (param.args[0] as String?)?.toByteArray()
                data?.let {
                    val result = param.result as ByteArray
                    submitMd5(it, result)
                }
            }
        })
    }

    private fun hookMD5ToMD5String() {
        XposedHelpers.findAndHookMethod(MD5, "toMD5", String::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = (param.args[0] as String?)?.toByteArray()
                data?.let {
                    val result = (param.result as String).hex2ByteArray()
                    submitMd5(it, result)
                }
            }
        })
    }

    private fun hookMD5ToMD5ByteArray() {
        XposedHelpers.findAndHookMethod(MD5, "toMD5", ByteArray::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = param.args[0] as ByteArray
                val result = (param.result as String).hex2ByteArray()
                submitMd5(data, result)
            }
        })
    }

    private fun submitMd5(data: ByteArray, result: ByteArray) {
        val value = ContentValues()
        value.put("mode", MODE_MD5)
        value.put("data", data)
        value.put("result", result)
        HookUtil.sendTo(defaultUri, value, source)
    }

    private fun hookTlv() {
        kotlin.runCatching {
            val cmd = tlv_t.getDeclaredField("_cmd").also {
                it.isAccessible = true
            }
            hookTlvGetBuf(cmd)
            hookTlvSetBuf(cmd)
        }
    }

    private fun hookTlvGetBuf(cmd: java.lang.reflect.Field) {
        tlv_t.hookMethod("get_buf")?.after {
            val thiz = it.thisObject
            val result = it.result as ByteArray
            val tlvVer = cmd.get(thiz) as Int
            sendTlvDataToDefaultUri(MODE_TLV_GET_BUF, result, tlvVer)
        }
    }

    private fun hookTlvSetBuf(cmd: java.lang.reflect.Field) {
        val buf = tlv_t.getDeclaredField("_buf").also {
            it.isAccessible = true
        }
        tlv_t.hookMethod("get_tlv")?.after {
            val thiz = it.thisObject
            val result = buf.get(thiz) as ByteArray
            val tlvVer = cmd.get(thiz) as Int
            sendTlvDataToDefaultUri(MODE_TLV_SET_BUF, result, tlvVer)
        }
    }

    private fun sendTlvDataToDefaultUri(mode: String, data: ByteArray, version: Int) {
        val value = ContentValues()
        value.put("mode", mode)
        value.put("data", data)
        value.put("version", version)
        HookUtil.sendTo(defaultUri, value, source)
    }

    private fun hookTea() {
        hookTeaEncrypt()
        hookTeaDecrypt()
    }

    private fun hookTeaEncrypt() {
        cryptor.hookMethod("encrypt")?.after {
            handleTeaHook(it, true)
        }
    }

    private fun hookTeaDecrypt() {
        cryptor.hookMethod("decrypt")?.after {
            handleTeaHook(it, false)
        }
    }

    private fun handleTeaHook(it: XC_MethodHook.MethodHookParam, isEnc: Boolean) {
        val data = it.args[0] as ByteArray
        val skip = it.args[1] as Int
        val len = it.args[2] as Int
        val key = (it.args[3] as ByteArray).toHexString()
        val result = (it.result as ByteArray).toHexString()
        if (len > 0) {
            val newData = data.copyOfRange(skip, skip + len).toHexString()
            val value = ContentValues()
            value.put("enc", isEnc)
            value.put("mode", MODE_TEA)
            value.put("data", newData)
            value.put("len", len)
            value.put("result", result)
            value.put("key", key)
            HookUtil.sendTo(defaultUri, value, source)
        }
    }

    private fun hookReceData() {
        CodecWarpper.hookMethod("onReceData")?.after {
            val args = it.args
            when (args.size) {
                1 -> handleReceDataOneArg(args)
                2 -> handleReceDataTwoArgs(args)
                3 -> handleReceDataThreeArgs(args)
                else -> log("[TXHook] onReceData 不知道hook到了个不知道什么东西")
            }
        }
    }

    private fun handleReceDataOneArg(args: Array<Any>) {
        val data = args[0] as ByteArray
        val size = data.size
        sendReceDataToDefaultUri(data, size)
    }

    private fun handleReceDataTwoArgs(args: Array<Any>) {
        val data = args[0] as ByteArray
        var size = args[1] as Int
        if (size == 0) size = data.size
        sendReceDataToDefaultUri(data, size)
    }

    private fun handleReceDataThreeArgs(args: Array<Any>) {
        val data = args[0] as ByteArray
        var size = args[1] as Int
        if (size == 0) size = data.size
        sendReceDataToDefaultUri(data, size)
    }

    private fun sendReceDataToDefaultUri(data: ByteArray, size: Int) {
        val util = ContentValues()
        util.put("data", data.toHexString())
        util.put("size", size)
        util.put("mode", MODE_RECE_DATA)
        HookUtil.sendTo(defaultUri, util, source)
    }

    private fun hookSendPacket() {
        CodecWarpper.hookMethod("encodeRequest")?.after { param ->
            val args = param.args
            when (args.size) {
                17, 14, 16, 15 -> handleSendPacket(args, param.result as ByteArray)
                else -> log("[TXHook] encodeRequest 不知道hook到了个不知道什么东西")
            }
        }
    }

    private fun handleSendPacket(args: Array<Any>, result: ByteArray) {
        val seq = args[0] as Int
        val cmd = args[5] as String
        val msgCookie = args[6] as? ByteArray
        val uin = args[9] as String
        val buffer = when (args.size) {
            17 -> args[15] as ByteArray
            14 -> args[12] as ByteArray
            16 -> args[14] as ByteArray
            15 -> args[13] as ByteArray
            else -> EMPTY_BYTE_ARRAY
        }
        sendSendPacketDataToDefaultUri(uin, seq, cmd, msgCookie, buffer, result)
    }

    private fun sendSendPacketDataToDefaultUri(uin: String, seq: Int, cmd: String, msgCookie: ByteArray?, buffer: ByteArray, result: ByteArray) {
        val util = ContentValues()
        util.put("uin", uin)
        util.put("seq", seq)
        util.put("cmd", cmd)
        util.put("type", "unknown")
        util.put("msgCookie", msgCookie ?: EMPTY_BYTE_ARRAY)
        util.put("buffer", buffer)
        util.put("result", result)
        util.put("mode", MODE_SEND)
        HookUtil.sendTo(defaultUri, util, source)
    }

    private fun hookReceive(clazz: Class<*>) {
        clazz.hookMethod("onResponse")?.after { param ->
            val from = param.args[1]
            val seq = HookUtil.invokeFromObjectMethod(from, "getRequestSsoSeq") as Int
            val cmd = HookUtil.invokeFromObjectMethod(from, "getServiceCmd") as String
            val msgCookie = HookUtil.invokeFromObjectMethod(from, "getMsgCookie") as? ByteArray
            val uin = HookUtil.invokeFromObjectMethod(from, "getUin") as String
            val buffer = HookUtil.invokeFromObjectMethod(from, "getWupBuffer") as ByteArray
            // -- qimei [15] imei [2] version [4]

            val util = ContentValues()
            util.put("uin", uin)
            util.put("seq", seq)
            util.put("cmd", cmd)
            util.put("type", "unknown")
            util.put("msgCookie", msgCookie ?: EMPTY_BYTE_ARRAY)
            util.put("buffer", buffer)

            util.put("mode", "receive")

            HookUtil.sendTo(defaultUri, util, source)
        }
    }

    private fun sendDataToDefaultUri(mode: String, data: ByteArray) {
        val values = ContentValues()
        values.put("mode", mode)
        values.put("data", data.toHexString())
        HookUtil.sendTo(defaultUri, values, source)
    }

    private fun postCallToken(type: String, data: String, salt: ByteArray, result: ByteArray) {
        HookUtil.postTo("callToken", JsonObject().apply {
            addProperty("type", type)
            addProperty("data", data)
            addProperty("salt", salt.toHexString())
            addProperty("result", result.toHexString())
            if ("checkData" in global) {
                addProperty("bit", global["checkData"] as String)
                global.remove("checkData")
            }
        }, source)
    }

    private fun postCallTokenWithExtraInfo(type: String, uin: String, cmd: String, subcmd: String, result: ByteArray) {
        HookUtil.postTo("callToken", JsonObject().apply {
            addProperty("type", type)
            addProperty("uin", uin)
            addProperty("cmd", cmd)
            addProperty("subcmd", subcmd)
            addProperty("result", result.toHexString())
            if ("checkData" in global) {
                addProperty("bit", global["checkData"] as String)
                global.remove("checkData")
            }
        }, source)
    }

    private fun postCallTokenWithD2Key(uin: String, d2key: ByteArray) {
        HookUtil.postTo("callToken", JsonObject().apply {
            addProperty("type", TYPE_NATIVE_SET_ACCOUNT_KEY)
            addProperty("uin", uin)
            addProperty("d2key", d2key.toHexString())
        }, source)
    }
}

data class Result(
    @SerializedName("extra") val extra: String,
    @SerializedName("sign") val sign: String,
    @SerializedName("token") val token: String
)

data class Wrapper(
    @SerializedName("source") val source: Int,
    @SerializedName("type") val type: String,
    @SerializedName("cmd") val cmd: String,
    @SerializedName("buffer") val buffer: String,
    @SerializedName("seq") val seq: Int,
    @SerializedName("uin") val uin: String,
    @SerializedName("result") val result: Result,
    @SerializedName("bit") val bit: String
)
