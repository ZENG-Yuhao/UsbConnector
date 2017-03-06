package com.zeng.example.usbconnector.UsbConnector;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


/**
 * This class helps you in: <br>
 * <ul  style="list-style-type:none">
 * <li> 1) Listening usb device's attach/detach events. </li>
 * <li> 2) Finding attached usb devices. </li>
 * <li> 3) Requesting permission for usb device found. </li>
 * <li> 4) Building connection and communication with the usb device. </li>
 * <li> 5) Handling disconnection and recycling of resources. </li>
 * </ul>
 * <p>
 * Created by ZENG Yuhao. <br>
 * Contact: enzo.zyh@gmail.com
 * </p>
 */

public class UsbConnector {
    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    // message codes
    public static final int MSG_NO_DEVICE_FOUND = 0;
    public static final int MSG_NO_MATCHED_DEVICE = 5;
    public static final int MSG_DEVICE_ATTACHED = 1;
    public static final int MSG_DEVICE_DETACHED = 2;
    public static final int MSG_PERMISSION_GRANTED = 3;
    public static final int MSG_PERMISSION_DENIED = 4;
    public static final int MSG_DEVICE_CONNECTED = 5;
    public static final int MSG_DEVICE_DISCONNECTED = 6;

    private boolean forceClaim = true;

    private Context mContext;
    private ConnectionListener mConnectionListener;
    private DeviceAdapter mDeviceAdapter;
    private ArrayList<UsbDevice> mIdentifiedDevices;

    private UsbManager mUsbManager;
    private UsbDevice mGrantedDevice;
    private UsbBroadCastReceiver mUsbBroadCastReceiver;

    private UsbInterface mUsbInterface;
    private UsbEndpoint mUsbEndPoint;
    private UsbDeviceConnection mUsbDeviceConnection;

    public UsbConnector(Context context) {
        this(context, null);
    }

    public UsbConnector(Context context, ConnectionListener listener) {
        if (context == null) throw new IllegalArgumentException("Context null.");
        mContext = context;
        setUsbConnectionListener(listener);
        setDeviceAdapter(new SimpleDeviceAdapter());

        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mUsbBroadCastReceiver = new UsbBroadCastReceiver();
        mContext.registerReceiver(mUsbBroadCastReceiver, filter);
    }

    public void setUsbConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            mConnectionListener = listener;
        } else {
            // add a empty listener in case "if (listener != null)" is required each time we call this listener;
            mConnectionListener = new ConnectionListener() {
                @Override
                public void onReceiveMessage(int msg) {
                    // do nothing
                }
            };
        }
    }

    public void setDeviceAdapter(DeviceAdapter adapter) {
        if (adapter != null)
            mDeviceAdapter = adapter;
    }

    public ArrayList<UsbDevice> getIdentifiedDevices() {
        return mIdentifiedDevices;
    }

    public ArrayList<UsbDevice> scan() {
        // clear old list
        mIdentifiedDevices = new ArrayList<>();
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        if (deviceList.size() == 0) {
            mConnectionListener.onReceiveMessage(MSG_NO_DEVICE_FOUND);
        } else {
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                if (mDeviceAdapter.identifyDevice(device))
                    mIdentifiedDevices.add(device);
            }
            if (mIdentifiedDevices.size() == 0)
                mConnectionListener.onReceiveMessage(MSG_NO_MATCHED_DEVICE);
        }
        return mIdentifiedDevices;
    }

    public void connect() {
        if (mIdentifiedDevices == null || mIdentifiedDevices.size() == 0)
            return;
        UsbDevice selectedDevice = mDeviceAdapter.selectDevice(mIdentifiedDevices);
        if (selectedDevice != null)
            askForPermission(selectedDevice);
    }

    private void askForPermission(UsbDevice device) {
        if (device == null) return;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mUsbManager.requestPermission(device, pendingIntent);
    }

    private void openCommunication() {
        if (mGrantedDevice != null) {
            mUsbInterface = mDeviceAdapter.getInterface(mGrantedDevice);
            mUsbEndPoint = mDeviceAdapter.getEndPoint(mUsbInterface);
            mUsbDeviceConnection = mUsbManager.openDevice(mGrantedDevice);
            boolean hasSucceeded = mUsbDeviceConnection.claimInterface(mUsbInterface, forceClaim);
            if (hasSucceeded)
                mConnectionListener.onReceiveMessage(MSG_DEVICE_CONNECTED);
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
     * USB Control Transfers Overview </a>
     */
    public int controlTransfer(int requestType, int request, int value, int index, byte[] buffer, int length, int
            timeout) {
        if (mUsbDeviceConnection == null) return -1;
        else
            return mUsbDeviceConnection.controlTransfer(requestType, request, value, index, buffer, length, timeout);
    }

    public int bulkTransfer(byte[] buffer, int length, int timeout) {
        if (mUsbDeviceConnection == null) return -1;
        else
            return mUsbDeviceConnection.bulkTransfer(mUsbEndPoint, buffer, length, timeout);
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
        mGrantedDevice = null;
        mConnectionListener.onReceiveMessage(MSG_DEVICE_DISCONNECTED);
    }

    /**
     * Call this method to release resources and unregister broadcast if this class finished its works. Otherwise,
     * there may be some issues.
     */
    public void destroy() {
        disconnect();
        mContext.unregisterReceiver(mUsbBroadCastReceiver);
        mContext = null;
        mUsbManager = null;
    }

    public boolean isDeviceConnected() {
        return (mGrantedDevice != null);
    }

    public boolean isCommunicationBuilt() {
        return (mUsbDeviceConnection != null);
    }

    /**
     * Custom BroadCastReceiver that listens events: device-attached, device-detached and request of
     * permission.
     */
    private class UsbBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                mConnectionListener.onReceiveMessage(MSG_DEVICE_ATTACHED);
                // when there is a device attached, scan all devices and update identified-device list.
                scan();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device != null && device == mGrantedDevice)
                    disconnect();
                scan(); // update list
                mConnectionListener.onReceiveMessage(MSG_DEVICE_DETACHED);
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                // This condition is satisfied only when a result is returned by the dialog asking user for the
                // permission, after you called requestPermission()/askForPermission.
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // mGrantedDevice is set only at here to make sure every instance of mGrantedDevice is
                            // permission-granted.
                            mGrantedDevice = device;
                            openCommunication(); // setup of interface and endpoint communication
                            mConnectionListener.onReceiveMessage(MSG_PERMISSION_GRANTED);
                        }
                    } else
                        mConnectionListener.onReceiveMessage(MSG_PERMISSION_DENIED);
                }
            }
        }
    }

    private class SimpleDeviceAdapter implements DeviceAdapter {
        @Override
        public boolean identifyDevice(UsbDevice device) {
            return true;
        }

        @Override
        public UsbDevice selectDevice(ArrayList<UsbDevice> identifiedDevices) {
            return identifiedDevices.get(0);
        }

        @Override
        public UsbInterface getInterface(UsbDevice selectedDevice) {
            return selectedDevice.getInterface(0);
        }

        @Override
        public UsbEndpoint getEndPoint(UsbInterface usbInterface) {
            return usbInterface.getEndpoint(0);
        }
    }

    public interface DeviceAdapter {
        boolean identifyDevice(UsbDevice device);

        UsbDevice selectDevice(ArrayList<UsbDevice> identifiedDevices);

        UsbInterface getInterface(UsbDevice selectedDevice);

        UsbEndpoint getEndPoint(UsbInterface usbInterface);
    }

    public interface ConnectionListener {
        void onReceiveMessage(int msg);
    }
}
