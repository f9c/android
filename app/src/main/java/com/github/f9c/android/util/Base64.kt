package com.github.f9c.android.util

import android.util.Base64

object Base64 {
    fun decode(data : String) : ByteArray {
        return android.util.Base64.decode(data, Base64.NO_WRAP + Base64.URL_SAFE)
    }

    fun encode(data : ByteArray) : String {
        return android.util.Base64.encodeToString(data, Base64.NO_WRAP + Base64.URL_SAFE)
    }
}