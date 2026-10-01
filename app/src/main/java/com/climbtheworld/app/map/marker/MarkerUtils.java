package com.climbtheworld.app.map.marker;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

public class MarkerUtils {
	public static final String UNKNOWN_TYPE = "-?-";
	public final static int DEFAULT_STYLE_ICON_SIZE = Globals.convertDpToPixel(20).intValue();

	private static final HashMap<String, Drawable> iconCache = new HashMap<>();

	public static Drawable getClusterIcon(AppCompatActivity parent, int color, int alpha) {
		final String cacheKey = "cluster" + "|" + color + "|" + alpha;

		if (!iconCache.containsKey(cacheKey)) {
			synchronized (iconCache) {
				if (!iconCache.containsKey(cacheKey)) {
					Drawable nodeIcon = ResourcesCompat.getDrawable(parent.getResources(),
							R.drawable.ic_clusters, null);
					nodeIcon.setTintList(ColorStateList.valueOf(color));
					nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
					nodeIcon.setAlpha(alpha);

					Bitmap bitmap = Bitmap.createBitmap(IconType.poiCLuster.iconPxWith,
							IconType.poiCLuster.iconPxHeight, Bitmap.Config.ARGB_8888);
					Canvas canvas = new Canvas(bitmap);
					canvas.setDrawFilter(new PaintFlagsDrawFilter(0,
							Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
					nodeIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
					nodeIcon.draw(canvas);
					iconCache.put(cacheKey, new BitmapDrawable(parent.getResources(), bitmap));
				}
			}
		}

		return iconCache.get(cacheKey);
	}

	public static Drawable getStyleIcon(AppCompatActivity parent,
	                                    List<GeoNode.ClimbingStyle> styles) {
		return getStyleIcon(parent, styles, DEFAULT_STYLE_ICON_SIZE);
	}

	public static Drawable getStyleIcon(AppCompatActivity parent,
	                                    List<GeoNode.ClimbingStyle> styles, int iconSIze) {
		final String cacheKey = "style" + "|" + iconSIze + "|" + stylesToString(parent, styles);
		if (!iconCache.containsKey(cacheKey)) {
			synchronized (iconCache) {
				if (!iconCache.containsKey(cacheKey)) {
					Bitmap bitmap =
							Bitmap.createBitmap(iconSIze, iconSIze, Bitmap.Config.ARGB_8888);
					if (!styles.isEmpty()) {
						Canvas canvas = new Canvas(bitmap);
						canvas.setDrawFilter(new PaintFlagsDrawFilter(0,
								Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

						// White background square behind the style cells
						Paint fillPaint = new Paint();
						fillPaint.setStyle(Paint.Style.FILL);
						fillPaint.setColor(Color.WHITE);
						canvas.drawRect(0, 0, iconSIze, iconSIze, fillPaint);

						drawStyleGrid(canvas, styles, iconSIze);
					}
					iconCache.put(cacheKey, new BitmapDrawable(parent.getResources(), bitmap));
				}
			}
		}

		return iconCache.get(cacheKey);
	}

	/**
	 * Position of a climbing style in the 3x3 style grid, and whether the style is roped.
	 * Rows group the styles by meaning:
	 * <pre>
	 * sport   | toprope    | trad       &lt;- protection
	 * boulder | rope dot   | deepwater  &lt;- unroped
	 * ice     | multipitch | mixed      &lt;- terrain and length
	 * </pre>
	 * The centre cell holds a dot drawn when at least one roped style is present.
	 */
	private static final class StyleCell {
		final int column;
		final int row;
		final boolean roped;

		StyleCell(int column, int row, boolean roped) {
			this.column = column;
			this.row = row;
			this.roped = roped;
		}
	}

	private static final EnumMap<GeoNode.ClimbingStyle, StyleCell> STYLE_CELLS =
			new EnumMap<>(GeoNode.ClimbingStyle.class);

	static {
		STYLE_CELLS.put(GeoNode.ClimbingStyle.sport, new StyleCell(0, 0, true));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.toprope, new StyleCell(1, 0, true));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.trad, new StyleCell(2, 0, true));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.boulder, new StyleCell(0, 1, false));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.deepwater, new StyleCell(2, 1, false));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.ice, new StyleCell(0, 2, true));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.multipitch, new StyleCell(1, 2, true));
		STYLE_CELLS.put(GeoNode.ClimbingStyle.mixed, new StyleCell(2, 2, true));
	}

	/**
	 * Draws the style cells on whole-pixel boundaries so they stay sharp at small sizes.
	 * Each cell is a black square with a 1px white gap on every side, so neighbouring
	 * filled cells remain distinguishable.
	 */
	private static void drawStyleGrid(Canvas canvas, List<GeoNode.ClimbingStyle> styles,
	                                  int iconSize) {
		Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		fillPaint.setStyle(Paint.Style.FILL);
		fillPaint.setColor(Color.BLACK);

		// Pitch = cell plus its 1px gap; the grid gets one extra trailing gap and is centred.
		int pitch = Math.max((iconSize - 1) / 3, 2);
		int origin = (iconSize - (3 * pitch + 1)) / 2;

		boolean roped = false;
		for (GeoNode.ClimbingStyle style : styles) {
			StyleCell cell = STYLE_CELLS.get(style);
			if (cell == null) {
				continue;
			}
			float left = origin + cell.column * pitch + 1;
			float top = origin + cell.row * pitch + 1;
			canvas.drawRect(left, top, left + pitch - 1, top + pitch - 1, fillPaint);
			roped |= cell.roped;
		}

		if (roped) {
			float center = origin + 1.5f * pitch + 0.5f;
			canvas.drawCircle(center, center, (pitch - 1) / 2f, fillPaint);
		}
	}

	public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi,
	                                  ColorStateList color) {
		final String cacheKey = "route" + "|" + poi.getNodeType().asString(parent) + "|" + color;
		if (!iconCache.containsKey(cacheKey)) {
			synchronized (iconCache) {
				if (!iconCache.containsKey(cacheKey)) {
					Bitmap bitmap;
					switch (poi.getNodeType()) {
						case area:
						case crag:
							bitmap = createBitmapWithPinTint(parent, poi.getNodeType().getIconId(), color);
							break;

						case artificial:
							bitmap = createBitmapFromLayout(
									View.inflate(parent, poi.getNodeType().getIconId(), null));
							break;

						case route:
						case unknown:
						default:
							bitmap = createRouteBitmapWithTint(parent, color);
							break;
					}
					iconCache.put(cacheKey, new BitmapDrawable(parent.getResources(), bitmap));
				}
			}
		}
		return iconCache.get(cacheKey);
	}

	public static Drawable getLayoutIcon(AppCompatActivity parent, int layoutID) {
		return new BitmapDrawable(parent.getResources(),
				createBitmapFromLayout(View.inflate(parent, layoutID, null)));
	}

	public static String stylesToString(AppCompatActivity parent,
	                                    List<GeoNode.ClimbingStyle> styles) {
		if (styles == null)
			return "null";

		int iMax = styles.size() - 1;
		if (iMax == -1)
			return "[]";

		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(styles.get(i).asString(parent));
			if (i == iMax)
				return b.append(']').toString();
			b.append(", ");
		}
	}

	private static Bitmap createRouteBitmapWithTint(AppCompatActivity parent,
	                                                ColorStateList color) {
		return createBitmapWithPinTint(parent, GeoNode.NodeTypes.route.getIconId(), color);
	}

	private static Bitmap createBitmapWithPinTint(AppCompatActivity parent, int layoutId,
	                                              ColorStateList color) {
		View newViewElement = View.inflate(parent, layoutId, null);
		((ImageView) newViewElement.findViewById(R.id.imagePin)).setImageTintList(color);

		return createBitmapFromLayout(newViewElement);
	}

	private static Bitmap createBitmapFromLayout(View newViewElement) {
		newViewElement.measure(IconType.poiRouteIcon.measuredWidth,
				IconType.poiRouteIcon.measuredHeight);
		newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(),
				newViewElement.getMeasuredHeight());

		Bitmap bitmap = Bitmap.createBitmap(newViewElement.getMeasuredWidth(),
				newViewElement.getMeasuredHeight(), Bitmap.Config.ARGB_8888);

		Canvas canvas = new Canvas(bitmap);
		canvas.setDrawFilter(
				new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));

		Drawable background = newViewElement.getBackground();

		if (background != null) {
			background.draw(canvas);
		}
		newViewElement.draw(canvas);

		return Bitmap.createScaledBitmap(bitmap, IconType.poiRouteIcon.iconPxWith,
				IconType.poiRouteIcon.iconPxHeight, true);
	}

	public enum IconType {
		poiRouteIcon(200, 300, Math.round(DisplayableGeoNode.POI_ICON_DP_SIZE)),
		poiCLuster(48, 48, DisplayableGeoNode.CLUSTER_ICON_DP_SIZE);

		private final int measuredHeight;
		private final int measuredWidth;
		private final int iconPxWith;
		private final int iconPxHeight;

		private final float aspectRatio;

		IconType(int originWith, int originHeight, int iconDP) {
			this.aspectRatio = (float) originWith / (float) originHeight;
			this.iconPxWith = Globals.convertDpToPixel(iconDP * aspectRatio).intValue();
			this.iconPxHeight = Globals.convertDpToPixel(iconDP).intValue();

			this.measuredWidth = View.MeasureSpec.makeMeasureSpec(
					Globals.convertDpToPixel(originWith).intValue(), View.MeasureSpec.EXACTLY);
			this.measuredHeight = View.MeasureSpec.makeMeasureSpec(
					Globals.convertDpToPixel(originHeight).intValue(), View.MeasureSpec.EXACTLY);
		}

		public float getAspectRatio() {
			return aspectRatio;
		}
	}

}
