package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.view.View;

/**
 * The chevrons of what collapses, ic_chevron_down and ic_chevrons_down: the log book's groups
 * and an area's crag cards.
 */
public final class Chevrons {
	private Chevrons() {
	}

	/**
	 * @return how far to turn a chevron, which points down while expanded and along the line
	 * while collapsed
	 */
	public static float rotation(Context context, boolean collapsed) {
		// From the configuration, as a newly inflated view has no direction until it is attached.
		boolean rightToLeft = context.getResources().getConfiguration().getLayoutDirection()
				== View.LAYOUT_DIRECTION_RTL;
		return !collapsed ? 0 : rightToLeft ? 90 : -90;
	}
}
