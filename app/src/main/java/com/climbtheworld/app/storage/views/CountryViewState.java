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
import com.climbtheworld.app.utils.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import needle.UiRelatedTask;

public class CountryViewState {
	protected static final int COUNTRY_ISO_ID = 0;
	protected static final int COUNTRY_NAME_ID = 1;
	public String countryISO;
	public String countryName;
	public int listViewOrder = -1;
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
				return new BitmapDrawable(parent.getResources(), getBitmapFromZip(parent.getResources(), "flag_" + CountryViewState.this.countryISO.toLowerCase() + ".png"));
			}

			@Override
			protected void thenDoUiRelatedWork(Drawable flag) {
				CountryViewState.this.flag = flag;

				if (((String)img.getTag()).contentEquals(CountryViewState.this.countryISO)) {
					img.setImageDrawable(flag);
					img.setColorFilter(null);
				}
			}
		});
	}

	private Bitmap getBitmapFromZip(Resources resources, final String imageFileInZip) {
		InputStream fis = resources.openRawResource(R.raw.flags);
		ZipInputStream zis = new ZipInputStream(fis);
		try {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.getName().equals(imageFileInZip)) {
					return BitmapFactory.decodeStream(zis);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return BitmapFactory.decodeResource(resources, R.drawable.flag_un);
	}

	void setViewState(final CountryViewState countryState, View countryView) {
		View statusAdd = countryView.findViewById(R.id.itemStatusAdd);
		View statusProgress = countryView.findViewById(R.id.itemStatusProgress);
		View statusWait = countryView.findViewById(R.id.itemStatusWaiting);
		View statusDel = countryView.findViewById(R.id.itemStatusRemove);
		switch (countryState.countryState) {
			case ADD:
				statusAdd.setVisibility(View.VISIBLE);
				statusDel.setVisibility(View.GONE);
				statusWait.setVisibility(View.GONE);
				statusProgress.setVisibility(View.GONE);
				break;
			case WAITING:
				statusAdd.setVisibility(View.GONE);
				statusWait.setVisibility(View.VISIBLE);
				statusProgress.setVisibility(View.GONE);
				statusDel.setVisibility(View.GONE);
				break;
			case PROGRESS:
				statusAdd.setVisibility(View.GONE);
				statusWait.setVisibility(View.GONE);
				statusProgress.setVisibility(View.VISIBLE);
				statusDel.setVisibility(View.GONE);
				updateProgress(statusProgress, countryState);
				break;
			case REMOVE:
				statusAdd.setVisibility(View.GONE);
				statusWait.setVisibility(View.GONE);
				statusProgress.setVisibility(View.GONE);
				statusDel.setVisibility(View.VISIBLE);
				break;
		}
		countryView.invalidate();
	}

	private void updateProgress(View statusProgress, CountryViewState countryState) {
		ProgressBar progressBar = statusProgress.findViewById(R.id.statusProgressBar);
		progressBar.setProgress(countryState.progress);
	}
}
