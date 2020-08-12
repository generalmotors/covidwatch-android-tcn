package org.covidwatch.android.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class InteractionCalibration(
    @Expose
    @SerializedName("device_model")
    val deviceModel: Int,
    @Expose
    @SerializedName("contact_device_model")
    val contactDeviceModel: Int,
    @Expose
    @SerializedName("distance_detected")
    val distanceDetected: Double

) {
    @SerializedName("id")
    val id : Long = 0
    @SerializedName("created_at")
    val createdAt: String = ""
}