package com.climbtheworld.app.utils.views;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the header of the group scrolled into at the top of a scroll view, until the next
 * group's header comes up, pushes it out and takes its place.
 *
 * <p>The headers are the direct children of the scroll view's content tagged {@link #TAG}, as
 * the SettingsGroupHeader style tags them, each an icon and a title. The pinned header is a copy
 * of them over the top of the scroll view, which shows the icon and title of the header
 * scrolled past, lying under it.
 */
public final class StickyHeaders {
	/** The tag of the headers, which the SettingsGroupHeader style sets. */
	public static final String TAG = "stickyHeader";

	private final ScrollView scrollView;
	private final ViewGroup content;
	private final List<View> headers = new ArrayList<>();
	private final View pinned;
	private final ImageView pinnedIcon;
	private final TextView pinnedTitle;
	/** The header the pinned one shows, null while it is hidden. */
	private View shown;

	/**
	 * @param pinned laid out as the headers, over the top of scrollView, hidden at first; nothing
	 *               in it is clickable, so it lets the touches through to scrollView
	 */
	public static void attach(ScrollView scrollView, ViewGroup pinned) {
		new StickyHeaders(scrollView, pinned).listen();
	}

	private StickyHeaders(ScrollView scrollView, ViewGroup pinned) {
		this.scrollView = scrollView;
		this.content = (ViewGroup) scrollView.getChildAt(0);
		this.pinned = pinned;
		this.pinnedIcon = child(pinned, ImageView.class);
		this.pinnedTitle = child(pinned, TextView.class);

		for (int i = 0; i < content.getChildCount(); i++) {
			View child = content.getChildAt(i);
			if (child.getTag() != null && TAG.contentEquals(child.getTag().toString())) {
				headers.add(child);
			}
		}
	}

	private void listen() {
		scrollView.setOnScrollChangeListener(
				(view, scrollX, scrollY, oldScrollX, oldScrollY) -> update());
		// The first layout, and any change of size above or in a group moving its header.
		content.addOnLayoutChangeListener(
				(view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> update());
	}

	private void update() {
		List<View> visible = new ArrayList<>(headers.size());
		for (View header : headers) {
			if (header.getVisibility() == View.VISIBLE) {
				visible.add(header);
			}
		}

		int contentTop = content.getTop() - scrollView.getScrollY();
		int[] tops = new int[visible.size()];
		int[] heights = new int[visible.size()];
		for (int i = 0; i < visible.size(); i++) {
			tops[i] = contentTop + visible.get(i).getTop();
			heights[i] = visible.get(i).getHeight();
		}

		PinnedHeader pin = PinnedHeader.of(tops, heights);
		if (pin.index < 0) {
			// The first header still shows in place.
			pinned.setVisibility(View.INVISIBLE);
			shown = null;
			return;
		}

		View header = visible.get(pin.index);
		if (header != shown) {
			show(header);
			shown = header;
		}
		pinned.setTranslationY(pin.offset);
		pinned.setVisibility(View.VISIBLE);
	}

	private void show(View header) {
		ImageView icon = child(header, ImageView.class);
		TextView title = child(header, TextView.class);

		Drawable drawable = icon == null ? null : icon.getDrawable();
		// Its own copy, as a drawable draws for one view only.
		Drawable.ConstantState state = drawable == null ? null : drawable.getConstantState();
		if (pinnedIcon != null) {
			pinnedIcon.setImageDrawable(state == null ? drawable
					: state.newDrawable(header.getResources()));
		}
		if (pinnedTitle != null) {
			pinnedTitle.setText(title == null ? null : title.getText());
		}
	}

	/** @return the first direct child of group of type, or null */
	private static <T extends View> T child(View group, Class<T> type) {
		if (!(group instanceof ViewGroup)) {
			return null;
		}
		ViewGroup viewGroup = (ViewGroup) group;
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			if (type.isInstance(viewGroup.getChildAt(i))) {
				return type.cast(viewGroup.getChildAt(i));
			}
		}
		return null;
	}
}
