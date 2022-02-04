package com.climbtheworld.app.intercom;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercom.networking.p2pwifi.P2PWiFiManager;
import com.climbtheworld.app.intercom.networking.wifi.LanManager;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class NetworkConnectionAgregator implements IClientEventListener, IRecordingListener {
	public static final UUID myUUID = UUID.randomUUID();
	private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	final Configs configs;
	private final ListView channelListView;
	private final Context context;
	private final PlaybackThread playbackThread;

	private final DataFrame dataFrame = new DataFrame();

	private final LanManager lanManager;
	private final BluetoothManager bluetoothManager;
	private final P2PWiFiManager p2pWifiManager;
	List<Client> clients = new LinkedList<>();
	private String callSign;

	private class Client {
		public Client(ClientType type, String address, String uuid) {
			this.type = type;
			this.address = address;
			this.uuid = uuid;
		}

		String address;
		String uuid;
		String Name;
		ClientType type;
	}

	private enum EditorType {
		CALL_SIGN,
		CHANNEL
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
			convertView = ListViewItemBuilder.getPaddedBuilder(context, convertView, false)
					.setTitle(clients.get(position).Name)
					.setDescription(clients.get(position).address)
					.setIcon(AppCompatResources.getDrawable(context, R.drawable.ic_person))
					.build();

			return convertView;
		}
	};

	public NetworkConnectionAgregator(final AppCompatActivity parent, Configs configs) {
		this.configs = configs;
		playbackThread = new PlaybackThread(queue);
		Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

		this.context = parent;

		channelListView = parent.findViewById(R.id.listChannel);
		channelListView.setAdapter(adapter);

		lanManager = new LanManager(parent, this);

		bluetoothManager = new BluetoothManager(parent, this);

		p2pWifiManager = new P2PWiFiManager(parent, this);

		initEditSwitcher(parent, parent.findViewById(R.id.callsignLayout), Configs.ConfigKey.callsign, EditorType.CALL_SIGN);
		initEditSwitcher(parent, parent.findViewById(R.id.channelLayout), Configs.ConfigKey.channel, EditorType.CHANNEL);
	}

	private void initEditSwitcher(final AppCompatActivity parent, final LinearLayout container, final Configs.ConfigKey configKey, final EditorType type) {
		final TextView switcherText = container.findViewById(R.id.textViewr);
		final EditText switcherEdit = container.findViewById(R.id.textEditor);
		final ImageView switcherEditDone = container.findViewById(R.id.textEditorDone);
		final ViewSwitcher switcher = container.findViewById(R.id.inputSwitcher);

		switcherText.setText(configs.getString(configKey));
		switcherEdit.setText(configs.getString(configKey));

		updateCallSign(type, switcherText.getText().toString());

		switcherText.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				switcher.showNext();
				switcherEdit.requestFocus();
				switcherEdit.setSelection(switcherEdit.getText().length());
				InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.showSoftInput(switcherEdit, InputMethodManager.SHOW_FORCED);
			}
		});

		switcherEditDone.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switcherText.setText(switcherEdit.getText());
				configs.setString(configKey, switcherText.getText().toString());
				updateCallSign(type, switcherText.getText().toString());
				switcherEdit.clearFocus();
				InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				switcher.showPrevious();
			}
		});

	}

	private void updateCallSign(EditorType type, String callSign) {
		switch (type) {
			case CALL_SIGN:
				this.callSign = callSign;
				clientUpdated("UPDATE");
//				lanManager.updateCallSign(callSign);
//				bluetoothManager.updateCallSign(callSign);
//				p2pWifiManager.updateCallSign(callSign);
				break;
			case CHANNEL:
				p2pWifiManager.updateChannel(callSign);
				break;
		}
	}

	@Override
	public void onData(DataFrame data) {
		if (data.getFrameType() == DataFrame.FrameType.DATA) {
			queue.offer(data.getData());
		}

		if (data.getFrameType() == DataFrame.FrameType.SIGNAL) {
			String dataStr = new String(data.getData());
			String uuid = dataStr.substring(0, dataStr.indexOf(" "));
			dataStr = dataStr.substring(dataStr.indexOf(" ") + 1);
			String command = dataStr.substring(0, dataStr.indexOf(" "));
			String name = dataStr.substring(dataStr.indexOf(" ") + 1);

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
		clientUpdated("REFRESH");
		clients.add(new Client(type, address, uuid));
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

	public void onStart() {
		lanManager.onStart();
		bluetoothManager.onStart();
		p2pWifiManager.onStart();
	}

	public void onResume() {
		lanManager.onResume();
		bluetoothManager.onResume();
		p2pWifiManager.onResume();
	}

	public void onDestroy() {
		playbackThread.stopPlayback();

		lanManager.onDestroy();
		bluetoothManager.onDestroy();
		p2pWifiManager.onDestroy();
	}

	public void onPause() {
		lanManager.onPause();
		bluetoothManager.onPause();
		p2pWifiManager.onPause();
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
		sendData(dataFrame.setFields((NetworkConnectionAgregator.myUUID + " " + command + " " + callSign).getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.SIGNAL));
	}

	private void sendData(DataFrame frame) {
		lanManager.sendData(frame);
		bluetoothManager.sendData(frame.getData(), frame.getLength());
	}

	private void notifyChange() {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				adapter.notifyDataSetChanged();
			}
		});
	}
}
