package moe.ore.xposed

import android.app.Application
import android.content.Context
import android.content.res.XModuleResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.ore.xposed.common.ModeleStatus
import moe.ore.xposed.hook.AntiDetection
import moe.ore.xposed.hook.MainHook
import moe.ore.xposed.hook.base.ProcUtil
import moe.ore.xposed.hook.base.hostAndroidId
import moe.ore.xposed.hook.base.hostApp
import moe.ore.xposed.hook.base.hostAppName
import moe.ore.xposed.hook.base.hostClassLoader
import moe.ore.xposed.hook.base.hostInit
import moe.ore.xposed.hook.base.hostPackageName
import moe.ore.xposed.hook.base.hostProcessName
import moe.ore.xposed.hook.base.hostVersionCode
import moe.ore.xposed.hook.base.hostVersionName
import moe.ore.xposed.hook.base.modulePath
import moe.ore.xposed.hook.base.moduleRes
import moe.ore.xposed.hook.enums.QQTypeEnum
import moe.ore.xposed.utils.FuzzySearchClass
import moe.ore.xposed.utils.XPClassloader
import moe.ore.xposed.utils.afterHook
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal class HookEntry: IXposedHookLoadPackage, IXposedHookZygoteInit {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (QQTypeEnum.contain(lpparam.packageName)) {
            if (QQTypeEnum.valueOfPackage(lpparam.packageName) != QQTypeEnum.TXHook) {
                hostPackageName = lpparam.packageName
                hostProcessName = lpparam.processName
                hostClassLoader = lpparam.classLoader
            }

            when (QQTypeEnum.valueOfPackage(lpparam.packageName)) {
                QQTypeEnum.QQ -> entryMQQ(0, lpparam)
                QQTypeEnum.TIM -> entryMQQ(1, lpparam)
                QQTypeEnum.WATCH -> entryMQQ(2, lpparam)
                QQTypeEnum.QIDIAN -> entryMQQ(3, lpparam)
                QQTypeEnum.TXHook -> {
                    XposedHelpers.findAndHookMethod(ModeleStatus::class.java.name, lpparam.classLoader, "isModuleActivated", XC_MethodReplacement.returnConstant(true))
                }
            }
        }
    }

    private fun entryMQQ(source: Int, loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (hostInit && !ProcUtil.isMain && ProcUtil.isMsf) return

        val startup = afterHook(50) { param ->
            try {
                val loader = param.thisObject.javaClass.classLoader!!
                XPClassloader.ctxClassLoader = loader
                val clz = if (source == 2) {
                    try {
                        loader.loadClass("com.tencent.qqnt.watch.app.WatchApplicationDelegate")
                    } catch (_: ClassNotFoundException) {
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
                    hostApp = app as Application
                    hostClassLoader = hostApp.classLoader
                    if (ProcUtil.isMain) {
                        XposedBridge.log("[TXHook] hook version: ${hostAppName}-$hostVersionName($hostVersionCode)")
                        XposedBridge.log("[TXHook] androidId: $hostAndroidId")
                    }
                    execStartupInit(source, app)
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
            } catch (_: ClassNotFoundException){
                loadPackageParam.classLoader.loadClass("com.tencent.qqnt.watch.startup.task.ApplicationCreateStageTask")
            }
            clz.declaredMethods
                .filter { it.returnType.equals(java.lang.Boolean.TYPE) && it.parameterTypes.isEmpty() }
                .forEach {
                    XposedBridge.hookMethod(it, startup)
                }
        } else {
            try {
                val loadDex = loadPackageParam.classLoader.loadClass("com.tencent.mobileqq.startup.step.LoadDex")
                loadDex.declaredMethods
                    .filter { it.returnType.equals(java.lang.Boolean.TYPE) && it.parameterTypes.isEmpty() }
                    .forEach {
                        XposedBridge.hookMethod(it, startup)
                    }
            } catch (_: ClassNotFoundException) {
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
            }
        }
    }

    private fun execStartupInit(source: Int, ctx: Context) {
        val classLoader = ctx.classLoader.also { requireNotNull(it) }
        XPClassloader.hostClassLoader = classLoader

        if (ProcUtil.isMsf) {
            AntiDetection()
            MainHook(source, ctx)
        }
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        if (startupParam == null) return
        modulePath = startupParam.modulePath
        moduleRes = XModuleResources.createInstance(modulePath, null)
    }
}
