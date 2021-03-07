package com.aa.bluetoothconnector

import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


//android.app.ServiceConnectionLeaked: Activity com.aa.bluetoothconnector.MainActivity has leaked ServiceConnection android.bluetooth.BluetoothA2dp$2@2f57d07 that was originally bound here
//at android.app.LoadedApk$ServiceDispatcher.<init>(LoadedApk.java:1610)

val TAG = "BluetoothConnector"

/**
 * Loads [MainFragment].
 */
class MainActivity : Activity(),  BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {

    val LEVEL_U = "Samsung Level U"

    var mAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Already connected, skip the rest
        if (mAdapter.isEnabled()) {
            onBluetoothConnected();
            return;
        }

        //Check if we're allowed to enable Bluetooth. If so, listen for a
        //successful enabling
        if (mAdapter.enable()) {
            BluetoothBroadcastReceiver.register(this, this);
        } else {
            Log.e(TAG, "Unable to enable Bluetooth. Is Airplane Mode enabled?");
        }

    }

    override fun onBluetoothError() {
        Log.e(TAG, "There was an error enabling the Bluetooth Adapter.")
    }

    override fun onBluetoothConnected() {
        BluetoothA2DPRequester(this).request(this, mAdapter)
    }

    override fun onA2DPProxyReceived(proxy: BluetoothA2dp?) {
        val connect: Method? = getConnectMethod()
        val device: BluetoothDevice? = findBondedDeviceByName(mAdapter, LEVEL_U)

        //If either is null, just return. The errors have already been logged
        if (connect == null || device == null) {
            return
        }
        try {
            connect.setAccessible(true)
            connect.invoke(proxy, device)
            Log.i(TAG, "Connected!!!")
            mAdapter.closeProfileProxy( BluetoothProfile.A2DP, proxy)
        } catch (ex: InvocationTargetException) {
            Log.e(
                TAG,
                "Unable to invoke connect(BluetoothDevice) method on proxy. " + ex.toString()
            )
        } catch (ex: IllegalAccessException) {
            Log.e(TAG, "Illegal Access! $ex")
        }


        finish()
    }

    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private fun getConnectMethod(): Method? {
        return try {
            BluetoothA2dp::class.java.getDeclaredMethod("connect", BluetoothDevice::class.java)
        } catch (ex: NoSuchMethodException) {
            Log.e(TAG, "Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.")
            null
        }
    }

    /**
     * Search the set of bonded devices in the BluetoothAdapter for one that matches
     * the given name
     * @param adapter the BluetoothAdapter whose bonded devices should be queried
     * @param name the name of the device to search for
     * @return the BluetoothDevice by the given name (if found); null if it was not found
     */
    private fun findBondedDeviceByName(adapter: BluetoothAdapter, name: String): BluetoothDevice? {
        for (device in getBondedDevices(adapter)!!) {
            if (name == device!!.name) {
                Log.v(
                    TAG,
                    String.format(
                        "Found device with name %s and address %s.",
                        device.name,
                        device.address
                    )
                )
                return device
            }
        }
        Log.w(TAG, String.format("Unable to find device with name %s.", name))
        return null
    }

    /**
     * Safety wrapper around BluetoothAdapter#getBondedDevices() that is guaranteed
     * to return a non-null result
     * @param adapter the BluetoothAdapter whose bonded devices should be obtained
     * @return the set of all bonded devices to the adapter; an empty set if there was an error
     */
    private fun getBondedDevices(adapter: BluetoothAdapter): Set<BluetoothDevice?>? {
        var results = adapter.bondedDevices
        if (results == null) {
            results = HashSet()
        }
        return results
    }
}