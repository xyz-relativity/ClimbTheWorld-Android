package com.climbtheworld.app.walkietalkie.application.client;

import com.climbtheworld.app.walkietalkie.ITransportClient;
import com.climbtheworld.app.walkietalkie.application.audiotools.PlaybackThread;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
	public final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	private final String clientUUID;
	public PlaybackThread playbackThread;
	TreeSet<ITransportClient> transportClientSet =
			new TreeSet<>(Comparator.comparing(ITransportClient::getType));
	private int distance = -1;
	private String callSign = "";
	public Client(String clientUUID, String callSign, ITransportClient client) {
		this.clientUUID = clientUUID;
		this.callSign = callSign;
		this.transportClientSet.add(client);
		playbackThread = new PlaybackThread(queue);
		playbackThread.start();
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	public UiClient getUiClient() {
		return new UiClient(clientUUID, callSign, transportClientSet.first().getType(), distance);
	}
}
