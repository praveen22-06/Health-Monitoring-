package com.example.heartrate

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class scan : AppCompatActivity() {

    private val REQUEST_CODE_LOCATION_PERMISSIONS = 101
    private val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 102
    private val REQUEST_CODE_PERMISSIONS = 103
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var deviceListView: ListView
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var deviceSpinner: Spinner
    private var selectedBluetoothDevice: BluetoothDevice? = null
    private lateinit var rotateAnimator: ObjectAnimator
    val deviceList = mutableListOf<BluetoothDevice>()
    val deviceInfoList = mutableListOf<ScanDeviceInfo>()
    private val SCAN_PERIOD: Long = 10000
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val device: BluetoothDevice? = null
    private var value: String? = null
    lateinit var stringList: List<String>
    lateinit var deviceAdapter: DeviceListAdapter

    private var readingSectionActivity: reading_section? = null

    private lateinit var intext: TextView
    companion object {
        var bluetoothGatt: BluetoothGatt? = null
        var count = 0
        val heartvalue: MutableList<Float> = mutableListOf()
      //  val hearttimestamps :MutableList<Float> = mutableListOf()
        val hearttimestamps = mutableListOf<String>()
        val spo2value: MutableList<Float> = mutableListOf()
        val bpmvalue: MutableList<Float> = mutableListOf()
        var currentItem = 0

        private val SERVICE_UUID: UUID = UUID.fromString("00001525-0000-1000-8000-00805F9B34FB")
        private val SERVICE_char: UUID = UUID.fromString("00001432-0000-1000-8000-00805F9B34FB")
        private val pulse_oximeter: UUID = UUID.fromString("00001822-0000-1000-8000-00805F9B34FB")
        private val pls_measure_char: UUID = UUID.fromString("00002a5f-0000-1000-8000-00805F9B34FB")
        private val NOTIFICATION_UUID_FF: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val NOTIFICATION_UUID_EE: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val messages = listOf(
            "Caring for your health daily",
            "Track your fitness every step",
            "Monitor heart rate effortlessly",
            "Stay connected to your wellness",
            "Empower your daily activity goals"
        )
        data class ScanDeviceInfo(
            val device: BluetoothDevice,
            var previousRssi: Int,
            var previousTimestampMs: Long,
            var currentRssi: Int,
            var currentTimestampMs: Long,
            var intervalMs: Long = 0
        )

        @SuppressLint("MissingPermission")
        fun enableNotification(serviceUUID: UUID, characteristicUUID: UUID) {
            val service = bluetoothGatt?.getService(serviceUUID)
            if (service == null) {
                Log.e("BluetoothGatt", "Service not found: $serviceUUID")
                return
            }

            val characteristic = service.getCharacteristic(characteristicUUID)
            if (characteristic == null) {
                Log.e("BluetoothGattCallback", "Characteristic not found: $characteristicUUID")
                return
            }

            bluetoothGatt?.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt?.writeDescriptor(descriptor)
                Log.d("BluetoothGatt", "Notifications enabled for $characteristicUUID")
            } else {
                Log.e("BluetoothGattCallback", "Descriptor not found for characteristic: $characteristicUUID")
            }
        }
    }
    fun ViewPager2.setSlowScrollSpeed(context: Context, durationMillis: Int) {
        val recyclerView = getChildAt(0) as? RecyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? RecyclerView.LayoutManager ?: return

        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val newScroller = object : LinearSmoothScroller(context) {
                    override fun calculateTimeForScrolling(dx: Int): Int {
                        return durationMillis // Set scroll duration
                    }
                }
                newScroller.targetPosition = position
                layoutManager.startSmoothScroll(newScroller)
            }
        })
    }
    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)


        val messagePager = findViewById<ViewPager2>(R.id.messagePager)
        messagePager.adapter = MessagePagerAdapter(messages)
        messagePager.setSlowScrollSpeed(this, 1000)

        messagePager.setPageTransformer { page, position ->
            val absPos = kotlin.math.abs(position)

            page.scaleY = 1f - 0.25f * absPos
            page.scaleX = 1f - 0.25f * absPos
            page.rotationY = position * -30f
            page.translationX = -position * page.width * 0.5f
            page.alpha = if (absPos > 1f) 0f else 1f - absPos * 0.5f

        }
