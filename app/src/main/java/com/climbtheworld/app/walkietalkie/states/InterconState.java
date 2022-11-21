package com.climbtheworld.app.walkietalkie.states;

import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.audiotools.IRecordingListener;

import needle.Needle;

abstract public class InterconState {
	private IRecordingListener listener;

	public static class FeedBackDisplay {
		ProgressBar energyDisplay;
		ImageView mic;
	}

	public AppCompatActivity parent;

	FeedBackDisplay feedbackView = new FeedBackDisplay();
	private double lastPeak = 0f;

	public void setListener(IRecordingListener listener) {
		this.listener = listener;
	}

	InterconState(AppCompatActivity parent) {
		this.parent = parent;
		feedbackView.energyDisplay = parent.findViewById(R.id.progressBar);
		feedbackView.mic = parent.findViewById(R.id.microphoneIcon);
	}

	void updateEnergy(double energy) {
		double peak = energy;
		if (lastPeak > peak) {
			peak = lastPeak * 0.575f;
		}

		lastPeak = peak;

		final double displayPeak = peak;

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				feedbackView.energyDisplay.setProgress((int) (displayPeak * 100));
			}
		});
	}

	void runOnUiThread(Runnable r) {
		Needle.onMainThread().execute(r);
	}

	void sendData(final byte[] frame, final int numberOfReadBytes) {
		if (numberOfReadBytes > 0 && listener != null) {
			Constants.AUDIO_TASK_EXECUTOR.execute(new Runnable() {
				@Override
				public void run() {
					listener.onRawAudio(frame, numberOfReadBytes);
				}
			});
		}
	}

	public abstract void finish();
}
