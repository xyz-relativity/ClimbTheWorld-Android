package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.BlockingQueue;

public class PlaybackThread extends Thread {
	private static final String TAG = PlaybackThread.class.getSimpleName();
	private final BlockingQueue<byte[]> queue;
	private volatile boolean isPlaying = true;

	public PlaybackThread(BlockingQueue<byte[]> queue) {
		this.queue = queue;
	}

	public void stopPlayback() {
		isPlaying = false;
		queue.add(new byte[0]); // Wake the thread up.
	}

	@Override
	public void run() {
		AudioAttributes audioAttributes = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.build();
		AudioTrack track = null;
		OpusTools.Decoder decoder = null;
		int playbackSampleRate = 0;
		int playbackChannelCount = 0;

		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

		try {
			decoder = OpusTools.createDecoder();

			while (isPlaying) {
				byte[] packet;
				try {
					packet = queue.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				if (!isPlaying) {
					break;
				}

				try {
					OpusTools.DecodedAudio decodedAudio = decoder.decode(packet);
					if (decodedAudio.getSamples().isEmpty()) {
						continue;
					}
					if (decodedAudio.getPcmEncoding() != AudioFormat.ENCODING_PCM_16BIT) {
						throw new IllegalStateException("Unsupported Opus decoder PCM encoding: "
								+ decodedAudio.getPcmEncoding());
					}

					if (track == null
							|| playbackSampleRate != decodedAudio.getSampleRate()
							|| playbackChannelCount != decodedAudio.getChannelCount()) {
						releaseTrack(track);
						track = null;
						track = createTrack(audioAttributes, decodedAudio.getSampleRate(),
								decodedAudio.getChannelCount());
						track.play();
						playbackSampleRate = decodedAudio.getSampleRate();
						playbackChannelCount = decodedAudio.getChannelCount();
					}

					for (short[] samples : decodedAudio.getSamples()) {
						track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
					}
				} catch (IllegalArgumentException | IllegalStateException e) {
					Log.w(TAG, "Unable to decode an Opus audio packet.", e);
				}
			}
		} catch (IllegalStateException e) {
			Log.w(TAG, "Opening playback stream failed.", e);
		} finally {
			if (decoder != null) {
				decoder.close();
			}
			releaseTrack(track);
		}
	}

	private static AudioTrack createTrack(AudioAttributes audioAttributes, int sampleRate,
	                                      int channelCount) {
		int channelMask;
		if (channelCount == 1) {
			channelMask = AudioFormat.CHANNEL_OUT_MONO;
		} else if (channelCount == 2) {
			channelMask = AudioFormat.CHANNEL_OUT_STEREO;
		} else {
			throw new IllegalStateException("Unsupported Opus decoder channel count: "
					+ channelCount);
		}

		int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask,
				AudioFormat.ENCODING_PCM_16BIT);
		if (minBufferSize <= 0) {
			throw new IllegalStateException("Unsupported playback format: " + sampleRate
					+ " Hz, " + channelCount + " channel(s).");
		}

		AudioFormat audioFormat = new AudioFormat.Builder()
				.setSampleRate(sampleRate)
				.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
				.setChannelMask(channelMask)
				.build();
		AudioTrack track = new AudioTrack.Builder()
				.setAudioAttributes(audioAttributes)
				.setAudioFormat(audioFormat)
				.setBufferSizeInBytes(minBufferSize)
				.setTransferMode(AudioTrack.MODE_STREAM)
				.build();
		if (track.getState() != AudioTrack.STATE_INITIALIZED) {
			track.release();
			throw new IllegalStateException("AudioTrack failed to initialize.");
		}
		Log.i(TAG, "Playing decoded Opus audio at " + sampleRate + " Hz with "
				+ channelCount + " channel(s).");
		return track;
	}

	private static void releaseTrack(AudioTrack track) {
		if (track == null) {
			return;
		}
		if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
			try {
				track.stop();
			} catch (IllegalStateException e) {
				Log.w(TAG, "The playback stream was already stopped.", e);
			}
		}
		track.release();
	}
}
