package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.activities.EditRelationActivity;
import com.climbtheworld.app.activities.MapActivity;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.TerrainElevation;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import java.util.Locale;

public class DialogueUtils {
	protected static void buildTitle(final AppCompatActivity activity, final View result,
	                                 final long osmId, final String name, Drawable icon,
	                                 final GeoNode location, final String osmEntityType,
	                                 final boolean editable) {
		((TextView) result.findViewById(R.id.textTitle)).setText(name);
		((ImageView) result.findViewById(R.id.imageIcon)).setImageDrawable(icon);

		RelativeLayout button = result.findViewById(R.id.menu);
		button.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View view) {
				//Creating the instance of PopupMenu
				PopupMenu popup = new PopupMenu(activity, view);
				popup.getMenuInflater().inflate(R.menu.dialog_nav_share_options, popup.getMenu());
				if (osmId == 0 || !editable) {
					popup.getMenu().findItem(R.id.menuEdit).setEnabled(false);
				}

				//registering popup with OnMenuItemClickListener
				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						String urlFormat;
						Intent intent;

						int id = item.getItemId();
						if (id == R.id.menuEdit) {
							if ("relation".equals(osmEntityType)) {
								intent = new Intent(activity, EditRelationActivity.class);
								intent.putExtra(EditRelationActivity.EXTRA_RELATION_ID, osmId);
							} else {
								intent = new Intent(activity, EditNodeActivity.class);
								intent.putExtra("poiID", osmId);
							}
							activity.startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);

							DialogBuilder.closeAllDialogs();
						} else if (id == R.id.centerLocation) {
							DialogBuilder.closeAllDialogs();
							MapActivity.showOnMap(activity, location);
						} else if (id == R.id.navigate) {
							urlFormat = String.format(Locale.ROOT, "geo:0,0?q=%f,%f",
									location.decimalLatitude,
									location.decimalLongitude);
							// Encoded, as a & or # in a name would end the query; an unnamed
							// place, titled " ", gets no label.
							if (!name.trim().isEmpty()) {
								urlFormat += " (" + Uri.encode(name) + ")";
							}
							intent = new Intent(Intent.ACTION_VIEW,
									Uri.parse(urlFormat));
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							activity.startActivity(intent);
						} else if (id == R.id.climbTheWorldUrlLocation) {
							urlFormat = String.format(Locale.ROOT, "climbtheworld://map_view/location/%s",
									new MapCoordinate(location.decimalLatitude, location.decimalLongitude,
											location.elevationMeters).toDelimitedString());
							copyLocation(activity, name, urlFormat);
						} else if (id == R.id.openStreetMapUrlLocation) {
							urlFormat = String.format(Locale.ROOT,
									"https://www.openstreetmap.org/%s/%d#map=19/%f/%f",
									osmEntityType,
									osmId,
									location.decimalLatitude,
									location.decimalLongitude);
							copyLocation(activity, name, urlFormat);
						} else if (id == R.id.googleMapsUrlLocation) {
							//Docs: https://developers.google.com/maps/documentation/urls/guide#search-action
							urlFormat = String.format(Locale.ROOT, "https://www.google.com/maps/place/%f,%f/@%f,%f,19z/data=!5m1!1e4",
									location.decimalLatitude,
									location.decimalLongitude,
									location.decimalLatitude,
									location.decimalLongitude);
							copyLocation(activity, name, urlFormat);
						} else if (id == R.id.geoUrlLocation) {
							urlFormat = String.format(Locale.ROOT, "geo:%f,%f,%f",
									location.decimalLatitude,
									location.decimalLongitude,
									location.elevationMeters);
							copyLocation(activity, name, urlFormat);
						}
						return true;
					}
				});
				popup.show();
			}
		});
	}

	/** The layout inflated in a vertical scroll, for a dialog's content. */
	static View scrollingView(AppCompatActivity activity, @LayoutRes int layout,
	                          ViewGroup container) {
		ScrollView wrapper = new ScrollView(activity);
		wrapper.addView(activity.getLayoutInflater().inflate(layout, container, false));
		wrapper.setVerticalScrollBarEnabled(true);
		wrapper.setHorizontalScrollBarEnabled(false);
		return wrapper;
	}

	/** Puts the link on the clipboard, under the location's name, and says so. */
	private static void copyLocation(AppCompatActivity activity, String name, String link) {
		ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboard.setPrimaryClip(ClipData.newPlainText(name, link));
		Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
				Toast.LENGTH_LONG).show();
	}

	protected static void addOkButton(AppCompatActivity activity, AlertDialog alertDialog) {
		addOkButton(activity, alertDialog, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
	}

	protected static void addOkButton(AppCompatActivity activity, AlertDialog alertDialog, DialogInterface.OnClickListener onCLickListener) {
		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.done), onCLickListener);
	}

	public static void setLocation(AppCompatActivity parent, View result, GeoNode tmpPoi) {
		setDistance(parent, result, Globals.virtualCamera, tmpPoi, null);

		((TextView) result.findViewById(R.id.editLatitude)).setText(
				GeoUtils.formatCoordinate(tmpPoi.decimalLatitude));
		((TextView) result.findViewById(R.id.editLongitude)).setText(
				GeoUtils.formatCoordinate(tmpPoi.decimalLongitude));
		setElevation(result.findViewById(R.id.editElevation), tmpPoi);
	}

	/**
	 * The elevation the POI is mapped at, or else the terrain model's, marked as an estimate and
	 * explained on a long press. The terrain is read while the view is shown, as its tiles may
	 * have to load first: until they do, or when they cannot, it stays empty.
	 */
	private static void setElevation(TextView view, GeoNode poi) {
		if (poi.getTags().has(ClimbingTags.KEY_ELEVATION)) {
			view.setText(GeoUtils.formatElevation(poi.elevationMeters));
			return;
		}
		TerrainElevationText terrainText = new TerrainElevationText(view, poi);
		view.addOnAttachStateChangeListener(terrainText);
		if (view.isAttachedToWindow()) {
			terrainText.onViewAttachedToWindow(view);
		}
	}

	/**
	 * Fills in the straight line distance and the compass point from the observer to the POI, and
	 * sets the button that shows the POI on the map, see MapActivity.showOnMap, the same in the
	 * info dialogs and in the lists of climbing POIs: editDistance, editBearings and
	 * showOnMapButton in the view.
	 *
	 * @param closeList closes what the button is in, before the map shows the POI, unless it is a
	 *                  dialog from getNewDialog, which all close anyway; null for nothing more
	 */
	public static void setDistance(AppCompatActivity parent, View result, GeoNode observer,
	                               GeoNode poi, Runnable closeList) {
		((TextView) result.findViewById(R.id.editDistance)).setText(
				Globals.getDistanceString(GeoUtils.calculateDistance(observer, poi)));
		((TextView) result.findViewById(R.id.editBearings)).setText(
				AugmentedRealityUtils.getStringBearings(parent,
						GeoUtils.calculateTheoreticalAzimuth(observer, poi)));

		View geolocButton = result.findViewById(R.id.showOnMapButton);
		if (geolocButton == null) {
			return;
		}
		geolocButton.setOnClickListener(clicked -> {
			if (closeList != null) {
				closeList.run();
			}
			DialogBuilder.closeAllDialogs();
			MapActivity.showOnMap(parent, poi);
		});
	}

	/**
	 * Shows the terrain model's elevation of a POI while the view is shown, see setElevation:
	 * it holds the terrain the A.R. screens share, so it finds the tiles they loaded, and is told
	 * when the ones it reads come in.
	 */
	private static final class TerrainElevationText
			implements View.OnAttachStateChangeListener, Runnable {
		private final TextView view;
		private final GeoNode poi;
		// Held while the view is shown.
		private TerrainElevation terrain;

		TerrainElevationText(TextView view, GeoNode poi) {
			this.view = view;
			this.poi = poi;
		}

		@Override
		public void onViewAttachedToWindow(View attached) {
			if (terrain != null) {
				return;
			}
			terrain = TerrainElevation.acquireShared(attached.getContext());
			terrain.addListener(this);
			run();
		}

		@Override
		public void onViewDetachedFromWindow(View detached) {
			if (terrain == null) {
				return;
			}
			terrain.removeListener(this);
			terrain = null;
			TerrainElevation.releaseShared();
		}

		/** Reads the elevation again, now that more tiles may have come in. */
		@Override
		public void run() {
			// Where the A.R. view stands the POI from close by, see
			// AugmentedRealityScene.getModelElevation: a route top on top of its wall, the rest
			// at the foot of theirs.
			double meters = poi.isRouteTop()
					? terrain.getElevation(poi.decimalLatitude, poi.decimalLongitude,
					TerrainElevation.DETAIL_ZOOM)
					: terrain.getFootElevation(poi.decimalLatitude, poi.decimalLongitude,
					TerrainElevation.DETAIL_ZOOM);
			if (Double.isNaN(meters)) {
				view.setText("");
				view.setTooltipText(null);
				return;
			}
			view.setText(view.getContext().getString(R.string.elevation_from_terrain,
					GeoUtils.formatElevation(meters)));
			view.setTooltipText(view.getContext().getString(R.string.elevation_from_terrain_hint));
		}
	}
}
