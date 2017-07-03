package com.ubudu.iot.sample;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ubudu.iot.Iot;
import com.ubudu.iot.ble.BleDevice;
import com.ubudu.iot.dongle.Dongle;
import com.ubudu.iot.ble.BleDeviceFilter;
import com.ubudu.iot.dongle.DongleManager;
import com.ubudu.iot.ble.Advertiser;
import com.ubudu.iot.ibeacon.IBeacon;
import com.ubudu.iot.peripheral.DeviceProfile;
import com.ubudu.iot.peripheral.PeripheralManager;
import com.ubudu.iot.sample.util.ToastUtil;

import java.util.Arrays;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements DongleManager.DiscoveryListener
        , Advertiser.AdvertisingListener, PeripheralManager.PeripheralListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE = 0;
    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_CONNECT = 1;
    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA = 2;

    private static final String PREF_NAME_DONGLE_NAME = "dongle_name";

    @BindView(R.id.send_message_edit_text)
    EditText messageEditText;
    @BindView(R.id.response_text_view)
    TextView responseTextView;
    @BindView(R.id.connect_button)
    Button connectButton;
    @BindView(R.id.send_data_button)
    Button sendDataButton;
    @BindView(R.id.advertise_button)
    Button advertiseButton;
    @BindView(R.id.communication_layout)
    LinearLayout communicationLayout;
    @BindView(R.id.separator_1)
    LinearLayout separatorLayout;

    @BindView(R.id.type_dongle_name)
    EditText deviceNameEditText;

    private Dongle mDongle;

    private Advertiser advertiser;
    private PeripheralManager peripheralManager;
    private SharedPreferences mSharedPref;

    private BleDevice.ReceiveDataEventListener mDataReceivedEventListener = new BleDevice.ReceiveDataEventListener() {
        @Override
        public void onDataReceived(byte[] data) {
            String response = Arrays.toString(data);
            Log.i(TAG, "Received data from dongle: " + response);
            responseTextView.setText(response);
        }
    };

    private BleDeviceFilter bleDeviceFilter = new BleDeviceFilter() {
        @Override
        public boolean isCorrect(BluetoothDevice device, int rssi, byte[] scanResponse) {
            return device.getName()!=null && device.getName().equals(deviceNameEditText.getText().toString());
        }
    };

    private void setDongle(Dongle dongle) {
        this.mDongle = dongle;
        this.mDongle.setDataReceivedEventListener(mDataReceivedEventListener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        Log.i(TAG,"Using IOT-SDK version: " +Iot.getVersion() + " ("+Iot.getVersionCode()+")");

        mSharedPref = getSharedPreferences("UbuduIotSdkPrefs", Context.MODE_PRIVATE);

        deviceNameEditText.setText(mSharedPref.getString(PREF_NAME_DONGLE_NAME,""));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            advertiser = Advertiser.getInstance();
            advertiser.setListener(this);

            peripheralManager = new PeripheralManager(getApplicationContext(), new DeviceProfile() {
                @Override
                public String getServiceUuid() {
                    return Dongle.SERVICE_UUID;
                }

                @Override
                public String getWriteCharacteristicUuid() {
                    return Dongle.WRITE_CHARACTERISTIC_UUID;
                }

                @Override
                public String getReadCharacteristicUuid() {
                    return Dongle.READ_CHARACTERISTIC_UUID;
                }
            });
            peripheralManager.setEventListener(this);
            peripheralManager.openGattServer();

//            final Handler mHandler = new Handler(Looper.getMainLooper());
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                        peripheralManager.writeData("time: "+System.currentTimeMillis());
//                    }mHandler.postDelayed(this,5000L);
//                }
//            });
        }

        initUI();

        advertiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE);
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    advertiseButton.setEnabled(false);
                    if (advertiser == null || !advertiser.isAdvertising()) {
                        advertise();
                    } else {
                        advertiser.stopAdvertising(getApplicationContext());
                    }
                }
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                deviceNameEditText.setEnabled(false);

                if (deviceNameEditText.getText().toString().equals("")) {
                    ToastUtil.showToast(getApplicationContext(), "Please specify your dongle's name");
                    deviceNameEditText.setEnabled(true);
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_CONNECT);
                    return;
                }
                mSharedPref.edit().putString(PREF_NAME_DONGLE_NAME,deviceNameEditText.getText().toString()).apply();
                connectButton.setEnabled(false);
                connectButton.setText(getResources().getString(R.string.connecting));
                if (mDongle == null)
                    findDongle();
                else
                    mDongle.disconnect();
            }
        });

        sendDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA);
                    return;
                }
                sendDataButton.setEnabled(false);
                String message = messageEditText.getText().toString();
                byte[] data = message.getBytes();
                mDongle.send(data, new BleDevice.SendDataEventListener() {
                    @Override
                    public void onDataSent(byte[] data) {
                        String dataString = Arrays.toString(data);
                        Log.i(TAG, "Data successfully sent: " + dataString);
                        sendDataButton.setEnabled(true);
                    }

                    @Override
                    public void onCommunicationError(Error error) {
                        Log.e(TAG, error.getLocalizedMessage());
                        ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
                    }
                });
            }
        });

    }

    private void findDongle() {
        DongleManager.findDongle(getApplicationContext(), 3000, bleDeviceFilter, this);
    }

    private void initUI() {
        communicationLayout.setEnabled(false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            advertiseButton.setEnabled(false);
        }
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
        if (id == R.id.action_settings)
            return true;

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA: {
                    sendDataButton.performClick();
                    break;
                }
                case ASK_GEOLOCATION_PERMISSION_REQUEST_ON_CONNECT: {
                    connectButton.performClick();
                    break;
                }
                case ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE: {
                    advertiseButton.performClick();
                    break;
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void advertise() {
        advertiser.advertise(getApplicationContext(), IBeacon.getAdvertiseBytes("67D37FC1-BE36-4EF5-A24D-D0ECD8119A7D", "12", "12", -55), true);
    }

    @Override
    public void onAdvertisingStarted() {
        Log.i(TAG, "Advertising started.");
        advertiseButton.setEnabled(true);
        advertiseButton.setText(getResources().getString(R.string.stop_advertising));
    }

    @Override
    public void onAdvertisingStopped() {
        Log.i(TAG, "Advertising stopped.");
        advertiseButton.setEnabled(true);
        advertiseButton.setText(getResources().getString(R.string.advertise));
    }

    @Override
    public void onAdvertisingError(Error error) {
        Log.e(TAG, error.getLocalizedMessage());
        ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
        advertiseButton.setEnabled(true);
        advertiseButton.setText(getResources().getString(R.string.advertise));
    }

    @Override
    public boolean onDongleFound(Dongle dongle) {
        Log.i(TAG, "Dongle found");
        setDongle(dongle);
        this.mDongle.connect(getApplicationContext(), new BleDevice.ConnectionListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "Connected to dongle !");
                connectButton.setText(R.string.disconnect);
                connectButton.setEnabled(true);
                separatorLayout.setVisibility(View.VISIBLE);
                communicationLayout.setVisibility(View.VISIBLE);
                mDongle.setDataReceivedEventListener(mDataReceivedEventListener);
            }

            @Override
            public void onDisconnected() {
                Log.i(TAG, "Disconnected from dongle !");
                mDongle = null;
                deviceNameEditText.setEnabled(true);
                connectButton.setText(R.string.connect);
                connectButton.setEnabled(true);
                separatorLayout.setVisibility(View.GONE);
                communicationLayout.setVisibility(View.GONE);
            }

            @Override
            public void onConnectionError(Error error) {
                ToastUtil.showToast(getApplicationContext(),error.getLocalizedMessage());
                Log.e(TAG, error.getLocalizedMessage());
                mDongle = null;
                connectButton.setText(R.string.connect);
                connectButton.setEnabled(true);
                separatorLayout.setVisibility(View.GONE);
                communicationLayout.setVisibility(View.GONE);
            }
        });
        return true;
    }

    @Override
    public void onDiscoveryStarted() {
        Log.i(TAG, "BLE discovery started");
    }

    @Override
    public void onDiscoveryFinished() {
        Log.i(TAG, "BLE discovery finished");
        if(mDongle==null) {
            ToastUtil.showToast(getApplicationContext(), "Dongle not found");
            connectButton.setText(getResources().getString(R.string.connect));
            connectButton.setEnabled(true);
            deviceNameEditText.setEnabled(true);
        }
    }

    @Override
    public void onDiscoveryError(Error error) {
        Log.e(TAG, error.getLocalizedMessage());
        ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
        connectButton.setText(getResources().getString(R.string.connect));
        connectButton.setEnabled(true);
        deviceNameEditText.setEnabled(true);
    }

    @Override
    public void onPeripheralReady() {
        Log.i(TAG,"Peripheral is ready to handle communication with foreign devices");
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice device, String stateDescription) {
        Log.d(TAG,"Foreign device "+device.getName()+" connection to the peripheral manager state changed: "+stateDescription);
    }

    @Override
    public void onCharacteristicWritten(UUID characteristicUUID, String value) {
        Log.i(TAG,"onDataWritten: "+value);
    }

    @Override
    public void onCharacteristicRead(UUID characteristicUUID, String value) {
        Log.i(TAG,"onDataRead: "+value);
    }

    @Override
    public void onPeripheralError(Error error) {
        Log.e(TAG,error.getLocalizedMessage());
        ToastUtil.showToast(getApplicationContext(),error.getLocalizedMessage());
    }
}
