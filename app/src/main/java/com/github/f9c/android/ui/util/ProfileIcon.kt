package com.github.f9c.android.ui.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.support.v4.graphics.drawable.RoundedBitmapDrawable
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory

object ProfileIcon {

    fun roundedIcon(profileIcon: Bitmap?): RoundedBitmapDrawable {
        val dr = RoundedBitmapDrawableFactory.create(Resources.getSystem(), profileIcon)
        dr.cornerRadius = 15F
        return dr
    }

}