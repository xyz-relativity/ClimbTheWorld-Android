package com.climbtheworld.app.walkietalkie.networking;

import androidx.annotation.NonNull;

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

	private DataFrame() {
		//hide constructor
	}

	public static DataFrame buildFrame(byte[] data, FrameType type) {
		return buildFrame(data, data.length, type);
	}

	public static DataFrame buildFrame(byte[] data, int dataSize, FrameType type) {
		DataFrame result = new DataFrame();
		result.data = Arrays.copyOfRange(data, 0, dataSize);
		result.type = type;
		return result;
	}

	public static DataFrame parseData(byte[] inData) {
		DataFrame result = new DataFrame();
		result.type = FrameType.fromByte(inData[0]);
		result.data = Arrays.copyOfRange(inData, 1, inData.length);
		return result;
	}

	public FrameType getFrameType() {
		return type;
	}

	public byte[] getData() {
		return data;
	}

	public int totalLength() {
		return data.length + 1; //+1 for type
	}

	public byte[] toByteArray() {
		return (ArrayUtils.addAll(new byte[]{type.frameByte}, data));
	}

	@NonNull
	@Override
	public String toString () {
		return type.name() + " / " + new String(data);
	}
}
