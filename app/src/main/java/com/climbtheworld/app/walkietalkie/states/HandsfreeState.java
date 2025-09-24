package com.climbtheworld.app.walkietalkie.states;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.audiotools.AudioTools;
import com.climbtheworld.app.walkietalkie.audiotools.BasicVoiceDetector;
import com.climbtheworld.app.walkietalkie.audiotools.IRecordingListener;
import com.climbtheworld.app.walkietalkie.audiotools.IVoiceDetector;
import com.climbtheworld.app.walkietalkie.audiotools.RecordingThread;

public class HandsfreeState extends WalkietalkieHandler implements IInterconState, IRecordingListener {
	private final RecordingThread recordingThread;
	IVoiceDetector voice;
	boolean transmissionState = false;
	long lastVoiceFrame = 0;

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
	public void onRawAudio(short[] frame, int numberOfReadBytes) {
		double[] characteristic = AudioTools.getSignalCharacteristics(frame);

		if (voice.isVoiceDetected(frame, numberOfReadBytes, characteristic[AudioTools.RMS_INDEX])) {
			lastVoiceFrame = System.currentTimeMillis();

			if (!transmissionState) {
				transmissionState = true;

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						feedbackView.mic.setColorFilter(MIC_BROADCASTING_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
					}
				});
			}
		}

		if (transmissionState && (System.currentTimeMillis() - lastVoiceFrame > 250)) {
			transmissionState = false;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
				}
			});
		}

		if (transmissionState) {
			encodeAndSend(frame, numberOfReadBytes);
		}

		updateEnergy(characteristic[AudioTools.PEAK_INDEX]);
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
