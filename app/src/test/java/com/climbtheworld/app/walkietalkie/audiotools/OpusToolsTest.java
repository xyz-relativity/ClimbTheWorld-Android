package com.climbtheworld.app.walkietalkie.application.audiotools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OpusToolsTest {
	@Test
	public void createOpusHeaderBuildsMonoVersionOneHeader() {
		ByteBuffer header = OpusTools.createOpusHeader().order(ByteOrder.LITTLE_ENDIAN);
		byte[] signature = new byte[8];
		header.get(signature);

		assertArrayEquals(new byte[]{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}, signature);
		assertEquals(1, header.get());
		assertEquals(1, header.get());
		assertEquals(0, header.getShort());
		assertEquals(IRecordingListener.AUDIO_SAMPLE_RATE, header.getInt());
		assertEquals(0, header.getShort());
		assertEquals(0, header.get());
		assertEquals(0, header.remaining());
	}
}
