package moe.ore.xposed.main

data class Result(val extra: String, val sign: String, val token: String)

data class Wrapper(
    val source: Int,
    val type: String,
    val cmd: String,
    val buffer: String,
    val seq: Int,
    val uin: String,
    val result: Result,
    val bit: String
)

data class EncryptStatisticInfo(val dhCostTime: Long, val encryptAlgorithm: String, val encryptTime: Long)

data class EncryptResult(
    val source: Int,
    val ecdhTag: String,
    val encryptContent: String,
    val encryptStatisticInfo: EncryptStatisticInfo?,
    val encryptType: Int,
    val isUseCache: Boolean,
    val iv: String,
    val msgNo: String,
    val publicKey: String,
    val secretKey: String,
    val uin: String,
    val str: String
)