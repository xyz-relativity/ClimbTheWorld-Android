package com.climbtheworld.app.walkietalkie.application;

import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.application.audiotools.PlaybackThread;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
	public final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	public final String clientUUID;
	public PlaybackThread playbackThread;
	public int distance = -1;
	public String callSign = "";
	public TreeSet<ITransportLayer> transportClientSet =
			new TreeSet<>(Comparator.comparing(ITransportLayer::getType));

	public Client(String clientUUID, String callSign, ITransportLayer client) {
		this.clientUUID = clientUUID;
		this.callSign = callSign;
		this.transportClientSet.add(client);
		playbackThread = new PlaybackThread(queue);
		playbackThread.start();
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	public void sendData(byte[] data) {
		transportClientSet.first().sendData(data);
	}
}
