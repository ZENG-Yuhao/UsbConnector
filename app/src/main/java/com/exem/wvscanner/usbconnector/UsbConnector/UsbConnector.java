package com.exem.wvscanner.usbconnector.UsbConnector;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.HashMap;
import java.util.Iterator;


/**
 * <p>
 * Created by ZENG Yuhao. <br>
 * Contact: enzo.zyh@gmail.com
 * </p>
 */

public class UsbConnector {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static final int MSG_NO_DEVICE_FOUND = 0;
    private static final int MSG_DEVICE_ATTACHED = 1;
    private static final int MSG_DEVICE_DETTACHED = 2;
    private static final int MSG_PERMISSION_GRANTED = 3;
    private static final int MSG_PERMISSION_DENIED = 4;

    private boolean forceClaim = true;

    private Context mContext;
    private UsbConnectionListener mUsbConnectionListener;
    private UsbManager mUsbManager;
    private UsbDevice mUsbDeviceGranted;
    private UsbBroadCastReceiver mUsbBroadCastReceiver;

    private UsbInterface mUsbInterface;
    private UsbEndpoint mUsbEndPoint;
    private UsbDeviceConnection mUsbDeviceConnection;

    public UsbConnector(Context context) {
        this(context, null);
    }

    public UsbConnector(Context context, UsbConnectionListener listener) {
        if (context == null) throw new IllegalArgumentException("Context null.");
        mContext = context;
        setUsbConnectionListener(listener);

        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mUsbBroadCastReceiver = new UsbBroadCastReceiver();
        mContext.registerReceiver(mUsbBroadCastReceiver, filter);
    }

    public void setUsbConnectionListener(UsbConnectionListener listener) {
        if (listener != null) {
            mUsbConnectionListener = listener;
        } else {
            // add a empty listener in case NULL pointer check is required each time we call this listener;
            mUsbConnectionListener = new UsbConnectionListener() {
                @Override
                public void onReceiveMessage(int msg) {
                    // do nothing
                }
            };
        }
    }

    public void connect() {
        if (mUsbDeviceGranted != null) return; // there is already a device connected.

        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        UsbDevice device = null;
        // In this case, pad has only one usb port and we assumed that the connected device is just right the one we
        // need.
        // For more general cases, you should add more conditions/filters (such like vendorId, deviceName etc.) to
        // check whether the device found is correct.
        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            if (device != null) break;
        }

        if (device == null)
            mUsbConnectionListener.onReceiveMessage(MSG_NO_DEVICE_FOUND);
        else {
            askForPermission(device);
        }
    }

    private void askForPermission(UsbDevice device) {
        if (device == null) return;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mUsbManager.requestPermission(device, pendingIntent);
    }

    private void openCommunication() {
        if (mUsbDeviceGranted != null) {
            //
            mUsbInterface = mUsbDeviceGranted.getInterface(0);
            mUsbEndPoint = mUsbInterface.getEndpoint(0);
            mUsbDeviceConnection = mUsbManager.openDevice(mUsbDeviceGranted);
            mUsbDeviceConnection.claimInterface(mUsbInterface, forceClaim);
        }
    }

    private void closeCommunication() {
        if (mUsbDeviceConnection != null) {
            if (mUsbInterface != null)
                mUsbDeviceConnection.releaseInterface(mUsbInterface);
            mUsbDeviceConnection.close();
        }
        mUsbInterface = null;
        mUsbEndPoint = null;
        mUsbDeviceConnection = null;
    }

    public void disconnect() {
        closeCommunication();
        mUsbDeviceGranted = null;
    }

    public void destroy() {
        disconnect();
        mContext.unregisterReceiver(mUsbBroadCastReceiver);
        mContext = null;
        mUsbManager = null;
    }

    private class UsbBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                mUsbConnectionListener.onReceiveMessage(MSG_DEVICE_ATTACHED);
                if (device != null)
                    askForPermission(device);
                else
                    connect(); // try to find a new available device.

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                disconnect();
                mUsbConnectionListener.onReceiveMessage(MSG_DEVICE_DETTACHED);
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                // This condition is satisfied only when a result is returned by the dialog asking user for the
                // permission, after you called requestPermission()/askForPermission.
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // mUsbDeviceGranted is set only at here to make sure every instance of mUsbDeviceGranted is
                            // permission-granted.
                            mUsbDeviceGranted = device;
                            openCommunication(); // setup of interface and endpoint communication
                            mUsbConnectionListener.onReceiveMessage(MSG_PERMISSION_GRANTED);
                        } else
                            connect(); // retry
                    } else {
                        mUsbConnectionListener.onReceiveMessage(MSG_PERMISSION_DENIED);
                    }
                }
            }
        }
    }

    public interface UsbConnectionListener {
        void onReceiveMessage(int msg);
    }
}
