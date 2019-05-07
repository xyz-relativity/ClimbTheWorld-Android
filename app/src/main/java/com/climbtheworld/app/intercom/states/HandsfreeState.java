package com.climbtheworld.app.intercom.states;

import android.app.Activity;

import com.climbtheworld.app.intercom.audiotools.BasicVoiceDetector;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.IVoiceDetector;
import com.climbtheworld.app.intercom.audiotools.RecordingThread;
import com.climbtheworld.app.utils.Constants;

public class HandsfreeState extends InterconState implements IInterconState, IRecordingListener {
    private RecordingThread recordingThread;
    IVoiceDetector voice = new BasicVoiceDetector();
    boolean state = false;

    public HandsfreeState(Activity parent) {
        super(parent);

        feedbackView.mic.setColorFilter(MIC_HANDSFREE_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);

        recordingThread = new RecordingThread();
        recordingThread.addListener(this);
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
        }
    }

    @Override
    public void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms) {
        if (voice.onAudio(frame, numberOfReadBytes, rms)) {
            updateEnergy(energy);
            if (!state) {
                sendData(frame, numberOfReadBytes); //sand this frame as well.
                state = true;
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
                        feedbackView.mic.setColorFilter(MIC_HANDSFREE_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
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
