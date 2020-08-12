package org.tcncoalition.tcnclient.bluetooth

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser
import com.neovisionaries.bluetooth.ble.advertising.ADStructure
import com.neovisionaries.bluetooth.ble.advertising.IBeacon
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import org.tcncoalition.tcnclient.OkHttpRequest
import org.tcncoalition.tcnclient.TcnConstants
import java.io.IOException
import java.lang.Double.parseDouble
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.set

class TcnBluetoothManager(
    private val context: Context,
    private val scanner: BluetoothLeScanner,
    private val advertiser: BluetoothLeAdvertiser,
    private val tcnCallback: TcnBluetoothServiceCallback
) {

    private var bluetoothGattServer: BluetoothGattServer? = null
    private val TAG = ScanCallback::class.java.simpleName
    private var isStarted: Boolean = false
    private var generatedTcn = ByteArray(0)
    private var tcnAdvertisingQueue = ArrayList<ByteArray>()
    private var inRangeBleAddressToTcnMap = mutableMapOf<String, ByteArray>()
    private var estimatedDistanceToRemoteDeviceAddressMap = mutableMapOf<String, Double>()
    private var deviceModelCodeMap = mutableMapOf<String, Int>()
    private var beaconIDToTcnMap = mutableMapOf<String, ByteArray>()

    private var handler = Handler()
    private var advertiseNextTcnTimer: Timer? = null

    private var executor: ExecutorService? = null

    private var beaconAPIInvoked = false

    fun start() {

        if (isStarted) return
        isStarted = true

        startScan()
        // Create the local GATTServer and open it once.
        initBleGattServer(
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager),
            TcnConstants.UUID_SERVICE
        )
        executor = Executors.newFixedThreadPool(2)
        changeOwnTcn() // This starts advertising also
        runAdvertiseNextTcnTimer()
    }



    fun stop() {
        if (!isStarted) return
        isStarted = false

        stopScan()
        stopAdvertising()
        bluetoothGattServer?.clearServices()
        bluetoothGattServer?.close()
        bluetoothGattServer = null

        handler.removeCallbacksAndMessages(null)
        advertiseNextTcnTimer?.cancel()
        advertiseNextTcnTimer = null

        tcnAdvertisingQueue.clear()
        inRangeBleAddressToTcnMap.clear()
        estimatedDistanceToRemoteDeviceAddressMap.clear()
        deviceModelCodeMap.clear()

        executor?.shutdown()
        executor = null
    }

    private fun runAdvertiseNextTcnTimer() {
        advertiseNextTcnTimer?.cancel()
        advertiseNextTcnTimer = Timer()
        advertiseNextTcnTimer?.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    if (tcnAdvertisingQueue.isEmpty()) return
                    val firstTCN = tcnAdvertisingQueue.first()
                    tcnAdvertisingQueue.removeAt(0)
                    tcnAdvertisingQueue.add(firstTCN)
                    stopAdvertising()
                    startAdvertising()
                }
            },
            TimeUnit.SECONDS.toMillis(20),
            TimeUnit.SECONDS.toMillis(20)
        )
    }

    fun changeOwnTcn() {
        executor?.execute {
            Log.i(TAG, "Changing own TCN ...")
            // Remove current TCN from the advertising queue.
            dequeueFromAdvertising(generatedTcn)
            val tcn = tcnCallback.generateTcn()
            Log.i(TAG, "Did generate TCN=${Base64.encodeToString(tcn, Base64.NO_WRAP)}")
            generatedTcn = tcn
            // Enqueue new TCN to the head of the advertising queue so it will be advertised next.
            enqueueForAdvertising(tcn, true)
            // Force restart advertising with new TCN
            stopAdvertising()
            startAdvertising()
        }
    }

    private fun dequeueFromAdvertising(tcn: ByteArray?) {
        tcn ?: return
        tcnAdvertisingQueue.remove(tcn)
        Log.i(TAG, "Dequeued TCN=${Base64.encodeToString(tcn, Base64.NO_WRAP)} from advertising")
    }

    private fun enqueueForAdvertising(tcn: ByteArray?, atHead: Boolean = false) {
        tcn ?: return
        if (atHead) {
            tcnAdvertisingQueue.add(0, tcn)
        } else {
            tcnAdvertisingQueue.add(tcn)
        }
        Log.i(TAG, "Enqueued TCN=${Base64.encodeToString(tcn, Base64.NO_WRAP)} for advertising")
    }

    private fun startScan() {
        if (!isStarted) return
        // Use try catch to handle DeadObject exception
        try {
            // The use of scan filters aren't required while the app is in the foreground.
            // This changes when the app is in the background. If they are missing then the Bluetooth
            // framework won't give us scan results.

            val scanFilters = arrayOf(TcnConstants.UUID_SERVICE).map {
                ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
            }


            val scanSettings = ScanSettings.Builder().apply {
                // Low latency is important for older Android devices to be able to discover nearby
                // devices.

                setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                //setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)

                setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)

                //setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)

                // Report delay plays an important role in keeping track of the devices nearby:
                // If a batch scan result doesn't include devices from the previous result,
                // then we consider those devices out of range.
                // Important: Using a large duration value (greater than 60 sec) won't get us scan
                // results on old OSes
                setReportDelay(TimeUnit.SECONDS.toMillis(3))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                    setLegacy(true)
                }


            }.build()

            val beaconScanFilters: MutableList<ScanFilter> = java.util.ArrayList()
            beaconScanFilters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(TcnConstants.UUID_SERVICE)).build())
            beaconScanFilters.add(ScanFilterUtils.getScanFilter(TcnConstants.BEACON_UUID_SERVICE,64578,48807))

            //scanner.startScan(scanCallback)
            scanner.startScan(beaconScanFilters, scanSettings, scanCallback)
            //scanner.startScan(scanFilters, scanSettings, scanCallback)
            Log.i(TAG, "Started scan")
        } catch (exception: Exception) {
            Log.e(TAG, "Start scan failed: $exception")
            startScan()
        }

        // Bug workaround: Restart periodically so the Bluetooth daemon won't get into a broken
        // state on old Android devices.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isStarted) {
                Log.i(TAG, "Restarting scan...")
                // If there are outstanding scan results then flush them so we can process them now
                // in onBatchScanResults
                scanner.flushPendingScanResults(scanCallback)
                stopScan()
                startScan()
            }
        }, TimeUnit.SECONDS.toMillis(20))
    }

    private fun stopScan() {
        // Use try catch to handle DeadObject exception
        try {
            scanner.stopScan(scanCallback)
            Log.i(TAG, "Stopped scan")
        } catch (exception: Exception) {
            Log.e(TAG, "Stop scan failed: $exception")
        }
    }

    private var scanCallback = object : ScanCallback() {

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "onScanFailed errorCode=$errorCode")
            if (errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {
                startScan()
            }
        }



        @RequiresApi(Build.VERSION_CODES.O)
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)

            Log.d(TAG, "onBatchScanResults: ${results?.size}")

            // Search for a TCN in the service data of the advertisement
            results?.forEach for_each@{
                Log.d(TAG, "result=$it")

                val scanRecord = it.scanRecord ?: return@for_each

                val tcnServiceData = scanRecord.serviceData[
                        ParcelUuid(TcnConstants.UUID_SERVICE)]



                var hintIsAndroid : Boolean = true
                val estimatedDistanceFeet: Double
                //zero default for beacons
                var deviceModelCode : Int = 0
                var txPower : Int
                var isBeacon: Boolean = false
                var tcn: ByteArray?

                val beaconData = getBeaconData(it)
                if(beaconData != null) {
                    isBeacon = true
                    hintIsAndroid = false
                    deviceModelCode = when(beaconData.uuid) {
                        TcnConstants.BEACON_UUID_SERVICE.toString() -> 10
                        else -> 0
                    }
                    //txPower = beaconData.txPower!!
                    val beaconID = beaconData?.deviceName
                    // find the base64
                    if (tcnAdvertisingQueue?.size <= 0) {
                        return
                    }
                    tcn = beaconIDToTcnMap.get(beaconID)
                    if (tcn == null) {
                        val jsonStr = beaconID?.let { it1 -> getBeaconTCNID(it1) }
                    }


                    //val tfirst = Base64.encode(tcnAdvertisingQueue.first(),Base64.NO_WRAP)
                    //val tcnBase = Base64.decode("TyFNJGlhbRrNu+s9sTnEXA",Base64.NO_WRAP)
                    //check again if the tcn is retrieved in the thread call
                    tcn = beaconIDToTcnMap[beaconID]
                    if (tcn == null) {
                        return
                    }

                    txPower = beaconData.txPower!!

                    //tcn = (beaconData?.deviceName?.toByteArray((Charset.defaultCharset())))

                    val uuid = beaconData.uuid
                    Log.d(TAG, "onBatchScanResults Found beacon: UUID: $uuid DeviceName: ${scanRecord!!.deviceName} Rssi: ${it.rssi} Tx: ${it.txPower}  " + it.toString())

                } else {
                    try {
                        deviceModelCode = scanRecord.manufacturerSpecificData.keyAt(0)

                        if(deviceModelCode == TcnConstants.IOS_DEFAULT_MANUFACTURING_ID) {
                            var deviceName =  scanRecord.deviceName // get the phone model id from device name
                            if (deviceName != null ){
                                try {
                                    val num = parseDouble(deviceName)
                                    deviceModelCode = deviceName.toInt()
                                   // Log.d(TAG, "iPhone device Found!")
                                } catch (e: NumberFormatException) {
                                    return
                                }
                            } else {
                                return
                            }
                        }
                    } catch (e : Exception){
                        Log.e(TAG, "No manufactoring data found for device")
                    }

                    tcn = scanRecord.manufacturerSpecificData.valueAt(0)

                    if(tcn != null ) {
                        if (tcn.size < TcnConstants.TEMPORARY_CONTACT_NUMBER_LENGTH) return@for_each
                        tcn = tcn.sliceArray(0 until TcnConstants.TEMPORARY_CONTACT_NUMBER_LENGTH)
                    }

                    txPower = it.txPower
                }
                // Update estimated distance
                estimatedDistanceFeet = getEstimatedDistanceFeet(
                    it.rssi,
                    getMeasuredRSSIAtOneMeter(txPower, hintIsAndroid)
                )
                estimatedDistanceToRemoteDeviceAddressMap[it.device.address] =
                    estimatedDistanceFeet
                deviceModelCodeMap[it.device.address] = deviceModelCode

                if(!isBeacon && tcnServiceData != null) {
                    // iOS may still broadcast tcn in servicedata form. For Android this has been moved to manufactoringdata with device model.
                    tcnServiceData ?: return@for_each
                    if (tcnServiceData.size < TcnConstants.TEMPORARY_CONTACT_NUMBER_LENGTH) return@for_each
                    tcn =
                        tcnServiceData.sliceArray(0 until TcnConstants.TEMPORARY_CONTACT_NUMBER_LENGTH)

                }
                try {
                    Log.i(
                        TAG,
                        "onBatchScanResults Did find TCN=${Base64.encodeToString(
                            tcn,
                            Base64.URL_SAFE
                        )} from device=${it.device.address}\" at estimated distance=${estimatedDistanceToRemoteDeviceAddressMap[it.device.address]} with device model code: ${deviceModelCodeMap[it.device.address]}"
                    )
                }catch (e : Exception){
                    //tcn could be invalid, revisit
                    return
                }
                tcn?.let { it1 ->
                    deviceModelCodeMap[it.device.address]?.let { it2 ->
                        tcnCallback.onTcnFound(
                            it1,
                            estimatedDistanceToRemoteDeviceAddressMap[it.device.address], it2
                        )
                    }
                }
            }

            // Remove TCNs from our advertising queue that we received from devices which are now
            // out of range.
            var currentInRangeAddresses = results?.mapNotNull { it.device.address }
            if (currentInRangeAddresses == null) {
                currentInRangeAddresses = arrayListOf()
            }
            val addressesToRemove: MutableList<String> = mutableListOf()
            inRangeBleAddressToTcnMap.keys.forEach {
                if (!currentInRangeAddresses.contains(it)) {
                    addressesToRemove.add(it)
                }
            }
            addressesToRemove.forEach {
                val tcn = inRangeBleAddressToTcnMap[it]
                Log.i(TAG, "onBatchScanResults isRangeBLE found TCN for ${it} =${Base64.encodeToString(tcn, Base64.URL_SAFE)}")
                dequeueFromAdvertising(tcn)
                inRangeBleAddressToTcnMap.remove(it)
            }

            // Notify the API user that TCNs which are left in the list are still in range and
            // we have just found them again so it can track the duration of the contact.
            inRangeBleAddressToTcnMap.forEach {
                Log.i(
                    TAG,
                    "onBatchScanResults Did find TCN (end of isRangeBLE)=${Base64.encodeToString(
                        it.value,
                        Base64.URL_SAFE
                    )} from device=${it.key} at estimated distance=${estimatedDistanceToRemoteDeviceAddressMap[it.key]} with device model code: ${deviceModelCodeMap[it.key]}"
                )
                deviceModelCodeMap[it.key]?.let { it1 ->
                    tcnCallback.onTcnFound(it.value, estimatedDistanceToRemoteDeviceAddressMap[it.key],
                        it1
                    )
                }
            }
        }


        override fun onScanResult(callbackType: Int, scanResult: ScanResult?) {
            super.onScanResult(callbackType, scanResult)
            // Log.d(TAG, "onScanResults: ${scanResult?.device} ,${scanResult?.rssi}")

            Log.d(TAG, "onScanResults - In the range of beacon: ${scanResult?.scanRecord!!.deviceName} " + scanResult.toString())
            super.onScanResult(callbackType, scanResult)


            // get Scan Record byte array (Be warned, this can be null)
            if (scanResult.scanRecord != null) {
                var mfgData = scanResult.scanRecord?.manufacturerSpecificData?.keyAt(0)
                var tcnData = scanResult.scanRecord?.manufacturerSpecificData?.get(mfgData!!).toString()

                Log.d("MFG DATA", mfgData.toString()+", "+tcnData)

                /*
                if(scanResult.scanRecord!!.serviceUuids.contains(ParcelUuid(UUID.fromString("0000C019-0000-1000-8000-00805F9B34FB")))){
                    Log.d(TAG, "onScanResults - TCN Device Found: ${scanResult?.scanRecord!!.deviceName} " + scanResult.toString())
                }
                */

                val scanRecord: ByteArray? = scanResult.scanRecord?.bytes
                var startByte = 2
                var patternFound = false
                if (scanRecord != null) {
                    while (startByte <= 5) {
                        if (scanRecord[startByte + 2].toInt() and 0xff == 0x02 &&  // identifies an iBeacon
                            scanRecord[startByte + 3].toInt() and 0xff == 0x15
                        ) {
                            // identifies correct data length
                            patternFound = true
                            break
                        }
                        startByte++
                    }
                    if (patternFound) {
                        // get the UUID from the hex result
                        val uuidBytes = ByteArray(16)
                        System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16)
                        val uuid: UUID = ConversionUtils.bytesToUuid(uuidBytes)

                        // get the major from hex result
                        val major: Int = ConversionUtils.byteArrayToInteger(
                            Arrays.copyOfRange(
                                scanRecord,
                                startByte + 20,
                                startByte + 22
                            )
                        )

                        // get the minor from hex result
                        val minor: Int = ConversionUtils.byteArrayToInteger(
                            Arrays.copyOfRange(
                                scanRecord,
                                startByte + 22,
                                startByte + 24
                            )
                        )


                    }
                }
            }
        }
    }
