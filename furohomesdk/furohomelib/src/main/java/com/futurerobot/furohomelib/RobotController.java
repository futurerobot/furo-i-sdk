package com.futurerobot.furohomelib;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


/**
 * Created by jaekwon on 2014. 12. 24..
 */

public class RobotController {

    public interface IOnRobotEventListener {
        public void onDistanceSensorChanged(java.lang.String values);
        public void onTouchSensorChanged(int touchPatern);
        public void onRobotNameChanged(java.lang.String values);
        public void onFirmwareVersionChanged(java.lang.String values);
        public void onBuildDateChanged(java.lang.String values);
        public void onSerialNumberChanged(java.lang.String values);
        public void onRobotStatusChanged(java.lang.String values);
        public void onTemperatureChanged(java.lang.String values);
        public void onHumidityChanged(java.lang.String values);
        public void onBatteryPercentChanged(java.lang.String values);
        public void onChargingStatusChanged(java.lang.String values);
    }

    private static final String TAG = "RobotController";
    public IOnRobotEventListener mEventListener;
    private ReceiveDataFromRobotThread receiveDataFromRobotThread = null;
    private DataCheckThread dataCheckThread = null;

    private String currentDeviceID = "";
    private String currentDeviceName = "";
    private boolean isConnected = false;

    //
    private static final byte CMD_PROCESS_IDLE = 0;
    private static final byte CMD_PROCESS_SOF = 1;
    private static final byte CMD_PROCESS_MID = 2;
    private static final byte CMD_PROCESS_TYPE = 3;
    private static final byte CMD_PROCESS_SID = 4;
    private static final byte CMD_PROCESS_DATA = 5;
    private static final byte CMD_PROCESS_CRC = 6;

    private static final byte TYPE_ID = 0;
    private static final byte TYPE_FW_VERSION = 1;
    private static final byte TYPE_BUILD_DATE = 2;
    private static final byte TYPE_BUILD_NUMBER = 3;
    private static final byte TYPE_SERIAL_NUMBER = 4;
    private static final byte TYPE_ROBOT_STATUS = 5;
    private static final byte TYPE_MOTOR_VELOCITY_ACTUAL = 0x23;
    private static final byte TYPE_DISTANCE_SENSOR = 0x60;
    private static final byte TYPE_BOTTOM_SENSOR = 0x61;
    private static final byte TYPE_TOUCH_PATTERN = 0x62;
    private static final byte TYPE_TOUCH_IO = 0x63;
    private static final byte TYPE_TEMPERATURE = 0x64;
    private static final byte TYPE_HUMIDITY = 0x65;
    private static final byte TYPE_BATTERY_PERCENT = 0x66;
    private static final byte TYPE_CHARGING_STATUS = 0x67;
    private static final byte TYPE_ACC = 0x68;

    private static final int testVariable = 0x41c73333;

    private RobotController() {

    }

    // public void setEventListener(OnDriveEventListener listener){
    // mDriveEventListener = listener;
    // }

    public final static RobotController INSTANCE = new RobotController();


    public void setRobotEventListener(IOnRobotEventListener listener) {
        mEventListener = listener;
    }

    public void startReceiveDataFromRobotThread() {
        if(receiveDataFromRobotThread == null) {
            runReceiveDataFromRobotThread = true;
            receiveDataFromRobotThread =  new ReceiveDataFromRobotThread();
            receiveDataFromRobotThread.start();

            dataCheckThread = new DataCheckThread();
            dataCheckThread.start();
        }
    }

    public void stopReceiveDataFromRobotThread() {
        if(receiveDataFromRobotThread != null) {
            runReceiveDataFromRobotThread = false;
            receiveDataFromRobotThread = null;
            dataCheckThread = null;
        }
    }

    // 이 부분 나중에 같이 수정을 해야 함.
//    private ByteBuffer buffer = ByteBuffer.allocate(10);

    // -----------------------------------------------------------------------------
    // Receive data from Connection
    // -----------------------------------------------------------------------------
    // queue process
    short SIZE_OF_QUEUE = 256;
    short head = 0;
    short tail = 0;
    byte receivedByteData[] = new byte[256];
    byte cmdProcessState;

    private class _packetParseBuffer {
        public byte mid;
        public byte readWrite;
        public byte type;
        public byte sid;
        public byte data[] = new byte[4];
        public byte crc;
        public byte rcvSize;
    }

    _packetParseBuffer packetParseBuffer = new _packetParseBuffer();

