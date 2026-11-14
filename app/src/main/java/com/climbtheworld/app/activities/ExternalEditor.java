package com.climbtheworld.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;

/**
 * Opens what the app's editor is on in another editor, the OpenStreetMap website or a JOSM
 * compatible app like Vespucci, and closes the app's editor, as what is changed over there would
 * leave its form out of date. What the user changed in the form is lost then, so with any change
 * the user is asked first.
 */
final class ExternalEditor {
	private ExternalEditor() {
	}

	/**
	 * @param editor the app's editor, closed once the other editor opens
	 * @param edited whether the user changed anything in the app's editor that leaving it loses
	 */
	static void open(AppCompatActivity editor, String url, boolean edited) {
		DiscardQuestion.ask(editor, edited, () -> start(editor, url));
	}

	/** Without an app for the link the app's editor stays open, with the user's changes. */
	private static void start(AppCompatActivity editor, String url) {
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			editor.startActivity(intent);
			editor.finish();
		} catch (ActivityNotFoundException e) {
			DialogBuilder.showErrorDialog(editor, editor.getString(R.string.no_josm_app), null);
		}
	}
}
