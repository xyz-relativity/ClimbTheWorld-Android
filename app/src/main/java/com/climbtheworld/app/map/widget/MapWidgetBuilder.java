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

public class MapWidgetBuilder {
    private MapViewWidget mapWidget;

    public static MapWidgetBuilder getBuilder(final AppCompatActivity parent, boolean startAtVirtualCamera) {
        return new MapWidgetBuilder(parent,startAtVirtualCamera);
    }

    private MapWidgetBuilder(final AppCompatActivity parent, boolean startAtVirtualCamera) {
        this.mapWidget = new MapViewWidget(parent, parent.findViewById(R.id.mapViewContainer), startAtVirtualCamera);
        final MapBoxTileSource mapBoxTileSource = new MapBoxTileSource();
        mapBoxTileSource.retrieveAccessToken(parent);
        mapBoxTileSource.retrieveMapBoxMapId(parent);

        mapWidget.setTileSource(TileSourceFactory.OpenTopo, TileSourceFactory.MAPNIK, mapBoxTileSource);
        mapWidget.setClusterOnClickListener(new MapViewWidget.MapMarkerClusterClickListener() {
            @Override
            public void onClusterCLick(StaticCluster cluster) {
                NodeDialogBuilder.showClusterDialog(parent, cluster);
            }
        });
        mapWidget.setShowObserver(true, null);
        mapWidget.setRotationMode(Configs.instance(parent).getBoolean(Configs.ConfigKey.mapViewCompassOrientation));
        mapWidget.setUseDataConnection(Globals.allowMapDownload(parent));
    }

    public MapWidgetBuilder setTapMarker(FolderOverlay tapMarkersFolder) {
        mapWidget.setTapMarker(tapMarkersFolder);
        return this;
    }

    public MapWidgetBuilder enableAutoDownload() {
        mapWidget.enableAutoLoad();
        return this;
    }

    public MapWidgetBuilder setFilterMethod (MapViewWidget.FilterType method) {
        mapWidget.setFilterMethod(method);
        return this;
    }

    public MapWidgetBuilder setMapAutoFollow(boolean autoFollow) {
        mapWidget.setMapAutoFollow(autoFollow);
        return this;
    }

    public MapWidgetBuilder enableMinimap() {
        mapWidget.setMinimap(true);
        return this;
    }

    public MapWidgetBuilder enableRotateGesture() {
        mapWidget.setRotateGesture(true);
        return this;
    }

    public MapViewWidget build() {
        return mapWidget;
    }
}
