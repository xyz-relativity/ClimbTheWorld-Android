package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.logbook.LogBook;
import com.climbtheworld.app.storage.logbook.LogBookBackup;
import com.climbtheworld.app.storage.logbook.LogBookDatabase;
import com.climbtheworld.app.storage.logbook.LogBookEntry;
import com.climbtheworld.app.storage.logbook.LogBookFilter;
import com.climbtheworld.app.storage.logbook.LogBookGroup;
import com.climbtheworld.app.storage.logbook.LogBookGroups;
import com.climbtheworld.app.storage.logbook.LogBookPlaces;
import com.climbtheworld.app.storage.views.CountryViewState;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.SpinnerUtils;
import com.climbtheworld.app.utils.views.TextWatchers;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.LogBookDialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import needle.UiRelatedTask;

/**
 * Lists the user's log book, grouped by country, area and crag and filtered by text, climbed
 * status and element type, and lets the user edit, locate or delete its entries, or save the log
 * book to a file and restore it.
 */
public class LogBookActivity extends AppCompatActivity {
	// Some file providers do not report .json files as JSON.
	private static final String[] BACKUP_FILE_TYPES =
			{LogBookBackup.MIME_TYPE, "text/plain", "application/octet-stream"};
	// How much further in each level of the groups is drawn.
	private static final int GROUP_INDENT_DP = 12;
	// The sizes of textAppearanceMedium, as the entries' names, and of the section titles.
	private static final float GROUP_TEXT_SIZE_SP = 18;
	private static final float TOP_GROUP_TEXT_SIZE_SP = 20;
	private static final int ROW_TYPE_HEADING = 0;
	private static final int ROW_TYPE_ENTRY = 1;

	private final ActivityResultLauncher<String> saveBackupLauncher = registerForActivityResult(
			new ActivityResultContracts.CreateDocument(LogBookBackup.MIME_TYPE), this::saveBackup);
	private final ActivityResultLauncher<String[]> restoreBackupLauncher =
			registerForActivityResult(new ActivityResultContracts.OpenDocument(),
					this::restoreBackup);
	private final List<LogBookItem> allItems = new ArrayList<>();
	private final List<LogBookItem> shownItems = new ArrayList<>();
	private final List<LogBookGroups.Row<LogBookItem>> shownRows = new ArrayList<>();
	// The flags of the country groups, by group key; null for a country the app has none for.
	private final Map<String, Bitmap> countryFlags = new HashMap<>();
	private final LogBookFilter filter = new LogBookFilter();
	private BaseAdapter listAdapter;
	private TextView countView;
	private TextView emptyView;
	private boolean loaded = false;

	/**
	 * @return the element from the downloaded OSM data, or null when it is not on the device.
	 */
	private static GeoNode loadStoredElement(AppDatabase osmData,
	                                         ClimbingRouteCounter routeCounter, LogBookEntry entry) {
		if (entry.osmType == OsmEntity.EntityOsmType.node) {
			OsmNode node = osmData.osmNodeDao().find(entry.osmType, entry.osmID);
			return node != null ? DataManager.toDisplayableNode(node).getGeoNode() : null;
		}
		OsmCollectionEntity collection =
				osmData.osmCollectionDao().find(entry.osmType, entry.osmID);
		return collection != null
				? DataManager.toDisplayableNode(collection, routeCounter).getGeoNode() : null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log_book);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		countView = findViewById(R.id.logBookCount);
		emptyView = findViewById(R.id.logBookListEmpty);
		findViewById(R.id.logBookMenu).setOnClickListener(this::showLogBookMenu);
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
			private final Map<String, Bitmap> flags = new HashMap<>();

			@Override
			protected List<LogBookItem> doWork() {
				List<LogBookItem> items = loadItems();
				for (LogBookItem item : items) {
					LogBookGroup country = item.groups.get(0);
					if (country.kind() == LogBookGroup.Kind.country
							&& !flags.containsKey(country.key())) {
						flags.put(country.key(), CountryViewState.readFlag(LogBookActivity.this,
								LogBookPlaces.flagCountryOf(country)));
					}
				}
				return items;
			}

