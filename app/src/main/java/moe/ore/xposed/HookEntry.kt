package moe.ore.xposed

import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.ore.txhook.common.ModeleStatus
import moe.ore.xposed.hooks.AntiDetection
import moe.ore.xposed.main.MainHook
import moe.ore.xposed.util.FuzzySearchClass
import moe.ore.xposed.util.XPClassloader
import moe.ore.xposed.util.afterHook
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal class HookEntry: IXposedHookLoadPackage {
    companion object {
        @JvmStatic
        var sec_static_stage_inited = false

        const val PACKAGE_NAME_QQ = "com.tencent.mobileqq"
        const val PACKAGE_NAME_TIM = "com.tencent.tim"
        const val PACKAGE_NAME_WATCH = "com.tencent.qqlite"
        const val PACKAGE_NAME_QIDIAN = "com.tencent.qidian"
        const val PACKAGE_NAME_TXHOOK = "moe.ore.txhook"
    }

    private var firstStageInit = false

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        when(param.packageName) {
            PACKAGE_NAME_TXHOOK -> {
                XposedHelpers.findAndHookMethod(ModeleStatus::class.java.name, param.classLoader, "isModuleActivated", XC_MethodReplacement.returnConstant(true))
            }
            PACKAGE_NAME_QQ -> entryMQQ(0, param)
            PACKAGE_NAME_TIM -> entryMQQ(1, param)
            PACKAGE_NAME_WATCH -> entryMQQ(2, param)
            PACKAGE_NAME_QIDIAN -> entryMQQ(3, param)
        }
    }

    private fun entryMQQ(source: Int, loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (firstStageInit) return
        val startup = afterHook(51) { param ->
            try {
                val loader = param.thisObject.javaClass.classLoader!!
                XPClassloader.ctxClassLoader = loader
                val clz = if (source == 2) {
                    try {
                        loader.loadClass("com.tencent.qqnt.watch.app.WatchApplicationDelegate")
                    } catch (e: ClassNotFoundException) {
                        loader.loadClass("com.tencent.common.app.BaseApplicationImpl")
                    }
                } else {
                    loader.loadClass("com.tencent.common.app.BaseApplicationImpl")
                }
                val field = clz.declaredFields.first {
                    it.type == clz
                }
                val app: Context? = field.get(null) as? Context
                if (app != null) {
                    execStartupInit(source, app, loadPackageParam.processName)
                } else {
                    XposedBridge.log("[TXHook] Unable to fetch context")
                }
            } catch (e: Throwable) {
                XposedBridge.log("[TXHook] $e")
            }
        }
        // 开始注入
        if (source == 2) {
            val clz = try {
                loadPackageParam.classLoader.loadClass("com.tencent.mobileqq.startup.step.LoadDex")
            } catch (e: ClassNotFoundException){
                loadPackageParam.classLoader.loadClass("com.tencent.qqnt.watch.startup.task.ApplicationCreateStageTask")
            }
            clz.declaredMethods
                .filter { it.returnType.equals(java.lang.Boolean.TYPE) && it.parameterTypes.isEmpty() }
                .forEach {
                    XposedBridge.hookMethod(it, startup)
                }
            firstStageInit = true
        } else {
            try {
                val loadDex = loadPackageParam.classLoader.loadClass("com.tencent.mobileqq.startup.step.LoadDex")
                loadDex.declaredMethods
                    .filter { it.returnType.equals(java.lang.Boolean.TYPE) && it.parameterTypes.isEmpty() }
                    .forEach {
                        XposedBridge.hookMethod(it, startup)
                    }
                firstStageInit = true
            } catch (e: ClassNotFoundException) {
                val fieldList = arrayListOf<Field>()
                FuzzySearchClass.findAllClassByField(loadPackageParam.classLoader, "com.tencent.mobileqq.startup.task.config") { _, field ->
                    (field.type == HashMap::class.java || field.type == Map::class.java) && Modifier.isStatic(field.modifiers)
                }.forEach {
                    it.declaredFields.forEach { field ->
                        if ((field.type == HashMap::class.java || field.type == Map::class.java) && Modifier.isStatic(field.modifiers))
                            fieldList.add(field)
                    }
                }
                fieldList.forEach { field ->
                    if (!field.isAccessible) field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    (field.get(null) as? Map<String, Class<*>>).also { map ->
                        map?.forEach { (key, clazz) ->
                            if (key.contains("LoadDex", ignoreCase = true)) {
                                clazz.declaredMethods.forEach { method ->
                                    if (method.parameterTypes.size == 1 && method.parameterTypes[0] == Context::class.java) {
                                        XposedBridge.hookMethod(method, startup)
                                    }
                                }
                            }
                        }
                    }
                }
                firstStageInit = true
            }
        }
    }

    private fun execStartupInit(source: Int, ctx: Context, processName: String) {
        if (sec_static_stage_inited) return

        val classLoader = ctx.classLoader.also { requireNotNull(it) }
        XPClassloader.hostClassLoader = classLoader

        if (injectClassloader(HookEntry::class.java.classLoader)) {
            if ("1" != System.getProperty("hook_flag")) {
                System.setProperty("hook_flag", "1")
            } else return

            if (processName.endsWith(":MSF")) {
                AntiDetection()
                MainHook(source, ctx)
                sec_static_stage_inited = true
            }
        }
    }

    private fun injectClassloader(moduleLoader: ClassLoader?): Boolean {
        if (moduleLoader != null) {
            if (kotlin.runCatching {
                    moduleLoader.loadClass("mqq.app.MobileQQ")
                }.isSuccess) {
                XposedBridge.log("[TXHook] ModuleClassloader already injected.")
                return true
            }

            val parent = moduleLoader.parent
            val field = ClassLoader::class.java.declaredFields
                .first { it.name == "parent" }
            field.isAccessible = true

            field.set(XPClassloader, parent)

            if (XPClassloader.load("mqq.app.MobileQQ") == null) {
                XposedBridge.log("[TXHook]XPClassloader init failed.")
                return false
            }

            field.set(moduleLoader, XPClassloader)

            return kotlin.runCatching {
                Class.forName("mqq.app.MobileQQ")
            }.onFailure {
                XposedBridge.log("[TXHook] Classloader inject failed.")
            }.onSuccess {
                XposedBridge.log("[TXHook] Classloader inject successfully.")
            }.isSuccess
        }
        return false
    }
}
