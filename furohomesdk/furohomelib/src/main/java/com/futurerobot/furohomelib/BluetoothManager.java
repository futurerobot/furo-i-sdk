package com.futurerobot.furohomelib;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;


/**
 * Created by smkim on 2014-07-25.
 */

public class BluetoothManager implements BluetoothProfile.ServiceListener {

    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "BluetoothManager";

    private BluetoothSocket  btDeviceSock;

    private BluetoothManager(){

    }

    /**
     *
     * @return
     */

    public final static BluetoothManager INSTANCE = new BluetoothManager();



    public List<String> getBtDeviceList(){
    	
    	List<String> deviceList = new ArrayList<String>();
    	
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    	Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
    	
    	for(BluetoothDevice bt : pairedDevices) {
    		deviceList.add(bt.getName());
    		deviceList.add(bt.getAddress());
    	}

    	return deviceList;
    }

//    // BluetoothBroadcastReceiver.Callback
//    @Override
//    public void onBluetoothError () {
//        Log.e(TAG, "There was an error enabling the Bluetooth Adapter.");
//    }
//
//    @Override
//    public void onBluetoothConnected () {
//        new BluetoothA2DPRequester(this).request(this, mAdapter);
//    }


    // CES 현장에서 추가된 코드 강제로 A2DP 접속이 체결이 되었는지 확인한다.
    private BluetoothDevice currentBt = null;
    public void checkConnection(Context context, BluetoothDevice bt) {
        currentBt = bt;
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.getProfileProxy(context, this, BluetoothProfile.A2DP);
    }

    // BluetoothProfile.ServiceListener
    @Override
    public void onServiceConnected(int i, BluetoothProfile bluetoothProfile) {
//        if (mCallback != null) {
//            mCallback.onA2DPProxyReceived((BluetoothA2dp) bluetoothProfile);
//        }
        onA2DPProxyReceived((BluetoothA2dp)bluetoothProfile);
    }

