package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.SQLException;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.LogBookActivity;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.logbook.LogBook;
import com.climbtheworld.app.storage.logbook.LogBookDatabase;
import com.climbtheworld.app.storage.logbook.LogBookEntry;
import com.climbtheworld.app.utils.constants.Constants;

import needle.UiRelatedTask;

public class LogBookDialogBuilder {
	private LogBookDialogBuilder() {
		//hide constructor
	}

	/**
	 * Fills the log book section of an element's info dialog. Reads the database, so like the
	 * rest of the info dialog it has to be built off the main thread.
	 *
	 * @param icon the element's icon from the info dialog title, reused by the editor's title.
	 */
	static void buildSection(AppCompatActivity activity, View dialogView, String osmEntityType,
	                         GeoNode element, Drawable icon) {
		View section = dialogView.findViewById(R.id.logBookContainer);
		OsmEntity.EntityOsmType osmType;
		try {
			osmType = OsmEntity.EntityOsmType.valueOf(osmEntityType);
		} catch (IllegalArgumentException ignored) {
			section.setVisibility(View.GONE);
			return;
		}

		if (!LogBook.canLog(element.osmID)) {
			section.findViewById(R.id.logBookEditButton).setVisibility(View.GONE);
			((TextView) section.findViewById(R.id.logBookNote))
					.setText(R.string.log_book_unavailable);
			return;
		}

		LogBookEntry stored =
				LogBook.load(LogBookDatabase.getInstance(activity), osmType, element);
		LogBookEntry entry = stored != null ? stored : new LogBookEntry(osmType, element.osmID);
		showEntry(section, entry);

		View.OnClickListener edit = view -> showEditor(activity, entry, element, icon,
				() -> showEntry(section, entry));
		section.findViewById(R.id.logBookEditButton).setOnClickListener(edit);
		section.findViewById(R.id.logBookContent).setOnClickListener(edit);
	}

	private static void showEntry(View section, LogBookEntry entry) {
		TextView attempt = section.findViewById(R.id.logBookAttempt);
		if (entry.attempt == LogBookEntry.Attempt.none) {
			attempt.setVisibility(View.GONE);
		} else {
			attempt.setText(entry.attempt.getNameId());
			attempt.setVisibility(View.VISIBLE);
		}

		TextView note = section.findViewById(R.id.logBookNote);
		note.setVisibility(View.VISIBLE);
		if (!entry.note.isEmpty()) {
			note.setText(entry.note);
		} else if (entry.isEmpty()) {
			note.setText(R.string.log_book_empty);
		} else {
			note.setVisibility(View.GONE);
		}
	}

