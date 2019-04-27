package com.climbtheworld.app.converter;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;

import com.climbtheworld.app.utils.IPagerViewFragment;

public class GradeConverter extends ConverterFragment implements IPagerViewFragment {

    public GradeConverter(AppCompatActivity parent, @LayoutRes int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {

    }

    @Override
    public void onViewSelected() {

    }
}
