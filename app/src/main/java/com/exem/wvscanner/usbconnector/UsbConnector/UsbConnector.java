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
    private static final int MSG_DEVICE_DETACHED = 2;
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
            // Here is a particular use-case of interface and endpoint, perhaps you should have more logic to find
            // the interface and endpoint correctly.
            mUsbInterface = mUsbDeviceGranted.getInterface(0);
            mUsbEndPoint = mUsbInterface.getEndpoint(0);
            mUsbDeviceConnection = mUsbManager.openDevice(mUsbDeviceGranted);
            mUsbDeviceConnection.claimInterface(mUsbInterface, forceClaim);
        }
    }

    /**
     * This method provides the same function as
     * {@link UsbDeviceConnection#controlTransfer(int, int, int, int, byte[], int, int)}, but it prevents instance of
     * {@link UsbDeviceConnection} from being revealed to outside of this class.
     * <p/>
     * Tips: <br>
     * You can use simply <b>0x40 (0b0100 0000)</b> as requestType for sending data and <b>0xC0 (0b1100 0000)</b> for
     * receiving data. <br>
     * <br>
     * requestType: <br>
     * Bit 7: Request direction (0=Host to device - Out, 1=Device to host - In). <br>
     * Bits 5-6: Request type (0=standard, 1=class, 2=vendor, 3=reserved). <br>
     * Bits 0-4: Recipient (0=device, 1=interface, 2=endpoint,3=other). <br>
     *
     * @return length of data
     * @see <a herf="http://www.jungo.com/st/support/documentation/windriver/811/wdusb_man_mhtml/node55.html">
     *     USB Control Transfers Overview </a>
     */
    public int controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int
            timeout) {
        if (mUsbDeviceConnection == null) return -1;
        else
            return mUsbDeviceConnection.controlTransfer(requestType, request, value, index, buffer, length, timeout);
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

    public boolean isDeviceConnected() {
        return (mUsbDeviceGranted != null);
    }

    public boolean isCommunicationBuilt() {
        return (mUsbDeviceConnection != null);
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
                mUsbConnectionListener.onReceiveMessage(MSG_DEVICE_DETACHED);
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
