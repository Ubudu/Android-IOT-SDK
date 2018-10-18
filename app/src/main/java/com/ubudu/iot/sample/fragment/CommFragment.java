package com.ubudu.iot.sample.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ubudu.iot.sample.R;
import com.ubudu.iot.sample.util.ToastUtil;
import com.ubudu.iot.util.LongDataProtocolV3;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by mgasztold on 08/09/2017.
 */

public class CommFragment extends BaseFragment {

    public static final String TAG = CommFragment.class.getCanonicalName();

    @BindView(R.id.send_message_edit_text)
    EditText messageEditText;
    @BindView(R.id.response_text_view)
    TextView responseTextView;
    @BindView(R.id.send_data_button)
    Button sendDataButton;
    @BindView(R.id.disconnect_button)
    Button disconnectButton;
    @BindView(R.id.loopback_mode)
    CheckBox loopBackModeCheckBox;
    @BindView(R.id.set_bytes_count_mode)
    CheckBox ascendingBytesModeCheckBox;
    @BindView(R.id.stream)
    CheckBox streamCheckBox;
    @BindView(R.id.stream_delay_edit_text)
    EditText streamDelayEditText;
    @BindView(R.id.stream_iterations_edit_text)
    EditText streamIterationsEditText;
    @BindView(R.id.iterations_text_view)
    TextView iterationsTextView;
    @BindView(R.id.iteration_delay_text_view)
    TextView iterationDelayTextView;
    @BindView(R.id.message_text_view)
    TextView messageTitleTextView;
    @BindView(R.id.bytes_count_edit_text)
    EditText bytesCountEditText;
    @BindView(R.id.bytes_count_text_view)
    TextView bytesCountTextView;
    @BindView(R.id.communication_layout)
    LinearLayout communicationLayout;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RelativeLayout mRootView = (RelativeLayout) inflater.inflate(R.layout.fragment_comm, container, false);
        ButterKnife.bind(this, mRootView);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        configureUI();
    }

    @Override
    public void onPause() {
        getViewController().onCommFragmentPaused();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        getViewController().onCommFragmentResumed();
    }

    private void configureUI() {

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnectButton.setEnabled(false);
                getViewController().onDisconnectRequested();
            }
        });

        loopBackModeCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    ascendingBytesModeCheckBox.setChecked(false);
                    streamCheckBox.setChecked(false);
                    ascendingBytesModeCheckBox.setChecked(false);

                    ascendingBytesModeCheckBox.setEnabled(false);
                    streamCheckBox.setEnabled(false);

                    messageEditText.setText("");
                    messageEditText.setEnabled(false);
                    sendDataButton.setVisibility(View.GONE);
                } else {
                    messageEditText.setEnabled(true);
                    sendDataButton.setVisibility(View.VISIBLE);

                    ascendingBytesModeCheckBox.setEnabled(true);
                    streamCheckBox.setEnabled(true);
                }
            }
        });

        ascendingBytesModeCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    bytesCountTextView.setVisibility(View.VISIBLE);
                    bytesCountEditText.setVisibility(View.VISIBLE);
                    messageEditText.setEnabled(false);
                    messageEditText.setText(getAscendingBytesDataString(Integer.parseInt(bytesCountEditText.getText().toString())));

                    loopBackModeCheckBox.setEnabled(false);

                    bytesCountEditText.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            try {
                                messageEditText.setText(getAscendingBytesDataString(Integer.parseInt(bytesCountEditText.getText().toString())));
                            } catch(Exception e) {
                                messageEditText.setText(getAscendingBytesDataString(1));
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable s) {

                        }
                    });

                } else {
                    if(!streamCheckBox.isChecked()) {
                        loopBackModeCheckBox.setEnabled(true);
                    }
                    bytesCountTextView.setVisibility(View.GONE);
                    bytesCountEditText.setVisibility(View.GONE);
                    messageEditText.setEnabled(true);
                    messageTitleTextView.setText(getResources().getString(R.string.string_message));
                    messageEditText.setText("");
                }
            }
        });

        streamCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    loopBackModeCheckBox.setEnabled(false);
                    streamDelayEditText.setVisibility(View.VISIBLE);
                    streamIterationsEditText.setVisibility(View.VISIBLE);
                    iterationsTextView.setVisibility(View.VISIBLE);
                    iterationDelayTextView.setVisibility(View.VISIBLE);
                } else {
                    if(!ascendingBytesModeCheckBox.isChecked()) {
                        loopBackModeCheckBox.setEnabled(true);
                    }
                    streamDelayEditText.setVisibility(View.GONE);
                    streamIterationsEditText.setVisibility(View.GONE);
                    iterationsTextView.setVisibility(View.GONE);
                    iterationDelayTextView.setVisibility(View.GONE);
                }
            }
        });

        sendDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestSend();
            }
        });

    }

    public void requestSend() {
        String output = messageEditText.getText().toString();
        if(output.isEmpty()) {
            ToastUtil.showToast(getContext(),"message is empty");
            return;
        }

        if(streamCheckBox.isChecked()) {
            final int iterations = Integer.parseInt(streamIterationsEditText.getText().toString());
            if(iterations > 0) {
                sendDataButton.setEnabled(false);
                final String message = output;
                final int delay = Integer.parseInt(streamDelayEditText.getText().toString());

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < iterations; i++) {
                            getViewController().onSendMessageRequested(message.getBytes(), LongDataProtocolV3.DATA_TYPE_STRING);
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
            }
        } else {
            getViewController().onSendMessageRequested(output.getBytes(), LongDataProtocolV3.DATA_TYPE_STRING);
        }

    }

    private String getAscendingBytesDataString(int count) {
        StringBuilder output = new StringBuilder();
        int startIndex = 48;
        int endIndex = 126;
        int a = 0;
        for(int i=0; i<count; i++) {
            int asciiIndex = startIndex+i-(endIndex-startIndex+1)*a;
            if(asciiIndex == endIndex+1){
                a++;
                asciiIndex = startIndex+i-(endIndex-startIndex+1)*a;
            }
            output.append(Character.toString((char)asciiIndex));
        }
        return output.toString();
    }

    public void onDataReceived(String msg) {
        responseTextView.setText(msg);
        if (loopBackModeCheckBox.isChecked()) {
            messageEditText.setText(msg);
            sendDataButton.callOnClick();
        }
    }

    public void onDataSent(final String sentData) {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(streamCheckBox.isChecked()) {
                    streamIterationsEditText.setText(String.valueOf(Integer.parseInt(streamIterationsEditText.getText().toString())-1));
                    if(Integer.parseInt(streamIterationsEditText.getText().toString())==0){
                        sendDataButton.setEnabled(true);
                    }
                }
            }
        });

        if(sentData==null)
            ToastUtil.showToast(getContext(),"Data sending error");
        else {
            try {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messageEditText.setText(sentData);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
