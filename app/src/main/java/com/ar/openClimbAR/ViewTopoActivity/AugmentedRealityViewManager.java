package com.ar.openClimbAR.ViewTopoActivity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.tools.TopoButtonClickListener;
import com.ar.openClimbAR.utils.AugmentedRealityUtils;
import com.ar.openClimbAR.utils.Constants;
import com.ar.openClimbAR.utils.Globals;
import com.ar.openClimbAR.utils.Quaternion;
import com.ar.openClimbAR.utils.Vector2d;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xyz on 12/27/17.
 */

public class AugmentedRealityViewManager {
    private Map<PointOfInterest, View> toDisplay = new HashMap<>(); //Visible POIs
    private final ViewGroup container;
    private final AppCompatActivity activity;

    public AugmentedRealityViewManager(AppCompatActivity pActivity) {
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

        float remapGradeScale = (float) AugmentedRealityUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                1f,
                0f,
                poi.getLevelId());
        ((ImageButton)newViewElement).setImageTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));

        container.addView(newViewElement);

        return newViewElement;
    }

    private void updateViewElement(View pButton, PointOfInterest poi) {
        int size = calculateSizeInDPI(poi.distanceMeters);
        Vector2d objSize = new Vector2d(size * 0.5f, size);

        Quaternion pos = AugmentedRealityUtils.getXYPosition(poi.difDegAngle, Globals.observer.degPitch,
                Globals.observer.degRoll, Globals.observer.screenRotation, objSize,
                Globals.observer.fieldOfViewDeg, Globals.displaySizeAfterOrientation);

        float xPos = (float)pos.x;
        float yPos = (float)pos.y;
        float roll = (float)pos.w;

        pButton.getLayoutParams().width = (int)objSize.x;
        pButton.getLayoutParams().height = (int)objSize.y;

        pButton.setX(xPos);
        pButton.setY(yPos);
        pButton.setRotation(roll);

//        pButton.setRotationX(Globals.observer.degPitch);

        pButton.bringToFront();
        pButton.requestLayout();
    }

    private int calculateSizeInDPI(double distance) {
        int result = (int) AugmentedRealityUtils.remapScale(Constants.MIN_DISTANCE_METERS, Constants.MAX_DISTANCE_METERS, Constants.UI_MAX_SCALE, Constants.UI_MIN_SCALE, distance);

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                result, activity.getResources().getDisplayMetrics());
    }

    public void removePOIFromView (PointOfInterest poi) {
        if (toDisplay.containsKey(poi)){
            deleteViewElement(toDisplay.get(poi));
            toDisplay.remove(poi);
        }
    }

    public void addOrUpdatePOIToView(PointOfInterest poi) {
        if (!toDisplay.containsKey(poi)) {
            toDisplay.put(poi, addViewElementFromTemplate(poi));
        }
        updateViewElement(toDisplay.get(poi), poi);
    }
}
