package com.ubudu.iot.sample;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ubudu.iot.ConnectionListener;
import com.ubudu.iot.DataListener;
import com.ubudu.iot.DataSentListener;
import com.ubudu.iot.Iot;
import com.ubudu.iot.ble.Advertiser;
import com.ubudu.iot.ble.BleDevice;
import com.ubudu.iot.ble.BleDeviceFilter;
import com.ubudu.iot.ble.DiscoveryManager;
import com.ubudu.iot.ibeacon.IBeacon;
import com.ubudu.iot.peripheral.DeviceProfile;
import com.ubudu.iot.peripheral.PeripheralManager;
import com.ubudu.iot.sample.fragment.BaseFragment;
import com.ubudu.iot.sample.fragment.CommFragment;
import com.ubudu.iot.sample.fragment.OptionSelectionFragment;
import com.ubudu.iot.sample.fragment.PeripheralFragment;
import com.ubudu.iot.sample.fragment.ScannedDevicesFragment;
import com.ubudu.iot.sample.model.LogItem;
import com.ubudu.iot.sample.model.Option;
import com.ubudu.iot.sample.util.CustomTypefaceSpan;
import com.ubudu.iot.sample.util.FragmentUtils;
import com.ubudu.iot.sample.util.ToastUtil;
import com.ubudu.iot.util.LongDataProtocol;
import com.ubudu.iot.util.LongDataProtocolV3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MainActivity extends AppCompatActivity implements BaseFragment.ViewController
        , DiscoveryManager.DiscoveryListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE = 0;
    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SCAN = 1;
    private static final int ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA = 2;

    private BleDevice mBleDevice;
    private BluetoothGattService selectedService;
    private Advertiser advertiser;

    private CommFragment commFragment;
    private PeripheralFragment peripheralFragment;
    private ScannedDevicesFragment scannedDevicesFragment;

    private PeripheralManager peripheralManager;

    private BluetoothGattCharacteristic gattCharForWriting;

    private MenuItem scanMenuItem;

    private DrawerLayout mRootView;

    @BindView(R.id.navigation_view)
    android.support.design.widget.NavigationView navigationView;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    private View headerLayout;
    private MaterialDialog progressDialog;

    private SharedPreferences mSharedPref;
    private int rssiThreshold;

    private LongDataProtocol longDataProtocol;

    private DataSentListener mDataSentListener = new DataSentListener() {
        @Override
        public void onDataSent(byte[] data) {
            dismissProgressDialog();
            Log.i(TAG, "Data successfully sent: " + Arrays.toString(data) );
            if(commFragment!=null)
                commFragment.onDataSent(new String(data));
        }

        @Override
        public void onError(final Error error) {
            if(commFragment!=null)
                commFragment.onDataSent(null);
            Log.e(TAG, error.getLocalizedMessage());
        }
    };

    private DataListener mDataReceivedEventListener = new DataListener() {
        @Override
        public void onDataReceived(final byte[] data) {

            Log.i(TAG, "Received data len: " + data.length + ", data: "+new String(data));
            if (commFragment != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        commFragment.onDataReceived(Arrays.toString(data));
                    }
                });
            }
        }
    };

    private BleDeviceFilter bleDeviceFilter = new BleDeviceFilter() {
        @Override
        public boolean isCorrect(BluetoothDevice device, int rssi, byte[] scanResponse) {
            return device != null;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.scan_menu, menu);
        scanMenuItem = menu.findItem(R.id.action_scan);
        initScanMenuItemActionView();
        return true;
    }

    private void initScanMenuItemActionView() {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView iv = (ImageView)inflater.inflate(R.layout.scan_refresh, null);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanForBleDevicesAction();
            }
        });
        scanMenuItem.setActionView(iv);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
        } else if (id == R.id.action_scan) {
            scanForBleDevicesAction();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void scanForBleDevicesAction() {
        scanMenuItem.setEnabled(false);
        hideKeyboardRequested();
        onScanningRequested();
    }

    @Override
    public void hideKeyboardRequested() {
        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow((null == getCurrentFocus()) ? null : getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRootView = (DrawerLayout) LayoutInflater.from(this).inflate(R.layout.activity_main, null);
        setContentView(mRootView);
        ButterKnife.bind(this);

        longDataProtocol = new LongDataProtocolV3();

        Log.i(TAG,"Using Ubudu IOT-SDK v"+Iot.getVersion()+"("+Iot.getVersionCode()+")");

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        rssiThreshold = mSharedPref.getInt("rssi_filter_value", -90);

        initNavigationDrawer();

        onScannedDevicesFragmentRequested();

        Log.i(TAG, "Using IOT-SDK version: " + Iot.getVersion() + " (" + Iot.getVersionCode() + ")");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            advertiser = Advertiser.getInstance();
            advertiser.setListener(new Advertiser.AdvertisingListener() {
                @Override
                public void onAdvertisingStarted() {
                    Log.i(TAG, "Advertising started.");
                    if(peripheralFragment!=null)
                        peripheralFragment.onAdvertisingStarted();

                    peripheralManager.openGattServer();
                }

                @Override
                public void onAdvertisingStopped() {
                    Log.i(TAG, "Advertising stopped.");
                    if(peripheralFragment!=null)
                        peripheralFragment.onAdvertisingStopped();
                    if(peripheralManager!=null)
                        peripheralManager.closeGattServer();
                }

                @Override
                public void onAdvertisingError(Error error) {
                    Log.e(TAG, error.getLocalizedMessage());
                    ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
                    if(peripheralFragment!=null)
                        peripheralFragment.onAdvertisingError(error.getMessage());
                }
            });

            peripheralManager = new PeripheralManager(getApplicationContext(), new DeviceProfile() {
                @Override
                public String getServiceUuid() {
                    return MyBleDevice.SERVICE_UUID;
                }

                @Override
                public String getWriteCharacteristicUuid() {
                    return MyBleDevice.WRITE_CHARACTERISTIC_UUID;
                }

                @Override
                public String getReadCharacteristicUuid() {
                    return MyBleDevice.NOTIFICATION_CHARACTERISTIC_UUID;
                }
            });

            peripheralManager.setLongMessageProtocol(new LongDataProtocolV3());

            peripheralManager.setEventListener(new PeripheralManager.PeripheralListener() {
                @Override
                public void onPeripheralReady() {
                    String msg = "Peripheral is ready to handle communication with foreign devices";
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.i(TAG, msg);
                }

                @Override
                public void onPeripheralClosed() {
                    String msg = "Peripheral closed";
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.i(TAG, msg);
                }

                private final Handler mHandler = new Handler();
                @Override
                public void onConnectionStateChange(BluetoothDevice device, String stateDescription) {
                    String msg = "Foreign device " + device.getName() + " connection to the peripheral manager state changed: " + stateDescription;
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.d(TAG, msg);
                }

                @Override
                public void onCharacteristicWritten(UUID characteristicUUID, final byte[] value) {
                    final String msg = "onDataWritten: " + Arrays.toString(value);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (peripheralFragment != null)
                                peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                        }
                    });
                    Log.d(TAG, msg);
                }

                @Override
                public void onCharacteristicRead(UUID characteristicUUID, byte[] value) {
                    String msg = "onDataRead: " + new String(value);
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.d(TAG, msg);
                }

                @Override
                public void onPeripheralError(Error error) {
                    final String msg = error.getLocalizedMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (peripheralFragment != null)
                                peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                        }
                    });
                    Log.e(TAG, msg);
                    ToastUtil.showToast(getApplicationContext(), msg);
                }
            });
        }
    }

    private ActionBarDrawerToggle actionBarDrawerToggle;
    /**
     * Sets up the side navigation drawer
     */
    private void initNavigationDrawer() {
        setSupportActionBar(toolbar);
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, mRootView, toolbar, R.string.openDrawer, R.string.closeDrawer);
        actionBarDrawerToggle.setToolbarNavigationClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        //Setting the actionbarToggle to drawer layout
        mRootView.setDrawerListener(actionBarDrawerToggle);
        //calling sync state is necessay or else your hamburger icon wont show up
        actionBarDrawerToggle.syncState();
        navigationView.setItemIconTintList(null);
        Menu m = navigationView.getMenu();
        for (int i = 0; i < m.size(); i++) {
            MenuItem mi = m.getItem(i);

            //for aapplying a font to subMenu ...
            SubMenu subMenu = mi.getSubMenu();
            if (subMenu != null && subMenu.size() > 0) {
                for (int j = 0; j < subMenu.size(); j++) {
                    MenuItem subMenuItem = subMenu.getItem(j);
                    applyFontToMenuItem(subMenuItem);
                }
            }
            //the method we have create in activity
            applyFontToMenuItem(mi);
        }
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.scan_item:
                        item.setChecked(true);
                        onScannedDevicesFragmentRequested();
                        break;
                    case R.id.peripheral_item:
                        item.setChecked(true);
                        onPeripheralFragmentRequested();
                        break;
                }

                mRootView.closeDrawers();
                return false;
            }
        });
        if (headerLayout == null)
            headerLayout = navigationView.inflateHeaderView(R.layout.navigation_header);
    }

    /**
     * Applies styled font to menu item
     *
     * @param mi menu item
     */
    private void applyFontToMenuItem(MenuItem mi) {
        Typeface font = Typeface.createFromAsset(getAssets(), "fonts/helvetica-neue-light.ttf");
        SpannableString mNewTitle = new SpannableString(mi.getTitle());
        mNewTitle.setSpan(new CustomTypefaceSpan("", font), 0, mNewTitle.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        mi.setTitle(mNewTitle);
    }
    @Override
    public void onBackPressed() {
        if(!isScannedDevicesFragmentVisible)
            super.onBackPressed();
    }

    private void findBleDevice() {

        animateScanMenuItem();

        DiscoveryManager.discover(getApplicationContext(), 3000, bleDeviceFilter, this);
    }

    private void animateScanMenuItem() {
        // Do animation start
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView iv = (ImageView)inflater.inflate(R.layout.scan_refresh, null);
        Animation rotation = AnimationUtils.loadAnimation(this, R.anim.rotate);
        rotation.setRepeatCount(Animation.INFINITE);
        iv.startAnimation(rotation);
        scanMenuItem.setActionView(iv);
    }

    private void stopAnimatingScanMenuItem() {
        if(scanMenuItem.getActionView()!=null)
        {
            // Remove the animation.
            scanMenuItem.getActionView().clearAnimation();
            initScanMenuItemActionView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA: {
                    commFragment.requestSend();
                    break;
                }
                case ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SCAN: {
                    scannedDevicesFragment.requestScanning();
                    break;
                }
                case ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE: {
                    peripheralFragment.requestAdvertising();
                    break;
                }
            }
        }
    }

    @Override
    public boolean onBleDeviceFound(BleDevice scannedBleDevice) {
        Log.i(TAG, "Ble device found");
        scannedDevicesFragment.updateDevice(scannedBleDevice);
        return false;
    }

    private List<Option> getOptions(List<BluetoothGattCharacteristic> characteristics) {
        List<Option> options = new ArrayList<Option>();
        for (BluetoothGattCharacteristic characteristic : characteristics) {

            String description = "Properties: ";

            boolean isWritable = BleDevice.isCharacteristicWriteable(characteristic);
            boolean isReadable = BleDevice.isCharacterisitcReadable(characteristic);
            boolean isNotifable = BleDevice.isCharacterisiticNotifiable(characteristic);

            if (isWritable)
                description += "WR, ";
            if (isReadable)
                description += "RD, ";
            if (isNotifable)
                description += "Notify, ";

            description = description.substring(0, description.length() - 2);

            options.add(new Option(characteristic.getUuid().toString(), description));
        }
        return options;
    }

    @Override
    public void onDiscoveryStarted() {
        Log.i(TAG, "BLE discovery started");
    }

    @Override
    public void onDiscoveryFinished() {
        Log.i(TAG, "BLE discovery finished");

        stopAnimatingScanMenuItem();

        scannedDevicesFragment.onScanningFinished();
        scanMenuItem.setEnabled(true);
    }

    @Override
    public void onDiscoveryError(Error error) {
        Log.e(TAG, error.getLocalizedMessage());
        ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
        scannedDevicesFragment.onScanningFinished();
    }

    @Override
    public void onOptionSelectionFragmentRequested(String title, List<Option> options, MainActivity.ChooseListener listener) {
        actionBarDrawerToggle.setDrawerIndicatorEnabled(false);
        // update the actionbar to show the up carat/affordance
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        OptionSelectionFragment optionSelectionFragment = new OptionSelectionFragment();
        optionSelectionFragment.setListener(listener);
        optionSelectionFragment.setOptions(options);
        optionSelectionFragment.setTitle(title);
        FragmentUtils.changeFragment(this, optionSelectionFragment, true);
    }

    private boolean isScannedDevicesFragmentVisible = false;

    @Override
    public void onPeripheralFragmentRequested() {
        peripheralFragment = new PeripheralFragment();
        FragmentUtils.changeFragment(this, peripheralFragment, false);
    }

    @Override
    public void onPeripheralFragmentResumed() {
        navigationView.setCheckedItem(R.id.peripheral_item);
    }

    @Override
    public void onPeripheralFragmentPaused() {

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean isAdvertising() {
        return advertiser.isAdvertising();
    }

    @Override
    public void onCommFragmentRequested() {
        commFragment = new CommFragment();
        FragmentUtils.changeFragment(this, commFragment, true);
    }

    @Override
    public void onCommFragmentResumed() {

    }

    @Override
    public void onCommFragmentPaused() {

    }

    @Override
    public void onNameMacFilterChanged(String filter) {
        mSharedPref.edit().putString("name_mac_filter_value",filter).apply();
    }

    @Override
    public void onConnectionRequested(BleDevice device) {

        showProgressDialog(getString(R.string.connecting));

        this.mBleDevice = device;
        this.mBleDevice.setDataSentListener(mDataSentListener);
        this.mBleDevice.setDataReceivedEventListener(mDataReceivedEventListener);

        // set long msg protocol for handling long data
        this.mBleDevice.setLongMessageProtocol(longDataProtocol);
        this.mBleDevice.setNegotiateMtuEnabled(true);
        this.mBleDevice.connect(getApplicationContext(), new ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.showToast(getApplicationContext(), "MTU: " + mBleDevice.getMtu());


                        Log.i(TAG, "Connected to ble device: " + mBleDevice.getDevice().getName());
                        mBleDevice.setDataReceivedEventListener(mDataReceivedEventListener);
                        dismissProgressDialog();

                        showProgressDialog(getString(R.string.discovering_services));
                        onSelectServiceFragmentRequested(new CompletionListener() {
                            @Override
                            public void onCompleted() {
                                dismissProgressDialog();
                                onSelectNotificationCharacteristicFragmentRequested(new CompletionListener() {
                                    @Override
                                    public void onCompleted() {
                                        onSelectWriteCharacteristicFragmentRequested(new CompletionListener() {
                                            @Override
                                            public void onCompleted() {
                                                onCommFragmentRequested();
                                            }

                                            @Override
                                            public void onCancelled() {

                                            }
                                        });
                                    }

                                    @Override
                                    public void onCancelled() {

                                    }
                                });
                            }

                            @Override
                            public void onCancelled() {
                                if (mBleDevice != null)
                                    mBleDevice.disconnect();
                                dismissProgressDialog();
                            }
                        });
                    }
                });
            }

            @Override
            public void onDisconnected() {
                dismissProgressDialog();
                Log.i(TAG, "Disconnected from ble device !");
                mBleDevice = null;
                onScannedDevicesFragmentRequested();
            }

            @Override
            public void onConnectionError(final Error error) {
                dismissProgressDialog();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
                        Log.e(TAG, error.getLocalizedMessage());
                        mBleDevice = null;
                        onScannedDevicesFragmentRequested();
                    }
                });
            }
        });
    }

    public void onSelectNotificationCharacteristicFragmentRequested(final CompletionListener listener) {
        List<Option> options = getOptions(selectedService.getCharacteristics());
        if(options.size()>0) {
            onOptionSelectionFragmentRequested(getString(R.string.title_notification_characteristic), options, new ChooseListener() {

                @Override
                public void onOptionChosen(String option) {
                    BluetoothGattCharacteristic selected = null;
                    for (BluetoothGattCharacteristic characteristic : selectedService.getCharacteristics()) {
                        if (characteristic.getUuid().toString().equalsIgnoreCase(option)) {
                            selected = characteristic;
                            break;
                        }
                    }
                    mBleDevice.registerForNotifications(selected, new BleDevice.RegisterForNotificationsListener() {
                        @Override
                        public void onRegistered(BluetoothGattCharacteristic characteristic) {
                            listener.onCompleted();
                        }

                        @Override
                        public void onError(Error error) {
                            ToastUtil.showToast(getApplicationContext(), error.getMessage());
                            listener.onCompleted();
                        }
                    });
                }

                @Override
                public void onChooserCancelled() {
                    listener.onCancelled();
                }
            });
        } else {
            ToastUtil.showToast(getApplicationContext(),"No characteristics available");
        }
    }

    public void onSelectWriteCharacteristicFragmentRequested(final CompletionListener listener) {
        List<Option> options = getOptions(selectedService.getCharacteristics());
        onOptionSelectionFragmentRequested(getString(R.string.title_write_characteristic), options, new MainActivity.ChooseListener() {

            @Override
            public void onOptionChosen(String option) {
                BluetoothGattCharacteristic selected = null;
                for (BluetoothGattCharacteristic characteristic : selectedService.getCharacteristics()) {
                    if (characteristic.getUuid().toString().equalsIgnoreCase(option)) {
                        selected = characteristic;
                        break;
                    }
                }

                gattCharForWriting = selected;
                listener.onCompleted();
            }

            @Override
            public void onChooserCancelled() {
                listener.onCancelled();
            }
        });

    }

    public void onSelectServiceFragmentRequested(final CompletionListener listener) {
        mBleDevice.discoverServices(new BleDevice.ServicesDiscoveryListener() {
            @Override
            public void onServicesDiscovered(final List<BluetoothGattService> services) {
                dismissProgressDialog();

                final List<Option> options = new ArrayList<Option>();
                for (BluetoothGattService service : services) {
                    options.add(new Option(service.getUuid().toString(), ""));
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onOptionSelectionFragmentRequested(getString(R.string.title_service), options, new ChooseListener() {

                            @Override
                            public void onOptionChosen(String option) {
                                BluetoothGattService selected = null;
                                for (BluetoothGattService service : services) {
                                    if (service.getUuid().toString().equalsIgnoreCase(option)) {
                                        selected = service;
                                        break;
                                    }
                                }
                                selectedService = selected;

                                listener.onCompleted();
                            }

                            @Override
                            public void onChooserCancelled() {
                                listener.onCancelled();
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(Error error) {
                mBleDevice.disconnect();
                dismissProgressDialog();
                Log.e(TAG,error.getMessage());
                ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
            }

        });
    }

    private void showProgressDialog(String msg) {
        progressDialog = new MaterialDialog.Builder(this)
                .title(R.string.please_wait)
                .content(msg)
                .cancelable(false)
                .progress(true, 0)
                .show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null)
            progressDialog.dismiss();
    }

    @Override
    public void onDisconnectRequested() {
        mBleDevice.disconnect();
    }

    @Override
    public void onRssiFilterThresholdChanged(int rssiThreshold) {
        mSharedPref.edit().putInt("rssi_filter_value",rssiThreshold).apply();
    }

    @Override
    public void onScannedDevicesFragmentRequested() {
        if (!isScannedDevicesFragmentVisible) {
            scannedDevicesFragment = new ScannedDevicesFragment();
            scannedDevicesFragment.setRssiThreshold(rssiThreshold);
            scannedDevicesFragment.setNameMacFilter(mSharedPref.getString("name_mac_filter_value", ""));
            FragmentUtils.changeFragment(this, scannedDevicesFragment, false);
        }
    }

    @Override
    public void onScannedDevicesFragmentPaused() {
        if(scanMenuItem!=null)
            scanMenuItem.setVisible(false);
        isScannedDevicesFragmentVisible = false;
    }

    @Override
    public void onScannedDevicesFragmentResumed() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
        isScannedDevicesFragmentVisible = true;
        navigationView.setCheckedItem(R.id.scan_item);

        if (mBleDevice!=null && mBleDevice.isConnected())
            mBleDevice.disconnect();

        if(scanMenuItem!=null)
            scanMenuItem.setVisible(true);
    }

    @Override
    public void onAdvertiseRequested() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_ADVERTISE);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (advertiser == null || !advertiser.isAdvertising()) {
                advertiser.advertise(getApplicationContext(), IBeacon.getAdvertiseBytes("67D37FC1-BE36-4EF5-A24D-D0ECD8119A7D", "12", "12", -55), true);
            } else {
                advertiser.stopAdvertising(getApplicationContext());
            }
        }
    }

    @Override
    public void onScanningRequested() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SCAN);
            return;
        }

        if(!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            if(progressDialog!=null) progressDialog.dismiss();
            new MaterialDialog.Builder(this)
                    .title(R.string.bluetooth)
                    .content(R.string.bluetooth_is_off)
                    .cancelable(true)
                    .positiveText("OK")
                    .negativeText("Cancel")
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            BluetoothAdapter.getDefaultAdapter().enable();
                        }
                    })
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
            return;
        }

        scannedDevicesFragment.onScanningStarted();
        findBleDevice();
    }

    @Override
    public void onSendMessageRequested(byte[] data, int dataType) {
        if(gattCharForWriting!=null && mBleDevice!=null && mBleDevice.isConnected()) {
            mBleDevice.send(data, gattCharForWriting, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, dataType, false);
        }
    }

    public interface ChooseListener {
        void onOptionChosen(String option);

        void onChooserCancelled();
    }

    public interface CompletionListener {
        void onCompleted();
        void onCancelled();
    }
}
