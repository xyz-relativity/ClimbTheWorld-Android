package com.climbtheworld.app.storage.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.DataFragment.CountryState;
import com.climbtheworld.app.utils.constants.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import needle.UiRelatedTask;

public class CountryViewState {
	protected static final int COUNTRY_ISO_ID = 0;
	protected static final int COUNTRY_NAME_ID = 1;
	/** The flag images by file name, read from the zip once instead of scanned for every flag. */
	private static Map<String, byte[]> flagImages;
	public String countryISO;
	public String countryName;
	public DataFragment.CountryState countryState;
	public int progress;
	Drawable flag = null;

	public CountryViewState(DataFragment.CountryState state, String countryInfo) {
		this.countryState = state;
		String[] countryInfoSplit = countryInfo.split(",");
		this.countryISO = countryInfoSplit[COUNTRY_ISO_ID];
		this.countryName = countryInfoSplit[COUNTRY_NAME_ID];
	}

	public void setFlag(ImageView img, Context parent) {
		if (flag != null) {
			img.setImageDrawable(flag);
			img.setColorFilter(null);
		} else {
			img.setImageResource(R.drawable.flag_un);
			img.setColorFilter(Color.argb(200, 200, 200, 200));
			loadFlag(img, parent);
		}
	}

	private void loadFlag(ImageView img, Context parent) {
		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Drawable>() {
			@Override
			protected Drawable doWork() {
				return new BitmapDrawable(parent.getResources(),
						getFlagBitmap(parent.getResources(),
								"flag_" + CountryViewState.this.countryISO.toLowerCase(Locale.ROOT) + ".png"
						));
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

	private static Bitmap getFlagBitmap(Resources resources, String imageFileInZip) {
		byte[] image = loadFlagImages(resources).get(imageFileInZip);
		Bitmap bitmap = image != null ? BitmapFactory.decodeByteArray(image, 0, image.length) : null;
		return bitmap != null ? bitmap : BitmapFactory.decodeResource(resources, R.drawable.flag_un);
	}

	private static synchronized Map<String, byte[]> loadFlagImages(Resources resources) {
		if (flagImages == null) {
			Map<String, byte[]> images = new HashMap<>();
			try (ZipInputStream zis = new ZipInputStream(resources.openRawResource(R.raw.flags))) {
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					images.put(entry.getName(), readEntry(zis));
				}
			} catch (IOException e) {
				// The flags not read by then get the UN flag.
			}
			flagImages = images;
		}
		return flagImages;
	}

	private static byte[] readEntry(ZipInputStream zis) throws IOException {
		ByteArrayOutputStream entry = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = zis.read(buffer)) != -1) {
			entry.write(buffer, 0, read);
		}
		return entry.toByteArray();
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
