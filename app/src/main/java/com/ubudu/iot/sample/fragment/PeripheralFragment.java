package com.ubudu.iot.sample.fragment;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.ubudu.iot.sample.R;
import com.ubudu.iot.sample.model.LogItem;
import com.ubudu.iot.sample.util.LogListAdapter;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by mgasztold on 08/09/2017.
 */

public class PeripheralFragment extends BaseFragment {

    public static final String TAG = PeripheralFragment.class.getCanonicalName();

    @BindView(R.id.advertise_button)
    Button advertiseButton;
    @BindView(R.id.peripheral_logs_list)
    ListView logsList;

    private LogListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout mRootView = (LinearLayout) inflater.inflate(R.layout.fragment_peripheral, container, false);
        ButterKnife.bind(this, mRootView);
        return mRootView;
    }

    private void initLogList() {
        adapter = new LogListAdapter(getContext(),
                R.layout.list_item_peripheral_log);
        logsList.setAdapter(adapter);
    }

    public void log(String msg, String type) {
        Calendar calander = Calendar.getInstance();
        msg = "[" + calander.get(Calendar.HOUR_OF_DAY) + ":"
                + calander.get(Calendar.MINUTE) + ":"
                + calander.get(Calendar.SECOND) + "] " + msg;
        Log.i(TAG, msg);

        adapter.add(new LogItem(msg,type));
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            advertiseButton.setEnabled(false);
        }

        initLogList();

        if(getViewController().isAdvertising()){
            advertiseButton.setText(getResources().getString(R.string.stop_advertising));
        } else {
            advertiseButton.setText(getResources().getString(R.string.advertise));
        }

        advertiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAdvertising();
            }
        });

    }

    @Override
    public void onPause() {
        super.onPause();
        getViewController().onPeripheralFragmentPaused();
    }

    @Override
    public void onResume() {
        super.onResume();
        getViewController().onPeripheralFragmentResumed();
    }

    public void requestAdvertising() {
        advertiseButton.setEnabled(false);
        getViewController().onAdvertiseRequested();
    }

    public void onAdvertisingStarted() {
        advertiseButton.setEnabled(true);
        advertiseButton.setText(getResources().getString(R.string.stop_advertising));
        log("Advertising started",LogItem.TYPE_MESSAGE);
    }

    public void onAdvertisingStopped() {
        advertiseButton.setEnabled(true);
        advertiseButton.setText(getResources().getString(R.string.advertise));
        log("Advertising stopped",LogItem.TYPE_MESSAGE);
    }

    public void onAdvertisingError(String message) {
        advertiseButton.setEnabled(true);
        advertiseButton.setText(getResources().getString(R.string.advertise));
        log(message,LogItem.TYPE_MESSAGE);
    }

}
