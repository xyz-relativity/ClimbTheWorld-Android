package com.climbtheworld.app.widgets;

import android.app.Activity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.NodeDialogBuilder;

import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.tileprovider.tilesource.MapQuestTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.overlay.FolderOverlay;

public class MapWidgetFactory {
    public static MapViewWidget buildMapView(final Activity pActivity) {
        return buildMapView(pActivity, null);
    }

    public static MapViewWidget buildMapView(final Activity pActivity, FolderOverlay tapMarkersFolder) {
        MapViewWidget mapWidget;
        if (tapMarkersFolder == null) {
            mapWidget = new MapViewWidget(pActivity, pActivity.findViewById(R.id.mapViewContainer), Globals.poiToGeoPoint(Globals.virtualCamera));
        } else {
            mapWidget = new MapViewWidget(pActivity, pActivity.findViewById(R.id.mapViewContainer), Globals.poiToGeoPoint(Globals.virtualCamera), tapMarkersFolder);
        }

        mapWidget.setTileSource(TileSourceFactory.OpenTopo, TileSourceFactory.MAPNIK, new MapQuestTileSource(pActivity));
        mapWidget.setClusterOnClickListener(new MapViewWidget.MapMarkerClusterClickListener() {
            @Override
            public void onClusterCLick(StaticCluster cluster) {
                NodeDialogBuilder.showClusterDialog(pActivity, cluster);
            }
        });
        mapWidget.setShowObserver(true, null);
        mapWidget.setMapAutoFollow(true);
        mapWidget.setUseDataConnection(Globals.allowMapDownload(pActivity.getApplicationContext()));

        return mapWidget;
    }
}
