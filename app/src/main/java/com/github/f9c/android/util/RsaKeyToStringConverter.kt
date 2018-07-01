package com.github.f9c.android.util


import java.security.PublicKey

object RsaKeyToStringConverter {
    fun encodePublicKey(publicKey: PublicKey) = Base64.encode(publicKey.encoded)
}