    void putChar(byte rd) {
        receivedByteData[head] = rd;
        if( head < 255)
        {
            head++;
        }
        else
        {
            head = 0;
        }
        //head &= (SIZE_OF_QUEUE - 1);
    }

    byte getChar() {
        byte rd = 0;

        rd = receivedByteData[tail];
        //tail &= (SIZE_OF_QUEUE - 1);
        if( tail < 255)
        {
            tail++;
        }
        else
        {
            tail = 0;
        }
        return (byte) rd;
    }

    private long remainSize() {
        long rd = 0;

        if (head >= tail)
            rd =  (head - tail);
        else
            rd =  (256 - (tail - head));

        return  rd;
    }

    private void robotNameChanged() {
        int vendorId;
        int robotId;
        StringBuilder strPsdBuilder = new StringBuilder();

        vendorId = byte2ToInt(packetParseBuffer.data[0], packetParseBuffer.data[1]);
        robotId = byte2ToInt(packetParseBuffer.data[2], packetParseBuffer.data[3]);

        switch (vendorId) {
            case 0:
                strPsdBuilder.append("FUTURE ROBOT");
                break;

            default:
                strPsdBuilder.append("unknow vendor");
                break;
        }

        switch (robotId) {
            // 0x31 : furo i parrot
            // 0x32 : furo i watch
            // 0x33 : furo i home
            case 0x31:
                strPsdBuilder.append(" : FURO I Parrot");
                break;
            case 0x32:
                strPsdBuilder.append(" : FURO I Watch");
                break;
            case 0x33:
                strPsdBuilder.append(" : FURO I Home");
                break;

            default:
                strPsdBuilder.append(" : unknow device");
                break;
        }
        mEventListener.onRobotNameChanged(strPsdBuilder.toString());
    }

    private void firmwareVersionChanged() {

        StringBuilder strPsdBuilder = new StringBuilder();

        strPsdBuilder.append("Firmware Version : ")
                .append(packetParseBuffer.data[0]).append('.')
                .append(packetParseBuffer.data[1]).append('.')
                .append(packetParseBuffer.data[2]).append('.')
                .append(packetParseBuffer.data[3]);

        mEventListener.onFirmwareVersionChanged(strPsdBuilder.toString());
    }

    private void buildDateChanged() {
        int year;
        int month;

        StringBuilder strPsdBuilder = new StringBuilder();

        year = byte2ToInt(packetParseBuffer.data[0], packetParseBuffer.data[1]);
        month = byte2ToInt(packetParseBuffer.data[2], packetParseBuffer.data[3]);
        strPsdBuilder.append("Build Date : ").append(year).append('/').append(month);

        mEventListener.onBuildDateChanged(strPsdBuilder.toString());
    }

    private void SerialNumberChanged() {
        int serialNumber;

        StringBuilder strPsdBuilder = new StringBuilder();

        serialNumber = byte4ToInt(packetParseBuffer.data);
        strPsdBuilder.append("Serial Number : ").append(serialNumber);
        mEventListener.onSerialNumberChanged(strPsdBuilder.toString());
    }

    private void robotStatusChanged() {
        int robotStatus;

        StringBuilder strPsdBuilder = new StringBuilder();

        robotStatus = byte4ToInt(packetParseBuffer.data);
        strPsdBuilder.append("Robot Status : ");

        switch (robotStatus) {
            case 0:
                strPsdBuilder.append("power on");
                break;

            case 1:
                strPsdBuilder.append("ready");
                break;

            case 2:
                strPsdBuilder.append("bluetooth connect");
                break;

            case 0xf0:
                strPsdBuilder.append("power on");
                break;

            default:
                break;
        }

        mEventListener.onRobotStatusChanged(strPsdBuilder.toString());
    }

    private void distanceSensorChanged(byte sid) {
        int distance;

        StringBuilder strPsdBuilder = new StringBuilder();

        distance = byte4ToInt(packetParseBuffer.data);
        // 0 번 ~ 9번까지.. 시계 방향 0번이 왼쪽 바퀴
        // distance in cm(centi meter)
        strPsdBuilder.append(sid)
                .append(',')
                .append(distance);

        mEventListener.onDistanceSensorChanged(strPsdBuilder.toString());
    }

    private void temperatureChanged() {
        int temp;
        float temperature;

        StringBuilder strPsdBuilder = new StringBuilder();

        temp = byte4ToInt(packetParseBuffer.data);

        //temperature = Float.intBitsToFloat(temp);
        strPsdBuilder.append(temp);

        mEventListener.onTemperatureChanged(strPsdBuilder.toString());
    }

