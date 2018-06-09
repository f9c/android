package com.github.f9c.android.util

import android.util.Base64
import java.security.KeyPair
import java.security.PublicKey

object RsaKeyToStringConverter {
    public fun encodePublicKey(publicKey: PublicKey) =
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
}