package android.serialport.repacking

import java.io.IOException
import java.io.InputStream

/**
 * The sticky packet processing of specific characters,
 * one Byte[] at the beginning and the end, cannot be empty at the same time,
 * if one of them is empty, then the non-empty is used as the split marker
 * Example: The protocol is formulated as ^+data+$, starting with ^ and ending with $
 */
class SpecifiedRepackingHelper(
    private val head: ByteArray?,
    private val tail: ByteArray?
) : Repackable {
    private val bytes: MutableList<Byte>
    private val headLen: Int
    private val tailLen: Int

    init {
        check(!(head == null || tail == null)) { " head or tail is Null" }
        check(!(head.isEmpty() && tail.isEmpty())) { " head and tail is Empty" }
        headLen = head.size
        tailLen = tail.size
        bytes = ArrayList()
    }

    private fun endWith(src: ByteArray, target: ByteArray?): Boolean {
        if (src.size < target!!.size) {
            return false
        }
        for (i in target.indices) {
            if (target[target.size - i - 1] != src[src.size - i - 1]) {
                return false
            }
        }
        return true
    }

    private fun getRangeBytes(list: List<Byte>, start: Int, end: Int): ByteArray {
        val temps = list.toTypedArray().copyOfRange(start, end)
        val result = ByteArray(temps.size)
        for (i in result.indices) {
            result[i] = temps[i]
        }
        return result
    }

    override fun execute(`is`: InputStream): ByteArray? {
        bytes.clear()
        var value: Int
        var startIndex = -1
        var result: ByteArray? = null
        try {
            while (`is`.read().also { value = it } != -1) {
                bytes.add(value.toByte())
                val byteArray: ByteArray = bytes.toByteArray()
                if (headLen == 0 || tailLen == 0) {
                    // Only head or tail matches
                    if (endWith(byteArray, head) || endWith(byteArray, tail)) {
                        if (startIndex == -1) {
                            startIndex = bytes.size - headLen
                        } else {
                            result = getRangeBytes(bytes, startIndex, bytes.size)
                            break
                        }
                    }
                } else {
                    // head and tail matches
                    if (startIndex == -1) {
                        if (endWith(byteArray, head)) {
                            startIndex = bytes.size - headLen
                        }
                    } else {
                        if (
                            endWith(byteArray, tail) &&
                            startIndex + headLen <= bytes.size - tailLen
                        ) {
                            result = getRangeBytes(bytes, startIndex, bytes.size)
                            break
                        }
                    }
                }
            }
            if (value == -1) {
                return null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return result
    }
}