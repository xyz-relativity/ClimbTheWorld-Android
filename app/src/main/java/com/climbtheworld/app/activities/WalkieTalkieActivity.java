package com.climbtheworld.app.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.views.dialogs.WalkieTalkieSettingsDialogue;
import com.climbtheworld.app.walkietalkie.IClientEventListener;
import com.climbtheworld.app.walkietalkie.IntercomServiceController;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.states.HandsfreeState;
import com.climbtheworld.app.walkietalkie.states.PushToTalkState;
import com.climbtheworld.app.walkietalkie.states.WalkietalkieHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import needle.Needle;

public class WalkieTalkieActivity extends AppCompatActivity implements IClientEventListener {
	final static String UPDATE_COMMAND = "UPDATE"; //last message for the info exchange
	final static String CONNECT_COMMAND = "CONNECT"; // receiver will send back an update.

	private WalkietalkieHandler activeState;
	private Configs configs;
	SwitchCompat handsFree;

	private ListView channelListView;
	private View noBuddiesFound;

	List<Client> clients = new ArrayList<>();
	private String callSign;
	private String channel;
	private IntercomServiceController serviceController;
	private AudioManager audioManager;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset mBluetoothHeadset;

	private static class Client {
		public Client(IClientEventListener.ClientType type, String address) {
			this.type = type;
			this.address = address;
		}
		String address;
		String Name = "";
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
				convertView = View.inflate(WalkieTalkieActivity.this, R.layout.list_item_intercomm_client, null);
			}

			Client client = clients.get(position);

			((ImageView)convertView.findViewById(R.id.imageIcon)).setImageDrawable(AppCompatResources.getDrawable(WalkieTalkieActivity.this, client.type.icoRes));

			((TextView)convertView.findViewById(R.id.textTypeName)).setText(client.Name);
			((TextView)convertView.findViewById(R.id.textTypeDescription)).setText(client.address);

