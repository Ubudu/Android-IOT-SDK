package com.ubudu.iot.sample.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.ubudu.iot.ble.BleDevice;
import com.ubudu.iot.sample.MainActivity;
import com.ubudu.iot.sample.model.Option;

import java.util.List;

/**
 * Created by mgasztold on 08/09/2017.
 */

public class BaseFragment extends Fragment {

    private ViewController mViewController;
    private FragmentActivity mActivity;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mActivity = (FragmentActivity) context;

        try {
            mViewController = (ViewController) context;

        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ViewController and UbuduInterface");
        }
    }

    public Activity getContextActivity() {
        return mActivity;
    }

    public ViewController getViewController() {
        return mViewController;
    }

    public interface ViewController {
        void onOptionSelectionFragmentRequested(String title, List<Option> options, MainActivity.ChooseListener listener);
        void onScannedDevicesFragmentRequested();
        void onScannedDevicesFragmentResumed();
        void onAdvertiseRequested();
        void onScanningRequested();
        void onSendMessageRequested(String message);
        void onScannedDevicesFragmentPaused();

        void onPeripheralFragmentRequested();
        void onPeripheralFragmentResumed();

        boolean isAdvertising();

        void onCommFragmentRequested();

        void onCommFragmentResumed();

        void onConnectionRequested(BleDevice device);

        void onDisconnectRequested();

        void onAudioSampleRateChanged(int sampleRateHz);

        void onRssiFilterThresholdChanged(int rssiThreshold);

        void onCommFragmentPaused();

        void onNameMacFilterChanged(String filter);

        void hideKeyboardRequested();
    }

}