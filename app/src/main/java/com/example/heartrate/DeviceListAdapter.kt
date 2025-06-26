package com.example.heartrate

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class DeviceListAdapter(
    private val context: Context,
    private val devices: List<BluetoothDevice>,
    private val deviceInfoList: List<scan.Companion.ScanDeviceInfo>
) : BaseAdapter() {

    override fun getCount(): Int = devices.size

    override fun getItem(position: Int): Any = devices[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.device_list_item, parent, false)

        val nameText = view.findViewById<TextView>(R.id.device_name)
        val addrText = view.findViewById<TextView>(R.id.device_address)
        val rssiText = view.findViewById<TextView>(R.id.device_rssi)
        val intervalText = view.findViewById<TextView>(R.id.device_interval)

        val device = devices[position]
        val info = deviceInfoList.getOrNull(position)

        nameText.text = device.name ?: "Unknown"
        addrText.text = device.address
        rssiText.text = "${info?.currentRssi ?: "??"} dBm"
        intervalText.text = "${info?.intervalMs ?: "??"} ms"
     //   rssiText.text = "${info?.currentRssi} dBm ↔ ${info?.intervalMs} ms"


        return view
    }
}