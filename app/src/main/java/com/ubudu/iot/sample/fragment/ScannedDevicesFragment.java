package com.ubudu.iot.sample.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ubudu.iot.ble.BleDevice;
import com.ubudu.iot.sample.R;
import com.ubudu.iot.sample.util.ScannedDevicesAdapter;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by mgasztold on 09/09/2017.
 */

public class ScannedDevicesFragment extends BaseFragment {

    public static final String TAG = ScannedDevicesFragment.class.getCanonicalName();

    @BindView(R.id.scanned_devices_list)
    ListView devicesList;

    @BindView(R.id.rssi_filter_seek_bar)
    DiscreteSeekBar rssiFilterSeekBar;

    @BindView(R.id.filter_value)
    TextView filterValueTextView;

    @BindView(R.id.rssi_filter_layout)
    LinearLayout rssiFilterLayout;

    @BindView(R.id.scanning_animation_layout)
    LinearLayout scanningAnimationView;

    @BindView(R.id.name_mac_filter_edit_text)
    EditText filterEditText;

    private int rssiThreshold;
    private String nameMacFilter;
    private ScannedDevicesAdapter adapter;
    private final List<BleDevice> removedDevices = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout mRootView = (LinearLayout) inflater.inflate(R.layout.fragment_scanned_devices, container, false);
        ButterKnife.bind(this, mRootView);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initLogList();

        rssiFilterSeekBar.setProgress(100+rssiThreshold);
        filterValueTextView.setText(String.valueOf(rssiThreshold) + " dBm");
        rssiFilterSeekBar.setIndicatorPopupEnabled(false);
        rssiFilterSeekBar.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
            @Override
            public void onProgressChanged(DiscreteSeekBar seekBar, int value, boolean fromUser) {
                rssiThreshold = -(100-value);
                getViewController().onRssiFilterThresholdChanged(rssiThreshold);
                filterValueTextView.setText(String.valueOf(rssiThreshold) + " dBm");
            }

            @Override
            public void onStartTrackingTouch(DiscreteSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(DiscreteSeekBar seekBar) {

            }
        });

        devicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                getViewController().onConnectionRequested(adapter.getItem(i));
            }
        });

        filterEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                Log.e(TAG,"charSequence: "+charSequence);
                nameMacFilter = charSequence.toString();
                filterDevices(nameMacFilter);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        filterEditText.setText(nameMacFilter);
        getViewController().hideKeyboardRequested();

    }

    private void filterDevices(String filter) {

        for(int i = 0; i<removedDevices.size(); i++) {
            BleDevice device = removedDevices.get(i);
            Log.e(TAG,"should BRING BACK device: "+device.getDevice().getName());
            if( filter.isEmpty()
                    || (device.getDevice().getName() != null && device.getDevice().getName().contains(filter))
                    || (device.getDevice().getAddress().contains(filter))){
                adapter.add(device);
                removedDevices.remove(i);
                i--;
                Log.e(TAG,"YES");
            } else {
                Log.e(TAG,"NO");
            }
        }

        for(int i = 0; i<adapter.getCount(); i++) {
            BleDevice device = adapter.getItem(i);
            Log.e(TAG,"should REMOVE device: "+device.getDevice().getName());
            Log.i(TAG,"filter empty: "+filter.isEmpty());
            if(!filter.isEmpty()
                    && (device.getDevice().getName()==null
                        || ( (device.getDevice().getName()!=null && !device.getDevice().getName().contains(filter))
                            && !device.getDevice().getAddress().contains(filter)))) {
                removedDevices.add(device);
                adapter.remove(device);
                i--;
                Log.e(TAG,"YES");
            } else {
                Log.e(TAG,"NO");
            }
        }

        adapter.notifyDataSetChanged();

    }

    @Override
    public void onPause() {
        super.onPause();
        getViewController().onNameMacFilterChanged(nameMacFilter);
        getViewController().onScannedDevicesFragmentPaused();
    }

    @Override
    public void onResume() {
        super.onResume();
        getViewController().onScannedDevicesFragmentResumed();
    }

    public void setRssiThreshold(int rssiThreshold) {
        this.rssiThreshold = rssiThreshold;
    }

    private void initLogList() {
        adapter = new ScannedDevicesAdapter(getContext(),
                R.layout.list_item_scanned_device);
        devicesList.setAdapter(adapter);
    }

    public void updateDevice(BleDevice device) {
        if (device.getRssi() >= rssiThreshold
                && (nameMacFilter.isEmpty()
                    || ( (device.getDevice().getName() != null
                            && device.getDevice().getName().contains(nameMacFilter) )
                        || device.getDevice().getAddress().contains(nameMacFilter) )))
            adapter.addOrUpdate(device);
    }

    public void requestScanning() {
        getViewController().onScanningRequested();
    }

    public void onScanningStarted() {
        devicesList.setEnabled(false);
        scanningAnimationView.setVisibility(View.VISIBLE);
        adapter.clear();
        adapter.setColor(getResources().getColor(R.color.colorDivider));
    }

    public void onScanningFinished() {
        devicesList.setEnabled(true);
        scanningAnimationView.setVisibility(View.GONE);
        adapter.setColor(getResources().getColor(R.color.colorSecondaryText));
    }

    public void setNameMacFilter(String macNameFilter) {
        nameMacFilter = macNameFilter;
    }
}