    @Override
    public void onServiceDisconnected(int i) {
        //It's a one-off connection attempt; we don't care about the disconnection event.
    }


    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getConnectMethod () {
        try {
            return BluetoothA2dp.class.getDeclaredMethod("connect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            Log.e(TAG, "Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    public void onA2DPProxyReceived (BluetoothA2dp proxy) {
        Method connect = getConnectMethod();

        //If either is null, just return. The errors have already been logged
        if (connect == null || currentBt == null) {
            return;
        }

        try {
            connect.setAccessible(true);
            connect.invoke(proxy, currentBt);
        } catch (InvocationTargetException ex) {
            Log.e(TAG, "Unable to invoke connect(BluetoothDevice) method on proxy. " + ex.toString());
        } catch (IllegalAccessException ex) {
            Log.e(TAG, "Illegal Access! " + ex.toString());
        } catch (Exception ex) {
            Log.e(TAG, "Illegal Access! " + ex.toString());
        }
        openSocketConnection();
    }

    void openSocketConnection() {
        try {
        if(btDeviceSock == null)
            btDeviceSock = currentBt.createRfcommSocketToServiceRecord(BT_UUID);
            btDeviceSock.connect();
        } catch( Exception e) {
            btDeviceSock = null;
            if(this.onResultHandler != null)
                this.onResultHandler.onResult("Bluetooth Connection Error." + e.toString());
            Log.d(TAG, "BT Connection Error." + e.toString() );
            // 여기서 리턴을 하지 않으면 에러가 난 경우 onResult 가 2번 호출이 됩니다.
            return;
        }
        if(this.onResultHandler != null)
            this.onResultHandler.onResult(null);

        Log.d(TAG, "BT Connected." + currentBt.getName() );
    }

    public interface onBluetoothConnectResult {
        public void onResult(String errorString);
    }

    private onBluetoothConnectResult onResultHandler = null;
    /**
     *
     * @param deviceID
     * @param deviceID
     * @return Connection ID
     */
    public void openConnection(Context context, String deviceID, onBluetoothConnectResult result) {

        this.onResultHandler = result;
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        for(BluetoothDevice bt : pairedDevices) {
            if( bt.getAddress().equals( deviceID ) ) {
                try {
//                    Boolean proxy = mBluetoothAdapter.getProfileProxy(AndroidHelper.INSTANCE.appContext, null, BluetoothProfile.A2DP);
//                    connect.invoke(proxy, result);
                    checkConnection(context, bt);

//                    if(btDeviceSock == null)
//                        btDeviceSock = bt.createRfcommSocketToServiceRecord(BT_UUID);
//                    btDeviceSock.connect();

//                    Log.d(TAG, "BT Connected." + bt.getName() );

                } catch( Exception e) {
                    btDeviceSock = null;
                    Log.d(TAG, "BT Connection Error." + e.toString() );
                    if(this.onResultHandler != null)
                        this.onResultHandler.onResult("BT Connection Error." + e.toString());
                }
                break;
            }
        }
    }


    public void closeConnection() {

        try {
            if(btDeviceSock != null){
                btDeviceSock.close();
                Log.d(TAG, "BT closed");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        btDeviceSock = null;
    }


    /**
     *
     * @return
     */
    public boolean isConnected() {
        if( btDeviceSock != null ) {

            if( Build.VERSION_CODES.ICE_CREAM_SANDWICH <= Build.VERSION.SDK_INT ) {
                return isConnected2();
            } else {
                return true;
            }
        }
        return false;
        // return true;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH )
    public boolean isConnected2() {
        return btDeviceSock.isConnected();
    }

    public String getRobotID(byte[] cmdPacket) {
        return null;
    }

    public String getRobotSpec(byte[] cmdPacket) {
        return null;
    }


    /**
     *
     * @param cmdPacket
     * @return
     */
    public boolean sendCommand(byte[] cmdPacket) {

        if( cmdPacket == null || cmdPacket.length == 0 ) {
            return false ;
        }

        if(cmdPacket.length != 9) {
            Log.e("Error", "Packet size error");
        }

        if( btDeviceSock != null ) {
            try {
                OutputStream os = btDeviceSock.getOutputStream();

                os.write(cmdPacket);
                os.write(makeCRC(cmdPacket));
                os.flush();
                os.flush();
                os.flush();

                //Log.d(TAG, "BT Send Complete." );

                return true;
            } catch( Exception e ) {
                Log.e("", e.getMessage() );
            }
        }

        return false;
        //return true;
    }

    /**
     *
     * @param cmdPacket
     * @return
     */
    public float getValue(byte[] cmdPacket) {
        /**
         * Futurerobot
         *
         */
        return 0.0f;
    }

    /**
     *
     * @param data
     * @return
     */
    public byte makeCRC( byte[] data ) {
        byte val = (byte)0x00;
        for( int i = 2 ; i < data.length; i++ ) {
            val += data[i];
        }
        return (byte)~val;
    }


    // 이거 사용 안하는 것 같습니다.
    /**
     *
     * @param data
     * @return
     */
    public boolean checkCRC( byte[] data ) {
        boolean isMatch = false;

        byte val = (byte)0x00;
        for( int i = 2 ; i < data.length; i++ ) {
            val += data[i];
        }

        if( data[data.length-1] ==  ~val ) {
            isMatch = true;
        }

        return isMatch;
    }


    /**
     *
     * @return
     * @throws Exception
     */

    byte[] buff = new byte[1024];

    public byte[] getByteData() throws Exception {

        byte[] data = null;

        Arrays.fill(buff, (byte) 0);

        if( btDeviceSock != null ) {

            int size=0, size2=0;

            try {
                InputStream is = btDeviceSock.getInputStream();

                size = is.read( buff, 0, buff.length );



//                if(size == 0 || buff == null){
//                    Log.e(TAG, "Nothing is received!!");
//                    Log.e(TAG, "BT socket connected:" + btDeviceSock.isConnected());
//                }

//                Log.i(TAG, "Received:" + (newSize) + ", " + buff[0] + " " + buff[1]  + " " + buff[2]);
                data = new byte[size];
                System.arraycopy( buff, 0, data, 0, size);
            } catch( Exception e ) {
                Log.e(TAG, e.getMessage() );
                data = null;
                throw e;
            }
        }

        return data;
    }


}