/*
    fun getBeaconTCN() {
         val okHttpClient = OkHttpClient
        val apiUrl = ""
        val url = "$apiUrl/submitReport"
        val returnString = "";

        val request = Request.Builder()
            .url(url)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }
    fun GETRequest(url: String, callback: Callback): Call {
        val request = Request.Builder()
            .url(url)
            .build()

        val call = okHttpClient.newCall(request)
        call.enqueue(callback)
        return call
    }

 */

    fun getBeaconTCNID(beaconID: String) {

        var client = OkHttpClient()
        var request= OkHttpRequest(client)
        val url =  TcnConstants.API_URL + "beacon_report/" + beaconID

        request.GET(url, object: Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                try {
                    var json = JSONObject(responseData)
                    //println("SUCCESS - " + json)
                    // println("TCN " + json.get("tcn_base64"))

                    beaconAPIInvoked = true
                    var tcnBase64 = json.get("tcn_base64").toString()
                    val tcnBase = Base64.decode(tcnBase64,Base64.NO_WRAP)
                    beaconIDToTcnMap.put(beaconID,tcnBase)
                } catch (e: Exception) {
                    //ignore excpetion
                    //e.printStackTrace()

                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Failed to retrieve TCN for beacon with id $beaconID")
            }
        })
    }

    fun URL.getText(): String {
        return openConnection().run {
            this as HttpURLConnection
            inputStream.bufferedReader().readText()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getBeaconData(scanResult: ScanResult?): BeaconData? {
        // get Scan Record byte array (Be warned, this can be null)

        if (scanResult?.getScanRecord() != null) {

            val scanRecord: ByteArray? = scanResult.scanRecord?.bytes
            var startByte = 2
            var patternFound = false
            if (scanRecord != null) {
                while (startByte <= 5) {
                    if (scanRecord[startByte + 2].toInt() and 0xff == 0x02 &&  // identifies an iBeacon
                        scanRecord[startByte + 3].toInt() and 0xff == 0x15
                    ) {
                        // identifies correct data length
                        patternFound = true
                        break
                    }
                    startByte++
                }

                if (patternFound) {
                    // get the UUID from the hex result
                    val uuidBytes = ByteArray(16)
                    System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16)
                    val uuid: UUID = ConversionUtils.bytesToUuid(uuidBytes)
                    var beaconData = BeaconData()
                    Log.d(TAG, "onScanResults Found beacon: UUID: ${uuid} ${scanResult.device} DeviceName: ${scanResult.scanRecord!!.deviceName} Rssi: ${scanResult.rssi} Tx: ${scanResult.txPower}  " + scanResult.toString())
                    beaconData.uuid = uuid.toString()

                    beaconData.rssi = scanResult.rssi

                    val structures: List<ADStructure> =
                        ADPayloadParser.getInstance().parse(scanRecord)
                    // For each AD structure contained in the advertising packet.
                    for (structure in structures)
                    {
                        if (structure is IBeacon) {
                            // iBeacon was found.
                            val beacon = structure as IBeacon
                            // Proximity UUID, major number, minor number and power.
                            var major = beacon.major
                            var minor = beacon.minor
                            beaconData.txPower = beacon.power
                            beaconData.deviceName = "" + minor + major
                            Log.i(TAG,beacon.toString())

                        }
                    }
                    /*
                    beaconData.deviceName = scanResult.scanRecord!!.deviceName
                    if(beaconData.deviceName == null){
                        beaconData.deviceName = scanResult.device.address
                    }
                    */


                    // get the major from hex result
                    val major: Int = ConversionUtils.byteArrayToInteger(
                        Arrays.copyOfRange(
                            scanRecord,
                            startByte + 20,
                            startByte + 22
                        )
                    )

                    // get the minor from hex result
                    val minor: Int = ConversionUtils.byteArrayToInteger(
                        Arrays.copyOfRange(
                            scanRecord,
                            startByte + 22,
                            startByte + 24
                        )
                    )
                    return beaconData

                }
            }
        }
        return null
    }

    private fun startAdvertising() {
        if (!isStarted) return
        // Use try catch to handle DeadObject exception
        try {
//            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
//                context.registerReceiver(null, ifilter)
//            }
//
//            val batteryPct : Float? = batteryStatus?.let { intent ->
//                val level : Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
//                val scale : Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
//                level * 100 / scale.toFloat()
//            }
//
//            var jsonBody = "{'tcn': '${tcnAdvertisingQueue.first()+generatedTcn.sliceArray(0..3)}', " +
//                    "'device': '${Build.MANUFACTURER + ' ' + Build.MODEL}', " +
//                    "'battery_percentage': '${batteryPct?.toDouble()?.roundToInt()}'}"

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(TcnConstants.UUID_SERVICE))
                .addManufacturerData(TcnConstants.PHONE_MODELS[Build.MODEL].toString().toInt(), tcnAdvertisingQueue.first()+generatedTcn.sliceArray(0..3))
//                .addServiceData(
//                    ParcelUuid(TcnConstants.UUID_SERVICE),
                // Attach the first 4 bytes of our TCN to work around the problem of iOS
                // devices writing a new TCN to us whenever we rotate the TCN (every 20 sec).
                // iOS devices use the last 4 bytes to identify the Android devices and write
                // only once a TCN to them.
//                    tcnAdvertisingQueue.first()+generatedTcn.sliceArray(0..3)
//                )
                .build()

            advertiser.startAdvertising(settings, data, advertisingCallback)

            /*
            val ttByte = tcnAdvertisingQueue.first()
            val tData = Base64.encodeToString(
                ttByte,
                Base64.URL_SAFE)
            val dByte = Base64.decode(tData,Base64.URL_SAFE)

            Log.i(TAG,"TCN by Byte ${ttByte.toString(Charsets.UTF_8)} Decoded Byte: ${dByte.toString(Charsets.UTF_8)}")

             */
            Log.i(
                TAG, "Started advertising TCN=${Base64.encodeToString(
                    tcnAdvertisingQueue.first(),
                    Base64.NO_WRAP
                )} isOwn=${tcnAdvertisingQueue.first().contentEquals(generatedTcn)}"
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Start advertising failed: $exception")
            startAdvertising()
        }
    }

    private fun initBleGattServer(
        bluetoothManager: BluetoothManager,
        serviceUUID: UUID?
    ) {
        bluetoothGattServer = bluetoothManager.openGattServer(context,
            object : BluetoothGattServerCallback() {
                override fun onCharacteristicWriteRequest(
                    device: BluetoothDevice?,
                    requestId: Int,
                    characteristic: BluetoothGattCharacteristic?,
                    preparedWrite: Boolean,
                    responseNeeded: Boolean,
                    offset: Int,
                    value: ByteArray?
                ) {
                    var result = BluetoothGatt.GATT_SUCCESS
                    try {
                        if (characteristic?.uuid == TcnConstants.UUID_CHARACTERISTIC) {
                            if (offset != 0) {
                                result = BluetoothGatt.GATT_INVALID_OFFSET
                                return
                            }

                            if (value == null || value.size != TcnConstants.TEMPORARY_CONTACT_NUMBER_LENGTH) {
                                result = BluetoothGatt.GATT_FAILURE
                                return
                            }

                            Log.i(
                                TAG,
                                "Did find TCN=${Base64.encodeToString(
                                    value,
                                    Base64.NO_WRAP
                                )} from device=${device?.address} at estimated distance=${estimatedDistanceToRemoteDeviceAddressMap[device?.address]} with device model code: ${deviceModelCodeMap[device?.address]}"
                            )
                            deviceModelCodeMap[device?.address]?.let {
                                tcnCallback.onTcnFound(
                                    value,
                                    estimatedDistanceToRemoteDeviceAddressMap[device?.address],
                                    it
                                )
                            }
                            // TCNs received through characteristic writes come from iOS apps in the
                            // background.
                            // We act as a bridge and advertise these TCNs so iOS apps can discover
                            // each other while in the background.
                            if (device != null) {
                                inRangeBleAddressToTcnMap[device.address] = value

                                enqueueForAdvertising(value)
                            }
                        } else {
                            result = BluetoothGatt.GATT_FAILURE
                        }
                    } catch (exception: Exception) {
                        result = BluetoothGatt.GATT_FAILURE
                    } finally {
                        Log.i(
                            TAG,
                            "onCharacteristicWriteRequest result=$result device=$device requestId=$requestId characteristic=$characteristic preparedWrite=$preparedWrite responseNeeded=$responseNeeded offset=$offset value=$value"
                        )
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device,
                                requestId,
                                result,
                                offset,
                                null
                            )
                        }
                    }
                }
            })

        val service = BluetoothGattService(
            serviceUUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                TcnConstants.UUID_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )

        bluetoothGattServer?.clearServices()
        bluetoothGattServer?.addService(service)
    }

    private val advertisingCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.w(TAG, "onStartSuccess settingsInEffect=$settingsInEffect")
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "onStartFailure errorCode=$errorCode")
            super.onStartFailure(errorCode)
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser.stopAdvertising(advertisingCallback)
            Log.i(TAG, "Stopped advertising")
        } catch (exception: Exception) {
            Log.e(TAG, "Stop advertising failed: $exception")
        }
    }

    companion object {
        private const val TAG = "TcnBluetoothService"
    }
}