			@Override
			protected void thenDoUiRelatedWork(List<LogBookItem> items) {
				countryFlags.clear();
				countryFlags.putAll(flags);
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
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(this);
		LogBookPlaces places = new LogBookPlaces(this);
		List<LogBookItem> result = new ArrayList<>();
		for (LogBookEntry entry : logBook.logBookDao().loadAll()) {
			GeoNode element = loadStoredElement(osmData, routeCounter, entry);
			if (element != null) {
				LogBook.refreshSnapshot(logBook, entry, element);
				result.add(new LogBookItem(entry, element, true, places.of(entry, element)));
			} else {
				result.add(new LogBookItem(entry, entry.toGeoNode(), false,
						places.notDownloaded()));
			}
		}
		return result;
	}

	private void initTextFilter() {
		EditText text = findViewById(R.id.editFind);
		text.setHint(R.string.log_book_filter_hint);
		text.addTextChangedListener(TextWatchers.afterTextChanged(s -> {
			filter.setText(s.toString());
			applyFilter();
		}));
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
		List<FilterChoice<OsmEntity.EntityClimbingType>> choices = new ArrayList<>();
		choices.add(new FilterChoice<>(getString(R.string.log_book_filter_any_type), null));
		for (OsmEntity.EntityClimbingType type : OsmEntity.EntityClimbingType.pinTypes()) {
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
		spinner.setOnItemSelectedListener(SpinnerUtils.onItemSelected(position -> {
			listener.onChoice(choices.get(position));
			applyFilter();
		}));
	}

	private void applyFilter() {
		shownItems.clear();
		for (LogBookItem item : allItems) {
			if (filter.matches(item.entry, item.groups)) {
				shownItems.add(item);
			}
		}
		shownRows.clear();
		shownRows.addAll(LogBookGroups.rows(shownItems, item -> item.groups));
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
				return shownRows.size();
			}

			@Override
			public Object getItem(int position) {
				return shownRows.get(position);
			}

			@Override
			public long getItemId(int position) {
				return position;
			}

			@Override
			public int getViewTypeCount() {
				return 2;
			}

			@Override
			public int getItemViewType(int position) {
				return shownRows.get(position).isHeading() ? ROW_TYPE_HEADING : ROW_TYPE_ENTRY;
			}

			@Override
			public boolean areAllItemsEnabled() {
				return false;
			}

			@Override
			public boolean isEnabled(int position) {
				return !shownRows.get(position).isHeading();
			}

			@Override
			public View getView(int position, View view, ViewGroup parent) {
				LogBookGroups.Row<LogBookItem> row = shownRows.get(position);
				if (row.isHeading()) {
					if (view == null) {
						view = getLayoutInflater()
								.inflate(R.layout.list_item_log_book_group, parent, false);
					}
					bindGroup(view, row.group());
					// Lined up with the icons of its entries.
					indent(view, row.depth());
				} else {
					if (view == null) {
						view = getLayoutInflater()
								.inflate(R.layout.list_item_log_book_entry, parent, false);
					}
					bindItem(view, row.item());
					indent(view, row.depth() - 1);
				}
				return view;
			}
		};
		((ListView) findViewById(R.id.listLogBook)).setAdapter(listAdapter);
	}

	private void indent(View view, int levels) {
		int start = Math.round(Math.max(0, levels) * GROUP_INDENT_DP
				* getResources().getDisplayMetrics().density);
		view.setPaddingRelative(start, view.getPaddingTop(), view.getPaddingEnd(),
				view.getPaddingBottom());
	}

	private void bindGroup(View view, LogBookGroup group) {
		ImageView icon = view.findViewById(R.id.logGroupIcon);
		if (group.kind() == LogBookGroup.Kind.area) {
			icon.setImageResource(R.drawable.ic_area);
			icon.setVisibility(View.VISIBLE);
		} else if (group.kind() == LogBookGroup.Kind.crag) {
			icon.setImageResource(R.drawable.ic_crag);
			icon.setVisibility(View.VISIBLE);
		} else {
			icon.setVisibility(View.GONE);
		}

		ImageView flag = view.findViewById(R.id.logGroupFlag);
		if (group.kind() == LogBookGroup.Kind.country) {
			Bitmap countryFlag = countryFlags.get(group.key());
			// As the download list, the UN flag for a country without one of its own.
			if (countryFlag != null) {
				flag.setImageBitmap(countryFlag);
			} else {
				flag.setImageResource(R.drawable.flag_un);
			}
			flag.setVisibility(View.VISIBLE);
		} else {
			flag.setVisibility(View.GONE);
		}

		TextView name = view.findViewById(R.id.logGroupName);
		name.setText(group.name());
		// The top level, the countries, stands out from the areas and crags in them.
		boolean topLevel = group.kind() != LogBookGroup.Kind.area
				&& group.kind() != LogBookGroup.Kind.crag;
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP,
				topLevel ? TOP_GROUP_TEXT_SIZE_SP : GROUP_TEXT_SIZE_SP);
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

	private void showLogBookMenu(View anchor) {
		PopupMenu popup = new PopupMenu(this, anchor);
		popup.getMenuInflater().inflate(R.menu.log_book_options, popup.getMenu());
		popup.setOnMenuItemClickListener(menuItem -> {
			int id = menuItem.getItemId();
			if (id == R.id.logBookBackupSave) {
				saveBackupLauncher.launch(LogBookBackup.getFileName(System.currentTimeMillis()));
			} else if (id == R.id.logBookBackupRestore) {
				restoreBackupLauncher.launch(BACKUP_FILE_TYPES);
			}
			return true;
		});
		popup.show();
	}

	private void saveBackup(Uri file) {
		if (file == null) {
			// No file was picked.
			return;
		}

		Constants.DB_EXECUTOR.execute(new UiRelatedTask<String>() {
			private int saved;

			@Override
			protected String doWork() {
				try (OutputStream output = getContentResolver().openOutputStream(file)) {
					if (output == null) {
						throw new FileNotFoundException(file.toString());
					}
					saved = LogBookBackup.write(LogBookDatabase.getInstance(LogBookActivity.this),
							output);
					return null;
				} catch (IOException | SQLException | SecurityException exception) {
					return exception.getMessage();
				}
			}

			@Override
			protected void thenDoUiRelatedWork(String errorMessage) {
				if (errorMessage != null) {
					DialogBuilder.showErrorDialog(LogBookActivity.this,
							getString(R.string.log_book_backup_save_failed, errorMessage), null);
					return;
				}
				Toast.makeText(LogBookActivity.this,
						getString(R.string.log_book_backup_saved, saved), Toast.LENGTH_LONG).show();
			}
		});
	}

	private void restoreBackup(Uri file) {
		if (file == null) {
			// No file was picked.
			return;
		}

		Constants.DB_EXECUTOR.execute(new UiRelatedTask<String>() {
			private LogBookBackup.RestoreResult result;

			@Override
			protected String doWork() {
				try (InputStream input = getContentResolver().openInputStream(file)) {
					if (input == null) {
						throw new FileNotFoundException(file.toString());
					}
					result = LogBookBackup.restore(
							LogBookDatabase.getInstance(LogBookActivity.this), input);
					return null;
				} catch (LogBookBackup.InvalidBackupException exception) {
					return getString(exception.getMessageId());
				} catch (IOException | SQLException | SecurityException exception) {
					return exception.getMessage();
				}
			}

			@Override
			protected void thenDoUiRelatedWork(String errorMessage) {
				if (errorMessage != null) {
					DialogBuilder.showErrorDialog(LogBookActivity.this,
							getString(R.string.log_book_restore_failed, errorMessage), null);
					return;
				}
				reload();
				new AlertDialog.Builder(LogBookActivity.this)
						.setMessage(getString(R.string.log_book_restore_done, result.added,
								result.updated, result.unchanged))
						.setPositiveButton(android.R.string.ok, null)
						.show();
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
		// Country, area and crag, as far as they are known.
		private final List<LogBookGroup> groups;
		private Drawable icon;

		private LogBookItem(LogBookEntry entry, GeoNode element, boolean storedLocally,
		                    List<LogBookGroup> groups) {
			this.entry = entry;
			this.element = element;
			this.storedLocally = storedLocally;
			this.groups = groups;
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
