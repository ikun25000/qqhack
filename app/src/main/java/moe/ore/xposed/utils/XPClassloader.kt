package moe.ore.xposed.utils

import moe.ore.xposed.hook.config.PACKAGE_NAME_QQ

object XPClassloader: ClassLoader() {
    lateinit var hostClassLoader: ClassLoader
    lateinit var ctxClassLoader: ClassLoader

    fun load(name: String): Class<*>? {
        return kotlin.runCatching {
            loadClass(name)
        }.getOrNull()
    }

    override fun loadClass(name: String?): Class<*>? {
        if (name.isNullOrEmpty()) return null

        var processedClassName = name.replace('/', '.')
        if (processedClassName.startsWith("L") && processedClassName.endsWith(";")) {
            processedClassName = processedClassName.substring(1, processedClassName.length - 1)
        } else if (processedClassName.endsWith(";")) {
            processedClassName = processedClassName.substring(0, processedClassName.length - 1)
        }
        if (processedClassName.startsWith(".")) {
            processedClassName = PACKAGE_NAME_QQ + processedClassName
        }

        return kotlin.runCatching {
            hostClassLoader.loadClass(processedClassName)
        }.getOrElse {
            kotlin.runCatching {
                ctxClassLoader.loadClass(processedClassName)
            }.getOrElse {
                super.loadClass(processedClassName)
            }
        }
    }
}