    private void humidityChanged() {
        int temp;
        float humidity;

        StringBuilder strPsdBuilder = new StringBuilder();

        temp = byte4ToInt(packetParseBuffer.data);

        //humidity = Float.intBitsToFloat(temp);
        strPsdBuilder.append(temp);
        mEventListener.onHumidityChanged(strPsdBuilder.toString());

    }

    private void batteryChanged() {
        int batteryPercent;

        StringBuilder strPsdBuilder = new StringBuilder();

        batteryPercent = byte4ToInt(packetParseBuffer.data);
        strPsdBuilder.append(batteryPercent).append("%");
        mEventListener.onBatteryPercentChanged(strPsdBuilder.toString());
    }

    private void chargingStatusChanged() {
        int chargingStatus;

        StringBuilder strPsdBuilder = new StringBuilder();

        chargingStatus = byte4ToInt(packetParseBuffer.data);
        switch (chargingStatus) {
            case 0:
                strPsdBuilder.append("No Charging");
                break;

            case 1:
                strPsdBuilder.append("Charging");
                break;

            default:
                break;
        }

        mEventListener.onChargingStatusChanged(strPsdBuilder.toString());
    }

    void readProcess() {
        byte sid;
        sid = packetParseBuffer.sid;
        switch (packetParseBuffer.type) {
            case TYPE_ID:
                if (sid > 0)
                    break;
                robotNameChanged();
                // sendReadResponse(&robot.robotInfo[sid]);
                break;

            case TYPE_FW_VERSION:
                if (sid > 0)
                    break;
                firmwareVersionChanged();
                // sendReadResponse(&robot.fwVersion[sid]);
                break;

            case TYPE_BUILD_DATE:
                if (sid > 0)
                    break;
                buildDateChanged();
                // sendReadResponse(&robot.buildDate[sid]);
                break;

            case TYPE_BUILD_NUMBER:
                if (sid > 0)
                    break;
                // sendReadResponse(&robot.buildNumber[sid]);
                break;

            case TYPE_SERIAL_NUMBER:
                if (sid > 0)
                    break;
                SerialNumberChanged();
                // sendReadResponse(&robot.serialNumber[sid]);
                break;

            case TYPE_ROBOT_STATUS:
                if (sid > 0)
                    break;
                robotStatusChanged();
                // sendReadResponse(&robot.robotStatus[sid]);
                break;

            case TYPE_DISTANCE_SENSOR:
                if (sid > 11)
                    break;
                distanceSensorChanged(sid);
                // sendReadResponse(&robot.babybear[sid]);
                break;

            case TYPE_BOTTOM_SENSOR:
                if (sid > 0)
                    break;

                // sendReadResponse(&robot.robotInfo[sid]);
                break;

            case TYPE_TOUCH_PATTERN:
                if (sid > 0)
                    break;
                // sendReadResponse(&robot.touchPattern[sid]);
                break;

            case TYPE_TOUCH_IO:
                if (sid > 0)
                    break;
                int touchPattern = 4;
                mEventListener.onTouchSensorChanged(touchPattern);
                // sendReadResponse(&robot.touchIO[sid]);
                break;

            case TYPE_TEMPERATURE:
                if (sid > 0)
                    break;
                temperatureChanged();
                // sendReadResponse(&robot.temperature[sid]);
                break;

            case TYPE_HUMIDITY:
                if (sid > 0)
                    break;
                humidityChanged();
                // sendReadResponse(&robot.touchPattern[sid]);
                break;

            case TYPE_BATTERY_PERCENT:
                if (sid > 0)
                    break;
                batteryChanged();
                // sendReadResponse(&robot.batteryPercent[sid]);
                break;

            case TYPE_CHARGING_STATUS:
                if (sid > 0)
                    break;
                chargingStatusChanged();
                // sendReadResponse(&robot.batteryPercent[sid]);
                break;

            default:
                break;

        }
    }

    void CMDPacketParse() // 패킷 분석
    {
        if (packetParseBuffer.readWrite == 0x00) {
            readProcess();
        }
    }


    private boolean runReceiveDataFromRobotThread;
    class ReceiveDataFromRobotThread extends Thread {

        byte receivedByteDataTemp[] = null;
        byte receivedByteDataLength;


