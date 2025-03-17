package moe.ore.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import moe.ore.xposed.util.XPClassloader.load

internal class AntiDetection {

    fun invoke() {
        disableNewMSF()
    }

    private fun disableNewMSF() {
        val clz = load("com.tencent.mobileqq.msf.core.f0.b")
        val argsClass = load("com.tencent.mobileqq.msf.core.MsfCore")
        if (clz != null && argsClass != null) {
            clz.declaredMethods.firstOrNull {
                it.parameterTypes.size == 2 && it.parameterTypes[0] == argsClass && it.parameterTypes[1] == Boolean::class.java
            }?.let { method ->
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
    }
}
