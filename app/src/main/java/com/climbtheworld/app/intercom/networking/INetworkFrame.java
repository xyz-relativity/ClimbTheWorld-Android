package com.climbtheworld.app.intercom.networking;

public interface INetworkFrame {
	enum FrameType {
		DATA((byte) 0),
		SIGNAL((byte) 1);

		public final byte frameByte;

		FrameType(byte frameByte) {
			this.frameByte = frameByte;
		}
	}

	FrameType getFrameType();

	byte[] getData();

	int getLength();

	byte[] toByteArray();
}
