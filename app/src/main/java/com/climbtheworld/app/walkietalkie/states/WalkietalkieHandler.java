package com.climbtheworld.app.walkietalkie.states;

import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.audiotools.OpusTools;

import org.concentus.OpusEncoder;
import org.concentus.OpusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import needle.Needle;

abstract public class WalkietalkieHandler {
	private static final String TAG = WalkietalkieHandler.class.getSimpleName();
	private final OpusEncoder encoder;
	private final List<byte[]> endBleep = new ArrayList<>();
	public AppCompatActivity parent;
	FeedBackDisplay feedbackView = new FeedBackDisplay();
	private IDataEvent dataChannelListener;
	private double lastPeak = 0f;

	WalkietalkieHandler(AppCompatActivity parent) {
		this.parent = parent;
		encoder = OpusTools.getEncoder();
		feedbackView.energyDisplay = parent.findViewById(R.id.progressBar);
		feedbackView.mic = parent.findViewById(R.id.microphoneIcon);

		loadEndBleepData();
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

	void encodeAndSend(final short[] samples, final int numberOfReadBytes) {
		byte[] dataEncoded = new byte[numberOfReadBytes];
		try {
			int bytesEncoded =
					encoder.encode(samples, 0, samples.length, dataEncoded, 0, dataEncoded.length);
			sendData(dataEncoded, bytesEncoded);
		} catch (OpusException e) {
			//skip this samples
		}
	}

	void sendEndBleep() {
		Constants.ASYNC_TASK_EXECUTOR.execute(() -> {
			for (byte[] sample : endBleep) {
				sendData(sample, sample.length);
			}
		});
	}

	public abstract void finish();

	public interface IDataEvent {
		void onData(byte[] frame, int numberOfReadBytes);
	}

	public static class FeedBackDisplay {
		ProgressBar energyDisplay;
		ImageView mic;
	}
}
