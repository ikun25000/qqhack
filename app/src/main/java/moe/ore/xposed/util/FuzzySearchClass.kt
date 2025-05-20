package moe.ore.xposed.util

import java.lang.reflect.Field

object FuzzySearchClass {
    /**
     * QQ混淆字典
     */
    private val dic = arrayOf(
        "r" , "t", "o", "a", "b", "c", "e", "f", "d", "g", "h", "i", "j", "k", "l", "m", "n", "p", "q", "s", "t", "u", "v", "w", "x", "y", "z"
    )

    fun findAllClassByField(classLoader: ClassLoader, prefix: String, check: (String, Field) -> Boolean): List<Class<*>> {
        val list = arrayListOf<Class<*>>()
        dic.forEach { className ->
            kotlin.runCatching {
                val clz = classLoader.loadClass("$prefix.$className")
                clz?.declaredFields?.forEach {
                    if (!isBaseType(it.type) && check(className, it)) {
                        list.add(clz)
                    }
                }
            }
        }
        return list
    }

    private fun isBaseType(clz: Class<*>): Boolean {
        return clz == Long::class.java ||
                clz == Double::class.java ||
                clz == Float::class.java ||
                clz == Int::class.java ||
                clz == Short::class.java ||
                clz == Char::class.java ||
                clz == Byte::class.java
    }
}