package moe.ore.xposed.main

import com.google.gson.annotations.SerializedName

data class Result(
    @SerializedName("extra") val extra: String,
    @SerializedName("sign") val sign: String,
    @SerializedName("token") val token: String
)

data class Wrapper(
    @SerializedName("source") val source: Int,
    @SerializedName("type") val type: String,
    @SerializedName("cmd") val cmd: String,
    @SerializedName("buffer") val buffer: String,
    @SerializedName("seq") val seq: Int,
    @SerializedName("uin") val uin: String,
    @SerializedName("result") val result: Result,
    @SerializedName("bit") val bit: String
)
