package com.climbtheworld.app.navigate.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.climbtheworld.app.sensors.orientation.HeadingConfidence;
import com.climbtheworld.app.utils.PixelBlur;

import java.util.EnumMap;
import java.util.Map;

/**
 * Blurs a compass icon while the heading is not to be trusted, see HeadingConfidence: a little
 * while it is rough, more while it is not to be trusted, never so much that it cannot be read,
 * and not at all while it is sure. Only the icon blurs: the button it is on and whatever is
 * drawn over it, such as the accuracy arc, stay sharp. One level fades into the next, rather
 * than snapping, and once it has, the view shows that level's icon alone, as it did before the
 * fade: nothing of the fade is left on it. The blur spills a little past the icon's edge, as a
 * blur does, so the blurred icon is drawn past its bounds, see BlurredIcon: the view must not
 * crop it to its padding.
 */
public class CompassBlur {
	// The blur's radius at each level, as a fraction of the icon's size, so that it is the same
	// whatever size the icon is drawn at: light enough to read the compass through.
	static final float ROUGH_RADIUS_FRACTION = 1f / 48;
	static final float UNSURE_RADIUS_FRACTION = 1f / 24;
	static final int FADE_MILLIS = 400;

	private final ImageView view;
	private Drawable icon;
	private HeadingConfidence confidence = HeadingConfidence.SURE;
	// The icon at each level, made once per icon.
	private final Map<HeadingConfidence, Drawable> blurred = new EnumMap<>(HeadingConfidence.class);

	/**
	 * @param view the view showing the compass icon, whose image this sets from now on
	 */
	public CompassBlur(ImageView view) {
		this.view = view;
	}

	/**
	 * @param icon the compass icon, sharp
	 */
	public void setIcon(Drawable icon) {
		this.icon = icon;
		blurred.clear();
		view.setImageDrawable(at(confidence));
	}

	public HeadingConfidence getConfidence() {
		return confidence;
	}

	public void setConfidence(HeadingConfidence confidence) {
		if (confidence == this.confidence) {
			return;
		}
		Drawable from = at(this.confidence);
		this.confidence = confidence;
		Drawable to = at(confidence);
		if (from == null || to == null) {
			view.setImageDrawable(to);
			return;
		}
		TransitionDrawable transition = new TransitionDrawable(new Drawable[]{from, to});
		transition.setCrossFadeEnabled(true);
		view.setImageDrawable(transition);
		transition.startTransition(FADE_MILLIS);
		// Unless another fade, or another icon, took over meanwhile.
		view.postDelayed(() -> {
			if (view.getDrawable() == transition) {
				view.setImageDrawable(to);
			}
		}, FADE_MILLIS);
	}

	/**
	 * @return the blur's radius at a level, as a fraction of the icon's size
	 */
	static float radiusFraction(HeadingConfidence confidence) {
		switch (confidence) {
			case ROUGH:
				return ROUGH_RADIUS_FRACTION;
			case UNSURE:
				return UNSURE_RADIUS_FRACTION;
			default:
				return 0;
		}
	}

	/**
	 * @return the icon as it looks at a level, null while there is no icon
	 */
	private Drawable at(HeadingConfidence confidence) {
		if (icon == null || confidence == HeadingConfidence.SURE) {
			return icon;
		}
		Drawable drawable = blurred.get(confidence);
		if (drawable == null) {
			drawable = blur(icon, radiusFraction(confidence));
			blurred.put(confidence, drawable);
		}
		return drawable;
	}

	/**
	 * @return the icon drawn at its own size and blurred, see PixelBlur, with room around it for
	 * the blur to spill into, or the icon itself when it has no size of its own or the radius
	 * comes to less than a pixel
	 */
	private static Drawable blur(Drawable icon, float radiusFraction) {
		int width = icon.getIntrinsicWidth();
		int height = icon.getIntrinsicHeight();
		int radius = Math.round(Math.min(width, height) * radiusFraction);
		if (width <= 0 || height <= 0 || radius <= 0) {
			return icon;
		}
		// As far as the blur reaches, see PixelBlur.PASSES, so that nothing is cut off square.
		int padding = PixelBlur.reach(radius);
		int paddedWidth = width + 2 * padding;
		int paddedHeight = height + 2 * padding;
		Bitmap bitmap = Bitmap.createBitmap(paddedWidth, paddedHeight, Bitmap.Config.ARGB_8888);
		Rect bounds = icon.copyBounds();
		icon.setBounds(padding, padding, padding + width, padding + height);
		icon.draw(new Canvas(bitmap));
		icon.setBounds(bounds);
		int[] pixels = new int[paddedWidth * paddedHeight];
		bitmap.getPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, paddedHeight);
		PixelBlur.blur(pixels, paddedWidth, paddedHeight, radius);
		bitmap.setPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, paddedHeight);
		return new BlurredIcon(bitmap, width, height, padding);
	}

	/**
	 * A blurred icon, the size of the sharp one, so that it is drawn at the same size, drawn
	 * past its bounds by the padding its blur spilled into.
	 */
	static class BlurredIcon extends Drawable {
		private final Bitmap bitmap;
		private final int width;
		private final int height;
		private final int padding;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		private final RectF destination = new RectF();

		/**
		 * @param bitmap  the blurred icon, with the padding around it
		 * @param width   the sharp icon's width, in pixels
		 * @param height  the sharp icon's height, in pixels
		 * @param padding how much of the bitmap is around the icon, on each side, in pixels
		 */
		BlurredIcon(Bitmap bitmap, int width, int height, int padding) {
			this.bitmap = bitmap;
			this.width = width;
			this.height = height;
			this.padding = padding;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			Rect bounds = getBounds();
			// The padding scaled as the icon is.
			float paddingX = padding * (float) bounds.width() / width;
			float paddingY = padding * (float) bounds.height() / height;
			destination.set(bounds.left - paddingX, bounds.top - paddingY,
					bounds.right + paddingX, bounds.bottom + paddingY);
			canvas.drawBitmap(bitmap, null, destination, paint);
		}

		@Override
		public int getIntrinsicWidth() {
			return width;
		}

		@Override
		public int getIntrinsicHeight() {
			return height;
		}

		@Override
		public void setAlpha(int alpha) {
			paint.setAlpha(alpha);
			invalidateSelf();
		}

		@Override
		public int getAlpha() {
			return paint.getAlpha();
		}

		@Override
		public void setColorFilter(@Nullable ColorFilter colorFilter) {
			paint.setColorFilter(colorFilter);
			invalidateSelf();
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}
}
