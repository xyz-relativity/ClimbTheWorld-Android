package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkServiceDiscoveryLayerBackend implements INetworkLayerBackend {
	private static final String TAG = "NDSPeerDiscovery";
	// Service Type must be in the format "_<protocol>._<transportlayer>"
	private static final String SERVICE_TYPE = "_walkie._udp.";
	private final Context parent;
	private final IEventListener clientEventListener;
	String serviceName = "CTW-WalkieTalkie-";

	private final NsdManager nsdManager;

	private NsdManager.RegistrationListener registrationListener;
	private NsdManager.DiscoveryListener discoveryListener;

	private final AtomicBoolean isDiscoveryActive = new AtomicBoolean(false);

	public NetworkServiceDiscoveryLayerBackend(Context parent, INetworkLayerBackend.IEventListener clientEventListener) {
		this.parent = parent;
		this.clientEventListener = clientEventListener;
		serviceName += android.os.Build.MODEL.replaceAll("\\s", "");
		nsdManager = (NsdManager) parent.getSystemService(Context.NSD_SERVICE);
	}

	@Override
	public void startServer() {
		Handler handler = new Handler(Looper.getMainLooper());
		// Append a unique suffix to the service name for this device
		handler.postDelayed(() -> {
			Log.d(TAG, "Starting discovery and broadcast...");
			int port = 0;
			try {
				ServerSocket serverSocket = null;
				serverSocket = new ServerSocket(0);
				port = serverSocket.getLocalPort();
				// We don't need the socket itself, just the port, so close it.
				serverSocket.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}


			registerService(port);
			initializeDiscoveryListener();
			nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
			isDiscoveryActive.set(true);
		}, 500);
	}

	@Override
	public void stopServer() {
		Log.d(TAG, "Stopping discovery and broadcast...");
		if (registrationListener != null) {
			nsdManager.unregisterService(registrationListener);
			registrationListener = null;
		}
		if (discoveryListener != null) {
			nsdManager.stopServiceDiscovery(discoveryListener);
			discoveryListener = null;
		}
		isDiscoveryActive.set(false);
	}

	public void registerService(int port) {
		NsdServiceInfo serviceInfo = new NsdServiceInfo();
		serviceInfo.setServiceName(serviceName);
		serviceInfo.setServiceType(SERVICE_TYPE);
		serviceInfo.setPort(port);

		serviceInfo.setAttribute("id", serviceName);
		serviceInfo.setAttribute("name", serviceName);
		serviceInfo.setAttribute("type", serviceName);
		serviceInfo.setAttribute("protocol", serviceName);

		initializeRegistrationListener();

		nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
	}

	private void initializeRegistrationListener() {
		registrationListener = new NsdManager.RegistrationListener() {
			@Override
			public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
				// Save the service name.
				String registeredServiceName = NsdServiceInfo.getServiceName();
				Log.d(TAG, "Service registered: " + registeredServiceName);
			}

			@Override
			public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				Log.e(TAG, "Service registration failed: Error code: " + errorCode);
			}

			@Override
			public void onServiceUnregistered(NsdServiceInfo arg0) {
				Log.d(TAG, "Service unregistered: " + arg0.getServiceName());
			}

			@Override
			public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				Log.e(TAG, "Service unregistration failed: Error code: " + errorCode);
			}
		};
	}

	private void initializeDiscoveryListener() {
		discoveryListener = new NsdManager.DiscoveryListener() {
			@Override
			public void onDiscoveryStarted(String regType) {
				Log.d(TAG, "Service discovery started");
			}

			@Override
			public void onServiceFound(NsdServiceInfo service) {
				Log.d(TAG, "Service discovery success: " + service);
				// A service was found! Do not resolve here.
				// The NSD service will resolve the service for you which may take a second.
				if (!service.getServiceType().equals(SERVICE_TYPE)) {
					Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
				} else if (service.getServiceName().equals(serviceName)) {
					Log.d(TAG, "Same machine: " + serviceName);
				} else {
					// Resolve the service to get its IP address and port.
					nsdManager.resolveService(service, createResolveListener(service.getServiceName()));
				}
			}

			@Override
			public void onServiceLost(NsdServiceInfo service) {
				Log.e(TAG, "service lost: " + service);
				clientEventListener.onClientDisconnected(service.getHost());
			}

			@Override
			public void onDiscoveryStopped(String serviceType) {
				Log.i(TAG, "Discovery stopped: " + serviceType);
			}

			@Override
			public void onStartDiscoveryFailed(String serviceType, int errorCode) {
				Log.e(TAG, "Discovery failed: Error code:" + errorCode);
				nsdManager.stopServiceDiscovery(this);
			}

			@Override
			public void onStopDiscoveryFailed(String serviceType, int errorCode) {
				Log.e(TAG, "Stop Discovery failed: Error code:" + errorCode);
				nsdManager.stopServiceDiscovery(this);
			}
		};
	}

	// We create a new ResolveListener for each service we find.
	private NsdManager.ResolveListener createResolveListener(final String serviceNameKey) {
		return new NsdManager.ResolveListener() {
			@Override
			public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
				Log.e(TAG, "Resolve failed: " + errorCode);
			}

			@Override
			public void onServiceResolved(NsdServiceInfo serviceInfo) {
				Log.d(TAG, "Resolve Succeeded. " + serviceInfo);
				clientEventListener.onClientConnected(serviceInfo.getHost());
			}
		};
	}
}
