package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.logbook.LogBook;
import com.climbtheworld.app.storage.logbook.LogBookDatabase;
import com.climbtheworld.app.storage.logbook.LogBookEntry;
import com.climbtheworld.app.storage.logbook.LogBookFilter;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.LogBookDialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import needle.UiRelatedTask;

/**
 * Lists the user's log book, filtered by text, climbed status and element type, and lets the
 * user edit, locate or delete its entries.
 */
public class LogBookActivity extends AppCompatActivity {
	private final List<LogBookItem> allItems = new ArrayList<>();
	private final List<LogBookItem> shownItems = new ArrayList<>();
	private final LogBookFilter filter = new LogBookFilter();
	private BaseAdapter listAdapter;
	private TextView countView;
	private TextView emptyView;
	private boolean loaded = false;

	/**
	 * @return the element from the downloaded OSM data, or null when it is not on the device.
	 */
	private static GeoNode loadStoredElement(AppDatabase osmData, LogBookEntry entry) {
		if (entry.osmType == OsmEntity.EntityOsmType.node) {
			OsmNode node = osmData.osmNodeDao().find(entry.osmType, entry.osmID);
			return node != null ? DataManagerNew.toDisplayableNode(node).getGeoNode() : null;
		}
		OsmCollectionEntity collection =
				osmData.osmCollectionDao().find(entry.osmType, entry.osmID);
		return collection != null
				? DataManagerNew.toDisplayableNode(collection).getGeoNode() : null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log_book);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});

		countView = findViewById(R.id.logBookCount);
		emptyView = findViewById(R.id.logBookListEmpty);
		initList();
		initTextFilter();
		initStatusFilter();
		initTypeFilter();
	}

	@Override
	protected void onResume() {
		super.onResume();

		Globals.onResume(this);
		reload();
	}

	@Override
	protected void onPause() {
		Globals.onPause(this);

		super.onPause();
	}

	/**
	 * Reads the log book again, e.g. after an entry was edited.
	 */
	public void reload() {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<List<LogBookItem>>() {
			@Override
			protected List<LogBookItem> doWork() {
				return loadItems();
			}

			@Override
			protected void thenDoUiRelatedWork(List<LogBookItem> items) {
				allItems.clear();
				allItems.addAll(items);
				loaded = true;
				applyFilter();
			}
		});
	}

	private List<LogBookItem> loadItems() {
		LogBookDatabase logBook = LogBookDatabase.getInstance(this);
		AppDatabase osmData = AppDatabase.getInstance(this);
		List<LogBookItem> result = new ArrayList<>();
		for (LogBookEntry entry : logBook.logBookDao().loadAll()) {
			GeoNode element = loadStoredElement(osmData, entry);
			if (element != null) {
				LogBook.refreshSnapshot(logBook, entry, element);
				result.add(new LogBookItem(entry, element, true));
			} else {
				result.add(new LogBookItem(entry, entry.toGeoNode(), false));
			}
		}
		return result;
	}

	private void initTextFilter() {
		EditText text = findViewById(R.id.editFind);
		text.setHint(R.string.log_book_filter_hint);
		text.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				filter.setText(s.toString());
				applyFilter();
			}
		});
	}

	private void initStatusFilter() {
		List<FilterChoice<Set<LogBookEntry.Attempt>>> choices = new ArrayList<>();
		choices.add(new FilterChoice<>(getString(R.string.log_book_filter_any_status),
				EnumSet.noneOf(LogBookEntry.Attempt.class)));
		choices.add(new FilterChoice<>(getString(R.string.log_book_climbed),
				EnumSet.complementOf(EnumSet.of(LogBookEntry.Attempt.none))));
		for (LogBookEntry.Attempt attempt : LogBookEntry.Attempt.values()) {
			if (attempt != LogBookEntry.Attempt.none) {
				choices.add(new FilterChoice<>(getString(attempt.getNameId()),
						EnumSet.of(attempt)));
			}
		}
		choices.add(new FilterChoice<>(getString(R.string.log_book_not_climbed),
				EnumSet.of(LogBookEntry.Attempt.none)));

		initSpinner(findViewById(R.id.logBookFilterStatus), choices,
				choice -> filter.setAttempts(choice.value));
	}

	private void initTypeFilter() {
		List<FilterChoice<GeoNode.NodeTypes>> choices = new ArrayList<>();
		choices.add(new FilterChoice<>(getString(R.string.log_book_filter_any_type), null));
		for (GeoNode.NodeTypes type : GeoNode.NodeTypes.values()) {
			choices.add(new FilterChoice<>(getString(type.getNameId()), type));
		}

		initSpinner(findViewById(R.id.logBookFilterType), choices,
				choice -> filter.setNodeType(choice.value));
	}

	private <T> void initSpinner(Spinner spinner, List<FilterChoice<T>> choices,
	                             ChoiceListener<T> listener) {
		ArrayAdapter<FilterChoice<T>> adapter =
				new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				listener.onChoice(choices.get(position));
				applyFilter();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
	}

	private void applyFilter() {
		shownItems.clear();
		for (LogBookItem item : allItems) {
			if (filter.matches(item.entry)) {
				shownItems.add(item);
			}
		}
		listAdapter.notifyDataSetChanged();
		if (!loaded) {
			// The spinners report their first selection before the log book is read.
			return;
		}

		countView.setText(getString(R.string.log_book_count, shownItems.size(), allItems.size()));
		if (allItems.isEmpty()) {
			emptyView.setText(R.string.log_book_list_empty);
			emptyView.setVisibility(View.VISIBLE);
		} else if (shownItems.isEmpty()) {
			emptyView.setText(R.string.no_match);
			emptyView.setVisibility(View.VISIBLE);
		} else {
			emptyView.setVisibility(View.GONE);
		}
	}

	private void initList() {
		listAdapter = new BaseAdapter() {
			@Override
			public int getCount() {
				return shownItems.size();
			}

			@Override
			public Object getItem(int position) {
				return shownItems.get(position);
			}

			@Override
			public long getItemId(int position) {
				return position;
			}

			@Override
			public View getView(int position, View view, ViewGroup parent) {
				if (view == null) {
					view = getLayoutInflater()
							.inflate(R.layout.list_item_log_book_entry, parent, false);
				}
				bindItem(view, shownItems.get(position));
				return view;
			}
		};
		((ListView) findViewById(R.id.listLogBook)).setAdapter(listAdapter);
	}

	private void bindItem(View view, LogBookItem item) {
		((ImageView) view.findViewById(R.id.logEntryIcon)).setImageDrawable(item.getIcon(this));
		((TextView) view.findViewById(R.id.logEntryName)).setText(item.getName());

		String status = getString(item.element.getNodeType().getNameId());
		if (item.entry.attempt != LogBookEntry.Attempt.none) {
			status = getString(item.entry.attempt.getNameId()) + " · " + status;
		}
		((TextView) view.findViewById(R.id.logEntryStatus)).setText(status);

		TextView note = view.findViewById(R.id.logEntryNote);
		note.setText(item.entry.note);
		note.setVisibility(item.entry.note.isEmpty() ? View.GONE : View.VISIBLE);

		view.setOnClickListener(v -> editEntry(item));
		view.setOnLongClickListener(v -> {
			showEntryMenu(v, item);
			return true;
		});
		view.findViewById(R.id.logEntryMenu).setOnClickListener(v -> showEntryMenu(v, item));
	}

	private void editEntry(LogBookItem item) {
		// The list is reloaded by the editor once the entry is saved.
		LogBookDialogBuilder.showEditor(this, item.entry, item.element, item.getIcon(this),
				null);
	}

	private void showEntryMenu(View anchor, LogBookItem item) {
		PopupMenu popup = new PopupMenu(this, anchor);
		popup.getMenuInflater().inflate(R.menu.log_book_entry_options, popup.getMenu());
		// The info dialog needs the element's OSM data.
		popup.getMenu().findItem(R.id.logBookShowInfo).setEnabled(item.storedLocally);
		popup.setOnMenuItemClickListener(menuItem -> {
			int id = menuItem.getItemId();
			if (id == R.id.logBookEdit) {
				editEntry(item);
			} else if (id == R.id.logBookShowInfo) {
				NodeDialogBuilder.showNodeInfoDialog(this, item.element);
			} else if (id == R.id.logBookCenterMap) {
				Intent intent = new Intent(this, MapActivity.class);
				intent.putExtra("GeoPoint", new MapCoordinate(item.entry.decimalLatitude,
						item.entry.decimalLongitude).toDelimitedString());
				startActivity(intent);
			} else if (id == R.id.logBookDelete) {
				confirmDelete(item);
			}
			return true;
		});
		popup.show();
	}

	private void confirmDelete(LogBookItem item) {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.delete_confirmation, item.getName()))
				.setMessage(R.string.log_book_delete_message)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(R.string.log_book_delete, (dialog, which) -> delete(item))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void delete(LogBookItem item) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<String>() {
			@Override
			protected String doWork() {
				try {
					LogBookDatabase.getInstance(LogBookActivity.this).logBookDao()
							.delete(item.entry.osmType, item.entry.osmID);
					return null;
				} catch (SQLException exception) {
					return exception.getMessage();
				}
			}

			@Override
			protected void thenDoUiRelatedWork(String errorMessage) {
				if (errorMessage != null) {
					DialogBuilder.showErrorDialog(LogBookActivity.this,
							getString(R.string.log_book_delete_failed, errorMessage), null);
					return;
				}
				reload();
			}
		});
	}

	private interface ChoiceListener<T> {
		void onChoice(FilterChoice<T> choice);
	}

	/**
	 * A spinner entry: the label shown and the filter value it stands for.
	 */
	private record FilterChoice<T>(String label, T value) {

		@NonNull
		@Override
		public String toString() {
			return label;
		}
	}

	private static final class LogBookItem {
		private final LogBookEntry entry;
		// The element from the OSM data when it is on the device, otherwise from the snapshot.
		private final GeoNode element;
		private final boolean storedLocally;
		private Drawable icon;

		private LogBookItem(LogBookEntry entry, GeoNode element, boolean storedLocally) {
			this.entry = entry;
			this.element = element;
			this.storedLocally = storedLocally;
		}

		private String getName() {
			return !element.getName().isEmpty() ? element.getName() : Long.toString(entry.osmID);
		}

		/**
		 * Rendered on first use and kept, so scrolling does not render it again.
		 */
		private Drawable getIcon(AppCompatActivity activity) {
			if (icon == null) {
				icon = new PoiMarkerDrawable(activity, new DisplayableGeoNode(element))
						.getDrawable();
			}
			return icon;
		}
	}
}
