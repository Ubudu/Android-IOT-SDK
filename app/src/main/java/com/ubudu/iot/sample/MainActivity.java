package com.ubudu.iot.sample;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.ubudu.iot.dongle.Dongle;
import com.ubudu.iot.dongle.DongleManager;
import com.ubudu.iot.dongle.filter.DongleFilter;
import com.ubudu.iot.ibeacon.Advertiser;
import com.ubudu.iot.sample.util.ToastUtil;

import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements DongleManager.DiscoveryListener
        , Dongle.CommunicationListener
        , Dongle.ConnectionListener
        , Advertiser.EventListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE = 0;
    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_CONNECT = 1;
    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA = 2;

    private static final String DEFAULT_DONGLE_NAME = "fa4fbd2bdd6b\n";

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
    @BindView(R.id.connection_title)
    TextView connectionTextView;
    @BindView(R.id.separator_1)
    LinearLayout separatorLayout;

    private Dongle mDongle;
    private DongleManager dongleManager;

    private Advertiser advertiser;

    private DongleFilter dongleFilter = new DongleFilter() {
        @Override
        public boolean isCorrect(BluetoothDevice device) {
            return device.getName() != null && device.getName().equals(DEFAULT_DONGLE_NAME);
        }
    };

    private void setDongle(Dongle dongle) {
        mDongle = dongle;
        mDongle.setConnectionListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            advertiser = new Advertiser(getApplicationContext());
            advertiser.setListener(this);
        }

        dongleManager = new DongleManager(getApplicationContext());

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
                        advertiser.stopAdvertising();
                    }
                }
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_CONNECT);
                    return;
                }
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
                mDongle.send(data);
            }
        });

    }

    private void findDongle() {
        dongleManager.findDongle(dongleFilter, this);
    }

    private void initUI() {
        connectionTextView.setText(getResources().getString(R.string.connection_title) + " " + DEFAULT_DONGLE_NAME);
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

    @Override
    public void onConnected() {
        Log.i(TAG, "Connected to dongle !");
        connectButton.setText(R.string.disconnect);
        connectButton.setEnabled(true);
        separatorLayout.setVisibility(View.VISIBLE);
        communicationLayout.setVisibility(View.VISIBLE);
        mDongle.setCommunicationListener(this);
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Disconnected from dongle !");
        mDongle = null;
        connectButton.setText(R.string.connect);
        connectButton.setEnabled(true);
        separatorLayout.setVisibility(View.GONE);
        communicationLayout.setVisibility(View.GONE);
    }

    @Override
    public void onConnectionError(Error error) {
        Log.e(TAG, error.getLocalizedMessage());
        mDongle = null;
        connectButton.setText(R.string.connect);
        connectButton.setEnabled(true);
        separatorLayout.setVisibility(View.GONE);
        communicationLayout.setVisibility(View.GONE);
    }

    @Override
    public void onDataReceived(byte[] data) {
        String response = Arrays.toString(data);
        Log.i(TAG, "Received data from dongle: " + response);
        responseTextView.setText(response);
    }

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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void advertise() {
        advertiser.advertise("67D37FC1-BE36-4EF5-A24D-D0ECD8119A7D", "12", "12", -55);
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
    public void onDongleFound(Dongle dongle) {
        Log.i(TAG, "Dongle found");
        setDongle(dongle);
        mDongle.connect(getApplicationContext());
    }

    @Override
    public void onDiscoveryError(Error error) {
        Log.e(TAG, error.getLocalizedMessage());
        ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
        connectButton.setText(getResources().getString(R.string.connect));
        connectButton.setEnabled(true);
    }
}
