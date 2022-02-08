package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercom.networking.p2pwifi.P2PWiFiManager;
import com.climbtheworld.app.intercom.networking.wifi.LanManager;
import com.climbtheworld.app.intercom.states.HandsfreeState;
import com.climbtheworld.app.intercom.states.IInterconState;
import com.climbtheworld.app.intercom.states.InterconState;
import com.climbtheworld.app.intercom.states.PushToTalkState;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.views.TextViewSwitcher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class IntercomActivity extends AppCompatActivity implements IClientEventListener, IRecordingListener {
	public static final UUID myUUID = UUID.randomUUID();
	private static final List<String> PERMISSIONS = new ArrayList<>(Arrays.asList(Manifest.permission.BLUETOOTH_CONNECT));

	private IInterconState activeState;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;
	SwitchCompat handsFree;

	private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	private ListView channelListView;
	private PlaybackThread playbackThread;

	private final DataFrame dataFrame = new DataFrame();

	private LanManager lanManager;
	private BluetoothManager bluetoothManager;
	private P2PWiFiManager p2pWifiManager;
	List<Client> clients = new LinkedList<>();
	private String callSign;
	private String channel;

	private class Client {
		public Client(IClientEventListener.ClientType type, String address, String uuid) {
			this.type = type;
			this.address = address;
			this.uuid = uuid;
		}

		String address;
		String uuid;
		String Name;
		IClientEventListener.ClientType type;
	}

	private final BaseAdapter adapter = new BaseAdapter() {
		@Override
		public int getCount() {
			return clients.size();
		}

		@Override
		public Object getItem(int position) {
			return clients.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = View.inflate(IntercomActivity.this, R.layout.list_item_intercomm_client, null);
			}

			Client client = clients.get(position);

			((ImageView)convertView.findViewById(R.id.imageIcon)).setImageDrawable(AppCompatResources.getDrawable(IntercomActivity.this, client.type.icoRes));

			((TextView)convertView.findViewById(R.id.textTypeName)).setText(client.Name);
			((TextView)convertView.findViewById(R.id.textTypeDescription)).setText(client.address);

			return convertView;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intercom);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
				.withRationales(getString(R.string.intercom_audio_permission_rational)) //optional
				.go();

		configs = Configs.instance(this);

		initNetwork();

		handsFree = findViewById(R.id.handsFreeSwitch);
		handsFree.setChecked(configs.getBoolean(Configs.ConfigKey.handsFreeSwitch));
		handsFree.setOnClickListener(this::toggleHandsFree);

		findViewById(R.id.connectMenuLayout).setOnClickListener(this::onMenuClick);

		PowerManager pm = (PowerManager) getSystemService(IntercomActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
	}

	private void initNetwork() {
		playbackThread = new PlaybackThread(queue);
		Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

		channelListView = findViewById(R.id.listChannel);
		channelListView.setAdapter(adapter);

		lanManager = new LanManager(this, this);
		bluetoothManager = new BluetoothManager(this, this);
		p2pWifiManager = new P2PWiFiManager(this, this);

		callSign = configs.getString(Configs.ConfigKey.callsign);
		channel = configs.getString(Configs.ConfigKey.channel);

		new TextViewSwitcher(this, findViewById(R.id.callsignLayout), callSign, new TextViewSwitcher.ISwitcherCallback() {
			@Override
			public void onChange(String value) {
				callSign = value;
				configs.setString(Configs.ConfigKey.callsign, value);
				clientUpdated("UPDATE");
			}
		});

		new TextViewSwitcher(this, findViewById(R.id.channelLayout), channel, new TextViewSwitcher.ISwitcherCallback() {
			@Override
			public void onChange(String value) {
				channel = value;
				configs.setString(Configs.ConfigKey.channel, value);
				clientUpdated("UPDATE");
			}
		});
	}

	@Override
	public void onData(DataFrame data) {
		if (data.getFrameType() == DataFrame.FrameType.DATA) {
			queue.offer(data.getData());
		}

		if (data.getFrameType() == DataFrame.FrameType.SIGNAL) {
			String[] dataStr = new String(data.getData()).split("\\|", 2);
			String[] control = dataStr[0].split(" ");
			String command = control[0];
			String uuid = control[1];

			String name = dataStr[1];

			for (Client client : clients) {
				if (client.uuid.equalsIgnoreCase(uuid)) {
					client.Name = name;
					break;
				}
			}
			notifyChange();

			if (command.equalsIgnoreCase("REFRESH")) {
				clientUpdated("UPDATE");
			}
		}
	}

	@Override
	public void onClientConnected(ClientType type, String address, String uuid) {
		clients.add(new Client(type, address, uuid));
		clientUpdated("REFRESH");
		notifyChange();
	}

	@Override
	public void onClientDisconnected(ClientType type, String address, String uuid) {
		for (Client client : clients) {
			if (client.address.equalsIgnoreCase(address)) {
				clients.remove(client);
				break;
			}
		}
		notifyChange();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_HEADSETHOOK:
				handsFreeToggle();
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private void handsFreeToggle() {
		SwitchCompat handsFree = findViewById(R.id.handsFreeSwitch);
		handsFree.toggle();
		toggleHandsFree(null);
	}

	@Override
	protected void onStart() {
		super.onStart();
		wakeLock.acquire();
		lanManager.onStart();
		bluetoothManager.onStart();
		p2pWifiManager.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		toggleHandsFree(null);
		lanManager.onResume();
		bluetoothManager.onResume();
		p2pWifiManager.onResume();
	}

	@Override
	protected void onPause() {
		lanManager.onPause();
		bluetoothManager.onPause();
		p2pWifiManager.onPause();

		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (wakeLock.isHeld()) {
			wakeLock.release();
		}

		playbackThread.stopPlayback();

		lanManager.onDestroy();
		bluetoothManager.onDestroy();
		p2pWifiManager.onDestroy();

		activeState.finish();
		super.onDestroy();
	}

	private void toggleHandsFree(View v) {
		if (activeState != null) {
			activeState.finish();
		}
		configs.setBoolean(Configs.ConfigKey.handsFreeSwitch, handsFree.isChecked());

		if (handsFree.isChecked()) {
			findViewById(R.id.pushToTalkButton).setVisibility(View.GONE);
			activeState = new HandsfreeState(this);
		} else {
			findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
			activeState = new PushToTalkState(this);
		}
		((InterconState) activeState).addListener(this);
	}

	public void onMenuClick(View v) {
		//Creating the instance of PopupMenu
		PopupMenu popup = new PopupMenu(IntercomActivity.this, v);
		//Inflating the Popup using xml file
		popup.getMenuInflater().inflate(R.menu.interconn_options, popup.getMenu());

		popup.getMenu().findItem(R.id.wifiDirectConnectSettings).setEnabled(isWifiDirectSupported());

		//registering popup with OnMenuItemClickListener
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent menuIntent;
				switch (item.getItemId()) {
					case R.id.handsFreeToggle:
						handsFreeToggle();
						return true;

					case R.id.wifiSettings:
						menuIntent = new Intent();
						menuIntent.setAction(Settings.ACTION_WIFI_SETTINGS);
						startActivity(menuIntent);
						return true;

					case R.id.wifiDirectConnectSettings:
						menuIntent = new Intent(Intent.ACTION_MAIN, null);
						menuIntent.addCategory(Intent.CATEGORY_LAUNCHER);
						final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.wifi.p2p.WifiP2pSettings");
						menuIntent.setComponent(cn);
						menuIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(menuIntent);
						return true;
					case R.id.bluetoothConnectSettings:
						menuIntent = new Intent();
						menuIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
						startActivity(menuIntent);
						return true;
				}
				return false;
			}
		});
		popup.show();//showing popup menu
	}

	private void notifyChange() {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onRecordingStarted() {

	}

	@Override
	public void onRawAudio(byte[] frame, int numberOfReadBytes) {
		sendData(dataFrame.setFields(frame, DataFrame.FrameType.DATA));
	}

	@Override
	public void onAudio(final byte[] frame, int numberOfReadBytes, double energy, double rms) {

	}

	@Override
	public void onRecordingDone() {

	}

	private void clientUpdated(String command) {
		sendData(dataFrame.setFields((command + " " + myUUID + "|" + callSign).getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.SIGNAL));
	}

	private void sendData(DataFrame frame) {
		lanManager.sendData(frame);
		bluetoothManager.sendData(frame);
	}

	private boolean isWifiDirectSupported() {
		PackageManager pm = this.getPackageManager();
		FeatureInfo[] features = pm.getSystemAvailableFeatures();
		for (FeatureInfo info : features) {
			if (info != null && info.name != null && info.name.equalsIgnoreCase("android.hardware.wifi.direct")) {
				return true;
			}
		}
		return false;
	}
}