// Auto-scroll every 2 seconds
        val handler = Handler(Looper.getMainLooper())
        val swipeRunnable = object : Runnable {
            override fun run() {
                val nextItem = (messagePager.currentItem + 1) % messages.size
                messagePager.setCurrentItem(nextItem, true)
                handler.postDelayed(this, 2500)
            }
        }

        handler.postDelayed(swipeRunnable, 2500)
        messagePager.setPageTransformer(null)

        deviceListView = findViewById(R.id.device_list)
        // Adapter for ListView (device names)
      //  deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        deviceAdapter = DeviceListAdapter(this, deviceList, deviceInfoList)
        deviceListView.adapter = deviceAdapter

        val ref = findViewById<ImageView>(R.id.refreshView)

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT)
                .show()
            finish() // Close the activity if Bluetooth is not supported
            return
        }
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        rotateAnimator = ObjectAnimator.ofFloat(ref, View.ROTATION, 0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        ref.setOnClickListener {

                        if (!scanning) {
                            if (bluetoothAdapter?.isEnabled == true) {
                                if (checkAndRequestPermissions()) {
                                    deviceListView.visibility = View.VISIBLE
                                    rotateAnimator.start()
                                    scanLeDevice()
                                }
                            } else {
                                Toast.makeText(this, "Turn on Bluetooth", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            selectedBluetoothDevice = deviceList[position]
            Toast.makeText(
                            this,
                            " ${selectedBluetoothDevice?.name ?: "Unknown"}",
                            Toast.LENGTH_SHORT
                        ).show()
            selectedBluetoothDevice?.let { device ->
                if (connectToDevice(device)) {
                    val intent = Intent(this@scan, reading_section::class.java)
                    intent.putExtra("deviceName", device.name) // optional
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Failed to connect to device", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice): Boolean {
        Toast.makeText(this, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()
        // Connect to the GATT server on the device
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        bluetoothGatt = device.connectGatt(this@scan, false, gattCallback)
        if (bluetoothGatt == null) {
            Toast.makeText(this, "Failed to connect to the device", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun stopScan() {
        if (scanning) {
            scanning = false
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            bluetoothLeScanner?.stopScan(scanCallback)
            rotateAnimator.cancel()
            Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            onDeviceFound(result.device, result.rssi, result.timestampNanos)

        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
                results.forEach { result ->
                    onDeviceFound(result.device, result.rssi, result.timestampNanos)
                }
            }


        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("MainActivity", "Scan failed with error: $errorCode")
            Toast.makeText(
                this@scan, "Scan failed with error: $errorCode", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun onDeviceFound(device: BluetoothDevice, rssi: Int, timestampNanos: Long) {
        runOnUiThread {
            val currentTimestampMs = timestampNanos / 1_000_000
            val index = deviceInfoList.indexOfFirst { it.device.address == device.address }
            val deviceName = device.name ?: "Unknown"

            if (index != -1 && index < deviceInfoList.size && index < deviceList.size) {
                // Existing device - update info
                val info = deviceInfoList[index]
                val intervalMs = (currentTimestampMs - info.currentTimestampMs).coerceAtLeast(0)

                info.previousRssi = info.currentRssi
                info.previousTimestampMs = info.currentTimestampMs
                info.currentRssi = rssi
                info.currentTimestampMs = currentTimestampMs
                info.intervalMs = intervalMs

                // Update device list (still pointing to same device object)
                deviceList[index] = device

            } else {
                // New device
                val newDeviceInfo = ScanDeviceInfo(
                    device = device,
                    previousRssi = rssi,
                    previousTimestampMs = currentTimestampMs,
                    currentRssi = rssi,
                    currentTimestampMs = currentTimestampMs,
                    intervalMs = 0 // first time seen
                )

                deviceInfoList.add(newDeviceInfo)
                deviceList.add(device)
            }

            // Notify adapter that list is updated
            deviceAdapter.notifyDataSetChanged()

            Log.d("Scan", "$deviceName (${device.address}) RSSI: $rssi dBm at $currentTimestampMs ms")
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread {
                        Toast.makeText(
                            this@scan,
                            "Connected to ${gatt.device.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                 //       val intent = Intent(this@scan, reading_section::class.java)
                  //      startActivity(intent)
                    }
                    bluetoothGatt = gatt
                    gatt.requestMtu(517)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    bluetoothGatt = null
                    runOnUiThread {
                        Toast.makeText(
                            this@scan,
                            "Disconnected from ${gatt.device.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("MTUCallback", "MTU successfully changed to: $mtu")

                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                gatt?.discoverServices() // Discover services after connection

            } else {
                Log.e("MTUCallback", "Failed to change MTU, status: $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BluetoothGattCallback", "Failed to discover services")
                return
            }

            if (ActivityCompat.checkSelfPermission(
                    this@scan,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            Log.d(
                "BluetoothGattCallback",
                "Discovered ${gatt.services.size} services on ${gatt.device.name}"
            )

            // FF Service
            val serviceFF = gatt.getService(SERVICE_UUID)
            val characteristicFF = serviceFF?.getCharacteristic(SERVICE_char)

            if (characteristicFF != null) {
                Log.d("BluetoothGattCallback", "FF Characteristic found.")
                gatt.readCharacteristic(characteristicFF)
              //  enableNotifications()
            } else {
                Log.e("BluetoothGattCallback", "FF Characteristic not found.")
            }

            val serviceEE = gatt.getService(pulse_oximeter)
            val characteristicEE = serviceEE?.getCharacteristic(pls_measure_char)

            if (characteristicEE != null) {
                Log.d("BluetoothGattCallback", "EE Characteristic found.")
                gatt.readCharacteristic(characteristicEE)
             //   tripNotifications()
            } else {
                Log.e("BluetoothGattCallback", "EE Characteristic not found.")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("onCharacteristicRead", "Read success: ${characteristic.uuid}")
                val byteArray = characteristic.value
//                Log.d("Received data", "Received on spo2 : " +
//                            "${byteArray.joinToString(", ") { String.format(
//                                    "%02X",
//                                    it
//                                )
//                            }
//                        }"
//                    )
                when (characteristic.uuid) {
                    pls_measure_char -> {
                        Log.d("Received data spo2", "Received Data spo2 Selection: " +
                            "${byteArray.joinToString(", ") { String.format(
                                    "%02X",
                                    it
                                )
                            }
                        }"
                    )
                        if (byteArray.size >= 5) {
                            val spo2 = byteArray[1].toInt() and 0xFF
                            val bpm = byteArray[3].toInt() and 0xFF

                            Log.d("readPulseData", "Initial Read - SpO₂: $spo2%, BPM: $bpm")

                            runOnUiThread {
                                Log.d("PulseData", String.format("Live SpO₂: %.1f%%, BPM: %.2f", spo2, bpm))
                            }
                        } else {
                            Log.w("readPulseData", "Invalid pulse oximeter data length: ${byteArray.size}")
                        }
                    }

                    SERVICE_char -> {
                        if (byteArray.size >= 2) {
                            val heartRate = byteArray[1].toInt() and 0xFF
                            Log.d("readHeartRate", "Initial HR Read: $heartRate bpm")

                            runOnUiThread {
                                Toast.makeText(this@scan, "Initial Heart Rate: $heartRate bpm", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w("readHeartRate", "Invalid heart rate data length: ${byteArray.size}")
                        }
                    }
                }

                if (count == 0) {
                    count++
                    runOnUiThread { callIntent() }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            characteristic?.let {
                val byteArray = it.value

                when (it.uuid) {
                    pls_measure_char -> {
                        Log.d("Received data spo2", "Received Data spo2 : " +
                                "${byteArray.joinToString(", ") { String.format(
                                    "%02X",
                                    it
                                )
                                }
                                }"
                        )
                        if (byteArray.size >= 5) {
                            val spo2Raw =  byteArray[1].toInt() and 0xFF
                            val bpmRaw =  byteArray[3].toInt() and 0xFF

                            val spo2 = spo2Raw.toFloat()
                            val bpm = bpmRaw.toFloat()

                            Log.d("PulseData", "Live SpO₂: %.1f%%, BPM: %.2f".format(spo2, bpm))
                            spo2value.add(spo2)
                            bpmvalue.add(bpm)

                            runOnUiThread {
                                reading_section.spo2TextView.text = " %.2f%%".format(spo2)
                                reading_section.bpmTextView.text = " %.2f".format(bpm)
                           //     Toast.makeText(this@scan, "SpO₂: %.2f%, BPM: %.2f".format(spo2, bpm), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w("PulseData", "Invalid value size: ${byteArray.size}")
                        }
                    }

                    SERVICE_char -> {
                        if (byteArray.size >= 2) {
                            Log.d("Received data hrtrate", "Received Data hrtrate Selection: " +
                                    "${byteArray.joinToString(", ") { String.format(
                                        "%02X",
                                        it
                                    )
                                    }
                                    }"
                            )
                            val raw = byteArray[1].toInt() and 0xFF
                            val heartLevel = raw.toFloat()
                            heartvalue.add(heartLevel)
                            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            hearttimestamps.add(timestamp)
                            Log.d("readHeartRate", "Live Heart Level: %.1f%%".format(heartLevel))

                            runOnUiThread {
                                reading_section.heartRateTextView.text = "%.1f%%".format(heartLevel)
                            //    readingSectionActivity?.updateHeartChart(heartLevel, timestamp)
                          //      reading_section.updateHeartChart()

                                Toast.makeText(this@scan, "Heart Level: %.1f%%".format(heartLevel), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.w("readHeartRate", "Invalid value size for notification: ${byteArray.size}")
                        }
                    }

                    else -> {
                        Log.w("BLE", "Unknown characteristic UUID: ${it.uuid}")
                    }
                }
            }

        }
    }
    private fun callIntent() {
        val intent = Intent(this@scan, reading_section::class.java)
        startActivity(intent)
    }
    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), REQUEST_CODE_PERMISSIONS
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_LOCATION_PERMISSIONS -> handleLocationPermissions(grantResults)
            REQUEST_CODE_BLUETOOTH_PERMISSIONS -> handleBluetoothPermissions(grantResults)
            REQUEST_CODE_PERMISSIONS -> handleBluetoothPermissions(grantResults)
            else -> Log.e("Permissions", "Unknown request code: $requestCode")
        }
    }

    private fun handleLocationPermissions(grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Log.i("Permissions", "All required location permissions granted")
            // Continue with BLE scanning or other location-dependent tasks
            scanLeDevice()
        } else {
            Log.e("Permissions", "Not all required location permissions granted")
            Toast.makeText(
                this,
                "Location permissions are required for Bluetooth scanning.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleBluetoothPermissions(grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i("Permissions", "Bluetooth permissions granted")
            // Permission granted, start MainActivity or continue with the Bluetooth task
            val intent = Intent(this, scan::class.java)
            startActivity(intent)
        } else {
            Log.e("Permissions", "Bluetooth permissions not granted")
            Toast.makeText(
                this, "Bluetooth permissions are required to start the app.", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun scanLeDevice() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            Log.e("scan", "Bluetooth LE Scanner is null")
            return
        }

        if (scanning) {
            Log.w("scan", "Scan already running. Stopping it first.")
            bluetoothLeScanner.stopScan(scanCallback)
            scanning = false
        }

        // Check for permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                1001
            )
            return
        }

      //  val filters: List<ScanFilter> = emptyList()

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(SERVICE_UUID)).build(),
            //            ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(SERVICE_UUID_EE)).build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner.startScan(filters, scanSettings, scanCallback)
        scanning = true

        Log.d("scan", "Started BLE scan.")
        Toast.makeText(this, "Scanning started", Toast.LENGTH_SHORT).show()

        // Stop after 10 seconds
        handler.postDelayed({
            bluetoothLeScanner.stopScan(scanCallback)
            scanning = false
            Log.d("scan", "Stopped BLE scan.")
        }, 20_000)
    }
}
