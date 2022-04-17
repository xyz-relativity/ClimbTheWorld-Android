package com.climbtheworld.app.activities;

import android.Manifest;
import android.os.Bundle;
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

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.IntercomServiceController;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.states.HandsfreeState;
import com.climbtheworld.app.intercom.states.InterconState;
import com.climbtheworld.app.intercom.states.PushToTalkState;
import com.climbtheworld.app.utils.views.dialogs.IntercomSettingsDialogue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import needle.Needle;

public class IntercomActivity extends AppCompatActivity implements IClientEventListener {
	final static String UPDATE_COMMAND = "UPDATE"; //last message for the info exchange
	final static String CONNECT_COMMAND = "CONNECT"; // receiver will send back an update.

	private InterconState activeState;
	private Configs configs;
	SwitchCompat handsFree;

	private ListView channelListView;
	private View noBuddiesFound;

	List<Client> clients = new ArrayList<>();
	private String callSign;
	private String channel;
	private IntercomServiceController serviceController;

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

		configs = Configs.instance(this);

		handsFree = findViewById(R.id.handsFreeSwitch);
		handsFree.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleHandsFree(view);
			}
		});

		findViewById(R.id.settingsButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				IntercomSettingsDialogue.showConfigDialog(IntercomActivity.this, new ConfigFragment.OnConfigChangeListener() {
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

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.RECORD_AUDIO,
						Manifest.permission.BLUETOOTH_CONNECT,
						Manifest.permission.BLUETOOTH_SCAN,
						Manifest.permission.ACCESS_FINE_LOCATION)
				.withRationales(R.string.intercom_audio_permission_rational,
						R.string.intercom_bluetooth_permission_rational,
						R.string.intercom_bluetooth_permission_rational,
						R.string.intercom_allow_location_rational) //optional
				.onCompleteListener(new Ask.IOnCompleteListener() {
					@Override
					public void onCompleted(String[] granted, String[] denied) {
						serviceController.initIntercom(IntercomActivity.this);
					}
				})
				.go();
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

				Collections.sort(clients, new Comparator<Client>() {
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
		activeState.finish();

		serviceController.onDestroy();

		super.onDestroy();
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
