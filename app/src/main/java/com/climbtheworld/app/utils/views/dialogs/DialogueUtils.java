package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.activities.ViewMapActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.Sorters;

import org.osmdroid.util.GeoPoint;

import java.util.Locale;

public class DialogueUtils {
	public static String buildDescription(final AppCompatActivity parent, GeoNode poi) {
		Configs configs = Configs.instance(parent);
		StringBuilder appender = new StringBuilder();
		String sepChr = "";
		switch (poi.getNodeType()) {
			case route:
				for (GeoNode.ClimbingStyle style : Sorters.sortStyles(parent, poi.getClimbingStyles())) {
					appender.append(sepChr).append(parent.getResources().getString(style.getNameId()));
					sepChr = ", ";
				}

				appender.append("\n");

				appender.append(parent.getString(R.string.length)).append(": ").append(Globals.getDistanceString(poi.getKey(GeoNode.KEY_LENGTH)));
				break;
			case crag:
				for (GeoNode.ClimbingStyle style : Sorters.sortStyles(parent, poi.getClimbingStyles())) {
					appender.append(sepChr).append(parent.getString(style.getNameId()));
					sepChr = ", ";
				}
				appender.append("\n");

				appender.append(parent.getString(R.string.min_grade, configs.getString(Configs.ConfigKey.usedGradeSystem)));
				appender.append(": ").append(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)));

				appender.append("\n");

				appender.append(parent.getString(R.string.max_grade,
						parent.getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
				appender.append(": ").append(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)));

				break;
			case artificial:
				if (poi.isArtificialTower()) {
					appender.append(parent.getString(R.string.artificial_tower));
				} else {
					appender.append(parent.getString(R.string.climbing_gym));
				}
			default:
				appender.append("\n");
				appender.append(poi.getKey(GeoNode.KEY_DESCRIPTION));
				break;
		}

		return appender.toString();
	}

	protected static void addNavigateButton(final AppCompatActivity activity, final AlertDialog alertDialog, final long osmId, final String name, final GeoPoint location) {
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.nav_share), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//add this so we have it in the list of views.
			}
		});

		alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialogInterface) {

				Button button = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
				button.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View view) {
						//Creating the instance of PopupMenu
						PopupMenu popup = new PopupMenu(activity, view);
						popup.getMenuInflater().inflate(R.menu.dialog_nav_share_options, popup.getMenu());

						//registering popup with OnMenuItemClickListener
						popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
							public boolean onMenuItemClick(MenuItem item) {
								ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
								String urlFormat;

								switch (item.getItemId()) {
									case R.id.centerLocation:
										DialogBuilder.closeAllDialogs();
										if (activity instanceof ViewMapActivity) {
											((ViewMapActivity) activity).centerOnLocation(location);
										} else {
											Intent intent = new Intent(activity, ViewMapActivity.class);
											intent.putExtra("GeoPoint", location.toDoubleString());
											activity.startActivity(intent);
										}
										break;

									case R.id.navigate:
										urlFormat = String.format(Locale.getDefault(), "geo:0,0?q=%f,%f (%s)",
												location.getLatitude(),
												location.getLongitude(),
												name);
										Intent intent = new Intent(Intent.ACTION_VIEW,
												Uri.parse(urlFormat));
										intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
										activity.startActivity(intent);
										break;

									case R.id.climbTheWorldUrlLocation:
										urlFormat = String.format(Locale.getDefault(), "climbtheworld://map_view/location/%s",
												location.toDoubleString());
										clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));

										Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
												Toast.LENGTH_LONG).show();
										break;

									case R.id.openStreetMapUrlLocation:
										urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/node/%d#map=19/%f/%f",
												osmId,
												location.getLatitude(),
												location.getLongitude());
										clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));

										Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
												Toast.LENGTH_LONG).show();
										break;

									case R.id.googleMapsUrlLocation:
										//Docs: https://developers.google.com/maps/documentation/urls/guide#search-action
										urlFormat = String.format(Locale.getDefault(), "https://www.google.com/maps/place/%f,%f/@%f,%f,19z/data=!5m1!1e4",
												location.getLatitude(),
												location.getLongitude(),
												location.getLatitude(),
												location.getLongitude());
										clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));

										Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
												Toast.LENGTH_LONG).show();
										break;

									case R.id.geoUrlLocation:
										urlFormat = String.format(Locale.getDefault(), "geo:%f,%f,%f",
												location.getLatitude(),
												location.getLongitude(),
												location.getAltitude());
										clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));
										Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
												Toast.LENGTH_LONG).show();
										break;
								}
								return true;
							}
						});
						popup.show();
					}
				});

				View geolocButton = alertDialog.findViewById(R.id.showOnMapButton);
				if (geolocButton != null) {
					geolocButton.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							DialogBuilder.closeAllDialogs();
							if (activity instanceof ViewMapActivity) {
								((ViewMapActivity) activity).centerOnLocation(location);
							} else {
								Intent intent = new Intent(activity, ViewMapActivity.class);
								intent.putExtra("GeoPoint", location.toDoubleString());
								activity.startActivity(intent);
							}
						}
					});
				}

				DialogBuilder.dismissLoadingDialogue();
			}
		});
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

	protected static void addEditButton(final AppCompatActivity activity, final AlertDialog alertDialog, final long poiId) {
		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.edit), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(activity, EditNodeActivity.class);
				intent.putExtra("poiID", poiId);
				activity.startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
			}
		});
	}
}