        @Override
        public void run() {
            super.run();

            while (runReceiveDataFromRobotThread) {
                try {
                    BluetoothManager manager = BluetoothManager.INSTANCE;
                    if(BluetoothManager.INSTANCE == null)
                        continue;

                    receivedByteDataTemp = BluetoothManager.INSTANCE.getByteData();
                    if(receivedByteDataTemp == null)
                        continue;

                    receivedByteDataLength = (byte) receivedByteDataTemp.length;
                    while (receivedByteDataLength > 0) {
                        putChar(receivedByteDataTemp[receivedByteDataTemp.length - receivedByteDataLength]);
                        receivedByteDataLength--;
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }

            }
        }
    }



    class DataCheckThread extends Thread {

        @Override
        public void run() {
            super.run();

            while (runReceiveDataFromRobotThread) {
                try {
                    if (remainSize() > 0) {
                        processReceivedData();
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    Log.e(TAG, e.toString());
                }

            }
        }
    }

    private void processReceivedData() {

        byte sd = getChar();
        byte checkSum;

        switch (cmdProcessState) {
            case CMD_PROCESS_IDLE:
                if (sd == 0x55)
                    cmdProcessState = CMD_PROCESS_SOF;
                break;

            case CMD_PROCESS_SOF:
                if (sd == 0x54)
                    cmdProcessState = CMD_PROCESS_MID;
                else {
                    cmdProcessState = CMD_PROCESS_IDLE;
                }
                break;

            case CMD_PROCESS_MID:
                packetParseBuffer.mid = (byte) sd;
                cmdProcessState++;
                break;

            case CMD_PROCESS_TYPE:
                packetParseBuffer.readWrite = (byte) (sd & 0x80);
                packetParseBuffer.type = (byte) (sd & 0x7F);
                cmdProcessState++;
                if(packetParseBuffer.type  != 96 )
                {
                    packetParseBuffer.mid = 0x00;
                }
                break;

            case CMD_PROCESS_SID:
                packetParseBuffer.sid = (byte) sd;
                cmdProcessState++;
                break;

            case CMD_PROCESS_DATA:
                packetParseBuffer.data[packetParseBuffer.rcvSize++] = (byte) sd;
                if (packetParseBuffer.rcvSize > 3) {
                    cmdProcessState++;
                }
                break;

            case CMD_PROCESS_CRC:
                packetParseBuffer.crc = (byte) sd;
                checkSum = (byte) (packetParseBuffer.mid
                        + packetParseBuffer.readWrite + packetParseBuffer.type
                        + packetParseBuffer.sid + packetParseBuffer.data[0]
                        + packetParseBuffer.data[1] + packetParseBuffer.data[2] + packetParseBuffer.data[3]);
                checkSum = (byte) ~checkSum;
                if (packetParseBuffer.crc == checkSum) {
                    CMDPacketParse();
                }
                cmdProcessState = 0;
                packetParseBuffer.rcvSize = 0;
                break;

            default:
                break;
        }

    }

    // ------------------------------------------------------------------------------
    // APIs for Robot Drivers
    // ------------------------------------------------------------------------------
    private static String KEY_PAIRED_ROBOT_BLUETOOTH_ID = "PAIRED_BLUETOOTH_ID";
    private static String KEY_PAIRED_ROBOT_BLUETOOTH_NAME = "PAIRED_BLUETOOTH_NAME";

    public void setString(Context context, String strKey, String strValue) {
        SharedPreferences preferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(strKey, strValue);
        editor.commit();
    }

    public String getString(Context context, String strKey) {
        SharedPreferences preferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        String strValue = preferences.getString(strKey, "");
        return strValue;
    }

    public void setPairedRobot(Context context, String bluetoothID, String bluetoothName) {
       setString(context, KEY_PAIRED_ROBOT_BLUETOOTH_ID, bluetoothID);
       setString(context, KEY_PAIRED_ROBOT_BLUETOOTH_NAME, bluetoothName);
    }

    public String getPairedRobotBluetoothID(Context context) {
        return getString(context, KEY_PAIRED_ROBOT_BLUETOOTH_ID);
    }

    public String getPairedRobotBluetoothName(Context context) {
        return getString(context, KEY_PAIRED_ROBOT_BLUETOOTH_NAME);
    }



    public OperationMode currentOperationMode = OperationMode.STOP;

    public enum OperationMode {
        MOVE_PREDEFINED(0x01), MOVE(0x05), STOP(0x12), IDLE(0x11), ALARM(0x13), SECURITY(0x0a), LOVE(0x16), DANCE(0x17);
        private int value;

