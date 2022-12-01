package com.climbtheworld.app.walkietalkie.states;

import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.audiotools.IRecordingListener;
import com.climbtheworld.app.walkietalkie.audiotools.RecordingThread;

public class PushToTalkState extends InterconState implements IInterconState, IRecordingListener {
	private RecordingThread recordingThread;

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

		feedbackView.mic.setColorFilter(MIC_DISABLED_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
		feedbackView.energyDisplay.setProgress(0);
	}

	@Override
	public void onRecordingStarted() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				feedbackView.mic.setColorFilter(MIC_BROADCASTING_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
			}
		});
	}

	@Override
	public void onRawAudio(byte[] frame, int numberOfReadBytes) {
		sendData(frame, numberOfReadBytes);
	}

	@Override
	public void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms) {
		updateEnergy(energy);
	}

	@Override
	public void onRecordingDone() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				feedbackView.mic.setColorFilter(MIC_DISABLED_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
				feedbackView.energyDisplay.setProgress(0);
			}
		});
	}

	private void start() {
		recordingThread = new RecordingThread(this);

		Constants.AUDIO_RECORDER_EXECUTOR
				.execute(recordingThread);
	}

	private void stop() {
		if (recordingThread != null) {
			recordingThread.cancel();
		}
	}

	@Override
	public void finish() {
		if (recordingThread != null) {
			recordingThread.cancel();
		}
	}
}
