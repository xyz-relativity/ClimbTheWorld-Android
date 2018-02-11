package com.ar.climbing.activitys.ViewTopoActivity;

import android.content.Context;
import android.graphics.Point;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.ar.climbing.R;
import com.ar.climbing.utils.AugmentedRealityUtils;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.PointOfInterest;
import com.ar.climbing.utils.Quaternion;
import com.ar.climbing.utils.TopoButtonClickListener;
import com.ar.climbing.utils.Vector2d;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xyz on 12/27/17.
 */

public class AugmentedRealityViewManager {
    private Map<PointOfInterest, View> toDisplay = new HashMap<>(); //Visible POIs
    private final ViewGroup container;
    private final AppCompatActivity activity;
    public Vector2d rotateDisplaySize = new Vector2d(0,0);
    private double maxDistance;

    public AugmentedRealityViewManager(AppCompatActivity pActivity) {
        this.activity = pActivity;
        this.container = activity.findViewById(R.id.augmentedReality);

        WindowManager wm = (WindowManager) container.getContext().getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);

        rotateDisplaySize = new Vector2d(size.x, size.y);
        maxDistance = Globals.globalConfigs.getMaxDistanceVisibleNodes();
    }

    private void deleteViewElement(View button) {
        container.removeView(button);
    }

    private View addViewElementFromTemplate(PointOfInterest poi) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.topo_display_button, null);
        newViewElement.setOnClickListener(new TopoButtonClickListener(activity, poi));

        ((ImageButton)newViewElement).setImageTintList(Globals.gradeToColorState(poi.getLevelId()));

        container.addView(newViewElement);

        return newViewElement;
    }

    private void updateViewElement(View pButton, PointOfInterest poi) {
        int size = calculateSizeInDPI(poi.distanceMeters);
        Vector2d objSize = new Vector2d(size * 0.3d, size);

        Quaternion pos = AugmentedRealityUtils.getXYPosition(poi.difDegAngle, Globals.observer.degPitch,
                Globals.observer.degRoll, Globals.observer.screenRotation, objSize,
                Globals.observer.fieldOfViewDeg, rotateDisplaySize);

        float xPos = (float)pos.x;
        float yPos = (float)pos.y;
        float roll = (float)pos.w;

        pButton.getLayoutParams().width = (int)objSize.x;
        pButton.getLayoutParams().height = (int)objSize.y;

        pButton.setX(xPos);
        pButton.setY(yPos);
        pButton.setRotation(roll);

        pButton.bringToFront();
    }

    private int calculateSizeInDPI(double distance) {
        int result = (int) AugmentedRealityUtils.remapScale((int)Configs.ConfigKey.maxNodesShowDistanceLimit.minValue,
                (int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue,
                Constants.UI_MAX_SCALE,
                Constants.UI_MIN_SCALE, distance);

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
