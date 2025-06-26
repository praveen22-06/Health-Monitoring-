package com.example.heartrate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID



class reading_section : AppCompatActivity() {

    private lateinit var measureNowButton: Button
    private lateinit var dateTextView: TextView
    private lateinit var dayTextView: TextView
    private lateinit var lastMeasuredTextView: TextView
    private var isMeasuring = false

    private lateinit var spo2Chart: LineChart
    private lateinit var bpmChart: LineChart
    private lateinit var heartrate: LineChart

    private val spo2Entries = ArrayList<KeyStore.Entry>()
    private val bpmEntries = ArrayList<KeyStore.Entry>()
    private var dataIndex = 0f
    private val heartvalues1 = mutableListOf<Float>()
    private val timestamps1 = mutableListOf<String>()

    @SuppressLint("MissingInflatedId", "ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reading_section)
//        scan.tripNotifications()
//        scan.enableNotifications()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }
        val heartdata = scan.heartvalue
        val timestampsData = scan.hearttimestamps

        val floatValues1 = heartdata.mapNotNull { it.toString().toFloatOrNull() }

//        spo2Chart = findViewById(R.id.spo2Chart)
//        bpmChart = findViewById(R.id.bpmChart)
        heartrate = findViewById(R.id.heartRateChart)

//        spo2Chart.data = LineData()
//        bpmChart.data = LineData()
        heartrate.data = LineData()
//        spo2Chart.description.isEnabled = false
//        bpmChart.description.isEnabled = false
        heartrate.description.isEnabled = false

        spo2TextView = findViewById(R.id.spo2Text)
        bpmTextView = findViewById(R.id.bpmTextview)
        heartRateTextView = findViewById(R.id.heartRateTextView)
        dateTextView = findViewById(R.id.dateTextview)
        dayTextView = findViewById(R.id.dayTextview)
        val measureNow = findViewById<Button>(R.id.measureNowButton)
         lastMeasuredTextView = findViewById<TextView>(R.id.lastMeasuredTextView)

        heartvalues1.clear()
        heartvalues1.addAll(heartdata)
        timestamps1.clear()
        timestamps1.addAll(timestampsData)

        showHeartRate()

        updateHeartChart()

        // updateHeartChart(value, timestamp)

        val bounceAnim = AnimationUtils.loadAnimation(this, R.drawable.bounce)

        measureNow.setOnClickListener {
            it.startAnimation(bounceAnim)
       //     showHeartRate()
        //    updateCharts(heartvalues1,timestamps1)
            updateDateTime()

            runOnUiThread {
                scan.enableNotification(SERVICE_UUID, SERVICE_char)
                Handler(Looper.getMainLooper()).postDelayed({
                    scan.enableNotification(pulse_oximeter, pls_measure_char)
                }, 1000)
            }
        }

    }
    private fun updateDateTime() {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())   // e.g. "Jun 6"
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())     // e.g. "Monday"
        val formatter = SimpleDateFormat(" hh:mm a", Locale.getDefault())

        val currentTime = System.currentTimeMillis()
        val formattedTime = formatter.format(Date(currentTime))
        dateTextView.text = dateFormat.format(now.time)
        dayTextView.text = dayFormat.format(now.time)

        lastMeasuredTextView.text = "Last Measured: $formattedTime"

    }

    private fun showspo2() {
        val floatValues = scan.spo2value.mapNotNull { it.toFloat() }

    }
    private fun showbpm() {
        scan.bpmvalue
    }
    private fun showHeartRate() {
        if (scan.heartvalue.isEmpty() || scan.hearttimestamps.isEmpty() || scan.heartvalue.size != scan.hearttimestamps.size) {

            Log.e("Graph Error", "Invalid heart rate data for graph")
            return
        }

        val entries = ArrayList<Entry>()
        for (i in heartvalues1.indices) {
            entries.add(Entry(i.toFloat(), heartvalues1[i]))
        }

        val dataSet = LineDataSet(entries, "Heart Rate (BPM)").apply {
            color = Color.RED
            valueTextColor = Color.BLACK
            lineWidth = 2f
            circleRadius = 4f
            setCircleColor(Color.RED)
            mode = LineDataSet.Mode.LINEAR
            valueTextSize = 10f
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(this@reading_section, R.drawable.rounded_button)
        }

        val lineData = LineData(dataSet)

        heartrate.apply {
            data = lineData
            description.isEnabled = false
            axisRight.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            setScaleEnabled(true)
            animateX(1000)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelRotationAngle = -45f
                valueFormatter = IndexAxisValueFormatter(timestamps1)
            }

            axisLeft.apply {
                axisMinimum = 40f
                axisMaximum = 140f
                setDrawGridLines(true)
            }

            legend.isEnabled = true
            invalidate()
        }

        Log.d("Graph", "Initial heart rate chart displayed with ${heartvalues1.size} points")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, 2000) // Run every 2 seconds
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        disconnect()
    }

    private fun disconnect() {
        if (scan.bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            scan.bluetoothGatt?.disconnect()
            scan.bluetoothGatt?.close()
            scan.bluetoothGatt = null

            Toast.makeText(this, "Device disconnected.", Toast.LENGTH_SHORT).show()
            Log.d("edit_section", "Bluetooth device disconnected.")
        } else {
            Toast.makeText(this, "BluetoothLeService is not available.", Toast.LENGTH_SHORT).show()
            Log.e("edit_section", "Failed to disconnect: BluetoothLeService is null.")
        }

        val intent = Intent(this, scan::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
    fun updateHeartChart() {
        val heartRates = scan.heartvalue
        val timestamps = scan.hearttimestamps

        if (heartRates.isEmpty() || timestamps.isEmpty() || heartRates.size != timestamps.size) {
            Log.e("Chart Error", "Heart rate or timestamp list is empty or size mismatch.")
            return
        }

        val floatHeartRates = heartRates.mapNotNull { it.toString().toFloatOrNull() }
        if (floatHeartRates.size != timestamps.size) {
            Log.e("Chart Error", "Heart rates could not be parsed to float correctly.")
            return
        }

        val lineData = heartrate.data ?: LineData().also { heartrate.data = it }
        var dataSet = lineData.getDataSetByIndex(0) as? LineDataSet

        if (dataSet == null) {
            dataSet = LineDataSet(null, "Heart Rate (BPM)").apply {
                color = Color.RED
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(true)
                circleRadius = 4f
                setCircleColor(Color.RED)
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(true)
                fillDrawable = ContextCompat.getDrawable(this@reading_section, R.drawable.rounded_button)
            }
            lineData.addDataSet(dataSet)
        } else {
            dataSet.clear()
        }

        for (i in floatHeartRates.indices) {
            dataSet.addEntry(Entry(i.toFloat(), floatHeartRates[i]))
        }

        lineData.notifyDataChanged()

        heartrate.xAxis.valueFormatter = IndexAxisValueFormatter(timestamps)
        heartrate.notifyDataSetChanged()
        heartrate.setVisibleXRangeMaximum(30f)
        heartrate.moveViewToX(dataSet.entryCount.toFloat())
        heartrate.invalidate()

        Log.d("Chart Update", "Updated heart chart with ${floatHeartRates.size} entries")
    }

    companion object {
        lateinit var spo2TextView: TextView
        lateinit var bpmTextView: TextView
        lateinit var heartRateTextView: TextView

        private val SERVICE_UUID: UUID = UUID.fromString("00001525-0000-1000-8000-00805F9B34FB")
        private val SERVICE_char: UUID = UUID.fromString("00001432-0000-1000-8000-00805F9B34FB")
        private val pulse_oximeter: UUID = UUID.fromString("00001822-0000-1000-8000-00805F9B34FB")
        private val pls_measure_char: UUID = UUID.fromString("00002a5f-0000-1000-8000-00805F9B34FB")
    }
}