@file:Suppress("LocalVariableName", "SpellCheckingInspection")
@file:OptIn(ExperimentalSerializationApi::class)

package moe.ore.xposed.main

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers
import kotlinx.serialization.ExperimentalSerializationApi
import moe.ore.android.AndroKtx
import moe.ore.txhook.app.CatchProvider
import moe.ore.txhook.helper.EMPTY_BYTE_ARRAY
import moe.ore.txhook.helper.hex2ByteArray
import moe.ore.txhook.helper.toHexString
import moe.ore.xposed.util.GlobalData
import moe.ore.xposed.util.HookUtil
import moe.ore.xposed.util.XPClassloader.load
import moe.ore.xposed.util.hookMethod
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

object MainHook {
    private val defaultUri = Uri.parse("content://${CatchProvider.MY_URI}")
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

        if (!isInit) checkPermissions(ctx)
        CodecWarpper.hookMethod("init")?.before {
            if (it.args.size >= 2) {
                it.args[1] = true // 强制打开调试模式
                // if (it.args.size >= 3) it.args[2] = true // test version
                if (!isInit) {
                    val thisClass = it.thisObject.javaClass
                    hookReceive(thisClass)
                }
            }
        }?.after {
            isInit = true
        }
        hookMD5()
        hookTlv()
        hookTea()
        hookSendPacket()
        hookReceData()
        hookBDH()
        hookParams()
    }

    private var has_permision = false

    private fun hasStorePermission(ctx: Context): Boolean {
        if (has_permision) return true
        has_permision = ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return has_permision
    }

    private fun requestStorePermission(ctx: Context) {
        kotlin.runCatching {
            ActivityCompat.requestPermissions(ctx as Activity, arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ), 0)
        }.onFailure {
            log(it)
        }
    }

    private fun checkPermissions(ctx: Context) {
        if (!hasStorePermission(ctx)) {
            // Toast.toast(ctx, "TXHook需要储存权限才可以运行，请授权对应权限。")
            if (ctx is Activity) {
                requestStorePermission(ctx)
            }
        }
    }

    private fun hookBDH() {
        // 强制使用HTTP
        val connMng = load("com.tencent.mobileqq.highway.config.ConfigManager") // com.tencent.mobileqq.highway.conn.ConnManager
        connMng.hookMethod("getNextSrvAddr")?.after {
            XposedHelpers.setIntField(it.result, "protoType", 2)
        }
        val pointClz = load("com.tencent.mobileqq.highway.utils.EndPoint")
        val tcpConn = load("com.tencent.mobileqq.highway.conn.TcpConnection")
        XposedBridge.hookAllConstructors(tcpConn, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args.filter { it.javaClass == pointClz }.forEach {
                    XposedHelpers.setIntField(it, "protoType", 2)
                }
            }
        })

        // 获取session
        HighwaySessionData.hookMethod("getHttpconn_sig_session")?.after {
            val result = it.result as ByteArray
            val values = ContentValues()
            values.put("mode", "bdh.session")
            values.put("data", result.toHexString())
            HookUtil.sendTo(defaultUri, values, source)
        }
        // 获取sessionkey
        HighwaySessionData.hookMethod("getSessionKey")?.after {
            val result = it.result as ByteArray
            val values = ContentValues()
            values.put("mode", "bdh.sessionkey")
            values.put("data", result.toHexString())
            HookUtil.sendTo(defaultUri, values, source)
        }
    }

    private fun hookParams() {
        val bytedataClz = load("com.tencent.secprotocol.ByteData")
        bytedataClz?.hookMethod("getSign")?.after {
            val result = it.result as ByteArray
            val data = it.args[1] as String
            val salt = it.args[2] as ByteArray
            HookUtil.postTo("callToken", JsonObject().apply {
                addProperty("type", "fly") // 名字不改了罢
                addProperty("data", data)
                addProperty("salt",salt.toHexString())
                addProperty("result", result.toHexString())
                if ("checkData" in global) {
                    addProperty("bit", global["checkData"] as String)
                    global.remove("checkData")
                }
            }, source)
        }

        val dandelionClz = load("com.tencent.mobileqq.qsec.qsecdandelionsdk.Dandelion")
        dandelionClz?.hookMethod("fly")?.after {
            val result = it.result as ByteArray
            val data = it.args[0] as String
            val salt = it.args[1] as ByteArray
            HookUtil.postTo("callToken", JsonObject().apply {
                addProperty("type", "fly")
                addProperty("data", data)
                addProperty("salt",salt.toHexString())
                addProperty("result", result.toHexString())
                if ("checkData" in global) {
                    addProperty("bit", global["checkData"] as String)
                    global.remove("checkData")
                }
            }, source)
        }

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
                    var extra: ByteArray? = null
                    var sign: ByteArray? = null
                    var token: ByteArray? = null
                    val gson = Gson()
                    val result = param.result
                    result?.let {
                        if (it.javaClass.name == "com.tencent.mobileqq.sign.QQSecuritySign\$SignResult") {
                            extra = it.javaClass.getField("extra").get(it) as? ByteArray
                            sign = it.javaClass.getField("sign").get(it) as? ByteArray
                            token = it.javaClass.getField("token").get(it) as? ByteArray
                        }
                    }
                    val resultData = Result(extra!!.toHexString(), sign!!.toHexString(), token!!.toHexString())
                    var checkData = ""
                    if ("checkData" in global) {
                        checkData = global["checkData"] as String
                        global.remove("checkData")
                    }
                    val wrapper = Wrapper(
                        type = "getsign",
                        cmd = cmd,
                        buffer = buffer.toHexString(),
                        seq = ByteBuffer.wrap(seq).int,
                        uin = uin,
                        result = resultData,
                        source = source,
                        bit = checkData
                    )
                    val json = gson.toJson(wrapper)
                    HookUtil.postTo("callToken", json)
                }
            })
        }

        val qsecClz = load("com.tencent.mobileqq.qsec.qsecurity.QSec")
        qsecClz?.hookMethod("getFeKitAttach")?.after{
            val uin = it.args[1] as String
            val cmd = it.args[2] as String
            val subcmd = it.args[3] as String
            val result = it.result as ByteArray
            HookUtil.postTo("callToken", JsonObject().apply {
                addProperty("type", "getFeKitAttach")
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
        // Hook D2Key
        when(source) {
            0, 1, 2, 8 -> {
                CodecWarpper.hookMethod("nativeSetAccountKey")?.after {
                    val uin = it.args[0] as String
                    val d2key = it.args[7] as ByteArray
                    HookUtil.postTo("callToken", JsonObject().apply {
                        addProperty("type", "nativeSetAccountKey")
                        addProperty("uin", uin)
                        addProperty("d2key", d2key.toHexString())
                    }, source)
                }
            }
            3, 4 -> {
                CodecWarpper.hookMethod("setAccountKey")?.after {
                    val uin = it.args[0] as String
                    val d2key = it.args[7] as ByteArray
                    HookUtil.postTo("callToken", JsonObject().apply {
                        addProperty("type", "nativeSetAccountKey")
                        addProperty("uin", uin)
                        addProperty("d2key", d2key.toHexString())
                    }, source)
                }
            }
        }
        // Hook D2Key End
    }

    private fun hookMD5() {
        XposedHelpers.findAndHookMethod(MD5, "toMD5Byte", ByteArray::class.java, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = param.args[0] as ByteArray
                val result = param.result as ByteArray? ?: EMPTY_BYTE_ARRAY
                submitMd5(data, result)
            }
        })
        XposedHelpers.findAndHookMethod(MD5, "toMD5Byte", String::class.java, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = (param.args[0] as String?)?.toByteArray()
                if (data != null) {
                    val result = param.result as ByteArray
                    submitMd5(data, result)
                }
            }
        })

        XposedHelpers.findAndHookMethod(MD5, "toMD5", String::class.java, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = (param.args[0] as String?)?.toByteArray()
                if (data != null) {
                    val result = (param.result as String).hex2ByteArray()
                    submitMd5(data, result)
                }
            }
        })
        XposedHelpers.findAndHookMethod(MD5, "toMD5", ByteArray::class.java, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val data = param.args[0] as ByteArray
                val result = (param.result as String).hex2ByteArray()
                submitMd5(data, result)
            }
        })
    }

    private fun submitMd5(data: ByteArray, result: ByteArray) {
        val value = ContentValues()
        value.put("mode", "md5")
        value.put("data", data)
        value.put("result", result)
        HookUtil.sendTo(defaultUri, value, source)
    }

    private fun hookTlv() {
        kotlin.runCatching {
            val cmd = tlv_t.getDeclaredField("_cmd").also {
                it.isAccessible = true
            }
            tlv_t.hookMethod("get_buf")?.after {
                val thiz = it.thisObject
                val result = it.result as ByteArray
                val tlvVer = cmd.get(thiz) as Int
                val value = ContentValues()
                value.put("mode", "tlv.get_buf")
                value.put("data", result)
                value.put("version", tlvVer)
                HookUtil.sendTo(defaultUri, value, source)
            }
            val buf = tlv_t.getDeclaredField("_buf").also {
                it.isAccessible = true
            }
            tlv_t.hookMethod("get_tlv")?.after {
                val thiz = it.thisObject
                val result = buf.get(thiz) as ByteArray
                val tlvVer = cmd.get(thiz) as Int
                val value = ContentValues()
                value.put("mode", "tlv.set_buf")
                value.put("data", result)
                value.put("version", tlvVer)
                HookUtil.sendTo(defaultUri, value, source)
            }
        }
    }

    private fun hookTea() {
        cryptor.hookMethod("encrypt")?.after {
            val data = it.args[0] as ByteArray
            val skip = it.args[1] as Int
            val len = it.args[2] as Int
            val key = (it.args[3] as ByteArray).toHexString()
            val result = (it.result as ByteArray).toHexString()
            if (len > 0) {
                val newData = data.copyOfRange(skip, skip + len).toHexString()
                val value = ContentValues()
                value.put("enc", true)
                value.put("mode", "tea")
                value.put("data", newData)
                value.put("len", len)
                value.put("result", result)
                value.put("key", key)
                HookUtil.sendTo(defaultUri, value, source)
            }
        }
        cryptor.hookMethod("decrypt")?.after {
            val data = it.args[0] as ByteArray
            val skip = it.args[1] as Int
            val len = it.args[2] as Int
            val key = (it.args[3] as ByteArray).toHexString()
            val result = (it.result as ByteArray).toHexString()
            if (len > 0) {
                val newData = data.copyOfRange(skip, skip + len).toHexString()
                val value = ContentValues()
                value.put("enc", false)
                value.put("mode", "tea")
                value.put("data", newData)
                value.put("len", len)
                value.put("result", result)
                value.put("key", key)
                HookUtil.sendTo(defaultUri, value, source)
            }
        }
    }

    private fun hookReceData() {
        CodecWarpper.hookMethod("onReceData")?.after {
            val args = it.args
            when(args.size) { // 搞定QQ手表
                1 -> { // 远古TIM也是一个参数
                    val data = args[0] as ByteArray
                    val size = data.size
                    val util = ContentValues()
                    util.put("data", data.toHexString())
                    util.put("size", size)
                    util.put("mode", "receData")
                    HookUtil.sendTo(defaultUri, util, source)
                }
                2 -> { // 搞定TIM
                    val data = args[0] as ByteArray
                    var size = args[1] as Int
                    if (size == 0) size = data.size
                    val util = ContentValues()
                    util.put("data", data.toHexString())
                    util.put("size", size)
                    util.put("mode", "receData")
                    HookUtil.sendTo(defaultUri, util, source)
                }
                3 -> { // 搞定QQ
                    val data = args[0] as ByteArray
                    var size = args[1] as Int
                    if (size == 0) size = data.size
                    val util = ContentValues()
                    util.put("data", data.toHexString())
                    util.put("size", size)
                    util.put("mode", "receData")
                    HookUtil.sendTo(defaultUri, util, source)
                }
                else -> {
                    log("[TXHook] onReceData 不知道hook到了个不知道什么东西")
                }
            }
        }
    }

    private fun hookSendPacket() {
        CodecWarpper.hookMethod("encodeRequest")?.after { param ->
            val args = param.args
            when (args.size) {
                17 -> {
                    val seq = args[0] as Int
                    val cmd = args[5] as String
                    // -- qimei [15] imei [2] version [4]

                    val result = param.result as ByteArray
                    val msgCookie = args[6] as? ByteArray
                    val uin = args[9] as String
                    val buffer = args[15] as ByteArray
                    val util = ContentValues()

                    util.put("uin", uin)
                    util.put("seq", seq)

                    util.put("cmd", cmd)
                    util.put("type", "unknown")
                    util.put("msgCookie", msgCookie ?: EMPTY_BYTE_ARRAY)
                    util.put("buffer", buffer)
                    util.put("result", result)
                    util.put("mode", "send")

                    HookUtil.sendTo(defaultUri, util, source)
                }
                14 -> {
                    val seq = args[0] as Int
                    val cmd = args[5] as String

                    val result = param.result as ByteArray
                    val msgCookie = args[6] as? ByteArray
                    val uin = args[9] as String
                    val buffer = args[12] as ByteArray

                    val util = ContentValues()
                    util.put("uin", uin)
                    util.put("seq", seq)
                    util.put("cmd", cmd)
                    util.put("type", "unknown")
                    util.put("msgCookie", msgCookie ?: EMPTY_BYTE_ARRAY)
                    util.put("buffer", buffer)
                    util.put("result", result)
                    util.put("mode", "send")

                    HookUtil.sendTo(defaultUri, util, source)
                }
                16 -> {
                    val seq = args[0] as Int
                    val cmd = args[5] as String

                    val result = param.result as ByteArray
                    val msgCookie = args[6] as? ByteArray
                    val uin = args[9] as String
                    val buffer = args[14] as ByteArray
                    // -- qimei [15] imei [2] version [4]

                    val util = ContentValues()
                    util.put("uin", uin)
                    util.put("seq", seq)
                    util.put("cmd", cmd)
                    util.put("type", "unknown")
                    util.put("msgCookie", msgCookie ?: EMPTY_BYTE_ARRAY)
                    util.put("buffer", buffer)
                    util.put("result", result)
                    util.put("mode", "send")

                    HookUtil.sendTo(defaultUri, util, source)
                }
                15 -> {
                    val seq = args[0] as Int
                    val cmd = args[5] as String

                    val result = param.result as ByteArray
                    val msgCookie = args[6] as? ByteArray
                    val uin = args[9] as String
                    val buffer = args[13] as ByteArray
                    val util = ContentValues()
                    util.put("uin", uin)
                    util.put("seq", seq)
                    util.put("cmd", cmd)
                    util.put("type", "unknown")
                    util.put("msgCookie", msgCookie ?: EMPTY_BYTE_ARRAY)
                    util.put("buffer", buffer)
                    util.put("result", result)
                    util.put("mode", "send")

                    HookUtil.sendTo(defaultUri, util, source)
                }
                else -> {
                    log("[TXHook] encodeRequest 不知道hook到了个不知道什么东西")
                }
            }
        }
    }

    private fun hookReceive(clazz: Class<*>) {
        clazz.hookMethod("onResponse")?.after { param ->
            val from = param.args[1]
            // ssoSeq serviceCmd msgCookie uin wupBuffer
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
}
