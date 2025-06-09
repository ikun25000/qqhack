package moe.ore.xposed.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UnPacket {
    private lateinit var buffer: ByteBuffer

    private fun ensureBufferInitialized() {
        if (!this::buffer.isInitialized) {
            throw IllegalStateException("Buffer not initialized. Call wrapBytesAddr first.")
        }
    }

    fun wrapBytesAddr(input: Any): UnPacket {
        val byteArray = when (input) {
            is ByteArray -> input
            is String -> hexStringToByteArray(input)
            else -> throw IllegalArgumentException("Unsupported input type")
        }
        buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.BIG_ENDIAN)
        return this
    }

    fun skip(len: Int): UnPacket {
        ensureBufferInitialized()
        if (len < 0 || buffer.position() + len > buffer.limit())
            throw IllegalStateException("Not enough bytes to skip $len")
        buffer.position(buffer.position() + len)
        return this
    }

    fun getPosition(): Int = buffer.position()
    fun getRemaining(): Int = buffer.remaining()

    fun getByte(): Byte {
        ensureBufferInitialized()
        if (buffer.remaining() < 1) throw IllegalStateException("Not enough bytes for byte")
        return buffer.get()
    }

    fun getShort(): Short {
        ensureBufferInitialized()
        if (buffer.remaining() < 2) throw IllegalStateException("Not enough bytes for short")
        return buffer.short
    }

    fun getInt(): Int {
        ensureBufferInitialized()
        if (buffer.remaining() < 4) throw IllegalStateException("Not enough bytes for int")
        return buffer.int
    }

    fun getLong(): Long {
        ensureBufferInitialized()
        if (buffer.remaining() < 8) throw IllegalStateException("Not enough bytes for long")
        return buffer.long
    }

    fun getFloat(): Float {
        ensureBufferInitialized()
        if (buffer.remaining() < 4) throw IllegalStateException("Not enough bytes for float")
        return buffer.float
    }

    fun getDouble(): Double {
        ensureBufferInitialized()
        if (buffer.remaining() < 8) throw IllegalStateException("Not enough bytes for double")
        return buffer.double
    }

    fun getBytes(length: Int): ByteArray {
        ensureBufferInitialized()
        if (buffer.remaining() < length)
            throw IllegalStateException("Not enough bytes to get $length bytes")
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }

    fun remainingBytes(): ByteArray {
        ensureBufferInitialized()
        val remainingBytes = ByteArray(buffer.remaining())
        buffer.get(remainingBytes)
        return remainingBytes
    }


    fun peekByte(): Byte {
        ensureBufferInitialized()
        if (buffer.remaining() < 1) throw IllegalStateException("Not enough bytes to peek byte")
        val dup = buffer.duplicate()
        dup.position(buffer.position())
        return dup.get()
    }

    fun peekShort(): Short {
        ensureBufferInitialized()
        if (buffer.remaining() < 2) throw IllegalStateException("Not enough bytes to peek short")
        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
        dup.position(buffer.position())
        return dup.short
    }

    fun peekInt(): Int {
        ensureBufferInitialized()
        if (buffer.remaining() < 4) throw IllegalStateException("Not enough bytes to peek int")
        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
        dup.position(buffer.position())
        return dup.int
    }

    fun peekLong(): Long {
        ensureBufferInitialized()
        if (buffer.remaining() < 8) throw IllegalStateException("Not enough bytes to peek long")
        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
        dup.position(buffer.position())
        return dup.long
    }

    fun peekFloat(): Float {
        ensureBufferInitialized()
        if (buffer.remaining() < 4) throw IllegalStateException("Not enough bytes to peek float")
        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
        dup.position(buffer.position())
        return dup.float
    }

    fun peekDouble(): Double {
        ensureBufferInitialized()
        if (buffer.remaining() < 8) throw IllegalStateException("Not enough bytes to peek double")
        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
        dup.position(buffer.position())
        return dup.double
    }

    fun peekBytes(length: Int): ByteArray {
        ensureBufferInitialized()
        if (buffer.remaining() < length)
            throw IllegalStateException("Not enough bytes to peek $length bytes")
        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
        dup.position(buffer.position())
        val bytes = ByteArray(length)
        dup.get(bytes)
        return bytes
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "")
        require(cleanHex.length % 2 == 0) { "Invalid hex string" }
        require(cleanHex.matches("[0-9a-fA-F]+".toRegex())) { "Invalid hex characters" }
        return ByteArray(cleanHex.length / 2) {
            cleanHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}
