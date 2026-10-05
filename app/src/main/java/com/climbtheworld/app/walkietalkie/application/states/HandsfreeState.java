package com.climbtheworld.app.walkietalkie.application.states;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.application.audiotools.AudioTools;
import com.climbtheworld.app.walkietalkie.application.audiotools.IRecordingListener;
import com.konovalov.vad.webrtc.Vad;
import com.konovalov.vad.webrtc.VadWebRTC;
import com.konovalov.vad.webrtc.config.FrameSize;
import com.konovalov.vad.webrtc.config.Mode;
import com.konovalov.vad.webrtc.config.SampleRate;

import java.util.ArrayDeque;
import java.util.Arrays;

public class HandsfreeState extends WalkietalkieHandler
		implements IInterconState, IRecordingListener {
	// Continuous speech needed to start transmitting; ignores clicks and gear noise.
	private static final int SPEECH_DURATION_MS = 100;
	// Silence needed to stop transmitting; bridges the pauses between words.
	private static final int SILENCE_DURATION_MS = 800;
	// Audio from before speech was detected, sent first so word onsets are not clipped.
	private static final int PRE_ROLL_FRAMES = 300 / AUDIO_FRAME_DURATION_MS;
	// How long received audio may still echo into the microphone after it was played.
	private static final int PLAYBACK_ECHO_TAIL_MS = 300;

	private final VadWebRTC vad;
	private final ArrayDeque<short[]> preRoll = new ArrayDeque<>(PRE_ROLL_FRAMES);
	private boolean transmitting = false;

	public HandsfreeState(AppCompatActivity parent) {
		super(parent);

		Configs configs = Configs.instance(parent);
		vad = Vad.builder()
				.setSampleRate(toVadSampleRate(AUDIO_SAMPLE_RATE))
				.setFrameSize(toVadFrameSize(AUDIO_FRAME_SIZE))
				.setMode(Mode.values()[configs.getInt(Configs.ConfigKey.intercomHandsFreeNoiseFilter)])
				.setSpeechDurationMs(SPEECH_DURATION_MS)
				.setSilenceDurationMs(SILENCE_DURATION_MS)
				.build();

		feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR,
				android.graphics.PorterDuff.Mode.MULTIPLY);
	}

	private static SampleRate toVadSampleRate(int sampleRate) {
		for (SampleRate vadSampleRate : SampleRate.values()) {
			if (vadSampleRate.getValue() == sampleRate) {
				return vadSampleRate;
			}
		}
		throw new IllegalArgumentException("Unsupported VAD sample rate: " + sampleRate);
	}

	private static FrameSize toVadFrameSize(int frameSize) {
		for (FrameSize vadFrameSize : FrameSize.values()) {
			if (vadFrameSize.getValue() == frameSize) {
				return vadFrameSize;
			}
		}
		throw new IllegalArgumentException("Unsupported VAD frame size: " + frameSize);
	}

	@Override
	public void onRecordingStarted() {

	}

	@Override
	public synchronized void onRawAudio(short[] frame, int numberOfReadBytes) {
		if (isFinished()) {
			return;
		}

		updateEnergy(AudioTools.getSignalCharacteristics(frame)[AudioTools.PEAK_INDEX]);

		// Half duplex: don't let what we are playing on the speaker open our own channel.
		if (!transmitting && isReceiving(PLAYBACK_ECHO_TAIL_MS)) {
			preRoll.clear();
			return;
		}

		boolean speech = vad.isSpeech(frame);
		if (speech && !transmitting) {
			startTransmission();
		} else if (!speech && transmitting) {
			stopTransmission();
		}

		if (transmitting) {
			encodeAndSend(frame, numberOfReadBytes);
		} else {
			if (preRoll.size() == PRE_ROLL_FRAMES) {
				preRoll.removeFirst();
			}
			preRoll.addLast(Arrays.copyOf(frame, numberOfReadBytes));
		}
	}

	private void startTransmission() {
		transmitting = true;
		for (short[] bufferedFrame : preRoll) {
			encodeAndSend(bufferedFrame, bufferedFrame.length);
		}
		preRoll.clear();

		runOnUiThread(() -> feedbackView.mic.setColorFilter(MIC_BROADCASTING_COLOR,
				android.graphics.PorterDuff.Mode.MULTIPLY));
	}

	private void stopTransmission() {
		// The encoder stays open between transmissions so the next one starts without delay.
		transmitting = false;
		sendEndBleep();

		runOnUiThread(() -> feedbackView.mic.setColorFilter(MIC_HANDS_FREE_COLOR,
				android.graphics.PorterDuff.Mode.MULTIPLY));
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
	public synchronized void finish() {
		if (isFinished()) {
			return;
		}

		if (transmitting) {
			transmitting = false;
			sendEndBleep();
		}
		vad.close();
		super.finish();
	}
}
