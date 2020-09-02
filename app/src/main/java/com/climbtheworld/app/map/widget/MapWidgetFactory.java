package com.climbtheworld.app.map.widget;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.dialogs.NodeDialogBuilder;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.overlay.FolderOverlay;

import androidx.appcompat.app.AppCompatActivity;

public class MapWidgetFactory {
    public static MapViewWidget buildMapView(final AppCompatActivity pActivity) {
        return buildMapView(pActivity, null, false);
    }

    public static MapViewWidget buildMapView(final AppCompatActivity pActivity, boolean startAtVirtualCamera) {
        return buildMapView(pActivity, null, startAtVirtualCamera);
    }

    public static MapViewWidget buildMapView(final AppCompatActivity pActivity, FolderOverlay tapMarkersFolder) {
        return buildMapView(pActivity, tapMarkersFolder, false);
    }

    public static MapViewWidget buildMapView(final AppCompatActivity pActivity, FolderOverlay tapMarkersFolder, boolean startAtVirtualCamera) {
        MapViewWidget mapWidget;
        if (tapMarkersFolder == null) {
            mapWidget = new MapViewWidget(pActivity, pActivity.findViewById(R.id.mapViewContainer), startAtVirtualCamera);
        } else {
            mapWidget = new MapViewWidget(pActivity, pActivity.findViewById(R.id.mapViewContainer), startAtVirtualCamera, tapMarkersFolder);
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
        mapWidget.setRotationMode(Configs.instance(pActivity).getBoolean(Configs.ConfigKey.mapViewCompassOrientation));
        mapWidget.setUseDataConnection(Globals.allowMapDownload(pActivity));

        return mapWidget;
    }
}
