package com.climbtheworld.app.walkietalkie.states;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.audiotools.BasicVoiceDetector;
import com.climbtheworld.app.walkietalkie.audiotools.IRecordingListener;
import com.climbtheworld.app.walkietalkie.audiotools.IVoiceDetector;
import com.climbtheworld.app.walkietalkie.audiotools.RecordingThread;

public class HandsfreeState extends InterconState implements IInterconState, IRecordingListener {
	private final RecordingThread recordingThread;
	IVoiceDetector voice;
	boolean state = false;
	private byte[] bufferedData;

	public HandsfreeState(AppCompatActivity parent) {
		super(parent);

		Configs configs = Configs.instance(parent);
		voice = new BasicVoiceDetector(configs.getInt(Configs.ConfigKey.intercomHandFreeThreshold) / 100.0);

		feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);

		recordingThread = new RecordingThread(this);
		Constants.AUDIO_RECORDER_EXECUTOR
				.execute(recordingThread);
	}

	@Override
	public void onRecordingStarted() {

	}

	@Override
	public void onRawAudio(byte[] frame, int numberOfReadBytes) {
		if (state) {
			sendData(frame, numberOfReadBytes);
		} else {
			bufferedData = new byte[numberOfReadBytes];
			System.arraycopy(frame, 0, bufferedData, 0, numberOfReadBytes);
		}
	}

	@Override
	public void onAudio(short[] frame, int numberOfReadBytes, double energy, double rms) {
		if (voice.onAudio(frame, numberOfReadBytes, rms)) {
			updateEnergy(energy);
			if (!state) {
				state = true;
				onRawAudio(bufferedData, bufferedData.length);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						feedbackView.mic.setColorFilter(MIC_BROADCASTING_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
					}
				});
			}
		} else {
			updateEnergy(energy);
			if (state) {
				state = false;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
					}
				});
			}
		}
	}

	@Override
	public void onRecordingDone() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				feedbackView.energyDisplay.setProgress(0);
			}
		});
	}

	@Override
	public void finish() {
		recordingThread.cancel();
	}
}
