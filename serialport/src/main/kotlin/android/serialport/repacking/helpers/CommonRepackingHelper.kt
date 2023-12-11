package android.serialport.repacking.helpers

import android.os.SystemClock
import android.serialport.repacking.Repackable
import java.io.IOException
import java.io.InputStream

/**
 * The simplest thing to do is not to deal with sticky packets,
 * read directly and return as much as InputStream.available() reads
 */
class CommonRepackingHelper : Repackable {
    override fun execute(`is`: InputStream): ByteArray? {
        try {
            val available = `is`.available()
            if (available > 0) {
                val buffer = ByteArray(available)
                val size = `is`.read(buffer)
                if (size > 0) {
                    return buffer
                }
            } else {
                SystemClock.sleep(50)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}