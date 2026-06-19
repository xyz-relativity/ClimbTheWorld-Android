package com.climbtheworld.app.walkietalkie.application.client;

import com.climbtheworld.app.walkietalkie.ITransportClient;
import com.climbtheworld.app.walkietalkie.application.audiotools.PlaybackThread;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
	public final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	public UiClient uiClient;
	public PlaybackThread playbackThread;
	ITransportClient transportClient;

	public Client(String clientUUID, ITransportClient client) {
		this.transportClient = client;
		playbackThread = new PlaybackThread(queue);
		playbackThread.start();
		uiClient = new UiClient(clientUUID, transportClient.getType());
	}
}
