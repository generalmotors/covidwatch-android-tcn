package org.covidwatch.android.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class BeaconReport(
    @Expose
    @SerializedName("beacon_id")
    val beaconId: String,
    @Expose
    @SerializedName("tcn_base64")
    val tcnBase64: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)