package moe.ore.xposed.base

interface IBaseHook {
    fun init() {}
    val enabled: Boolean
        get() = false
    val isCompatible: Boolean
        get() = false
    val description: String
        get() = ""
}
