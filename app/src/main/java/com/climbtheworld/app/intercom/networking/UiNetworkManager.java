package com.climbtheworld.app.intercom.networking;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.lan.LanManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class UiNetworkManager implements IUiEventListener, IRecordingListener {
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private PlaybackThread playbackThread;

    private ClientsContainer channelListView;
    private ViewSwitcher callsignEdit;
    private ViewSwitcher channelEdit;

    private LayoutInflater inflater;
    private EditText callsign;
    private LanManager lanManager;

    public UiNetworkManager(final AppCompatActivity parent) throws SocketException {
        playbackThread = new PlaybackThread(queue);
        Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

        callsign = parent.findViewById(R.id.editCallsign);
        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        channelListView = new ClientsContainer();
        channelListView.listView = parent.findViewById(R.id.listChannel);

        initTextEditors(parent);

        lanManager = new LanManager(parent);
        lanManager.addListener(this);
    }

    private void initTextEditors(final AppCompatActivity parent) {
        callsignEdit = parent.findViewById(R.id.callsignSwitcher);
        ((TextView)callsignEdit.findViewById(R.id.textCallsign)).setText(Globals.globalConfigs.getString(Configs.ConfigKey.callsign));
        callsignEdit.findViewById(R.id.textCallsign).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callsignEdit.showNext();
                EditText callsign = callsignEdit.findViewById(R.id.editCallsign);
                callsign.requestFocus();
                callsign.setSelection(callsign.getText().length());
                InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(callsign, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        callsignEdit.findViewById(R.id.editCallsign).setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    ((TextView)callsignEdit.findViewById(R.id.textCallsign)).setText(((EditText)callsignEdit.findViewById(R.id.editCallsign)).getText());
                    Globals.globalConfigs.setString(Configs.ConfigKey.callsign, ((EditText)callsignEdit.findViewById(R.id.editCallsign)).getText().toString());
                    callsignEdit.showNext();
                }
            }
        });

        channelEdit = parent.findViewById(R.id.channelSwitcher);
        ((TextView)channelEdit.findViewById(R.id.textChannel)).setText(Globals.globalConfigs.getString(Configs.ConfigKey.channel));
        channelEdit.findViewById(R.id.textChannel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                channelEdit.showNext();
                EditText callsign = channelEdit.findViewById(R.id.editChannel);
                callsign.requestFocus();
                callsign.setSelection(callsign.getText().length());
                InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(callsign, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        channelEdit.findViewById(R.id.editChannel).setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    ((TextView)channelEdit.findViewById(R.id.textChannel)).setText(((EditText)channelEdit.findViewById(R.id.editChannel)).getText());
                    Globals.globalConfigs.setString(Configs.ConfigKey.channel, ((EditText)channelEdit.findViewById(R.id.editChannel)).getText().toString());
                    channelEdit.showNext();
                }
            }
        });
    }

    @Override
    public void onData(byte[] data) {
        queue.offer(data);
    }

    @Override
    public void onClientConnected(ClientType type, String address, String data) {
        addClients(channelListView, address, data);
    }

    @Override
    public void onClientUpdated(ClientType type, String address, String data) {
        updateClients(channelListView, address, data);
    }

    @Override
    public void onClientDisconnected(ClientType type, String address, String data) {
        removeClients(channelListView, address, data);
    }

    private class ClientsContainer {
        ViewGroup listView;
        ViewGroup emptyListView;
    }

    public void onStart() {
        lanManager.onStart();
    }

    public void onResume() {
        lanManager.updateCallsign(callsign.getText().toString());
    }

    public void onDestroy() {
        lanManager.onDestroy();
        playbackThread.stopPlayback();
    }

    public void onPause() {
    }

    @Override
    public void onRecordingStarted() {

    }

    @Override
    public void onRawAudio(byte[] frame, int numberOfReadBytes) {
        lanManager.sendData(frame, numberOfReadBytes);
    }

    @Override
    public void onAudio(final byte[] frame, int numberOfReadBytes, double energy, double rms) {

    }

    @Override
    public void onRecordingDone() {

    }

    private void addClients(final ClientsContainer container, final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.itemTitle)).getText().toString().compareTo(data) >= 0) {
                        break;
                    }
                }

                final View newViewElement = inflater.inflate(R.layout.list_item_with_description, container.listView, false);
                ((TextView) newViewElement.findViewById(R.id.itemTitle)).setText(data);
                ((TextView) newViewElement.findViewById(R.id.itemDescription)).setText(address);
                container.listView.addView(newViewElement, i);
                updateEmpty(container);
            }
        });
    }

    private void updateClients(final ClientsContainer container, final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.itemDescription)).getText().toString().compareTo(address) == 0) {
                        break;
                    }
                }

                ((TextView) container.listView.getChildAt(i).findViewById(R.id.itemTitle)).setText(data);
                ((TextView) container.listView.getChildAt(i).findViewById(R.id.itemDescription)).setText(address);
            }
        });
    }

    private void removeClients(final ClientsContainer container, final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.itemDescription)).getText().toString().compareTo(address) == 0) {
                        container.listView.removeViewAt(i);
                        break;
                    }
                }
                updateEmpty(container);
            }
        });
    }

    private void updateEmpty(ClientsContainer container) {
        if (container.listView.getChildCount() > 0) {
            container.emptyListView.setVisibility(View.GONE);
        } else {
            container.emptyListView.setVisibility(View.VISIBLE);
        }
    }
}
