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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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
import com.crashlytics.android.Crashlytics;
import com.ubudu.iot.ConnectionListener;
import com.ubudu.iot.DataListener;
import com.ubudu.iot.Iot;
import com.ubudu.iot.DataSentListener;
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
import com.ubudu.iot.sample.sound.ADPCMDecoder;
import com.ubudu.iot.sample.util.CustomTypefaceSpan;
import com.ubudu.iot.sample.util.FragmentUtils;
import com.ubudu.iot.sample.util.ToastUtil;
import com.ubudu.iot.util.LongDataProtocol;
import com.ubudu.iot.util.LongDataProtocolV2;

import io.fabric.sdk.android.Fabric;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;

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

    private DrawerLayout mRootView;

    @BindView(R.id.navigation_view)
    android.support.design.widget.NavigationView navigationView;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    private View headerLayout;
    private MaterialDialog progressDialog;
    private int pcmSamplingFrequencyHz;
    private AudioTrack mAudioTrack;
    private ADPCMDecoder mAdpcmDecoder;
    private SharedPreferences mSharedPref;
    private int rssiThreshold;

    private Handler mAudioHandler;
    private HandlerThread mAudioHandlerThread;

    private LongDataProtocol longDataProtocol;

    private DataListener mDataReceivedEventListener = new DataListener() {
        @Override
        public void onDataReceived(final byte[] data) {
            Log.i(TAG, "Received data from ble device: " + String.format("%020x", new BigInteger(1, data)));
            Log.i(TAG, "Received data len: " + data.length);
            try {
                if(data[0] != (byte)0xFA && commFragment!=null) {
                    Log.e(TAG,"normal data...");
                    String msg = new String(data, "UTF-8");
                    commFragment.onDataReceived(msg);
                } else if(data[0] == (byte)0xFA && mAdpcmDecoder!=null){
                    final byte[] audioBytes = Arrays.copyOfRange(data, 1, data.length);
                    Log.e(TAG,"audio bytes len: "+audioBytes.length);
                    playAudioBytes(audioBytes);

                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    private void playAudioBytes(final byte[] audioBytes) {
        mAudioHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mAdpcmDecoder==null)
                    return;

                if(audioBytes.length>ADPCMDecoder.FRAME_SIZE) {
                    int iterations = audioBytes.length/ADPCMDecoder.FRAME_SIZE;
                    byte[] block = new byte[ADPCMDecoder.FRAME_SIZE];
                    for(int i=0;i<iterations;i++) {
                        System.arraycopy(audioBytes, i*ADPCMDecoder.FRAME_SIZE, block, 0, ADPCMDecoder.FRAME_SIZE);
                        addDataToDecoder(block);
                    }
                } else {
                    addDataToDecoder(audioBytes);
                }
            }

            private void addDataToDecoder(byte[] audioBytes) {
                if (mBleDevice!=null && mBleDevice.getMtu() >131) { //Pre lollipop devices may not have the max mtu size hence the check
                    final byte[] newData = new byte[ADPCMDecoder.FRAME_SIZE];
                    System.arraycopy(audioBytes, 0, newData, 0, ADPCMDecoder.FRAME_SIZE);
                    mAdpcmDecoder.add(newData);
                } else {
                    mAdpcmDecoder.add(audioBytes);
                }
            }
        });
    }

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

    private MenuItem scanMenuItem;
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
        Fabric.with(this, new Crashlytics());

        mRootView = (DrawerLayout) LayoutInflater.from(this).inflate(R.layout.activity_main, null);
        setContentView(mRootView);
        ButterKnife.bind(this);

        longDataProtocol = new LongDataProtocolV2();

        Log.i(TAG,"Using Ubudu IOT-SDK v"+Iot.getVersion()+"("+Iot.getVersionCode()+")");

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        pcmSamplingFrequencyHz = mSharedPref.getInt("pcm_audio_sampling_frequency_hz", 8000);
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
                    peripheralFragment.onAdvertisingStarted();
                }

                @Override
                public void onAdvertisingStopped() {
                    Log.i(TAG, "Advertising stopped.");
                    peripheralFragment.onAdvertisingStopped();
                }

                @Override
                public void onAdvertisingError(Error error) {
                    Log.e(TAG, error.getLocalizedMessage());
                    ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
                    peripheralFragment.onAdvertisingError(error.getMessage());
                }
            });

            PeripheralManager peripheralManager = new PeripheralManager(getApplicationContext(), new DeviceProfile() {
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
                    return MyBleDevice.READ_CHARACTERISTIC_UUID;
                }
            });
            peripheralManager.setEventListener(new PeripheralManager.PeripheralListener() {
                @Override
                public void onPeripheralReady() {
                    String msg = "Peripheral is ready to handle communication with foreign devices";
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.i(TAG, msg);
                }

                @Override
                public void onConnectionStateChange(BluetoothDevice device, String stateDescription) {
                    String msg = "Foreign device " + device.getName() + " connection to the peripheral manager state changed: " + stateDescription;
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.d(TAG, msg);
                }

                @Override
                public void onCharacteristicWritten(UUID characteristicUUID, String value) {
                    String msg = "onDataWritten: " + value;
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.d(TAG, msg);
                }

                @Override
                public void onCharacteristicRead(UUID characteristicUUID, String value) {
                    String msg = "onDataRead: " + value;
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.d(TAG, msg);
                }

                @Override
                public void onPeripheralError(Error error) {
                    String msg = error.getLocalizedMessage();
                    if (peripheralFragment != null)
                        peripheralFragment.log(msg, LogItem.TYPE_MESSAGE);
                    Log.e(TAG, msg);
                    ToastUtil.showToast(getApplicationContext(), msg);
                }
            });
            peripheralManager.openGattServer();
        }
    }

    @Override
    protected void onDestroy() {
        stopPcmPlayback();
        super.onDestroy();
    }

    private void initPcmPlayback() {

        // init handler that plays the data
        mAudioHandlerThread = new HandlerThread("AudioHandlerThread");
        mAudioHandlerThread.start();
        mAudioHandler = new Handler(mAudioHandlerThread.getLooper());

        int bufferSize = AudioTrack.getMinBufferSize(pcmSamplingFrequencyHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, pcmSamplingFrequencyHz
                , AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
        mAudioTrack.play();
        mAdpcmDecoder = new ADPCMDecoder(getApplicationContext(), false);
        mAdpcmDecoder.setListener(new ADPCMDecoder.DecoderListener() {
            @Override
            public void onFrameDecoded(byte[] pcm, int frameNumber) {
                if(mAudioTrack != null)
                    mAudioTrack.write(pcm, 0, pcm.length/*, AudioTrack.WRITE_NON_BLOCKING*/);
            }
        });
    }

    private void stopPcmPlayback() {
        if(mAudioTrack!=null) {
            mAudioTrack.stop();
            mAudioTrack = null;
            mAdpcmDecoder = null;
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
    protected void onResume() {
        super.onResume();
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
        initPcmPlayback();
    }

    @Override
    public void onCommFragmentPaused() {
        stopPcmPlayback();
    }

    @Override
    public void onNameMacFilterChanged(String filter) {
        Log.e(TAG,"saving filter: "+filter);
        mSharedPref.edit().putString("name_mac_filter_value",filter).apply();
    }

    @Override
    public void onConnectionRequested(BleDevice device) {

        showProgressDialog(getString(R.string.connecting));

        this.mBleDevice = device;
        this.mBleDevice.setDataReceivedEventListener(mDataReceivedEventListener);

        // set long msg protocol for handling long data
        this.mBleDevice.setLongMessageProtocol(longDataProtocol);

        this.mBleDevice.connect(getApplicationContext(), new ConnectionListener() {
            @Override
            public void onConnected() {
                ToastUtil.showToast(getApplicationContext(),"MTU: "+mBleDevice.getMtu());

                Log.i(TAG, "Connected to ble device: "+mBleDevice.getDevice().getName());
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
                        if(mBleDevice!=null)
                            mBleDevice.disconnect();
                        dismissProgressDialog();
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
            public void onConnectionError(Error error) {
                dismissProgressDialog();
                ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
                Log.e(TAG, error.getLocalizedMessage());
                mBleDevice = null;
                onScannedDevicesFragmentRequested();
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

                mBleDevice.setGattCharacteristicForWriting(selected);
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

                List<Option> options = new ArrayList<Option>();
                for (BluetoothGattService service : services) {
                    options.add(new Option(service.getUuid().toString(), ""));
                }

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
    public void onAudioSampleRateChanged(int sampleRateHz) {
        pcmSamplingFrequencyHz = sampleRateHz;
        stopPcmPlayback();
        initPcmPlayback();
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
                && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
    public void onSendMessageRequested(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ASK_GEOLOCATION_PERMISSION_REQUEST_ON_SEND_DATA);
            return;
        }

        byte[] data = new byte[0];
        try {
            data = (message).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        mBleDevice.send(data, new DataSentListener() {
            @Override
            public void onDataSent(byte[] data) {
                dismissProgressDialog();
                Log.i(TAG, "Data successfully sent: " + String.format("%020x", new BigInteger(1, data)));
                commFragment.onMessageSendingFinished();
            }

            @Override
            public void onError(Error error) {
                Log.e(TAG, error.getLocalizedMessage());
                ToastUtil.showToast(getApplicationContext(), error.getLocalizedMessage());
                commFragment.onMessageSendingFinished();
            }
        });
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
