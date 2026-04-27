package com.lgremote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TVDevice(
    val ip: String,
    val friendlyName: String,
    var clientKey: String? = null
) : Parcelable
