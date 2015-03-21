package com.futurerobot.furohomesdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.futurerobot.furohomelib.BluetoothManager;
import com.futurerobot.furohomelib.RobotController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    private Button buttonConnect;
    private Button buttonGoForward, buttonGoBackward, buttonTurnLeft, buttonTurnRight;
    private Button buttonMode, buttonTemperature, buttonHumidity;
    private TextView textViewBattery;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonConnect = (Button)findViewById(R.id.buttonConnect);
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectRobot();
            }
        });
        buttonGoForward = (Button)findViewById(R.id.buttonForward);
        buttonGoForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RobotController.INSTANCE.goForward(30);
            }
        });

        buttonGoBackward = (Button)findViewById(R.id.buttonBack);
        buttonGoBackward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RobotController.INSTANCE.goBackward(30);
            }
        });

        buttonTurnLeft = (Button)findViewById(R.id.buttonTurnLeft);
        buttonTurnLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RobotController.INSTANCE.turnLeft(50);
            }
        });

        buttonTurnRight = (Button)findViewById(R.id.buttonTurnRight);
        buttonTurnRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RobotController.INSTANCE.turnRight(50);
            }
        });

        buttonMode = (Button)findViewById(R.id.buttonMode);
        buttonMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Button button = (Button)v;
                final CharSequence[] items = {
                        "Stop", "Explore", "Security", "Dance", "Love",
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Make your selection");
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        // Do something with the selection
                        button.setText("Mode: " + items[item]);
                        switch (item) {
                            case 0:
                                RobotController.INSTANCE.setOperationMode(RobotController.OperationMode.STOP);
                                break;
                            case 1:
                                RobotController.INSTANCE.setOperationMode(RobotController.OperationMode.IDLE);
                                break;
                            case 2:
                                RobotController.INSTANCE.setOperationMode(RobotController.OperationMode.SECURITY);
                                break;
                            case 3:
                                RobotController.INSTANCE.setOperationMode(RobotController.OperationMode.DANCE);
                                break;
                            case 4:
                                RobotController.INSTANCE.setOperationMode(RobotController.OperationMode.LOVE);
                                break;
                        };
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });

        buttonTemperature = (Button)findViewById(R.id.buttonTemperature);
        buttonTemperature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RobotController.INSTANCE.requestTemperature();
            }
        });


        buttonHumidity = (Button)findViewById(R.id.buttonHumidity);
        buttonHumidity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RobotController.INSTANCE.requestHumidity();
            }
        });

        textViewBattery = (TextView)findViewById(R.id.textViewBattery);
        RobotController.INSTANCE.setRobotEventListener(new RobotController.IOnRobotEventListener() {
            @Override
            public void onDistanceSensorChanged(String values) {
                Log.v("onDistanceSensorChanged", values);
            }

            // 자리는 있으나 쓰지 않음.
            @Override
            public void onTouchSensorChanged(int touchPatern) {
            }

            @Override
            public void onRobotNameChanged(String values) {
                Log.v("onRobotNameChanged", values);
                // FURO I Parrot
            }

            @Override
            public void onFirmwareVersionChanged(String values) {
                Log.v("onFirmwareVersionChanged", values);
                // 0.0.1.0 으로 옴.
            }

            @Override
            public void onBuildDateChanged(String values) {
                Log.v("onBuildDateChanged", values);
                // 2014/10 으로 전달 받음.
            }

            @Override
            public void onSerialNumberChanged(String values) {
                Log.v("onSerialNumberChanged", values);
                // 현재는 1만 옴
            }

            @Override
            public void onRobotStatusChanged(String values) {
                Log.v("onRobotStatusChanged", values);
            }

            @Override
            public void onBottomSensorChanged(String values) {
                Log.v("onBottomSensorChanged", values);
            }

            @Override
            public void onTemperatureChanged(final String values) {
                Log.v("onTemperatureChanged", values);
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonTemperature.setText("Temperature : " + values);
                    }
                });
            }

            @Override
            public void onHumidityChanged(final String values) {
                Log.v("onHumidityChanged", values);
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonHumidity.setText("Humidity : " + values);
                    }
                });
            }

            @Override
            public void onBatteryPercentChanged(final String values) {
                Log.v("onBatteryPercentChanged", values);
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textViewBattery.setText(values);
                    }
                });
            }

            @Override
            public void onChargingStatusChanged(String values) {
                Log.v("onChargingStatusChanged", values);
            }
        });

        TextView textViewIP = (TextView)findViewById(R.id.textViewIP);
        textViewIP.setText(getIPAdress(this));
        startTimerTask();
    }

    public void onDestroy() {
        super.onDestroy();
        stopTimerTask();
        RobotController.INSTANCE.disconnectRobot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(RobotController.INSTANCE.isConnected() == false) {
            String pairedBluetoothID = RobotController.INSTANCE.getPairedRobotBluetoothID(this);
            if(pairedBluetoothID == null || pairedBluetoothID.length() == 0) {
                selectRobot();
            } else {
                connectRobot();
            }
        }
    }

    void connectRobot() {

        if(RobotController.INSTANCE.isConnected() == true) {
            RobotController.INSTANCE.setPairedRobot(this, "", "");
            buttonConnect.setText("Connect" );
            RobotController.INSTANCE.disconnectRobot();
            return;
        }

        RobotController.INSTANCE.connectRobot(this, new RobotController.onRobotConnectResult() {
            @Override
            public void onResult(String errorString) {
                if(errorString != null && errorString.length() > 0) {
                    showAlert("Error", errorString);
                } else {
//                    buttonConnectFuro.setText("Connected");
                    buttonConnect.setText("Disconnect" );
                }
            }
        });
    }

    public void selectRobot() {
        final List<String> deviceList = BluetoothManager.INSTANCE.getBtDeviceList();
        final List<String> listNameNID = new ArrayList<String>();
        final List<String> listName = new ArrayList<String>();
        final List<String> listID = new ArrayList<String>();


        for (int deviceListIdx =0; deviceListIdx < deviceList.size(); deviceListIdx+=2) {
            String name = deviceList.get(deviceListIdx);  // name
            String uuid = deviceList.get(deviceListIdx + 1);	  // id
            listName.add(name);
            listNameNID.add(name + ":" + uuid);
            if(name.startsWith("FURO")) {
                listID.add(uuid);
            } else {
                listID.add(uuid);
            }
        }

        if(listID.size() == 0) {
            AlertDialog.Builder ab = new AlertDialog.Builder(this);
            ab.setTitle("Notice");
            ab.setMessage("No paired FuroHome Found. Pair first.");
            ab.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intentBluetooth = new Intent();
                    intentBluetooth.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(intentBluetooth);
                }
            });
            ab.show();
            return;
        }

        AlertDialog alert = new AlertDialog.Builder(this)
                .setTitle("Select Robot")
                .setItems(listName.toArray(new String[listName.size()]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        RobotController.INSTANCE.setPairedRobot(MainActivity.this, listID.get(item), listName.get(item));
                        connectRobot();
                    }
                })
                .create();
        alert.show();
    }

    private Timer timer;
    private void startTimerTask() {
        if(timer == null) {
            timer = new Timer();
            timer.schedule(timerTask, 1000, 1000 * 10);
        }
    }

    private void stopTimerTask() {
        if(timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
//            takePhoto(true);
            RobotController.INSTANCE.requestBatteryPercent();
        }
    };

    public String getIPAdress(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();
        for(Method method: wmMethods){
            if(method.getName().equals("isWifiApEnabled")) {

                try {
                    if(method.invoke(wifi).toString().equals("false")) {
                        WifiInfo wifiInfo = wifi.getConnectionInfo();
                        int ipAddress = wifiInfo.getIpAddress();
                        String ip = (ipAddress & 0xFF) + "." +
                                ((ipAddress >> 8 ) & 0xFF) + "." +
                                ((ipAddress >> 16 ) & 0xFF) + "." +
                                ((ipAddress >> 24 ) & 0xFF ) ;
                        return ip;
                    } else if(method.invoke(wifi).toString().equals("true")) {
                        return "192.168.43.1";
                    }
                } catch (IllegalArgumentException e) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
            }
        }
        return "";
    }


    private void showAlert(final String strTitle, final String strMessage) {
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle(strTitle);
        ab.setMessage(strMessage);
        ab.setPositiveButton(android.R.string.ok, null);
        ab.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
