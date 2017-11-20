package com.ubudu.iot.sample.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.PopupMenu;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
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
    CheckBox byteCountMode;
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
                    messageEditText.setText("");
                    messageEditText.setEnabled(false);
                    sendDataButton.setVisibility(View.GONE);
                } else {
                    messageEditText.setEnabled(true);
                    sendDataButton.setVisibility(View.VISIBLE);
                }
            }
        });

        byteCountMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    messageEditText.setText("");
                    messageEditText.setHint("Type message size");
                    messageEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                } else {
                    messageEditText.setText("");
                    messageEditText.setHint("Type string message");
                    messageEditText.setInputType(InputType.TYPE_CLASS_TEXT);
                }
            }
        });

        streamCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    streamDelayEditText.setVisibility(View.VISIBLE);
                    streamIterationsEditText.setVisibility(View.VISIBLE);
                    iterationsTextView.setVisibility(View.VISIBLE);
                    iterationDelayTextView.setVisibility(View.VISIBLE);
                } else {
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
        sendDataButton.setEnabled(false);
        String output = "";
        if(!byteCountMode.isChecked()) {
            output = messageEditText.getText().toString();
        } else {
            StringBuilder data = new StringBuilder();
            for(int i=0; i<Integer.parseInt(messageEditText.getText().toString()); i++) {
                data.append("a");
            }
            output = data.toString();
        }

        if(output.isEmpty()) {
            ToastUtil.showToast(getContext(),"message is empty");
            return;
        }

        if(streamCheckBox.isChecked()) {
            final String message = output;
            final int delay = Integer.parseInt(streamDelayEditText.getText().toString());
            final int iterations = Integer.parseInt(streamIterationsEditText.getText().toString());

            new Thread(new Runnable() {
                @Override
                public void run() {
                    for(int i = 0; i<iterations; i++) {
                        getViewController().onSendMessageRequested(message);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        } else {
            getViewController().onSendMessageRequested(output);
        }

    }

    public void onDataReceived(String msg) {
        responseTextView.setText(msg);
        if(loopBackModeCheckBox.isChecked()) {
            messageEditText.setText(msg);
            sendDataButton.callOnClick();
        }
    }

    public void onMessageSendingFinished() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendDataButton.setEnabled(true);
            }
        });
    }

}