	/**
	 * Opens the editor of an element's log book entry.
	 *
	 * @param entry   the stored entry, or a new one for an element that has none yet.
	 * @param icon    the element's icon, shown next to its name.
	 * @param onSaved run on the main thread once the entry is saved.
	 */
	public static void showEditor(AppCompatActivity activity, LogBookEntry entry,
	                              GeoNode element, Drawable icon, @Nullable Runnable onSaved) {
		AlertDialog dialog = DialogBuilder.getNewDialog(activity);
		dialog.setCancelable(true);
		// A stray tap outside should not throw away a note being typed.
		dialog.setCanceledOnTouchOutside(false);

		View view = activity.getLayoutInflater()
				.inflate(R.layout.dialog_log_book_edit, dialog.getListView(), false);

		// Same title as the info dialog, without its menu.
		((TextView) view.findViewById(R.id.textTitle)).setText(!element.getName().isEmpty()
				? element.getName() : activity.getString(R.string.log_book));
		// The info dialog still shows the icon, and a drawable can only belong to one view.
		Drawable.ConstantState iconState = icon.getConstantState();
		((ImageView) view.findViewById(R.id.imageIcon)).setImageDrawable(iconState != null
				? iconState.newDrawable(activity.getResources()) : icon);
		view.findViewById(R.id.menu).setVisibility(View.GONE);

		CheckBox climbed = view.findViewById(R.id.logBookClimbedCheck);
		RadioGroup attemptGroup = view.findViewById(R.id.logBookAttemptGroup);
		// Attempts only make sense for routes; still show them if the element stopped being one.
		boolean showAttempts = element.getNodeType() == GeoNode.NodeTypes.route
				|| entry.attempt != LogBookEntry.Attempt.none;
		if (showAttempts) {
			for (LogBookEntry.Attempt attempt : LogBookEntry.Attempt.values()) {
				if (attempt == LogBookEntry.Attempt.none) {
					// Not climbed is the unchecked check box, not an option.
					continue;
				}
				RadioButton option = new RadioButton(activity);
				option.setId(View.generateViewId());
				option.setTag(attempt);
				option.setText(buildAttemptLabel(activity, attempt));
				attemptGroup.addView(option);
				if (attempt == entry.attempt) {
					attemptGroup.check(option.getId());
				}
			}
			climbed.setChecked(entry.attempt != LogBookEntry.Attempt.none);
			setOptionsEnabled(attemptGroup, climbed.isChecked());
		} else {
			view.findViewById(R.id.logBookClimbedContainer).setVisibility(View.GONE);
		}

		EditText note = view.findViewById(R.id.logBookNoteEdit);
		TextView counter = view.findViewById(R.id.logBookNoteCounter);
		note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(LogBook.MAX_NOTE_LENGTH)});
		note.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				counter.setText(activity.getString(R.string.log_book_note_counter, s.length(),
						LogBook.MAX_NOTE_LENGTH));
			}
		});
		note.setText(entry.note);

		dialog.setView(view);
		dialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.save),
				(dialogInterface, which) -> {
					entry.note = note.getText().toString().trim();
					entry.attempt = getSelectedAttempt(climbed, attemptGroup);
					entry.updateSnapshot(element);
					save(activity, entry, onSaved);
				});
		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getString(R.string.cancel),
				(dialogInterface, which) -> dialogInterface.dismiss());
		dialog.show();

		// The buttons only exist once the dialog is shown. An attempt needs an outcome to be saved.
		Button saveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
		Runnable updateSaveButton = () -> saveButton.setEnabled(!climbed.isChecked()
				|| attemptGroup.getCheckedRadioButtonId() != View.NO_ID);
		climbed.setOnCheckedChangeListener((button, isChecked) -> {
			setOptionsEnabled(attemptGroup, isChecked);
			updateSaveButton.run();
		});
		attemptGroup.setOnCheckedChangeListener((group, checkedId) -> updateSaveButton.run());
		updateSaveButton.run();
	}

	private static LogBookEntry.Attempt getSelectedAttempt(CheckBox climbed,
	                                                       RadioGroup attemptGroup) {
		View checked = attemptGroup.findViewById(attemptGroup.getCheckedRadioButtonId());
		if (!climbed.isChecked() || checked == null) {
			return LogBookEntry.Attempt.none;
		}
		return (LogBookEntry.Attempt) checked.getTag();
	}

	private static void setOptionsEnabled(RadioGroup group, boolean enabled) {
		group.setEnabled(enabled);
		for (int i = 0; i < group.getChildCount(); i++) {
			group.getChildAt(i).setEnabled(enabled);
		}
	}

	private static CharSequence buildAttemptLabel(AppCompatActivity activity,
	                                              LogBookEntry.Attempt attempt) {
		SpannableStringBuilder label =
				new SpannableStringBuilder(activity.getString(attempt.getNameId()));
		int descriptionStart = label.length() + 1;
		label.append('\n').append(activity.getString(attempt.getDescriptionId()));
		label.setSpan(new RelativeSizeSpan(0.85f), descriptionStart, label.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return label;
	}

	private static void save(AppCompatActivity activity, LogBookEntry entry,
	                         @Nullable Runnable onSaved) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<String>() {
			@Override
			protected String doWork() {
				try {
					LogBook.save(LogBookDatabase.getInstance(activity), entry);
					return null;
				} catch (SQLException exception) {
					return exception.getMessage();
				}
			}

			@Override
			protected void thenDoUiRelatedWork(String errorMessage) {
				if (errorMessage != null) {
					DialogBuilder.showErrorDialog(activity,
							activity.getString(R.string.log_book_save_failed, errorMessage), null);
					return;
				}
				if (onSaved != null) {
					onSaved.run();
				}
				// Also when edited from an info dialog opened on top of the log book list.
				if (activity instanceof LogBookActivity) {
					((LogBookActivity) activity).reload();
				}
			}
		});
	}
}
