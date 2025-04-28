package moe.ore.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import moe.ore.xposed.util.XPClassloader.load
import moe.ore.xposed.util.hookMethod
import java.lang.reflect.Method

internal object AntiDetection {

    operator fun invoke() {
        disableSwitch()
    }

    private fun disableNewMSF() {
        val primaryClass = load("com.tencent.mobileqq.msf.core.f0.b")
        val fallbackClass = load("com.tencent.mobileqq.msf.core.g0.b")
        val msfCoreClass = load("com.tencent.mobileqq.msf.core.MsfCore")
        var targetMethod: Method? = null

        if (primaryClass != null && msfCoreClass != null) {
            targetMethod = primaryClass.declaredMethods.firstOrNull {
                it.parameterTypes.size == 2 && it.parameterTypes[0] == msfCoreClass && it.parameterTypes[1] == Boolean::class.java
            }
        }

        if (targetMethod == null && fallbackClass != null && msfCoreClass != null) {
            targetMethod = fallbackClass.declaredMethods.firstOrNull {
                it.parameterTypes.size == 2 && it.parameterTypes[0] == msfCoreClass && it.parameterTypes[1] == Boolean::class.java
            }
        }

        targetMethod?.let { method ->
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val isNewMSF = param.args[1] as Boolean
                    if (isNewMSF) {
                        param.args[1] = false
                    }
                }
            }
            XposedBridge.hookMethod(method, hook)
        }
    }

    private fun disableSwitch() {
        val configClass = load("com.tencent.freesia.UnitedConfig")
        configClass?.let {
            it.hookMethod("isSwitchOn")?.after { param ->
                val tag = param.args[1] as String
                if (tag == "msf_init_optimize" || tag == "msf_network_service_switch_new") {
                    param.result = false
                }
            }
        }
    }
}
