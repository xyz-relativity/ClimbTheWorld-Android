package com.climbtheworld.app.intercon.states;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.audiotools.IRecordingListener;
import com.climbtheworld.app.intercon.audiotools.RecordingThread;
import com.climbtheworld.app.utils.Constants;

public class PushToTalkState extends InterconState implements IInterconState, IRecordingListener {
    private RecordingThread recordingThread;

    private boolean recording = false;

    public PushToTalkState(Activity parent) {
        super(parent);

        parent.findViewById(R.id.pushToTalkButton).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
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
        recordingThread = new RecordingThread();
        recordingThread.addListener(this);

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
        Constants.AUDIO_RECORDER_EXECUTOR
                .execute(recordingThread);
    }

    private void stop() {
        recordingThread.stopRecording();
    }

    @Override
    public void finish() {
        recordingThread.stopRecording();
    }
}
