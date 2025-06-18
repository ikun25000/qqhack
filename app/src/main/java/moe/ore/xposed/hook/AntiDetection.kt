package moe.ore.xposed.hook

import moe.ore.xposed.utils.XPClassloader.load
import moe.ore.xposed.utils.hookMethod

internal object AntiDetection {

    operator fun invoke() {
        disableSwitch()
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
