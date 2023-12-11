package android.serialport.repacking.helpers

import android.serialport.repacking.Repackable
import java.io.IOException
import java.io.InputStream

/**
 * Fixed-length adhesive package treatment
 * Example: The protocol stipulates that the length of each packet is 16
 */
class FixedLengthRepackingHelper(
    private val stackLen: Int = 16
) : Repackable {

    override fun execute(`is`: InputStream): ByteArray? {
        var count = 0
        var len = -1
        var temp: Byte
        val result = ByteArray(stackLen)
        try {
            while (count < stackLen && `is`.read().also { len = it } != -1) {
                temp = len.toByte()
                result[count] = temp
                count++
            }
            if (len == -1) {
                return null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return result
    }
}