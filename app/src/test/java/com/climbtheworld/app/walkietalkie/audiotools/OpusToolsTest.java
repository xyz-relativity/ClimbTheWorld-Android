package com.climbtheworld.app.walkietalkie.audiotools;

import org.concentus.OpusEncoder;
import org.concentus.OpusException;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class OpusToolsTest {

	public static class WavFormat {
		public final int sampleRate;
		public final int numChannels;
		public final int bitsPerSample;
		public final int totalAudioDataSize;

		public WavFormat(int sampleRate, int numChannels, int bitsPerSample, int totalAudioDataSize) {
			this.sampleRate = sampleRate;
			this.numChannels = numChannels;
			this.bitsPerSample = bitsPerSample;
			this.totalAudioDataSize = totalAudioDataSize;
		}
	}

	public static class Result {
		public WavFormat wavFormat;
		public byte[] data;

		public Result(WavFormat wavFormat, byte[] data) {
			this.wavFormat = wavFormat;
			this.data = data;
		}
	}

	public static Result readWavFile(File wavFile) throws IOException {
		FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(wavFile);

			// Read the WAV header (first 44 bytes)
			byte[] headerBytes = new byte[44];
			inputStream.read(headerBytes, 0, 44);

			// Parse header info using ByteBuffer for Little-Endian order
			ByteBuffer byteBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);

			// Skip unnecessary header fields
			byteBuffer.position(16);

			int subchunk1Size = byteBuffer.getInt(); // 16 for PCM
			byteBuffer.position(20);

			short audioFormat = byteBuffer.getShort(); // 1 for PCM
			short numChannels = byteBuffer.getShort();
			int sampleRate = byteBuffer.getInt();
			byteBuffer.position(34);
			short bitsPerSample = byteBuffer.getShort();

			// Find the 'data' sub-chunk
			byteBuffer.position(36);
			byte[] dataChunkHeader = new byte[4];
			byteBuffer.get(dataChunkHeader, 0, 4);
			String dataChunkId = new String(dataChunkHeader);

			int totalAudioDataSize;
			if ("data".equals(dataChunkId)) {
				totalAudioDataSize = byteBuffer.getInt();
			} else {
				// Handle optional "LIST" or other sub-chunks
				// A simplified approach is to skip remaining chunks until "data" is found
				int currentPosition = 36;
				while (inputStream.available() > 0) {
					currentPosition += 4;
					byte[] chunkSizeBuffer = new byte[4];
					inputStream.read(chunkSizeBuffer);
					int chunkSize = ByteBuffer.wrap(chunkSizeBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

					byte[] nextChunkIdBuffer = new byte[4];
					inputStream.read(nextChunkIdBuffer);
					if ("data".equals(new String(nextChunkIdBuffer))) {
						byte[] dataSizeBuffer = new byte[4];
						inputStream.read(dataSizeBuffer);
						totalAudioDataSize = ByteBuffer.wrap(dataSizeBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
						break;
					} else {
						inputStream.skip(chunkSize - 4); // Skip the rest of the sub-chunk
					}
				}
				totalAudioDataSize = inputStream.available(); // Assume the rest is audio data
			}

			// Read the raw audio data
			byte[] audioData = new byte[totalAudioDataSize];
			inputStream.read(audioData, 0, totalAudioDataSize);

			WavFormat format = new WavFormat(sampleRate, numChannels, bitsPerSample, totalAudioDataSize);
			return new Result(format, audioData);

		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	public static List<short[]> getFrames(WavFormat wavFormat, byte[] audioData) {
		List<short[]> frames = new ArrayList<>();
		int bytesPerSample = wavFormat.bitsPerSample / 8;
		int bytesPerFrame = bytesPerSample * wavFormat.numChannels;

		ByteBuffer byteBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN);

		while (byteBuffer.remaining() >= bytesPerFrame) {
			short[] frame = new short[wavFormat.numChannels];
			for (int i = 0; i < wavFormat.numChannels; i++) {
				switch (wavFormat.bitsPerSample) {
					case 8:
						frame[i] = byteBuffer.get(); // Unsigned 8-bit to short
						break;
					case 16:
						frame[i] = byteBuffer.getShort();
						break;
					// Additional cases for 24-bit or 32-bit if needed
				}
			}
			frames.add(frame);
		}

		return frames;
	}

	@Test
	public void getEncoder() throws IOException, OpusException {
		Base64.Encoder base64Encoder = Base64.getEncoder();
		OpusEncoder encoder = OpusTools.getEncoder();
		File wavFile = new File("/media/data/xyz/devel/projects/climbing/ClimbTheWorld/ClimbTheWorld/app/src/test/java/com/climbtheworld/app/walkietalkie/audiotools/walkie_over.wav");

		FileWriter opusSamples = new FileWriter("/media/data/xyz/devel/projects/climbing/ClimbTheWorld/ClimbTheWorld/app/src/test/java/com/climbtheworld/app/walkietalkie/audiotools/end_bleep.b64");

		Result result = readWavFile(wavFile);

		List<short[]> frames = getFrames(result.wavFormat, result.data);

		byte[] dataEncoded = new byte[320];
		int i = 0;
		short[] sample = new short[320];
		for (short[] frame: frames) {
			sample[i] = frame[0];
			if (i < (sample.length - 1)) {
				i ++;
			} else {
				i = 0;
				int bitesEncoded = encoder.encode(sample, 0, sample.length, dataEncoded, 0, dataEncoded.length);

				byte[] trimmedEncoded = Arrays.copyOfRange(dataEncoded, 0, bitesEncoded);

				opusSamples.write(base64Encoder.encodeToString(trimmedEncoded) + "\n");
			}
		}
	}
}