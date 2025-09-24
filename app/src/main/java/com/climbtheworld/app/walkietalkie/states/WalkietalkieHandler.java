package com.climbtheworld.app.walkietalkie.states;

import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.audiotools.OpusTools;

import org.concentus.OpusEncoder;
import org.concentus.OpusException;

import needle.Needle;

abstract public class WalkietalkieHandler {
	public interface IDataEvent {
		void onData(byte[] frame, int numberOfReadBytes);
	}

	private IDataEvent listener;
	private final OpusEncoder encoder;

	public static class FeedBackDisplay {
		ProgressBar energyDisplay;
		ImageView mic;
	}

	public AppCompatActivity parent;

	FeedBackDisplay feedbackView = new FeedBackDisplay();
	private double lastPeak = 0f;

	public void setListener(IDataEvent listener) {
		this.listener = listener;
	}

	WalkietalkieHandler(AppCompatActivity parent) {
		this.parent = parent;
		encoder = OpusTools.getEncoder();
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

	private void sendData(final byte[] frame, final int numberOfReadBytes) {
		if (numberOfReadBytes > 0 && listener != null) {
			Constants.AUDIO_TASK_EXECUTOR.execute(new Runnable() {
				@Override
				public void run() {
					listener.onData(frame, numberOfReadBytes);
				}
			});
		}
	}

	void encodeAndSend(final short[] samples, final int numberOfReadBytes) {
		byte[] dataEncoded = new byte[numberOfReadBytes];
		try {
			int bytesEncoded = encoder.encode(samples, 0, samples.length, dataEncoded, 0, dataEncoded.length);
			sendData(dataEncoded, bytesEncoded);
		} catch (OpusException e) {
			//skip this samples
		}
	}

	public abstract void finish();
}
