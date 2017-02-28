package com.exem.wvscanner.usbconnector.UsbConnector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;


/**
 * <p>
 * Created by ZENG Yuhao. <br>
 * Contact: enzo.zyh@gmail.com
 * </p>
 */

public class UsbConnector {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private UsbConnectionListener mUsbConnectionListener;

    public UsbConnector(Context context) {
        this(context, null);
    }

    public UsbConnector(Context context, UsbConnectionListener listener) {
        setUsbConnectionListener(listener);

    }

    public void setUsbConnectionListener(UsbConnectionListener listener) {
        if (listener != null) {
            mUsbConnectionListener = listener;
        }
    }

    public void connect() {

    }

    private class UsbBroadCastReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // todo
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // todo
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                // This condition is satisfied when a result is returned by the dialog asking user for the
                // permission, after you called requestPermission().
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            // todo
                        }
                    } else {
                        //Log.d(TAG, "permission denied for device " + device);
                        // todo
                    }
                }
            }
        }
    }

    public interface UsbConnectionListener {
    }
}
