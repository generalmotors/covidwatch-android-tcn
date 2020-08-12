package org.covidwatch.android.service

import org.covidwatch.android.GlobalConstants
import org.covidwatch.android.model.BeaconReport
import org.covidwatch.android.model.ContactRegistration
import org.covidwatch.android.model.InteractionCalibration
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.ResponseBody
import org.tcncoalition.tcnclient.TcnConstants
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*


interface ContactTracerService {
    companion object {

        private const val BASE_URL = TcnConstants.API_URL
        private const val API_KEY = "ct_key: ${GlobalConstants.API_CT_KEY}"
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }
    @Headers(API_KEY)
    @POST("interaction_calibration")
    fun postCalibrationInteraction(@Body interactionCalibration: InteractionCalibration): Completable
    @Headers(API_KEY)
    @GET("beacon_report/{beaconId}")
    fun getBeaconReport(@Path("beaconId") beaconId: String): Single<BeaconReport?>?
    @Headers(API_KEY)
    @POST("contact_registration")
    fun postContactInformation(@Body contactRegistration: ContactRegistration) : Completable
    @Headers(API_KEY)
    @GET("phone_profile/{deviceId}")
    fun getPhoneDistanceProfile(@Path("deviceId") deviceId: Int) : Single<ResponseBody>
    @Headers(API_KEY)
    @GET("phone_models")
    fun getPhoneModels() : Single<ResponseBody>
}