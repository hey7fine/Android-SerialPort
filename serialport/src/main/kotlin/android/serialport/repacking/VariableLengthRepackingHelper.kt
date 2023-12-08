package android.serialport.repacking

import java.io.IOException
import java.io.InputStream
import java.nio.ByteOrder

/**
 * Variable-length sticky packet processing, used in the protocol with a length field
 * Example: The protocol is: type+dataLen+data+md5
 * type: Named type, two bytes
 * dataLen: The length of the data field, two bytes
 * data: Data field, variable length, length dataLen
 * md5: md5 field, 8 bytes
 * Use: 1.byteOrder: first determine the big and small ends, ByteOrder.BIG_ENDIAN or ByteOrder.LITTLE_ENDIAN;
 * 2.lenSize: The length of the len field, 2 in this example
 * 3.lenIndex: The position of the len field, 2 in this example, because the len field is preceded by type, and its length is 2
 * 4.offset: the length of the entire package -len, this example is the length of the three fields of type+dataLen+md5, that is, 2+2+8=12
 */
class VariableLengthRepackingHelper(
    private val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
    private val lenSize: Int = 2,
    lenIndex: Int = 0,
    private val offset: Int = 0
) : Repackable {
    private val mBytes: MutableList<Byte>
    private val lenStartIndex: Int
    private val lenEndIndex: Int

    init {
        mBytes = ArrayList()
        lenStartIndex = lenIndex
        lenEndIndex = lenIndex + lenSize - 1
        check(lenStartIndex <= lenEndIndex) { "lenStartIndex>lenEndIndex" }
    }

    private fun getLen(src: ByteArray, order: ByteOrder): Int {
        var re = 0
        if (order == ByteOrder.BIG_ENDIAN) {
            for (b in src) {
                re = re shl 8 or (b.toInt() and 0xff)
            }
        } else {
            for (i in src.indices.reversed()) {
                re = re shl 8 or (src[i].toInt() and 0xff)
            }
        }
        return re
    }

    override fun execute(`is`: InputStream): ByteArray? {
        mBytes.clear()
        var count = 0
        var value: Int
        var temp: Byte
        var msgLen = -1
        val lenField = ByteArray(lenSize)
        try {
            while (`is`.read().also { value = it } != -1) {
                temp = value.toByte()
                if (count in lenStartIndex..lenEndIndex) {
                    lenField[count - lenStartIndex] = temp
                    if (count == lenEndIndex) {
                        msgLen = getLen(lenField, byteOrder)
                    }
                }
                count++
                mBytes.add(temp)
                if (msgLen != -1) {
                    if (count == msgLen + offset) {
                        break
                    } else if (count > msgLen + offset) {
                        value = -1
                        break
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
        val result = ByteArray(mBytes.size)
        for (i in result.indices) {
            result[i] = mBytes[i]
        }
        return result
    }
}