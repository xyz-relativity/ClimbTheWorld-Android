package com.climbtheworld.app.map.widget.climbing;

import static org.osmdroid.views.overlay.Marker.ANCHOR_CENTER;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.locationtech.jts.geom.Geometry;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.util.Map;

public class ClimbingAreaOverlayWidget extends ClimbingOverlayWidget {
	private static final double ZOOM_THRESHOLD = 18;
	private static final int AREA_FILL_COLOR = 0x200000ff;
	private static final double INFLATE_RATIO = 0.00005; //could be proportional to latitude, but the difference is negligible.

	private static final int ICON_TINT_COLOR = 0xeeffffff;
	private static final float TEXT_SIZE = 12;
	private static final int TEXT_COLOUR = Color.WHITE;
	private final static float TEXT_OUTLINE_STRENGTH = Globals.convertDpToPixel(3).floatValue();

	private final TextPaint iconTextPaint;
	private Drawable markerIcon = null;

	public ClimbingAreaOverlayWidget(ClimbingViewWidget climbingViewWidget) {
		super(climbingViewWidget);
		entityClimbingType = new OsmEntity.EntityClimbingType[]{OsmEntity.EntityClimbingType.area};

		iconTextPaint = new TextPaint();
		iconTextPaint.setColor(TEXT_COLOUR);
		iconTextPaint.setStrokeWidth(TEXT_OUTLINE_STRENGTH);
		iconTextPaint.setFakeBoldText(true);
		iconTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		iconTextPaint.setTextSize(Globals.convertDpToPixel(TEXT_SIZE).floatValue());
		iconTextPaint.setTextAlign(Paint.Align.CENTER);
		iconTextPaint.setAntiAlias(true);
	}

	boolean inVisibleZoom() {
		return osmMap.getZoomLevelDouble() <= ZOOM_THRESHOLD;
	}

	@Override
	protected PolygonWithCenter buildCollectionOverlay(OsmEntity collection, Map<Long, OsmNode> nodesCache) {
		Polygon polygon = null;
		Marker center = null;

		if (collection.osmType == OsmEntity.EntityOsmType.relation) {
			Geometry geometry = osmToConvexHullGeometry(nodesCache, ((OsmCollectionEntity)collection).osmNodes).buffer(INFLATE_RATIO);
			polygon = buildPolygon(collection, geometry);
			center = buildMarker(collection, geometry);
		}

		if (collection.osmType == OsmEntity.EntityOsmType.way) {
			Geometry geometry = osmToConvexHullGeometry(nodesCache, ((OsmCollectionEntity)collection).osmNodes);
			polygon = buildPolygon(collection, geometry);
			center = buildMarker(collection, geometry);
		}

		if (collection.osmType == OsmEntity.EntityOsmType.node) {
			assert collection instanceof OsmNode;
			center = buildMarker(collection, ((OsmNode)collection).toGeoPoint());
		}

		return new PolygonWithCenter(polygon, center);
	}

	Polygon buildPolygon(OsmEntity collection, Geometry areaGeometry) {
		Polygon polygon = new Polygon();

		polygon.setId(String.valueOf(collection.osmID));
		polygon.setPoints(geometryToGeoPoints(areaGeometry));
		polygon.getFillPaint().setColor(AREA_FILL_COLOR);
		polygon.getOutlinePaint().setStrokeWidth(Globals.convertDpToPixel(2).floatValue());
		return polygon;
	}

	Marker buildMarker(OsmEntity collection, Geometry coordinates) {
		org.locationtech.jts.geom.Point centroid = coordinates.getCentroid();
		return buildMarker(collection, new GeoPoint(centroid.getY(), centroid.getX()));
	}

	Marker buildMarker(OsmEntity collection, GeoPoint coordinates) {
		Marker marker = new Marker(osmMap);
		marker.setPosition(coordinates);
		marker.setPanToView(false);
		marker.setId(String.valueOf(collection.osmID));
		marker.setTitle(collection.getTags().optString(ClimbingTags.KEY_NAME));
		marker.setIcon(buildIcon(collection));
		marker.setAnchor(ANCHOR_CENTER, ANCHOR_CENTER);

		marker.setInfoWindow(new InfoWindow(R.layout.fragment_info_window_route, osmMap) {
			@Override
			public void onOpen(Object item) {
				closeAllInfoWindowsOn(osmMap);
				mView.setAlpha((float) 0.94);
				((TextView)mView.findViewById(R.id.textTitle)).setText(marker.getTitle());
				mView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						close();
					}
				});
			}

			@Override
			public void onClose() {

			}
		});
		return marker;
	}

	private BitmapDrawable buildIcon(OsmEntity collection) {
		if (markerIcon == null) {
			markerIcon = AppCompatResources.getDrawable(osmMap.getContext(), R.drawable.ic_clusters);
			markerIcon.setTint(ICON_TINT_COLOR);
			markerIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
		}

		Bitmap finalIcon = Bitmap.createBitmap(markerIcon.getIntrinsicWidth(),
				markerIcon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		Canvas iconCanvas = new Canvas(finalIcon);
		markerIcon.setBounds(0, 0, iconCanvas.getWidth(), iconCanvas.getHeight());
		markerIcon.draw(iconCanvas);
		String text = String.valueOf(((OsmCollectionEntity)collection).osmNodes.size());
		int textHeight = (int) (iconTextPaint.descent() + iconTextPaint.ascent());

		drawTextWithOutline(iconCanvas, text, iconTextPaint, 0.5f * finalIcon.getWidth(), 0.5f * finalIcon.getHeight() - textHeight / 2);

		return new BitmapDrawable(osmMap.getContext().getResources(), finalIcon);
	}
}
