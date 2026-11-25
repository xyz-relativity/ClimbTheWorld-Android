package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Cards that show their header only until their chevron is tapped, all collapsed at first, with
 * a button that expands every card while a shown one is collapsed and collapses them all
 * otherwise, as the log book's groups.
 */
public final class CollapsibleCards {
	private final Context context;
	private final ImageView toggleAllView;
	private final List<Card> cards = new ArrayList<>();

	/**
	 * @param toggleAllView the Expand all / Collapse all button, shown once a card is added
	 */
	public CollapsibleCards(Context context, ImageView toggleAllView) {
		this.context = context;
		this.toggleAllView = toggleAllView;
		toggleAllView.setOnClickListener(view -> toggleAll());
	}

	/**
	 * @param card    shown or hidden by a filter, which then calls {@link #update()}
	 * @param chevron collapses or expands the card
	 * @param details what the card hides while collapsed
	 */
	public void add(View card, ImageView chevron, View details) {
		Card entry = new Card(card, chevron, details);
		cards.add(entry);
		chevron.setOnClickListener(view -> {
			setCollapsed(entry, !entry.collapsed);
			update();
		});
		setCollapsed(entry, true);
		update();
	}

	/**
	 * Matches the Expand all / Collapse all button to the shown cards, after a filter shows or
	 * hides some.
	 */
	public void update() {
		boolean anyShown = false;
		boolean expand = false;
		for (Card card : cards) {
			if (card.card.getVisibility() == View.VISIBLE) {
				anyShown = true;
				expand |= card.collapsed;
			}
		}
		// Turned as the cards' chevrons: along the line while a card is collapsed, down otherwise.
		toggleAllView.setRotation(Chevrons.rotation(context, expand));
		String label = context.getString(expand ? R.string.expand_all : R.string.collapse_all);
		toggleAllView.setContentDescription(label);
		toggleAllView.setTooltipText(label);
		// Kept in its place while a filter hides every card, so the heading does not move.
		toggleAllView.setVisibility(
				cards.isEmpty() ? View.GONE : anyShown ? View.VISIBLE : View.INVISIBLE);
	}

	private void toggleAll() {
		boolean collapse = true;
		for (Card card : cards) {
			if (card.card.getVisibility() == View.VISIBLE && card.collapsed) {
				collapse = false;
				break;
			}
		}
		// The hidden cards too, so that they match once the filter shows them again.
		for (Card card : cards) {
			setCollapsed(card, collapse);
		}
		update();
	}

	private void setCollapsed(Card card, boolean collapsed) {
		card.collapsed = collapsed;
		card.details.setVisibility(collapsed ? View.GONE : View.VISIBLE);
		card.chevron.setRotation(Chevrons.rotation(context, collapsed));
		String label = context.getString(collapsed ? R.string.expand : R.string.collapse);
		card.chevron.setContentDescription(label);
		card.chevron.setTooltipText(label);
	}

	private static final class Card {
		private final View card;
		private final ImageView chevron;
		private final View details;
		private boolean collapsed;

		private Card(View card, ImageView chevron, View details) {
			this.card = card;
			this.chevron = chevron;
			this.details = details;
		}
	}
}
