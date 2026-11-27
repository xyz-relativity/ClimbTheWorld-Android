package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;

import java.util.Objects;

/**
 * The star of what is bookmarked, as in the title of the details dialogs.
 */
public final class BookmarkStar {
	private BookmarkStar() {
		//hide constructor
	}

	/**
	 * @return the star in the bookmark colour: its white fill tinted, its outline kept black, as
	 * the route pins are tinted in their grade colour
	 */
	public static Drawable create(Context context) {
		Drawable star = Objects.requireNonNull(
				AppCompatResources.getDrawable(context, R.drawable.ic_bookmark)).mutate();
		star.setTint(ContextCompat.getColor(context, R.color.bookmarked));
		star.setTintMode(PorterDuff.Mode.MULTIPLY);
		return star;
	}
}
