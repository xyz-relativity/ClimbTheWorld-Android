package com.climbtheworld.app.activities;

import android.Manifest;
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
import com.climbtheworld.app.walkietalkie.WalkietalkieServiceController;
import com.climbtheworld.app.walkietalkie.networking.lan.LanController;
import com.climbtheworld.app.walkietalkie.states.HandsfreeState;
import com.climbtheworld.app.walkietalkie.states.PushToTalkState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import needle.Needle;

public class WalkieTalkieActivity extends AppCompatActivity implements IClientEventListener {
	private final static String CALL_SIGN_COMMAND = "CALL_SIGN:";
	private static final String TAG = LanController.class.getSimpleName();
	SwitchCompat handsFree;
	List<Client> clients = new ArrayList<>();
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
				convertView =
						View.inflate(WalkieTalkieActivity.this,
								R.layout.list_item_intercomm_client,
								null);
			}

			Client client = clients.get(position);

			((ImageView) convertView.findViewById(R.id.imageIcon)).setImageDrawable(
					AppCompatResources.getDrawable(WalkieTalkieActivity.this, client.type.icoRes));

			((TextView) convertView.findViewById(R.id.textTypeName)).setText(client.name);
			((TextView) convertView.findViewById(R.id.textTypeDescription)).setText(
					client.displayId);

			return convertView;
		}
	};
	private Configs configs;
	private ListView channelListView;
	private View noBuddiesFound;
	private String callSign;
	private String channel;
	private WalkietalkieServiceController serviceController;

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
				.id(503) // in case you are invoking multiple time Ask from same activity or
				// fragment
				.addPermission(Manifest.permission.RECORD_AUDIO,
						R.string.walkie_talkie_audio_permission_rational)
				.addPermission(Manifest.permission.ACCESS_FINE_LOCATION,
						R.string.walkie_talkie_allow_location_rational)
				.addPermission(Manifest.permission.ACCESS_COARSE_LOCATION,
						R.string.walkie_talkie_allow_location_rational)
				.addPermission(Manifest.permission.BLUETOOTH,
						R.string.walkie_talkie_bluetooth_permission_rational)
				.addPermission(Manifest.permission.BLUETOOTH_CONNECT,
						R.string.walkie_talkie_bluetooth_permission_rational)
				.addPermission(Manifest.permission.BLUETOOTH_SCAN,
						R.string.walkie_talkie_bluetooth_permission_rational)
				.addPermission(Manifest.permission.BLUETOOTH_ADMIN,
						R.string.walkie_talkie_bluetooth_permission_rational)
				.addPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
				.addPermission(Manifest.permission.ACCESS_WIFI_STATE)
				.addPermission(Manifest.permission.CHANGE_WIFI_STATE)
				.addPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
				.addPermission(Manifest.permission.INTERNET)
				.addPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS).onCompleteListener(
						(granted, denied) -> serviceController.initIntercom(WalkieTalkieActivity.this))
				.go();

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
				WalkieTalkieSettingsDialogue.showConfigDialog(WalkieTalkieActivity.this,
						new ConfigFragment.OnConfigChangeListener() {
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

		serviceController = new WalkietalkieServiceController(this, configs);
		initConfigs();
	}

	private void initConfigs() {
		callSign = configs.getString(Configs.ConfigKey.intercomCallsign);
		channel = configs.getString(Configs.ConfigKey.intercomChannel);

		serviceController.updateConfigs();

		refreshUI();
	}

	private void refreshUI() {
		handsFree.setChecked(configs.getBoolean(Configs.ConfigKey.intercomHandsFreeSwitch));
		toggleHandsFree(null);

		((TextView) findViewById(R.id.intercomCallsignText)).setText(callSign);
		((TextView) findViewById(R.id.intercomChannelText)).setText(channel);

		sendControlMessage(CALL_SIGN_COMMAND + callSign);
	}

	@Override
	public void onData(String sourceAddress, byte[] data) {
		// should not receive any raw data here.
	}

	@Override
	public void onControlMessage(String sourceAddress, String message) {
		Log.i(TAG, "Control message from: " + sourceAddress + " received: " + message);
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

				if (message.startsWith(CALL_SIGN_COMMAND)) {
					String[] controlData = message.split(CALL_SIGN_COMMAND);
					crClient.name = controlData[1];
					adapter.notifyDataSetChanged();
				}
			}
		});
	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		Log.i(TAG, "Client connected: " + address);
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				clients.add(new Client(type, address));

				updateClientViews();

				clients.sort(Comparator.comparing(client -> client.name));
				adapter.notifyDataSetChanged();

				sendControlMessage(CALL_SIGN_COMMAND + callSign);
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

		serviceController.onDestroy();
	}

	private void toggleHandsFree(View v) {
		configs.setBoolean(Configs.ConfigKey.intercomHandsFreeSwitch, handsFree.isChecked());

		if (handsFree.isChecked()) {
			findViewById(R.id.pushToTalkButton).setVisibility(View.GONE);
			serviceController.setRecordingState(new HandsfreeState(this));
		} else {
			findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
			serviceController.setRecordingState(new PushToTalkState(this));
		}
	}

	private void sendControlMessage(String message) {
		serviceController.sendControlMessage(message);
	}

	private static class Client {
		String address;
		String displayId;
		String name = "";
		IClientEventListener.ClientType type;

		public Client(IClientEventListener.ClientType type, String address) {
			this.type = type;
			this.address = address;
			this.displayId = address.split("-")[0];
		}
	}
}
