package com.ubudu.iot.sample.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.ubudu.iot.sample.R;
import com.ubudu.iot.sample.model.LogItem;

import java.util.ArrayList;

public class LogListAdapter extends ArrayAdapter<LogItem> {

	private final int MAX_LOGS_SIZE = 1000;

	private ArrayList<LogItem> msgs = new ArrayList<>();

	@Override
	public void add(LogItem object) {
		msgs.add(object);

		if(msgs.size()>MAX_LOGS_SIZE)
			msgs.remove(0);

		super.add(object);
	}

	public void putLogs(ArrayList<LogItem> m){
		msgs.clear();
		msgs.addAll(m);
	}
	
	public LogListAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}
	
	public int getCount() {
		return this.msgs.size();
	}

	public LogItem getItem(int index) {
		return this.msgs.get(index);
	}

	public void recoverMsgs(ArrayList<LogItem> c){
		msgs = c;
	}
	
	public ArrayList<LogItem> getElements(){
		return msgs;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
	
		View view = convertView;
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.list_item_peripheral_log, parent, false);
		}

	    TextView logmsg = (TextView) view.findViewById(R.id.text);
		LogItem logItem = getItem(position);
	    logmsg.setText(logItem.getLogMessage());

		switch (logItem.getType()){
			case LogItem.TYPE_MESSAGE:
				logmsg.setTextColor(getContext().getResources().getColor(R.color.colorSecondaryText));
				break;
			case LogItem.TYPE_ERROR:
				logmsg.setTextColor(getContext().getResources().getColor(R.color.errorColor));
				break;
		}

	    return view;
	}

}