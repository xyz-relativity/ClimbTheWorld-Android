package com.climbtheworld.app.intercom.networking;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public class DataFrame {
	public enum FrameType {
		UNKNOWN((byte) 0),
		NETWORK((byte) 1),
		SIGNAL((byte) 2),
		DATA((byte) 3);

		public final byte frameByte;

		FrameType(byte frameByte) {
			this.frameByte = frameByte;
		}

		public static FrameType fromByte(byte b) {
			for (FrameType type: FrameType.values()) {
				if (b == type.frameByte) {
					return type;
				}
			}
			return UNKNOWN;
		}
	}

	byte[] data;
	FrameType type;

	public DataFrame setFields(byte[] data, FrameType type) {
		this.data = data;
		this.type = type;
		return this;
	}

	public DataFrame parseData(byte[] data) {
		type = FrameType.fromByte(data[0]);
		this.data = Arrays.copyOfRange(data, 1, data.length);
		return this;
	}

	public FrameType getFrameType() {
		return type;
	}

	public byte[] getData() {
		return data;
	}

	public int getLength() {
		return data.length + 1;
	}

	public byte[] toByteArray() {
		return (ArrayUtils.addAll(new byte[]{type.frameByte}, data));
	}
}
