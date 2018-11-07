package com.climbtheworld.app.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.climbtheworld.app.R;

public class ViewUtils {

    public static View buildCustomSwitch(Context parent, int name, int description) {
        LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.list_item_node_type, null);
        v.findViewById(R.id.imageIcon).setVisibility(View.GONE);
        TextView textView = v.findViewById(R.id.textTypeName);
        textView.setText(name);
        textView = v.findViewById(R.id.textTypeDescription);
        textView.setText(description);

        return v;
    }
}