        OperationMode(int i) {
            value = i;
        }

        byte getByteValue() {
            return (byte)value;
        }
    }

    public void setOperationMode(OperationMode mode) {
//        if (this.currentOperationMode == OperationMode.MOVE && mode != OperationMode.MOVE) {
//            RobotController.INSTANCE.setOperationMode((byte)0x01);
//        }

        this.currentOperationMode = mode;
        setOperationMode((mode.getByteValue()));
    }

    private boolean setOperationMode(byte mode)
    {
        if(BluetoothManager.INSTANCE == null)
            return false;
        byte[] datas = new byte[9];

		/* Set left wheel target velocity */
        datas[0] = (byte) 0x55; // pre-amble HIGH
        datas[1] = (byte) 0x54; // pre-amble LOW
        datas[2] = (byte) 0x55; // main id
        datas[3] = (byte) 0xA1; // write 0x80
        // type 0x21 ( target velocity )
        datas[4] = (byte) 0x00; // sub id ( left wheel )

        // data
        datas[5] = (byte) (mode);
        datas[6] = (byte) (0x00);
        datas[7] = (byte) (0x00);
        datas[8] = (byte) (0x00);

        BluetoothManager.INSTANCE.sendCommand(datas);

        return true;
    }

    public boolean stop() {
        driveWheel(0, 0);
        return true;
    }

    private void readData(byte type, byte subId) {
        if(BluetoothManager.INSTANCE == null)
            return;
        byte[] datas = new byte[9];

		/* Set left wheel target velocity */
        datas[0] = (byte) 0x55; // pre-amble HIGH
        datas[1] = (byte) 0x54; // pre-amble LOW
            datas[2] = (byte) 0x55; // main id
        datas[3] = (byte) (0x00 + type); // read 0x00
        datas[4] = (byte) subId; // sub id ( left wheel )

        // data
        datas[5] = (byte) (0x00);
        datas[6] = (byte) (0x00);
        datas[7] = (byte) (0x00);
        datas[8] = (byte) (0x00);

        BluetoothManager.INSTANCE.sendCommand(datas);
    }

    // 2014.11.05
    public void requestRobotName() {
        readData(TYPE_ID, (byte) 0);
    }

    public void requestVersion() {
        readData(TYPE_FW_VERSION, (byte) 0);
    }

    public void requestBuildDate() {
        readData(TYPE_BUILD_DATE, (byte) 0);
    }

    public void requestSN() {
        readData(TYPE_SERIAL_NUMBER, (byte) 0);
    }

    public void requestRobotStatus() {
        readData(TYPE_ROBOT_STATUS, (byte) 0);
    }

    public void reqestDistance(byte subId) {
        readData(TYPE_DISTANCE_SENSOR, subId);
    }

    public void requestTouchIo() {
        readData(TYPE_TOUCH_IO, (byte) 0);
    }

    public void requestTemperature() {
        readData(TYPE_TEMPERATURE, (byte) 0);
    }

    public void requestHumidity() {
        readData(TYPE_HUMIDITY, (byte) 0);
    }

    public void requestBatteryPercent() {
        readData(TYPE_BATTERY_PERCENT, (byte) 0);
    }

    public void requestChargingStatus() {
        readData(TYPE_CHARGING_STATUS, (byte) 0);
    }

    // -------------------------------------------------------------------------------------
    // Robot Device Control
    // -------------------------------------------------------------------------------------


    public void goForward() {
        goForward(30);
    }

    public void goBackward() {
        goBackward(30);
    }

    public void turnLeft() {
        turnLeft(55);
    }

    public void turnRight() {
        turnRight(45);
    }
    public void goForward(int rpm) {
        // 나중에 쓸 예정
        driveWheel(rpm, 0);
    }

    public void goBackward(int rpm) {
        driveWheel(-rpm, 0);
    }

//    public void stop() {
//        RobotController.getInstance().setOperationMode((byte)0x012);
//        RobotController.getInstance().stop();
//    }

    public void turnLeft(int speedInRPM) {
        driveWheel(0, speedInRPM);
    }
    public void turnRight(int speedInRPM) {
        driveWheel(0, -speedInRPM);
    }

