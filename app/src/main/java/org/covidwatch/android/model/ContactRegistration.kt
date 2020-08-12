package org.covidwatch.android.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ContactRegistration(
    @Expose
    @SerializedName("phone_number")
    val phoneNumber : String,
    @Expose
    @SerializedName("registration_time")
    val registrationTime: String,
    @Expose
    @SerializedName("is_primary")
    val isPrimary: Boolean
)