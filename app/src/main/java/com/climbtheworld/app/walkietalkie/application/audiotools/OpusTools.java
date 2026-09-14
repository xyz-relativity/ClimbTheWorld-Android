package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OpusTools {
	private static final String TAG = OpusTools.class.getSimpleName();
	private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_OPUS;
	private static final int CHANNEL_COUNT = 1;
	private static final int BIT_RATE = 24000;
	private static final long INPUT_TIMEOUT_US = 50000;
	private static final long OUTPUT_TIMEOUT_US = 20000;

	private OpusTools() {
		// Hide utility class constructor.
	}

	public static Encoder createEncoder() {
		try {
			return new Encoder();
		} catch (IOException e) {
			throw new IllegalStateException("The platform Opus encoder is unavailable.", e);
		}
	}

	public static Decoder createDecoder() {
		try {
			return new Decoder();
		} catch (IOException e) {
			throw new IllegalStateException("The platform Opus decoder is unavailable.", e);
		}
	}

	public static final class Encoder extends Codec {
		private long samplesQueued;

		private Encoder() throws IOException {
			super(true);
		}

		public synchronized List<byte[]> encode(short[] samples, int numberOfSamples) {
			ensureOpen();
			if (numberOfSamples < 0 || numberOfSamples > samples.length) {
				throw new IllegalArgumentException("Invalid PCM sample count: " + numberOfSamples);
			}
			if (numberOfSamples == 0) {
				return Collections.emptyList();
			}

			int sampleOffset = 0;
			while (sampleOffset < numberOfSamples) {
				int inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US);
				if (inputIndex < 0) {
					throw new IllegalStateException("Timed out waiting for an Opus encoder buffer.");
				}

				ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
				if (inputBuffer == null) {
					throw new IllegalStateException("The Opus encoder returned no input buffer.");
				}
				inputBuffer.clear();
				ShortBuffer pcmBuffer = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();
				int sampleCount = Math.min(numberOfSamples - sampleOffset, pcmBuffer.remaining());
				if (sampleCount == 0) {
					throw new IllegalStateException("The Opus encoder input buffer is empty.");
				}
				pcmBuffer.put(samples, sampleOffset, sampleCount);

				long presentationTimeUs = samplesQueued * 1000000L
						/ IRecordingListener.AUDIO_SAMPLE_RATE;
				codec.queueInputBuffer(inputIndex, 0, sampleCount * 2, presentationTimeUs, 0);
				samplesQueued += sampleCount;
				sampleOffset += sampleCount;
			}

			return drainEncodedPackets();
		}

		private List<byte[]> drainEncodedPackets() {
			List<byte[]> packets = new ArrayList<>();
			boolean firstBuffer = true;
			while (true) {
				int outputIndex = codec.dequeueOutputBuffer(bufferInfo,
						firstBuffer ? OUTPUT_TIMEOUT_US : 0);
				if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
					break;
				}
				if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
						|| outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					continue;
				}
				if (outputIndex < 0) {
					continue;
				}

				firstBuffer = false;
				try {
					if (bufferInfo.size > 0
							&& (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
						ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
						if (outputBuffer != null) {
							outputBuffer.position(bufferInfo.offset);
							outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
							byte[] packet = new byte[bufferInfo.size];
							outputBuffer.get(packet);
							packets.add(packet);
						}
					}
				} finally {
					codec.releaseOutputBuffer(outputIndex, false);
				}
			}
			return packets;
		}
	}

	public static final class Decoder extends Codec {
		private long packetsQueued;
		private int outputSampleRate = IRecordingListener.AUDIO_SAMPLE_RATE;
		private int outputChannelCount = CHANNEL_COUNT;
		private int outputPcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

		private Decoder() throws IOException {
			super(false);
		}

		public synchronized DecodedAudio decode(byte[] packet) {
			ensureOpen();
			if (packet.length == 0) {
				return new DecodedAudio(outputSampleRate, outputChannelCount,
						outputPcmEncoding, Collections.emptyList());
			}

			int inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US);
			if (inputIndex < 0) {
				throw new IllegalStateException("Timed out waiting for an Opus decoder buffer.");
			}

			ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
			if (inputBuffer == null || inputBuffer.capacity() < packet.length) {
				throw new IllegalStateException("The Opus packet exceeds the decoder input buffer.");
			}
			inputBuffer.clear();
			inputBuffer.put(packet);
			codec.queueInputBuffer(inputIndex, 0, packet.length, packetsQueued++, 0);

			return drainDecodedAudio();
		}

		private DecodedAudio drainDecodedAudio() {
			List<short[]> decodedAudio = new ArrayList<>();
			boolean firstBuffer = true;
			while (true) {
				int outputIndex = codec.dequeueOutputBuffer(bufferInfo,
						firstBuffer ? OUTPUT_TIMEOUT_US : 0);
				if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
					break;
				}
				if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					MediaFormat outputFormat = codec.getOutputFormat();
					outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
					outputChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
					if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
						outputPcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
					}
					Log.i(TAG, "Platform Opus decoder output: " + outputSampleRate + " Hz, "
							+ outputChannelCount + " channel(s), PCM encoding "
							+ outputPcmEncoding + ".");
					continue;
				}
				if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					continue;
				}
				if (outputIndex < 0) {
					continue;
				}

				firstBuffer = false;
				try {
					if (bufferInfo.size > 0) {
						if ((bufferInfo.size & 1) != 0) {
							throw new IllegalStateException("The Opus decoder returned partial PCM data.");
						}
						ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
						if (outputBuffer != null) {
							outputBuffer.position(bufferInfo.offset);
							outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
							ShortBuffer pcmBuffer = outputBuffer.slice()
									.order(ByteOrder.nativeOrder()).asShortBuffer();
							short[] samples = new short[pcmBuffer.remaining()];
							pcmBuffer.get(samples);
							decodedAudio.add(samples);
						}
					}
				} finally {
					codec.releaseOutputBuffer(outputIndex, false);
				}
			}
			return new DecodedAudio(outputSampleRate, outputChannelCount,
					outputPcmEncoding, decodedAudio);
		}
	}

	public static final class DecodedAudio {
		private final int sampleRate;
		private final int channelCount;
		private final int pcmEncoding;
		private final List<short[]> samples;

		DecodedAudio(int sampleRate, int channelCount, int pcmEncoding, List<short[]> samples) {
			this.sampleRate = sampleRate;
			this.channelCount = channelCount;
			this.pcmEncoding = pcmEncoding;
			this.samples = samples;
		}

		public int getSampleRate() {
			return sampleRate;
		}

		public int getChannelCount() {
			return channelCount;
		}

		public int getPcmEncoding() {
			return pcmEncoding;
		}

		public List<short[]> getSamples() {
			return samples;
		}
	}

	private abstract static class Codec implements AutoCloseable {
		final MediaCodec codec;
		final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		private boolean closed;

		Codec(boolean encoder) throws IOException {
			codec = encoder
					? MediaCodec.createEncoderByType(MIME_TYPE)
					: MediaCodec.createDecoderByType(MIME_TYPE);
			try {
				MediaFormat format = encoder ? createEncoderFormat() : createDecoderFormat();
				codec.configure(format, null, null,
						encoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
				codec.start();
			} catch (RuntimeException e) {
				codec.release();
				throw e;
			}
		}

		void ensureOpen() {
			if (closed) {
				throw new IllegalStateException("The Opus codec is closed.");
			}
		}

		@Override
		public synchronized void close() {
			if (closed) {
				return;
			}
			closed = true;
			try {
				codec.stop();
			} catch (RuntimeException e) {
				Log.w(TAG, "The platform Opus codec was already stopped or reclaimed.", e);
			}
			try {
				codec.release();
			} catch (RuntimeException e) {
				Log.w(TAG, "The platform Opus codec was already released.", e);
			}
		}
	}

	private static MediaFormat createEncoderFormat() {
		MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE,
				IRecordingListener.AUDIO_SAMPLE_RATE, CHANNEL_COUNT);
		format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
		format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
		return format;
	}

	private static MediaFormat createDecoderFormat() {
		MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE,
				IRecordingListener.AUDIO_SAMPLE_RATE, CHANNEL_COUNT);
		format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
		format.setByteBuffer("csd-0", createOpusHeader());
		format.setByteBuffer("csd-1", createLongBuffer(0));
		format.setByteBuffer("csd-2", createLongBuffer(0));
		return format;
	}

	static ByteBuffer createOpusHeader() {
		ByteBuffer header = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
		header.put(new byte[]{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'});
		header.put((byte) 1);
		header.put((byte) CHANNEL_COUNT);
		header.putShort((short) 0);
		header.putInt(IRecordingListener.AUDIO_SAMPLE_RATE);
		header.putShort((short) 0);
		header.put((byte) 0);
		header.flip();
		return header;
	}

	private static ByteBuffer createLongBuffer(long value) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
		buffer.putLong(value);
		buffer.flip();
		return buffer;
	}
}