    public void driveWheel(int linearComponent, int angularComponent) {
        if(BluetoothManager.INSTANCE == null)
            return;

        setOperationMode(OperationMode.MOVE);
        byte[] datas = new byte[9];


		/* Set left wheel target velocity */
        datas[0] = (byte) 0x55; // pre-amble HIGH
        datas[1] = (byte) 0x54; // pre-amble LOW
        datas[2] = (byte) 0x55; // main id
        datas[3] = (byte) 0xA2; // write 0x80
        // type 0x22 ( target velocity )
        datas[4] = (byte) 0x00; // sub id ( left wheel )

        // data
        datas[5] = (byte) (linearComponent);
        datas[6] = (byte) (linearComponent >> 8);
        datas[7] = (byte) (linearComponent >> 16);
        datas[8] = (byte) (linearComponent >> 24);

        BluetoothManager.INSTANCE.sendCommand(datas);

			/* Set right wheel target velocity */
        datas[0] = (byte) 0x55; // pre-amble HIGH
        datas[1] = (byte) 0x54; // pre-amble LOW
        datas[2] = (byte) 0x55; // main id
        datas[3] = (byte) 0xA2; // write 0x80
        // type 0x22 ( target velocity )
        datas[4] = (byte) 0x01; // sub id ( right wheel )

        // data
        datas[5] = (byte) (angularComponent);
        datas[6] = (byte) (angularComponent >> 8);
        datas[7] = (byte) (angularComponent >> 16);
        datas[8] = (byte) (angularComponent >> 24);

        BluetoothManager.INSTANCE.sendCommand(datas);
    }

    // 로봇 에서 바라볼 때 10시빙향이 1번 이고,  3개씩이 한 세트  78ㄱ개 있음 24새
    public void setRgbLed( byte id, byte red, byte green, byte blue )
    {
        byte[] datas = new byte[9];

        datas[0] = (byte) 0x55; // pre-amble HIGH

        datas[1] = (byte) 0x54; // pre-amble LOW
        datas[2] = (byte) 0x55; // main id
        datas[3] = (byte) (0x80 + 0x56); // write 0x80
        // type ( RGB LED )
        datas[4] = id; // sub id (  )

        // data
        datas[5] = red;
        datas[6] = green;
        datas[7] = blue;
        datas[8] = 0x00;

        BluetoothManager.INSTANCE.sendCommand(datas);
    }

    public int byte4ToInt(byte data[])
    {
        int temp, temp1, temp2,temp3,temp4;

        temp1 = (data[0] << 24) & 0xff000000;
        temp2 = (data[1] << 16) & 0x00ff0000 ;
        temp3 = (data[2] << 8) & 0x0000ff00;
        temp4 = data[3] & 0x000000ff;

        temp = temp1 + temp2 + temp3 + temp4;

        return temp;
    }

    public int byte2ToInt(byte dataHigh, byte dataLow)
    {
        int temp, temp1, temp2;

        temp1 = (dataHigh << 8) & 0x0000ff00;
        temp2 = dataLow & 0x000000ff;

        temp = temp1 + temp2;

        return temp;
    }

    public interface onRobotConnectResult {
        public void onResult(String errorString);
    }

    private void connectWithBluetoothID(Context context, final String deviceID, final String name, final onRobotConnectResult handler) {
        BluetoothManager.INSTANCE.openConnection(context, deviceID, new BluetoothManager.onBluetoothConnectResult() {
            @Override
            public void onResult(String errorString) {
                if(errorString != null && errorString.length() > 0) {
                    handler.onResult(errorString);
                } else {
                    currentDeviceID = deviceID;
                    currentDeviceName = name;

                    isConnected = true;
                    handler.onResult(null);
                    startReceiveDataFromRobotThread();
                }
            }
        });
    }


    public boolean isConnected() {
        return this.isConnected;
    }

    public void connectRobot(Context context, onRobotConnectResult handler) {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled() == false) {
            handler.onResult("Error! bluetooth not enabled");
            return;
        }

        String pairedBluetoothID = RobotController.INSTANCE.getPairedRobotBluetoothID(context);
        if(pairedBluetoothID == null || pairedBluetoothID.length() == 0) {
            handler.onResult("No paired devices");
            return;
        }
        connectWithBluetoothID(context, pairedBluetoothID, RobotController.INSTANCE.getPairedRobotBluetoothName(context), handler);
    }

    public void disconnectRobot() {
        BluetoothManager.INSTANCE.closeConnection();
        RobotController.INSTANCE.stopReceiveDataFromRobotThread();
        isConnected = false;
    }

    public String getCurrentDeviceID() {
        return currentDeviceID;
    }


    public String getCurrentDeviceName() {
        return currentDeviceName;
    }
}