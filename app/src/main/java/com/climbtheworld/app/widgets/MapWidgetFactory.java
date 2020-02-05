package com.climbtheworld.app.widgets;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.NodeDialogBuilder;

import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.overlay.FolderOverlay;

public class MapWidgetFactory {
    public static MapViewWidget buildMapView(final AppCompatActivity pActivity) {
        return buildMapView(pActivity, null);
    }

    public static MapViewWidget buildMapView(final AppCompatActivity pActivity, FolderOverlay tapMarkersFolder) {
        MapViewWidget mapWidget;
        if (tapMarkersFolder == null) {
            mapWidget = new MapViewWidget(pActivity, pActivity.findViewById(R.id.mapViewContainer), Globals.poiToGeoPoint(Globals.virtualCamera));
        } else {
            mapWidget = new MapViewWidget(pActivity, pActivity.findViewById(R.id.mapViewContainer), Globals.poiToGeoPoint(Globals.virtualCamera), tapMarkersFolder);
        }

        final MapBoxTileSource mapBoxTileSource = new MapBoxTileSource();
        mapBoxTileSource.retrieveAccessToken(pActivity);
        mapBoxTileSource.retrieveMapBoxMapId(pActivity);

        mapWidget.setTileSource(TileSourceFactory.OpenTopo, TileSourceFactory.MAPNIK, mapBoxTileSource);
        mapWidget.setClusterOnClickListener(new MapViewWidget.MapMarkerClusterClickListener() {
            @Override
            public void onClusterCLick(StaticCluster cluster) {
                NodeDialogBuilder.showClusterDialog(pActivity, cluster);
            }
        });
        mapWidget.setShowObserver(true, null);
        mapWidget.setMapAutoFollow(true);
        mapWidget.setRotationMode(Globals.globalConfigs.getBoolean(Configs.ConfigKey.mapViewCompassOrientation));
        mapWidget.setUseDataConnection(Globals.allowMapDownload(pActivity.getApplicationContext()));

        return mapWidget;
    }
}
