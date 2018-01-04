package com.ar.openClimbAR.ViewTopoActivity;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.tools.ArUtils;
import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.OrientationPointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.tools.TopoButtonClickListener;
import com.ar.openClimbAR.utils.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xyz on 12/27/17.
 */

public class ArViewManager {
    private Map<PointOfInterest, View> toDisplay = new HashMap<>(); //Visible POIs
    private final ViewGroup container;
    private final Activity activity;

    public ArViewManager(Activity pActivity) {
        this.activity = pActivity;
        this.container = activity.findViewById(R.id.augmentedReality);
    }

    private void deleteViewElement(View button) {
        container.removeView(button);
    }

    private View addViewElementFromTemplate(PointOfInterest poi) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.topo_display_button, null);
        newViewElement.setOnClickListener(new TopoButtonClickListener(activity, poi));

        float remapGradeScale = ArUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.getLevelId());
        ((ImageButton)newViewElement).setImageTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));

        container.addView(newViewElement);

        return newViewElement;
    }

    private void updateViewElement(View pButton, PointOfInterest poi, OrientationPointOfInterest observer) {
        int size = calculateSizeInDPI(poi.distanceMeters);
        int sizeX = (int)(size*0.5);
        int sizeY = size;

        float[] pos = ArUtils.getXYPosition(poi.difDegAngle, observer.degPitch, observer.degRoll, observer.screenRotation, sizeX, sizeY, observer.horizontalFieldOfViewDeg);
        float xPos = pos[0];
        float yPos = pos[1];
        float roll = pos[2];

        pButton.getLayoutParams().height = sizeY;
        pButton.getLayoutParams().width = sizeX;

        pButton.setX(xPos);
        pButton.setY(yPos);
        pButton.setRotation(roll);

        pButton.setRotationX(observer.degPitch);

        pButton.bringToFront();
        pButton.requestLayout();
    }

    private int calculateSizeInDPI(float distance) {
        int result = Math.round(ArUtils.remapScale(Constants.MIN_DISTANCE_METERS, Constants.MAX_DISTANCE_METERS, Constants.UI_MIN_SCALE, Constants.UI_MAX_SCALE, distance));

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                result, activity.getResources().getDisplayMetrics());
    }

    public void removePOIFromView (PointOfInterest poi) {
        if (toDisplay.containsKey(poi)){
            deleteViewElement(toDisplay.get(poi));
            toDisplay.remove(poi);
        }
    }

    public void addOrUpdatePOIToView(PointOfInterest poi, OrientationPointOfInterest observer) {
        if (!toDisplay.containsKey(poi)) {
            toDisplay.put(poi, addViewElementFromTemplate(poi));
        }
        updateViewElement(toDisplay.get(poi), poi, observer);
    }
}
