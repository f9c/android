package com.github.f9c.android.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

object ByteArrayHelper {

    fun toByteArray(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len = input.read(buffer)
        while (len  != -1) {
            out.write(buffer, 0, len)
            len = input.read(buffer)
        }
        return out.toByteArray()
    }
}