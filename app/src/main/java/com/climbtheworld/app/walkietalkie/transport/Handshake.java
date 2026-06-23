package com.climbtheworld.app.walkietalkie.transport;

public class Handshake {
	private static final String COMMAND_SEPARATOR = ":";
	public final ConnectionState connectionState;
	public final String data;

	private Handshake(ConnectionState connectionState, String data) {
		this.connectionState = connectionState;
		this.data = data;
	}

	public static Handshake fromData(byte[] data) {
		return fromData(new String(data));
	}

	public static Handshake fromData(String data) {
		String[] split = data.split(COMMAND_SEPARATOR);
		return new Handshake(ConnectionState.valueOf(split[0]), split[1]);
	}

	public static byte[] buildMessage(ConnectionState state, String data) {
		return (state.command + data).getBytes();
	}

	public enum ConnectionState {
		IDENTITY("IDENTITY" + COMMAND_SEPARATOR),
		AUTH("AUTH" + COMMAND_SEPARATOR),
		ACTIVE("MESSAGE" + COMMAND_SEPARATOR),
		DISCONNECTING("BYE!!");

		public final String command;

		ConnectionState(String command) {
			this.command = command;
		}
	}
}
