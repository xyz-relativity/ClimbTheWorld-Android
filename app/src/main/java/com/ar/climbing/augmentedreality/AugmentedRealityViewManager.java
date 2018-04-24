package com.ar.climbing.augmentedreality;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.GeoNodeDialogBuilder;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.Quaternion;
import com.ar.climbing.utils.Vector2d;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xyz on 12/27/17.
 */

public class AugmentedRealityViewManager {
    private Map<GeoNode, View> toDisplay = new HashMap<>(); //Visible POIs
    private final ViewGroup container;
    private final AppCompatActivity activity;
    private Vector2d containerSize = new Vector2d(0, 0);

    public AugmentedRealityViewManager(AppCompatActivity pActivity) {
        this.activity = pActivity;
        this.container = activity.findViewById(R.id.arContainer);
    }

    public Vector2d getContainerSize () {
        return containerSize;
    }

    public void postInit()
    {
        containerSize = new Vector2d(container.getMeasuredWidth(), container.getMeasuredHeight());
    }

    private void deleteViewElement(View button) {
        container.removeView(button);
    }

    private View addViewElementFromTemplate(final GeoNode poi) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.topo_display_button, container, false);

        newViewElement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GeoNodeDialogBuilder.buildNodeInfoDialog(activity, poi).show();
            }
        });

        ((ImageButton)newViewElement).setImageTintList(Globals.gradeToColorState(poi.getLevelId()));

        container.addView(newViewElement);

        return newViewElement;
    }

    private void updateViewElement(View pButton, GeoNode poi) {
        double size = calculateSizeInDPI(poi.distanceMeters);
        Vector2d objSize = new Vector2d(size * 0.2d, size);

        Quaternion pos = AugmentedRealityUtils.getXYPosition(poi.difDegAngle, Globals.virtualCamera.degPitch,
                Globals.virtualCamera.degRoll, Globals.virtualCamera.screenRotation, objSize,
                Globals.virtualCamera.fieldOfViewDeg, getContainerSize());

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

    private double calculateSizeInDPI(double distance) {
        double scale;
        if (distance > Constants.UI_CLOSE_TO_FAR_THRASHHOLE) {
            scale = AugmentedRealityUtils.remapScale(
                    Constants.UI_CLOSE_TO_FAR_THRASHHOLE,
                    (int) Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue,
                    Constants.UI_FAR_MAX_SCALE,
                    Constants.UI_FAR_MIN_SCALE, distance);
        } else {
            scale = AugmentedRealityUtils.remapScale(
                    (int) Configs.ConfigKey.maxNodesShowDistanceLimit.minValue,
                    Constants.UI_CLOSE_TO_FAR_THRASHHOLE,
                    Constants.UI_CLOSEUP_MAX_SCALE,
                    Constants.UI_CLOSEUP_MIN_SCALE, distance);
        }

        return AugmentedRealityUtils.sizeToDPI(activity, (float)scale);
    }

    public void removePOIFromView (GeoNode poi) {
        if (toDisplay.containsKey(poi)){
            deleteViewElement(toDisplay.get(poi));
            toDisplay.remove(poi);
        }
    }

    public void addOrUpdatePOIToView(GeoNode poi) {
        if (!toDisplay.containsKey(poi)) {
            toDisplay.put(poi, addViewElementFromTemplate(poi));
        }
        updateViewElement(toDisplay.get(poi), poi);
    }
}
