package org.covidwatch.android

import android.app.Application
import org.covidwatch.android.data.signedreport.SignedReportsDownloader
import org.covidwatch.android.data.signedreport.firestore.SignedReportsUploader
import org.covidwatch.android.di.appModule
import org.covidwatch.android.util.NotificationUtils
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.tcncoalition.tcnclient.TcnClient


class CovidWatchApplication : Application() {

    private val tcnManager: CovidWatchTcnManager by inject()
    private val signedReportsUploader: SignedReportsUploader by inject()
    private val signedReportsDownloader: SignedReportsDownloader by inject()


//    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        NotificationUtils.init(applicationContext!!)


        startKoin {
            androidContext(applicationContext)
            modules(appModule)
        }


        TcnClient.init(tcnManager)
        signedReportsUploader.startUploading()
        signedReportsDownloader.schedulePeriodicPublicSignedReportsRefresh()

    }






}
