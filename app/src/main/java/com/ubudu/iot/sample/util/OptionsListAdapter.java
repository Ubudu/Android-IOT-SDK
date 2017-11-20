package com.ubudu.iot.sample.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.ubudu.iot.sample.R;
import com.ubudu.iot.sample.model.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mgasztold on 08/09/2017.
 */

public class OptionsListAdapter extends ArrayAdapter<Option> {

    private final ArrayList<Option> options = new ArrayList<Option>();

    public OptionsListAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public int getCount() {
        return this.options.size();
    }

    public Option getItem(int index) {
        return this.options.get(index);
    }

    @Override
    public void add(Option object) {
        options.add(object);
        super.add(object);
    }

    @Override
    public void clear() {
        super.clear();
        options.clear();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolderItem viewHolderItem;

        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.list_item_option, parent, false);

            // well set up the ViewHolder
            viewHolderItem = new ViewHolderItem();
            viewHolderItem.title = (TextView) view.findViewById(R.id.option_title);
            viewHolderItem.description = (TextView) view.findViewById(R.id.option_description);

            // store the holder with the view.
            view.setTag(viewHolderItem);
        } else {
            // we've just avoided calling findViewById() on resource everytime
            // just use the viewHolder
            viewHolderItem = (ViewHolderItem) view.getTag();
        }

        Option option = getItem(position);
        viewHolderItem.title.setText(option.title);
        if(option.description==null || option.description.equals(""))
            viewHolderItem.description.setHeight(0);
        else
            viewHolderItem.description.setText(option.description);


        return view;
    }

    public void setOptions(List<Option> options) {
        this.options.clear();
        this.options.addAll(options);
        notifyDataSetChanged();
    }

    private static class ViewHolderItem {
        TextView title;
        TextView description;
    }
}