			return convertView;
		}
	};

	private final BroadcastReceiver bluetoothConnectReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			startBluetoothSCO();
		}
	};

	final BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
		public void onServiceConnected(int profile, BluetoothProfile proxy) {
			Log.d("Audio-Bluetooth","BT Onservice Connected");
			if (profile == BluetoothProfile.HEADSET) {
				mBluetoothHeadset = (BluetoothHeadset) proxy;
			}
		}
		public void onServiceDisconnected(int profile) {
			if (profile == BluetoothProfile.HEADSET) {
				mBluetoothHeadset = null;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_walkie_talkie);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		Ask.on(this)
				.id(503) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.RECORD_AUDIO,
						Manifest.permission.ACCESS_FINE_LOCATION,
						Manifest.permission.ACCESS_COARSE_LOCATION,
						Manifest.permission.BLUETOOTH,
						Manifest.permission.BLUETOOTH_CONNECT,
						Manifest.permission.BLUETOOTH_SCAN,
						Manifest.permission.BLUETOOTH_ADMIN,
						Manifest.permission.NEARBY_WIFI_DEVICES,
						Manifest.permission.ACCESS_WIFI_STATE,
						Manifest.permission.CHANGE_WIFI_STATE,
						Manifest.permission.INTERNET,
						Manifest.permission.MODIFY_AUDIO_SETTINGS,
						Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
				)
				.withRationales(R.string.walkie_talkie_audio_permission_rational,
						R.string.walkie_talkie_allow_location_rational,
						R.string.walkie_talkie_allow_location_rational,
						R.string.walkie_talkie_bluetooth_permission_rational,
						R.string.walkie_talkie_bluetooth_permission_rational,
						R.string.walkie_talkie_bluetooth_permission_rational,
						R.string.walkie_talkie_bluetooth_permission_rational
						) //optional
				.onCompleteListener(new Ask.IOnCompleteListener() {
					@Override
					public void onCompleted(String[] granted, String[] denied) {
						serviceController.initIntercom(WalkieTalkieActivity.this);
					}
				})
				.go();

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		startBluetoothSCO();

		registerReceiver(bluetoothConnectReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));

		configs = Configs.instance(this);

		handsFree = findViewById(R.id.handsFreeSwitch);
		handsFree.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleHandsFree(view);
			}
		});

		findViewById(R.id.ButtonWalkieTalkieMenu).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				WalkieTalkieSettingsDialogue.showConfigDialog(WalkieTalkieActivity.this, new ConfigFragment.OnConfigChangeListener() {
					@Override
					public void onConfigChange() {
						initConfigs();
					}
				});
			}
		});

		noBuddiesFound = findViewById(R.id.messageNoBuddies);
		channelListView = findViewById(R.id.listChannelMembers);
		channelListView.setAdapter(adapter);

		serviceController = new IntercomServiceController(this, configs);
		initConfigs();
	}

	private void initConfigs() {
		callSign = configs.getString(Configs.ConfigKey.intercomCallsign);
		channel = configs.getString(Configs.ConfigKey.intercomChannel);

		serviceController.updateConfigs();

		refreshUI();
	}

	private void startBluetoothSCO() {
		// Start Bluetooth SCO
		audioManager.startBluetoothSco();

		// Request audio focus
		audioManager.requestAudioFocus(focusChange -> {
			// Handle focus change
		}, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		bluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
	}

	private void stopBluetoothSCO() {
		// Stop Bluetooth SCO
		if(audioManager != null)
			audioManager.stopBluetoothSco();

		if(bluetoothAdapter != null && mBluetoothHeadset != null)
			bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);

		// Unregister the BroadcastReceiver
		try {
			unregisterReceiver(bluetoothConnectReceiver);
		} catch (Exception e) {
			Log.d("walkietalkie", "destroy");
		}
	}

	private void refreshUI() {
		handsFree.setChecked(configs.getBoolean(Configs.ConfigKey.intercomHandsFreeSwitch));
		toggleHandsFree(null);

		((TextView)findViewById(R.id.intercomCallsignText)).setText(callSign);
		((TextView)findViewById(R.id.intercomChannelText)).setText(channel);

		clientUpdated(UPDATE_COMMAND);
	}

	@Override
	public void onData(DataFrame data, String sourceAddress) {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				Client crClient = null;
				for (Client client : clients) {
					if (client.address.equalsIgnoreCase(sourceAddress)) {
						crClient = client;
						break;
					}
				}

				if (crClient == null) {
					return;
				}

				if (data.getFrameType() == DataFrame.FrameType.SIGNAL) {
					String[] dataStr = new String(data.getData()).split("\\|", 2);
					if (dataStr.length != 2) {
						return;
					}

					String command = dataStr[0];
					crClient.Name = dataStr[1];
					adapter.notifyDataSetChanged();

					if (command.equalsIgnoreCase(CONNECT_COMMAND)) {
						clientUpdated(UPDATE_COMMAND);
					}
				}
			}
		});
	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				clientUpdated(CONNECT_COMMAND);
				clients.add(new Client(type, address));

				updateClientViews();

				clients.sort(new Comparator<Client>() {
					@Override
					public int compare(Client client, Client t1) {
						return client.Name.compareTo(t1.Name);
					}
				});
				adapter.notifyDataSetChanged();
			}
		});
	}

	@Override
	public void onClientDisconnected(ClientType type, String address) {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				for (Client client : clients) {
					if (client.address.equalsIgnoreCase(address)) {
						clients.remove(client);
						adapter.notifyDataSetChanged();
						break;
					}
				}

				updateClientViews();
			}
		});
	}

	private void updateClientViews() {
		if (clients.isEmpty()) {
			noBuddiesFound.setVisibility(View.VISIBLE);
			channelListView.setVisibility(View.GONE);
		} else {
			noBuddiesFound.setVisibility(View.GONE);
			channelListView.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_HEADSETHOOK:
				handsFree.toggle();
				toggleHandsFree(null);
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onStart() {
		serviceController.onStart();
		super.onStart();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		activeState.finish();
		serviceController.onDestroy();
		stopBluetoothSCO();
	}

	private void toggleHandsFree(View v) {
		if (activeState != null) {
			activeState.finish();
		}
		configs.setBoolean(Configs.ConfigKey.intercomHandsFreeSwitch, handsFree.isChecked());

		if (handsFree.isChecked()) {
			findViewById(R.id.pushToTalkButton).setVisibility(View.GONE);
			activeState = new HandsfreeState(this);
		} else {
			findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
			activeState = new PushToTalkState(this);
		}

		serviceController.setRecordingState(activeState);
	}

	private void clientUpdated(String command) {
		sendData(DataFrame.buildFrame((command + "|" + callSign).getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.SIGNAL));
	}

	private void sendData(DataFrame frame) {
		serviceController.sendData(frame);
	}
}
