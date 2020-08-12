package org.covidwatch.android.service

import org.covidwatch.android.model.BeaconReport
import org.covidwatch.android.model.ContactRegistration
import org.covidwatch.android.model.InteractionCalibration
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit


class ContactTracerStreams {
    private val contactTracerService = ContactTracerService.retrofit.create(
        ContactTracerService::class.java
    )
    fun getBeaconReport(beaconId: String): Single<BeaconReport?>? {
        return contactTracerService.getBeaconReport(beaconId)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.timeout(20, TimeUnit.SECONDS)
    }

    fun postCalibrationInteraction(interactionCalibration: InteractionCalibration): Completable? {
        return contactTracerService.postCalibrationInteraction(interactionCalibration)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.timeout(20, TimeUnit.SECONDS)
    }

    fun postContactInformation(contactRegistration: ContactRegistration): Completable? {
        return contactTracerService.postContactInformation(contactRegistration)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.timeout(20, TimeUnit.SECONDS)
    }
    fun getPhoneDistanceProfile(deviceId: Int): Single<ResponseBody?>? {
        return contactTracerService.getPhoneDistanceProfile(deviceId)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.timeout(20, TimeUnit.SECONDS)
    }
    fun getPhoneModels() : Single<ResponseBody?>? {
        return contactTracerService.getPhoneModels()
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.timeout(20, TimeUnit.SECONDS)
    }
}
