/*
 * Copyright 2009 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.serialport

import android.serialport.repacking.helpers.CommonRepackingHelper
import android.serialport.repacking.Repackable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.*

/**
 * 串口
 *
 * @param device 串口设备文件
 * @param baudrate 波特率
 * @param dataBits 数据位；默认8,可选值为5~8
 * @param parity 奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
 * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
 * @param flags 默认0
 * @param repackingHelper 粘包处理类
 * @throws SecurityException
 * @throws IOException
 */
open class SerialPort @JvmOverloads constructor(
    /** 串口设备文件  */
    private val device: File,
    /** 波特率  */
    private val baudrate: Int,
    /** 数据位；默认8,可选值为5~8  */
    private val dataBits: Int = 8,
    /** 奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)  */
    private val parity: Int = 0,
    /** 停止位；默认1；1:1位停止位；2:2位停止位  */
    private val stopBits: Int = 1,
    private val flags: Int = 0,
    private val repackingHelper: Repackable = CommonRepackingHelper()
) {

    /*
     * Do not remove or rename the field mFd: it is used by native method close();
     */
    private lateinit var mFd: FileDescriptor
    private lateinit var mFileInputStream: FileInputStream
    private lateinit var mFileOutputStream: FileOutputStream
    val inputStream: InputStream
        // Getters and setters
        get() = mFileInputStream
    val outputStream: OutputStream
        get() = mFileOutputStream

    /**
     * 串口协程域
     */
    protected val lifecycleScope = CoroutineScope(Dispatchers.IO + Job())

    // JNI
    private external fun open(
        absolutePath: String, baudrate: Int, dataBits: Int, parity: Int,
        stopBits: Int, flags: Int
    ): FileDescriptor?

    external fun close()

    /**
     * 连接成功
     */
    open fun onStart() {

    }

    /**
     * 接收到数据
     */
    open fun onDataReceived(data:ByteArray) {

    }

    /**
     * 打开流和串口
     */
    fun connect() {
        mFd = open(device.absolutePath, baudrate, dataBits, parity, stopBits, flags)
            ?: throw IOException()
        mFileInputStream = FileInputStream(mFd)
        mFileOutputStream = FileOutputStream(mFd)

        execute()
        onStart()
    }

    /** 关闭流和串口，已经try-catch  */
    fun disconnect() {
        try {
            lifecycleScope.cancel()
            mFileInputStream.close()
            mFileOutputStream.close()
            close()
        } catch (e: Exception) {
            //e.printStackTrace();
        }
    }

    private fun execute() {
        lifecycleScope.launch {
            flow {
                while (isActive) {
                    repackingHelper.execute(inputStream)
                        ?.takeIf { it.isNotEmpty() }
                        ?.run {
                            emit(this)
                        }
                }
            }.catch {
                it.printStackTrace()
            }.collect {
                onDataReceived(it)
            }
        }
    }

    fun submit(data: ByteArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                outputStream.write(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        /* Check access permission */if (!device.canRead() || !device.canWrite()) {
            try {
                /* Missing read/write permission, trying to chmod the file */
                val su: Process = Runtime.getRuntime().exec(sSuPath)
                val cmd = """
                    chmod 666 ${device.absolutePath}
                    exit
                    
                    """.trimIndent()
                su.outputStream.write(cmd.toByteArray())
                if (su.waitFor() != 0 || !device.canRead() || !device.canWrite()) {
                    throw SecurityException()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                e.printStackTrace()
                throw SecurityException()
            }
        }
    }

    class Builder(
        private val device: File,
        private val baudrate: Int
    ) {
        private var dataBits = 8
        private var parity = 0
        private var stopBits = 1
        private var flags = 0
        private var repackingHelper: Repackable = CommonRepackingHelper()

        constructor(devicePath: String, baudrate: Int) : this(File(devicePath), baudrate)

        /**
         * 数据位
         *
         * @param dataBits 默认8,可选值为5~8
         * @return
         */
        fun dataBits(dataBits: Int): Builder = apply {
            this.dataBits = dataBits
        }

        /**
         * 校验位
         *
         * @param parity 0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
         * @return
         */
        fun parity(parity: Int): Builder = apply {
            this.parity = parity
        }

        /**
         * 停止位
         *
         * @param stopBits 默认1；1:1位停止位；2:2位停止位
         * @return
         */
        fun stopBits(stopBits: Int): Builder = apply {
            this.stopBits = stopBits
        }

        /**
         * 标志
         *
         * @param flags 默认0
         * @return
         */
        fun flags(flags: Int): Builder = apply {
            this.flags = flags
        }

        /**
         * 粘包处理类
         *
         * @param helper 默认常规处理类
         * @return
         */
        fun repacking(helper: Repackable): Builder = apply {
            this.repackingHelper = helper
        }

        /**
         * 打开并返回串口
         *
         * @return
         * @throws SecurityException
         * @throws IOException
         */
        @Throws(SecurityException::class, IOException::class)
        fun build(): SerialPort {
            return SerialPort(device, baudrate, dataBits, parity, stopBits, flags, repackingHelper)
        }
    }

    companion object {
        private const val TAG = "SerialPort"
        private const val DEFAULT_SU_PATH = "/system/bin/su"
        private var sSuPath = DEFAULT_SU_PATH
        var suPath: String?
            /**
             * Get the su binary path
             *
             * @return
             */
            get() = sSuPath
            /**
             * Set the su binary path, the default su binary path is [.DEFAULT_SU_PATH]
             *
             * @param suPath su binary path
             */
            set(suPath) {
                if (suPath == null) {
                    return
                }
                sSuPath = suPath
            }

        init {
            System.loadLibrary("serial_port")
        }

        fun newBuilder(device: File, baudrate: Int): Builder {
            return Builder(device, baudrate)
        }

        @JvmStatic
        fun newBuilder(devicePath: String, baudrate: Int): Builder {
            return Builder(devicePath, baudrate)
        }
    }
}