package com.github.f9c.android.contact

import android.graphics.Bitmap

class Contact(val rowId: Int, val publicKey: String, val server: String, var alias: String, var statusText: String?,var profileIcon : Bitmap?) {

}
