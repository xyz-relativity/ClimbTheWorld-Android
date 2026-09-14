package com.climbtheworld.app.walkietalkie.application.states;

import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.application.audiotools.OpusTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import needle.Needle;

abstract public class WalkietalkieHandler {
	private static final String TAG = WalkietalkieHandler.class.getSimpleName();
	private OpusTools.Encoder encoder;
	private final List<byte[]> endBleep = new ArrayList<>();
	public AppCompatActivity parent;
	FeedBackDisplay feedbackView = new FeedBackDisplay();
	private IDataEvent dataChannelListener;
	private double lastPeak = 0f;

	WalkietalkieHandler(AppCompatActivity parent) {
		this.parent = parent;
		feedbackView.energyDisplay = parent.findViewById(R.id.progressBar);
		feedbackView.mic = parent.findViewById(R.id.microphoneIcon);

		loadEndBleepData(); // disable bleep for now
	}

	public void setDataChannelListener(IDataEvent dataChannelListener) {
		this.dataChannelListener = dataChannelListener;
	}

	private void loadEndBleepData() {
		try {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(parent.getResources().openRawResource(R.raw.end_bleep)));
			String line = reader.readLine();

			while (line != null) {
				byte[] decodedBytes = Base64.decode(line, Base64.DEFAULT);
				endBleep.add(decodedBytes);
				line = reader.readLine();
			}

			reader.close();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	void updateEnergy(double energy) {
		double peak = energy;
		if (lastPeak > peak) {
			peak = lastPeak * 0.575f;
		}

		lastPeak = peak;

		final double displayPeak = peak;

		runOnUiThread(() -> feedbackView.energyDisplay.setProgress((int) (displayPeak * 100)));
	}

	void runOnUiThread(Runnable r) {
		Needle.onMainThread().execute(r);
	}

	private void sendData(final byte[] frame, final int numberOfReadBytes) {
		if (numberOfReadBytes > 0 && dataChannelListener != null) {
			Constants.AUDIO_TASK_EXECUTOR.execute(
					() -> dataChannelListener.onData(frame, numberOfReadBytes));
		}
	}

	synchronized void encodeAndSend(final short[] samples, final int numberOfSamples) {
		List<byte[]> packets;
		try {
			packets = encodeWithRecovery(samples, numberOfSamples);
		} catch (IllegalArgumentException | IllegalStateException e) {
			Log.w(TAG, "Unable to encode an Opus audio frame.", e);
			packets = Collections.emptyList();
		}

		for (byte[] packet : packets) {
			sendData(packet, packet.length);
		}
	}

	private synchronized List<byte[]> encodeWithRecovery(short[] samples, int numberOfSamples) {
		if (encoder == null) {
			encoder = OpusTools.createEncoder();
		}
		try {
			return encoder.encode(samples, numberOfSamples);
		} catch (IllegalStateException e) {
			Log.w(TAG, "Restarting reclaimed Opus encoder.", e);
			releaseEncoder();
			encoder = OpusTools.createEncoder();
			return encoder.encode(samples, numberOfSamples);
		}
	}

	protected final synchronized void releaseEncoder() {
		if (encoder != null) {
			encoder.close();
			encoder = null;
		}
	}

	synchronized void sendEndBleep() {
		for (byte[] sample : endBleep) {
			sendData(sample, sample.length);
		}
	}

	public void finish() {
		releaseEncoder();
	}

	public interface IDataEvent {
		void onData(byte[] frame, int numberOfReadBytes);
	}

	public static class FeedBackDisplay {
		ProgressBar energyDisplay;
		ImageView mic;
	}
}
