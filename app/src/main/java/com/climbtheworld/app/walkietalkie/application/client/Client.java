package com.climbtheworld.app.walkietalkie.application.client;

import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.application.audiotools.PlaybackThread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
	public final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	public UiClient uiClient;
	public PlaybackThread playbackThread;
	String address;
	ClientType type;

	public Client(ClientType type, String address) {
		this.type = type;
		this.address = address;
		playbackThread = new PlaybackThread(queue);
		playbackThread.start();
		uiClient = new UiClient(type, address);
	}
}
