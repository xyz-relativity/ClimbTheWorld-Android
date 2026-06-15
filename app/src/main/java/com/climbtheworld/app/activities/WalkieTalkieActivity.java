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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.views.dialogs.WalkieTalkieSettingsDialogue;
import com.climbtheworld.app.walkietalkie.frontend.UiClient;
import com.climbtheworld.app.walkietalkie.frontend.WalkietalkieServiceController;
import com.climbtheworld.app.walkietalkie.frontend.states.HandsfreeState;
import com.climbtheworld.app.walkietalkie.frontend.states.PushToTalkState;

import java.util.Collections;
import java.util.List;

import needle.Needle;

public class WalkieTalkieActivity extends AppCompatActivity {
	SwitchCompat handsFree;
	private Configs configs;
	private ListView channelListView;
	private View noBuddiesFound;
	private String callSign;
	private String channel;
	private WalkietalkieServiceController serviceController;
	private final BaseAdapter clientListAdapter = new BaseAdapter() {
		@Override
		public int getCount() {
			return getUiClientList().size();
		}

		@Override
		public Object getItem(int position) {
			return getUiClientList().get(position);
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

			UiClient client = getUiClientList().get(position);

			((ImageView) convertView.findViewById(R.id.imageIcon)).setImageDrawable(
					AppCompatResources.getDrawable(WalkieTalkieActivity.this, client.type.icoRes));

			((TextView) convertView.findViewById(R.id.textTypeName)).setText(client.callSign);
			((TextView) convertView.findViewById(R.id.textTypeDescription)).setText(
					client.displayId);

			return convertView;
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
						(granted, denied) -> serviceController.initIntercom(new UiClient.IUiClientEvent() {
							@Override
							public void notifyClientChange() {
								Needle.onMainThread().execute(new Runnable() {
									@Override
									public void run() {
										clientListAdapter.notifyDataSetChanged();
										updateClientViews();
									}
								});
							}
						})).go();

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
		channelListView.setAdapter(clientListAdapter);

		serviceController = new WalkietalkieServiceController(this, configs);
		initConfigs();
	}

	private void initConfigs() {
		callSign = configs.getString(Configs.ConfigKey.intercomCallsign);
		channel = configs.getString(Configs.ConfigKey.intercomChannel);

		if (serviceController != null) {
			serviceController.updateConfigs();
		}

		refreshUI();
	}

	private void refreshUI() {
		handsFree.setChecked(configs.getBoolean(Configs.ConfigKey.intercomHandsFreeSwitch));
		toggleHandsFree(null);

		((TextView) findViewById(R.id.intercomCallsignText)).setText(callSign);
		((TextView) findViewById(R.id.intercomChannelText)).setText(channel);

		if (serviceController != null) {
			serviceController.updateConfigs();
		}
	}

	private void updateClientViews() {
		if (getUiClientList().isEmpty()) {
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

	private List<UiClient> getUiClientList() {
		if (serviceController == null) {
			return Collections.emptyList();
		}

		return serviceController.getUiClientList();
	}
}
