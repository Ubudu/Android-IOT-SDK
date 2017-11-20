package com.ubudu.iot.sample.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ubudu.iot.sample.MainActivity;
import com.ubudu.iot.sample.R;
import com.ubudu.iot.sample.model.Option;
import com.ubudu.iot.sample.util.OptionsListAdapter;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by mgasztold on 08/09/2017.
 */

public class OptionSelectionFragment extends BaseFragment {

    public static final String TAG = OptionSelectionFragment.class.getCanonicalName();

    private MainActivity.ChooseListener listener;
    private List<Option> options;
    private String optionsTitle;
    private OptionsListAdapter adapter;
    private Option selectedOption;

    @BindView(R.id.options_title)
    TextView title;
    @BindView(R.id.options_list)
    ListView listview;

    public void setListener(MainActivity.ChooseListener listener) {
        this.listener = listener;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout mRootView = (LinearLayout) inflater.inflate(R.layout.fragment_option_selection, container, false);
        ButterKnife.bind(this, mRootView);
        return mRootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if(selectedOption == null)
            listener.onChooserCancelled();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        title.setText(optionsTitle);

        // init list view
        adapter = new OptionsListAdapter(getContext(),
                R.layout.list_item_option);
        listview.setAdapter(adapter);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectedOption = adapter.getItem(i);
                listener.onOptionChosen(selectedOption.title);
            }
        });

        adapter.setOptions(options);
    }

    public void setTitle(String title) {
        this.optionsTitle = title;
    }
}
