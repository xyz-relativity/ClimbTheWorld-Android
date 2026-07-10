package com.climbtheworld.app.walkietalkie.application.states;

import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.walkietalkie.application.audiotools.AudioTools;
import com.climbtheworld.app.walkietalkie.application.audiotools.IRecordingListener;

public class PushToTalkState extends WalkietalkieHandler
		implements IInterconState, IRecordingListener {

	private boolean isMuted = true;

	public PushToTalkState(AppCompatActivity parent) {
		super(parent);

		parent.findViewById(R.id.pushToTalkButton).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						start();
						break;
					case MotionEvent.ACTION_UP:
						stop();
						break;
				}
				return false;
			}
		});

		feedbackView.mic.setColorFilter(MIC_DISABLED_COLOR,
				android.graphics.PorterDuff.Mode.MULTIPLY);
		feedbackView.energyDisplay.setProgress(0);
	}

	@Override
	public void onRecordingStarted() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				feedbackView.mic.setColorFilter(MIC_BROADCASTING_COLOR,
						android.graphics.PorterDuff.Mode.MULTIPLY);
			}
		});
	}

	@Override
	public void onRawAudio(short[] frame, int numberOfReadBytes) {
		double[] characteristic = AudioTools.getSignalCharacteristics(frame);
		if (!isMuted) {
			encodeAndSend(frame, numberOfReadBytes);
		}
		updateEnergy(characteristic[AudioTools.PEAK_INDEX]);
	}

	@Override
	public void onRecordingDone() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				feedbackView.mic.setColorFilter(MIC_DISABLED_COLOR,
						android.graphics.PorterDuff.Mode.MULTIPLY);
				feedbackView.energyDisplay.setProgress(0);
			}
		});
	}

	private void start() {
		isMuted = false;
		onRecordingStarted();
	}

	private void stop() {
		isMuted = true;
		onRecordingDone();
		sendEndBleep();
	}

	@Override
	public void finish() {
	}
}
