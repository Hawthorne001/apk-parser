package net.dongliu.apk.parser.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object Inputs {
    @JvmStatic
    @Throws(IOException::class)
    fun readAll(inputStream: InputStream): ByteArray {
        val buf = ByteArray(1024 * 8)
        ByteArrayOutputStream().use { bos ->
            var len: Int
            while (inputStream.read(buf).also { len = it } != -1) {
                bos.write(buf, 0, len)
            }
            return bos.toByteArray()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readAllAndClose(inputStream: InputStream): ByteArray {
        inputStream.use { return readAll(inputStream) }
    }
}
