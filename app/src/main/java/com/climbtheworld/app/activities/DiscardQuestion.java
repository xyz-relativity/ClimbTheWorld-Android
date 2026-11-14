package com.climbtheworld.app.activities;

import android.app.Activity;
import android.app.AlertDialog;

import com.climbtheworld.app.R;

/**
 * Asks before an editor is left with changes that are not saved: by Cancel, by back, or for
 * another editor. One question for all of them, so that the user always reads the same one.
 */
final class DiscardQuestion {
	private DiscardQuestion() {
	}

	/**
	 * Runs leave at once when nothing was edited; otherwise asks, and runs it only on Discard. On
	 * the question's cancel the editor stays as it is.
	 *
	 * @param edited whether the user changed anything in the editor that leaving it loses
	 */
	static void ask(Activity editor, boolean edited, Runnable leave) {
		if (!edited) {
			leave.run();
			return;
		}
		new AlertDialog.Builder(editor)
				.setTitle(R.string.discard_changes)
				.setMessage(R.string.discard_changes_message)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(R.string.discard, (dialog, which) -> leave.run())
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}
