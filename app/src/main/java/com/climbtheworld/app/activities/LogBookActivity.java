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
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.ViewPager;

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
import com.climbtheworld.app.storage.logbook.Bookmarks;
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
import com.climbtheworld.app.utils.views.BookmarkStar;
import com.climbtheworld.app.utils.views.Chevrons;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.PagerViewAdapter;
import com.climbtheworld.app.utils.views.SpinnerUtils;
import com.climbtheworld.app.utils.views.TextWatchers;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.LogBookDialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import needle.UiRelatedTask;

/**
 * Lists the user's log book and bookmarks, on a tab each: the entries holding a note or a climbed
 * status, and the bookmarked ones. Each tab groups its entries by country, area and crag, each
 * group collapsible and all collapsed at first, and filters them by text, climbed status and
 * element type. The user can edit, bookmark, locate or delete the entries, or save the log book,
 * bookmarks included, to a file and restore it.
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
	// Each kept per tab, see LogBookPage.stateKey.
	private static final String STATE_GROUPS_COLLAPSED = "groupsCollapsed";
	private static final String STATE_TOGGLED_GROUPS = "toggledGroups";
	private static final String STATE_FILTER_TEXT = "filterText";
	private static final String STATE_FILTER_STATUS = "filterStatus";
	private static final String STATE_FILTER_TYPE = "filterType";

	private final ActivityResultLauncher<String> saveBackupLauncher = registerForActivityResult(
			new ActivityResultContracts.CreateDocument(LogBookBackup.MIME_TYPE), this::saveBackup);
	private final ActivityResultLauncher<String[]> restoreBackupLauncher =
			registerForActivityResult(new ActivityResultContracts.OpenDocument(),
					this::restoreBackup);
	// Every entry, of both tabs.
	private final List<LogBookItem> allItems = new ArrayList<>();
	// The flags of the country groups, by group key; null for a country the app has none for.
	private final Map<String, Bitmap> countryFlags = new HashMap<>();
	// In the order of the tabs.
	private final List<LogBookPage> pages = new ArrayList<>();
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

		for (Tab tab : Tab.values()) {
			LogBookPage page = new LogBookPage(tab);
			if (savedInstanceState != null) {
				page.restoreState(savedInstanceState);
			}
			pages.add(page);
		}
		ViewPager viewPager = findViewById(R.id.logBookPager);
		viewPager.setAdapter(new PagerViewAdapter(getLayoutInflater(), pages));

		BottomNavigationView navigation = findViewById(R.id.logBookNavigation);
		// The icons in their own colours, as the data manager's tabs.
		navigation.setItemIconTintList(null);
		navigation.getMenu().findItem(R.id.logBookNavigationBookmarks)
				.setIcon(BookmarkStar.create(this));
		PagerViewAdapter.linkNavigation(navigation, viewPager, pages);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		for (LogBookPage page : pages) {
			page.saveState(outState);
		}
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
				for (LogBookPage page : pages) {
					page.applyFilter();
				}
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
				result.add(new LogBookItem(entry, element, true, places.of(entry, element),
						routeCounter.isFilteredOut(element)));
			} else {
				GeoNode snapshot = entry.toGeoNode();
				result.add(new LogBookItem(entry, snapshot, false, places.notDownloaded(),
						routeCounter.isFilteredOut(snapshot)));
			}
		}
		return result;
	}

	private void indent(View view, int levels) {
		int start = Math.round(Math.max(0, levels) * GROUP_INDENT_DP
				* getResources().getDisplayMetrics().density);
		view.setPaddingRelative(start, view.getPaddingTop(), view.getPaddingEnd(),
				view.getPaddingBottom());
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
		popup.getMenu().findItem(R.id.logBookBookmark).setTitle(
				item.entry.bookmarked ? R.string.bookmark_remove : R.string.bookmark_add);
		popup.setOnMenuItemClickListener(menuItem -> {
			int id = menuItem.getItemId();
			if (id == R.id.logBookEdit) {
				editEntry(item);
			} else if (id == R.id.logBookBookmark) {
				toggleBookmark(item);
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

	/**
	 * Sets or clears the entry's bookmark. An entry holding only the bookmark goes with it.
	 */
	private void toggleBookmark(LogBookItem item) {
		LogBookEntry entry = item.entry;
		entry.bookmarked = !entry.bookmarked;
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<String>() {
			@Override
			protected String doWork() {
				try {
					LogBook.save(LogBookDatabase.getInstance(LogBookActivity.this), entry);
				} catch (SQLException exception) {
					return exception.getMessage();
				}
				Bookmarks.changed(LogBookActivity.this);
				return null;
			}

			@Override
			protected void thenDoUiRelatedWork(String errorMessage) {
				if (errorMessage != null) {
					// Not stored, so listed as it is.
					entry.bookmarked = !entry.bookmarked;
					DialogBuilder.showErrorDialog(LogBookActivity.this,
							getString(R.string.log_book_save_failed, errorMessage), null);
					return;
				}
				reload();
			}
		});
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
				} catch (SQLException exception) {
					return exception.getMessage();
				}
				Bookmarks.changed(LogBookActivity.this);
				return null;
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
					Bookmarks.changed(LogBookActivity.this);
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

	/**
	 * The tabs, in the order of the navigation bar's items.
	 */
	private enum Tab {
		// The entries holding a note or a climbed status.
		logBook(R.string.log_book_tab, R.string.log_book_list_empty),
		bookmarks(R.string.bookmarks, R.string.bookmarks_list_empty);

		@StringRes
		private final int titleId;
		// Shown while the tab holds no entry.
		@StringRes
		private final int emptyId;

		Tab(@StringRes int titleId, @StringRes int emptyId) {
			this.titleId = titleId;
			this.emptyId = emptyId;
		}

		private boolean holds(LogBookEntry entry) {
			return this == bookmarks ? entry.bookmarked : entry.hasLog();
		}
	}

	private interface ChoiceListener<T> {
		void onChoice(int position, T value);
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

	/**
	 * A tab: its entries, as filtered, grouped and collapsed in it. Its views are made and
	 * dropped by the pager, so they may not be there yet when the log book is read.
	 */
	private final class LogBookPage implements IPagerViewFragment {
		private final Tab tab;
		private final LogBookFilter filter = new LogBookFilter();
		private final List<LogBookItem> shownItems = new ArrayList<>();
		private final List<LogBookGroups.Row<LogBookItem>> shownRows = new ArrayList<>();
		// Whether the groups show their heading only, but for the toggled ones, by group id: all
		// collapsed at first and after Collapse all, all expanded after Expand all. Kept while
		// the activity lives.
		private final Set<String> toggledGroups = new HashSet<>();
		private boolean groupsCollapsed = true;
		// The filter as picked, kept with the activity's state here: the pager makes the page's
		// views only after the activity's views got their state back.
		private String filterText = "";
		private int statusPosition = 0;
		private int typePosition = 0;
		// The views; null while the pager holds none of the page.
		private BaseAdapter listAdapter;
		private TextView countView;
		private ImageView expandAllView;
		private TextView emptyView;

		private LogBookPage(Tab tab) {
			this.tab = tab;
		}

		private String stateKey(String name) {
			return name + "." + tab.name();
		}

		private void restoreState(Bundle state) {
			groupsCollapsed = state.getBoolean(stateKey(STATE_GROUPS_COLLAPSED), true);
			ArrayList<String> toggled = state.getStringArrayList(stateKey(STATE_TOGGLED_GROUPS));
			if (toggled != null) {
				toggledGroups.addAll(toggled);
			}
			filterText = state.getString(stateKey(STATE_FILTER_TEXT), "");
			filter.setText(filterText);
			// Given to the filter by the spinners, as any first selection.
			statusPosition = state.getInt(stateKey(STATE_FILTER_STATUS), 0);
			typePosition = state.getInt(stateKey(STATE_FILTER_TYPE), 0);
		}

		private void saveState(Bundle state) {
			state.putBoolean(stateKey(STATE_GROUPS_COLLAPSED), groupsCollapsed);
			state.putStringArrayList(stateKey(STATE_TOGGLED_GROUPS),
					new ArrayList<>(toggledGroups));
			state.putString(stateKey(STATE_FILTER_TEXT), filterText);
			state.putInt(stateKey(STATE_FILTER_STATUS), statusPosition);
			state.putInt(stateKey(STATE_FILTER_TYPE), typePosition);
		}

		@Override
		public int getViewId() {
			return R.layout.fragment_log_book_page;
		}

		@Override
		public void onCreate(ViewGroup view) {
			((TextView) view.findViewById(R.id.logBookTitle)).setText(tab.titleId);
			countView = view.findViewById(R.id.logBookCount);
			emptyView = view.findViewById(R.id.logBookListEmpty);
			expandAllView = view.findViewById(R.id.logBookExpandAll);
			expandAllView.setOnClickListener(v -> toggleAllGroups());
			view.findViewById(R.id.logBookMenu)
					.setOnClickListener(LogBookActivity.this::showLogBookMenu);
			initList(view);
			initTextFilter(view);
			initStatusFilter(view);
			initTypeFilter(view);
			applyFilter();
		}

		@Override
		public void onDestroy(ViewGroup view) {
			listAdapter = null;
			countView = null;
			expandAllView = null;
			emptyView = null;
		}

		@Override
		public void onViewSelected() {
			// Both tabs are kept up to date.
		}

		private void initTextFilter(View view) {
			EditText text = view.findViewById(R.id.editFind);
			text.setHint(R.string.log_book_filter_hint);
			text.setText(filterText);
			text.addTextChangedListener(TextWatchers.afterTextChanged(s -> {
				filterText = s.toString();
				filter.setText(filterText);
				applyFilter();
			}));
		}

		private void initStatusFilter(View view) {
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

			initSpinner(view.findViewById(R.id.logBookFilterStatus), choices, statusPosition,
					(position, attempts) -> {
						statusPosition = position;
						filter.setAttempts(attempts);
					});
		}

		private void initTypeFilter(View view) {
			List<FilterChoice<OsmEntity.EntityClimbingType>> choices = new ArrayList<>();
			choices.add(new FilterChoice<>(getString(R.string.log_book_filter_any_type), null));
			for (OsmEntity.EntityClimbingType type : OsmEntity.EntityClimbingType.pinTypes()) {
				choices.add(new FilterChoice<>(getString(type.getNameId()), type));
			}

			initSpinner(view.findViewById(R.id.logBookFilterType), choices, typePosition,
					(position, type) -> {
						typePosition = position;
						filter.setNodeType(type);
					});
		}

		private <T> void initSpinner(Spinner spinner, List<FilterChoice<T>> choices,
		                             int selected, ChoiceListener<T> listener) {
			ArrayAdapter<FilterChoice<T>> adapter = new ArrayAdapter<>(LogBookActivity.this,
					android.R.layout.simple_spinner_item, choices);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spinner.setAdapter(adapter);
			// Reported to the listener once laid out, as any first selection.
			spinner.setSelection(Math.min(selected, choices.size() - 1), false);
			spinner.setOnItemSelectedListener(SpinnerUtils.onItemSelected(position -> {
				listener.onChoice(position, choices.get(position).value());
				applyFilter();
			}));
		}

		private void applyFilter() {
			shownItems.clear();
			int total = 0;
			for (LogBookItem item : allItems) {
				if (tab.holds(item.entry)) {
					total++;
					if (filter.matches(item.entry, item.groups)) {
						shownItems.add(item);
					}
				}
			}
			if (listAdapter == null) {
				// Listed once the pager makes the page's views.
				return;
			}
			refreshRows();
			if (!loaded) {
				// The spinners report their first selection before the log book is read.
				return;
			}

			countView.setText(getString(R.string.log_book_count, shownItems.size(), total));
			if (total == 0) {
				emptyView.setText(tab.emptyId);
				emptyView.setVisibility(View.VISIBLE);
			} else if (shownItems.isEmpty()) {
				emptyView.setText(R.string.no_match);
				emptyView.setVisibility(View.VISIBLE);
			} else {
				emptyView.setVisibility(View.GONE);
			}
		}

		private void refreshRows() {
			shownRows.clear();
			shownRows.addAll(LogBookGroups.rows(shownItems, item -> item.groups,
					groupId -> groupsCollapsed != toggledGroups.contains(groupId)));
			listAdapter.notifyDataSetChanged();
			updateExpandAll();
		}

		private void toggleGroup(String groupId) {
			if (!toggledGroups.remove(groupId)) {
				toggledGroups.add(groupId);
			}
			refreshRows();
		}

		/**
		 * Expands every group while a listed one is collapsed, collapses them all otherwise.
		 */
		private void toggleAllGroups() {
			groupsCollapsed = !anyShownGroupCollapsed();
			toggledGroups.clear();
			refreshRows();
		}

		private boolean anyShownGroupCollapsed() {
			for (LogBookGroups.Row<LogBookItem> row : shownRows) {
				if (row.collapsed()) {
					return true;
				}
			}
			return false;
		}

		private void updateExpandAll() {
			boolean expand = anyShownGroupCollapsed();
			// Turned as a heading's chevron: at the names while a group is collapsed, down
			// otherwise.
			expandAllView.setRotation(Chevrons.rotation(LogBookActivity.this, expand));
			String label = getString(expand ? R.string.expand_all : R.string.collapse_all);
			expandAllView.setContentDescription(label);
			expandAllView.setTooltipText(label);
			expandAllView.setVisibility(shownRows.isEmpty() ? View.INVISIBLE : View.VISIBLE);
		}

		private void initList(View view) {
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
				public View getView(int position, View row, ViewGroup parent) {
					LogBookGroups.Row<LogBookItem> shown = shownRows.get(position);
					if (shown.isHeading()) {
						if (row == null) {
							row = getLayoutInflater()
									.inflate(R.layout.list_item_log_book_group, parent, false);
						}
						bindGroup(row, shown);
						// Lined up with the icons of its entries.
						indent(row, shown.depth());
					} else {
						if (row == null) {
							row = getLayoutInflater()
									.inflate(R.layout.list_item_log_book_entry, parent, false);
						}
						bindItem(row, shown.item());
						indent(row, shown.depth() - 1);
					}
					return row;
				}
			};
			((ListView) view.findViewById(R.id.listLogBook)).setAdapter(listAdapter);
		}

		private void bindGroup(View view, LogBookGroups.Row<LogBookItem> row) {
			LogBookGroup group = row.group();
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

			((TextView) view.findViewById(R.id.logGroupCount))
					.setText(String.valueOf(row.entryCount()));
			view.findViewById(R.id.logGroupChevron)
					.setRotation(Chevrons.rotation(LogBookActivity.this, row.collapsed()));
			ViewCompat.setStateDescription(view, getString(row.collapsed()
					? R.string.log_book_group_collapsed : R.string.log_book_group_expanded));
			view.setOnClickListener(v -> toggleGroup(row.groupId()));
		}

		private void bindItem(View view, LogBookItem item) {
			ImageView iconView = view.findViewById(R.id.logEntryIcon);
			iconView.setImageDrawable(item.getIcon(LogBookActivity.this));
			TextView nameView = view.findViewById(R.id.logEntryName);
			nameView.setText(item.getName());
			// Faded out as on the map when the display filters leave the element out; on the log
			// book tab, still edited on a tap. The entry editor shows its pin as usual.
			float alpha = item.filteredOut ? DisplayableGeoNode.GHOST_VIEW_ALPHA : 1;
			iconView.setAlpha(alpha);
			nameView.setAlpha(alpha);

			String status = getString(item.element.getNodeType().getNameId());
			if (item.entry.attempt != LogBookEntry.Attempt.none) {
				status = getString(item.entry.attempt.getNameId()) + " · " + status;
			}
			((TextView) view.findViewById(R.id.logEntryStatus)).setText(status);

			TextView note = view.findViewById(R.id.logEntryNote);
			note.setText(item.entry.note);
			note.setVisibility(item.entry.note.isEmpty() ? View.GONE : View.VISIBLE);

			// Not on the bookmarks tab, where every entry is bookmarked.
			view.findViewById(R.id.logEntryBookmark).setVisibility(
					item.entry.bookmarked && tab != Tab.bookmarks ? View.VISIBLE : View.GONE);

			if (tab == Tab.bookmarks && item.filteredOut) {
				// Browsed, as the map: what the display filters leave out opens nothing. Its
				// menu still shows the info.
				view.setOnClickListener(null);
				view.setClickable(false);
			} else {
				view.setOnClickListener(v -> openEntry(item));
			}
			view.setOnLongClickListener(v -> {
				showEntryMenu(v, item);
				return true;
			});
			view.findViewById(R.id.logEntryMenu).setOnClickListener(v -> showEntryMenu(v, item));
		}

		/**
		 * A tap on an entry: on the log book tab, edits it; on the bookmarks tab, shows the
		 * element's details, as on the map, or edits the entry when the element's OSM data,
		 * which the details need, is not on the device.
		 */
		private void openEntry(LogBookItem item) {
			if (tab == Tab.bookmarks && item.storedLocally) {
				NodeDialogBuilder.showNodeInfoDialog(LogBookActivity.this, item.element);
			} else {
				editEntry(item);
			}
		}
	}

	private static final class LogBookItem {
		private final LogBookEntry entry;
		// The element from the OSM data when it is on the device, otherwise from the snapshot.
		private final GeoNode element;
		private final boolean storedLocally;
		// Country, area and crag, as far as they are known.
		private final List<LogBookGroup> groups;
		// Whether the display filters leave the element out, see
		// ClimbingRouteCounter.isFilteredOut.
		private final boolean filteredOut;
		private Drawable icon;

		private LogBookItem(LogBookEntry entry, GeoNode element, boolean storedLocally,
		                    List<LogBookGroup> groups, boolean filteredOut) {
			this.entry = entry;
			this.element = element;
			this.storedLocally = storedLocally;
			this.groups = groups;
			this.filteredOut = filteredOut;
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
