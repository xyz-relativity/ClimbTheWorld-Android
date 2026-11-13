package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.util.Log;

import needle.CancelableTask;

@SuppressLint("MissingPermission") //permission checked at WalkieTalkieActivity activity startup
public class RecordingThread extends CancelableTask {
	private static final String TAG = RecordingThread.class.getSimpleName();
	// Listeners encode on this thread; keep ~500 ms of slack so a codec start-up does not overrun.
	private static final int RECORDER_BUFFER_SIZE = Math.max(IRecordingListener.AUDIO_BUFFER_SIZE,
			IRecordingListener.AUDIO_FRAME_SIZE * 2 * 25);
	// A failed read is tried again after a pause, longer each time up to the longest: a recorder
	// that keeps failing, as once the audio server died, fails at once every time.
	private static final long READ_RETRY_PAUSE_MS = 20;
	private static final long READ_RETRY_LONGEST_PAUSE_MS = 500;
	// Of the reads failing in a row, the first and every so many after it are logged.
	private static final int READ_FAILURES_PER_LOG = 50;
	// The recorder is made anew when it says it died, as when the audio server restarted, and
	// else once this many reads failed in a row, in case that brings it back.
	private static final int READ_FAILURES_PER_RESTART = 10;
	// A recorder that could not be made is made again after a pause, longer each time up to the
	// longest: the audio server takes seconds to come back.
	private static final long RESTART_PAUSE_MS = 500;
	private static final long RESTART_LONGEST_PAUSE_MS = 5000;
	// Null between a recorder that died and the one made in its place.
	private AudioRecord recorder;
	private boolean recording;
	private volatile IRecordingListener audioListener;
	private AcousticEchoCanceler acousticEchoCanceler;

	public RecordingThread() {
		recorder = createRecorder();
	}

