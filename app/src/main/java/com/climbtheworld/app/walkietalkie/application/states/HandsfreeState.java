package com.climbtheworld.app.walkietalkie.application.states;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.application.audiotools.AudioTools;
import com.climbtheworld.app.walkietalkie.application.audiotools.BasicVoiceDetector;
import com.climbtheworld.app.walkietalkie.application.audiotools.IRecordingListener;
import com.climbtheworld.app.walkietalkie.application.audiotools.IVoiceDetector;

public class HandsfreeState extends WalkietalkieHandler
		implements IInterconState, IRecordingListener {
	IVoiceDetector voice;
	boolean transmissionState = false;
	long lastVoiceFrame = 0;

	public HandsfreeState(AppCompatActivity parent) {
		super(parent);

		Configs configs = Configs.instance(parent);
		voice = new BasicVoiceDetector(
				configs.getInt(Configs.ConfigKey.intercomHandFreeThreshold) / 100.0);

		feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR,
				android.graphics.PorterDuff.Mode.MULTIPLY);
	}

	@Override
	public void onRecordingStarted() {

	}

	@Override
	public void onRawAudio(short[] frame, int numberOfReadBytes) {
		double[] characteristic = AudioTools.getSignalCharacteristics(frame);

		if (voice.isVoiceDetected(frame, numberOfReadBytes,
				characteristic[AudioTools.RMS_INDEX])) {
			lastVoiceFrame = System.currentTimeMillis();

			if (!transmissionState) {
				transmissionState = true;

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						feedbackView.mic.setColorFilter(MIC_BROADCASTING_COLOR,
								android.graphics.PorterDuff.Mode.MULTIPLY);
					}
				});
			}
		}

		if (transmissionState && (System.currentTimeMillis() - lastVoiceFrame > 250)) {
			transmissionState = false;
			sendEndBleep();
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR,
							android.graphics.PorterDuff.Mode.MULTIPLY);
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
	}
}
