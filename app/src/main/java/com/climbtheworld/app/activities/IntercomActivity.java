package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
import com.climbtheworld.app.intercom.IntercomBackgroundService;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.states.HandsfreeState;
import com.climbtheworld.app.intercom.states.IInterconState;
import com.climbtheworld.app.intercom.states.PushToTalkState;
import com.climbtheworld.app.utils.views.TextViewSwitcher;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import needle.Needle;

public class IntercomActivity extends AppCompatActivity implements IClientEventListener {
	private IInterconState activeState;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;
	SwitchCompat handsFree;

	private ListView channelListView;

	private final DataFrame dataFrame = new DataFrame();

	List<Client> clients = new LinkedList<>();
	private String callSign;
	private String channel;
	private Intent intercomServiceIntent;

	private static class Client {
		public Client(IClientEventListener.ClientType type, String address) {
			this.type = type;
			this.address = address;
		}
		String address;
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
				.onCompleteListener(new Ask.IOnCompleteListener() {
					@Override
					public void onCompleted(String[] granted, String[] denied) {
						initNetwork();
					}
				})
				.go();

		configs = Configs.instance(this);

		handsFree = findViewById(R.id.handsFreeSwitch);
		handsFree.setChecked(configs.getBoolean(Configs.ConfigKey.handsFreeSwitch));
		handsFree.setOnClickListener(this::toggleHandsFree);

		findViewById(R.id.connectMenuLayout).setOnClickListener(this::onMenuClick);

		channelListView = findViewById(R.id.listChannel);
		channelListView.setAdapter(adapter);

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

		PowerManager pm = (PowerManager) getSystemService(IntercomActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
	}

	private void initNetwork() {
		intercomServiceIntent = new Intent(this, IntercomBackgroundService.class);
		bindService(intercomServiceIntent, new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

			}

			@Override
			public void onServiceDisconnected(ComponentName componentName) {

			}
		}, BIND_AUTO_CREATE);
	}

	@Override
	public void onData(DataFrame data, String sourceAddress) {
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
			String[] control = dataStr[0].split(" ");
			String command = control[0];

			crClient.Name = dataStr[1];
			notifyChange();

			if (command.equalsIgnoreCase("REFRESH")) {
				clientUpdated("UPDATE");
			}
		}
	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		clients.add(new Client(type, address));
		clientUpdated("REFRESH");
		notifyChange();
	}

	@Override
	public void onClientDisconnected(ClientType type, String address) {
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
	}

	@Override
	protected void onResume() {
		super.onResume();
		toggleHandsFree(null);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (wakeLock.isHeld()) {
			wakeLock.release();
		}

		stopService(intercomServiceIntent);

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
//		((InterconState) activeState).addListener(this);
	}

	public void onMenuClick(View v) {
		//Creating the instance of PopupMenu
		PopupMenu popup = new PopupMenu(IntercomActivity.this, v);
		//Inflating the Popup using xml file
		popup.getMenuInflater().inflate(R.menu.interconn_options, popup.getMenu());

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

	private void clientUpdated(String command) {
		sendData(dataFrame.setFields((command + "|" + callSign).getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.SIGNAL));
	}

	private void sendData(DataFrame frame) {

	}
}