	private static AudioRecord createRecorder() {
		return new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
				IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_IN, IRecordingListener.AUDIO_ENCODING,
				RECORDER_BUFFER_SIZE);
	}

	public void setAudioListener(
			IRecordingListener audioListener) {
		this.audioListener = audioListener;
	}

	/**
	 * Stops handing the audio to a listener, when it is still the one that gets it: one set
	 * since stays. Called on the thread that sets them, the main one.
	 */
	public void removeAudioListener(IRecordingListener audioListener) {
		if (this.audioListener == audioListener) {
			this.audioListener = null;
		}
	}

	@Override
	protected void doWork() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
		short[] recordingBuffer = new short[IRecordingListener.AUDIO_FRAME_SIZE];

		try {
			if (!startRecorder()) {
				return;
			}

			int failedReads = 0;
			// How many recorders were made in a row without one of them recording anything.
			int restartsWithoutAudio = 0;
			while (!isCanceled()) {
				int numberOfSamples = recorder.read(recordingBuffer, 0, recordingBuffer.length);
				if (numberOfSamples <= 0) {
					if (failedReads % READ_FAILURES_PER_LOG == 0) {
						Log.w(TAG, "Audio recorder read failed with status: " + numberOfSamples
								+ ", " + (failedReads + 1) + " in a row");
					}
					failedReads++;
					if (!pause(readRetryPauseMs(failedReads))) {
						break;
					}
					if (numberOfSamples == AudioRecord.ERROR_DEAD_OBJECT
							|| failedReads % READ_FAILURES_PER_RESTART == 0) {
						// A recorder that fails as soon as it is made is not made over and over.
						if (restartsWithoutAudio > 0
								&& !pause(restartPauseMs(restartsWithoutAudio))) {
							break;
						}
						restartsWithoutAudio++;
						if (!restartRecorder()) {
							break;
						}
						failedReads = 0;
					}
					continue;
				}
				failedReads = 0;
				restartsWithoutAudio = 0;

				IRecordingListener listener = audioListener;
				if (listener != null) {
					listener.onRawAudio(recordingBuffer, numberOfSamples);
				}
			}
		} catch (IllegalStateException e) {
			Log.w(TAG, "Audio recording failed.", e);
		} finally {
			closeRecorder();
		}
	}

	/**
	 * Starts the recorder, with echo cancellation where the phone has it.
	 *
	 * @return whether it records
	 */
	private boolean startRecorder() {
		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			Log.w(TAG, "Audio recorder failed to initialize.");
			return false;
		}
		initializeAcousticEchoCanceler();
		recorder.startRecording();
		recording = true;
		return true;
	}

	/** Stops and releases the recorder and its echo cancellation, if there are any. */
	private void closeRecorder() {
		if (recorder != null) {
			if (recording) {
				try {
					recorder.stop();
				} catch (IllegalStateException e) {
					Log.w(TAG, "Audio recorder was already stopped.", e);
				}
			}
			recorder.release();
			recorder = null;
		}
		recording = false;
		releaseAcousticEchoCanceler();
	}

	/**
	 * Makes the recorder anew in place of one that died, trying until one records, see
	 * RESTART_PAUSE_MS.
	 *
	 * @return false when the thread is to end instead: it was cancelled or interrupted
	 */
	private boolean restartRecorder() {
		Log.w(TAG, "Restarting the audio recorder.");
		closeRecorder();
		int failedRestarts = 0;
		while (!isCanceled()) {
			try {
				recorder = createRecorder();
				if (startRecorder()) {
					Log.i(TAG, "Audio recorder restarted.");
					return true;
				}
			} catch (RuntimeException e) {
				// As while the audio server is still away.
				Log.w(TAG, "Audio recorder could not be restarted.", e);
			}
			closeRecorder();
			failedRestarts++;
			if (!pause(restartPauseMs(failedRestarts))) {
				return false;
			}
		}
		return false;
	}

	/**
	 * @param failedRestarts how many times in a row the recorder could not be made, at least 1
	 * @return how long to wait before making it again
	 */
	static long restartPauseMs(int failedRestarts) {
		int doublings = Math.min(failedRestarts - 1, 10);
		return Math.min(RESTART_PAUSE_MS << doublings, RESTART_LONGEST_PAUSE_MS);
	}

	/**
	 * @param failedReads how many reads failed in a row, at least 1
	 * @return how long to wait before reading again
	 */
	static long readRetryPauseMs(int failedReads) {
		// Doubled for each failure; past a few of them it is the longest pause anyway.
		int doublings = Math.min(failedReads - 1, 10);
		return Math.min(READ_RETRY_PAUSE_MS << doublings, READ_RETRY_LONGEST_PAUSE_MS);
	}

	/** @return false when the thread was interrupted, so is to end */
	private static boolean pause(long milliseconds) {
		try {
			Thread.sleep(milliseconds);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private void initializeAcousticEchoCanceler() {
		boolean available = AcousticEchoCanceler.isAvailable();
		Log.i(TAG, "Acoustic echo cancellation available: " + available);
		if (!available) {
			return;
		}

		try {
			acousticEchoCanceler = AcousticEchoCanceler.create(recorder.getAudioSessionId());
			if (acousticEchoCanceler == null) {
				Log.w(TAG, "Acoustic echo cancellation could not be created.");
				return;
			}

			boolean enabledByDefault = acousticEchoCanceler.getEnabled();
			int enableResult = acousticEchoCanceler.setEnabled(true);
			Log.i(TAG, "Acoustic echo cancellation default enabled: " + enabledByDefault
					+ ", enable result: " + enableResult
					+ ", final enabled: " + acousticEchoCanceler.getEnabled());
		} catch (RuntimeException e) {
			Log.w(TAG, "Acoustic echo cancellation initialization failed.", e);
		}
	}

	private void releaseAcousticEchoCanceler() {
		if (acousticEchoCanceler == null) {
			return;
		}

		try {
			acousticEchoCanceler.setEnabled(false);
		} catch (RuntimeException e) {
			Log.w(TAG, "Acoustic echo cancellation could not be disabled.", e);
		}
		acousticEchoCanceler.release();
		acousticEchoCanceler = null;
	}
}
