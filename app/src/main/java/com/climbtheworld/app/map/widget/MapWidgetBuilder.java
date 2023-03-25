package com.climbtheworld.app.map.widget;

import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.dialogs.ClusterDialog;

import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.overlay.Marker;

import bugfix.osmdroid.tileprovider.tilesource.MapBoxTileSource;

public class MapWidgetBuilder {
	private final MapViewWidget mapWidget;

	public static MapWidgetBuilder getBuilder(final AppCompatActivity parent, boolean startAtVirtualCamera) {
		return new MapWidgetBuilder(parent, startAtVirtualCamera);
	}

	private MapWidgetBuilder(final AppCompatActivity parent, boolean startAtVirtualCamera) {
		this.mapWidget = new MapViewWidget(parent, parent.findViewById(R.id.mapViewContainer), startAtVirtualCamera);
		final MapBoxTileSource mapBoxTileSource = new MapBoxTileSource(parent.getString(R.string.MAPBOX_MAPID), parent.getString(R.string.MAPBOX_ACCESS_TOKEN));
		mapBoxTileSource.enableHighDPI(true);

		mapWidget.setTileSource(TileSourceFactory.OpenTopo, TileSourceFactory.MAPNIK, mapBoxTileSource);
		mapWidget.setClusterOnClickListener(new MapViewWidget.MapMarkerClusterClickListener() {
			@Override
			public void onClusterCLick(StaticCluster cluster) {
				ClusterDialog.showClusterDialog(parent, cluster);
			}
		});
		mapWidget.setShowObserver(true, null);
		mapWidget.setRotationMode(Configs.instance(parent).getInt(Configs.ConfigKey.mapViewCompassOrientation, parent.getClass().getSimpleName()));
		mapWidget.setUseDataConnection(Globals.allowMapDownload(parent));
	}

	private Marker initTapMarker() {
		Drawable nodeIcon = ResourcesCompat.getDrawable(mapWidget.parentRef.get().getResources(), R.drawable.ic_tap_marker, null);

		Marker tapMarker = new Marker(mapWidget.getOsmMap());
		tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
		tapMarker.setIcon(nodeIcon);
		tapMarker.setImage(nodeIcon);
		tapMarker.setInfoWindow(null);
		tapMarker.setPanToView(false);
		tapMarker.setPosition(Globals.geoNodeToGeoPoint(Globals.virtualCamera));

		//put into FolderOverlay list
		return tapMarker;
	}

	public MapWidgetBuilder enableTapMarker() {
		mapWidget.setTapMarker(initTapMarker());
		return this;
	}

	public MapWidgetBuilder enableAutoDownload() {
		mapWidget.enableAutoLoad();
		return this;
	}

	public MapWidgetBuilder setFilterMethod(MapViewWidget.FilterType method) {
		mapWidget.setFilterMethod(method);
		return this;
	}

	public MapWidgetBuilder setMapAutoFollow(boolean autoFollow) {
		mapWidget.setMapAutoFollow(autoFollow);
		return this;
	}

	public MapWidgetBuilder enableMinimap() {
		mapWidget.setMinimap(true, -1);
		return this;
	}

	public MapWidgetBuilder enableMinimap(int zoomDiff) {
		mapWidget.setMinimap(true, zoomDiff);
		return this;
	}

	public MapWidgetBuilder setZoom(double zoom) {
		mapWidget.setZoom(zoom);
		return this;
	}

	public MapViewWidget build() {
		return mapWidget;
	}
}
