package com.climbtheworld.app.storage.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.DownloadedCountry;
import com.climbtheworld.app.storage.views.DataFragment.CountryState;
import com.climbtheworld.app.utils.constants.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import needle.UiRelatedTask;

public class CountryViewState {
	protected static final int COUNTRY_ISO_ID = 0;
	protected static final int COUNTRY_NAME_ID = 1;
	private static final int BOX_WEST_ID = 2;
	private static final int BOX_SOUTH_ID = 3;
	private static final int BOX_EAST_ID = 4;
	private static final int BOX_NORTH_ID = 5;
	/** The flag images, one file per country, read as each one shows. */
	private static final String FLAGS_FOLDER = "flags/";
	public String countryISO;
	/** In the user's language. */
	public String countryName;
	/** From the country list, so a search in English finds the country in any language. */
	String englishName;
	/** The bounding box in degrees, NaN if the list has none. */
	double boxWest;
	double boxSouth;
	double boxEast;
	double boxNorth;
	public DataFragment.CountryState countryState;
	public int progress;
	/** Null while the country is not installed. */
	DownloadedCountry download;
	/** The installed country's climbing routes, counted the way crags count theirs. */
	int routeCount;
	/** Kept as long as the country list is, which is while the data manager is open. */
	Drawable flag = null;

	public CountryViewState(DataFragment.CountryState state, String countryInfo) {
		this.countryState = state;
		String[] countryInfoSplit = countryInfo.split(",");
		this.countryISO = countryInfoSplit[COUNTRY_ISO_ID];
		this.englishName = countryInfoSplit[COUNTRY_NAME_ID];
		this.countryName = displayName(countryISO, englishName);
		this.boxWest = coordinate(countryInfoSplit, BOX_WEST_ID);
		this.boxSouth = coordinate(countryInfoSplit, BOX_SOUTH_ID);
		this.boxEast = coordinate(countryInfoSplit, BOX_EAST_ID);
		this.boxNorth = coordinate(countryInfoSplit, BOX_NORTH_ID);
	}

	private static double coordinate(String[] countryInfo, int index) {
		try {
			return Double.parseDouble(countryInfo[index]);
		} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** The box also covers parts of the neighbouring countries. */
	boolean boxContains(double latitude, double longitude) {
		return latitude >= boxSouth && latitude <= boxNorth
				&& longitude >= boxWest && longitude <= boxEast;
	}

	/** The countries across the 180th meridian have a box around the whole globe. */
	boolean boxSpansAllLongitudes() {
		return boxWest <= -180 && boxEast >= 180;
	}

	double boxArea() {
		return (boxNorth - boxSouth) * (boxEast - boxWest);
	}

	/** The country's name in the user's language, or the English one if Android has none. */
	private static String displayName(String countryIso, String englishName) {
		String name = new Locale("", countryIso).getDisplayCountry();
		return name.isEmpty() || name.equalsIgnoreCase(countryIso) ? englishName : name;
	}

	public void setFlag(ImageView img, Context parent) {
		if (flag != null) {
			img.setImageDrawable(flag);
			img.setColorFilter(null);
		} else {
			// The UN flag, greyed out, until the country's own is read.
			img.setImageResource(R.drawable.flag_un);
			img.setColorFilter(Color.argb(200, 200, 200, 200));
			loadFlag(img, parent);
		}
	}

	private void loadFlag(ImageView img, Context parent) {
		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Drawable>() {
			@Override
			protected Drawable doWork() {
				Bitmap bitmap = readFlag(parent, countryISO);
				// A country without a flag of its own keeps the UN one.
				return bitmap != null ? new BitmapDrawable(parent.getResources(), bitmap)
						: parent.getDrawable(R.drawable.flag_un);
			}

			@Override
			protected void thenDoUiRelatedWork(Drawable flag) {
				CountryViewState.this.flag = flag;

				if (((String) img.getTag()).contentEquals(CountryViewState.this.countryISO)) {
					img.setImageDrawable(flag);
					img.setColorFilter(null);
				}
			}
		});
	}

	/** The country's flag, or null if the app has none for it. Reads a file. */
	public static Bitmap readFlag(Context context, String countryIso) {
		try (InputStream image = context.getAssets().open(
				FLAGS_FOLDER + "flag_" + countryIso.toLowerCase(Locale.ROOT) + ".png")) {
			return BitmapFactory.decodeStream(image);
		} catch (IOException e) {
			return null;
		}
	}

	void setViewState(final CountryViewState countryState, View countryView) {
		CountryState state = countryState.countryState;
		View statusProgress = countryView.findViewById(R.id.itemStatusProgress);
		setVisible(countryView.findViewById(R.id.itemStatusAdd), state == CountryState.ADD);
		setVisible(countryView.findViewById(R.id.itemStatusRemove), state == CountryState.REMOVE);
		setVisible(countryView.findViewById(R.id.itemStatusWaiting),
				state == CountryState.WAITING || state == CountryState.DELETING);
		setVisible(statusProgress, state == CountryState.PROGRESS);
		setVisible(countryView.findViewById(R.id.countryCancelButton),
				state == CountryState.WAITING || state == CountryState.PROGRESS);
		if (state == CountryState.PROGRESS) {
			updateProgress(statusProgress, countryState);
		}
		countryView.invalidate();
	}

	private static void setVisible(View view, boolean visible) {
		view.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	private void updateProgress(View statusProgress, CountryViewState countryState) {
		ProgressBar progressBar = statusProgress.findViewById(R.id.statusProgressBar);
		progressBar.setProgress(countryState.progress);
	}
}
