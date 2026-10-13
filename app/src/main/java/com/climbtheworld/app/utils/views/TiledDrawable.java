package com.climbtheworld.app.utils.views;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.climbtheworld.app.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Repeats a drawable, like a vector, over its bounds. A bitmap drawable's tileMode only takes a
 * bitmap, so the tile is drawn once at the screen's density and repeated from there. In XML:
 * {@code <drawable class="com.climbtheworld.app.utils.views.TiledDrawable"
 * app:tiledSource="@drawable/tile" />}.
 */
public class TiledDrawable extends Drawable {
	private final Paint paint = new Paint();

	/** For inflating from XML. */
	public TiledDrawable() {
	}

	public TiledDrawable(Drawable tile) {
		setTile(tile);
	}

	@Override
	public void inflate(@NonNull Resources resources, @NonNull XmlPullParser parser,
	                    @NonNull AttributeSet attrs, @Nullable Resources.Theme theme)
			throws XmlPullParserException, IOException {
		super.inflate(resources, parser, attrs, theme);
		TypedArray values = theme != null
				? theme.obtainStyledAttributes(attrs, R.styleable.TiledDrawable, 0, 0)
				: resources.obtainAttributes(attrs, R.styleable.TiledDrawable);
		try {
			int tileId = values.getResourceId(R.styleable.TiledDrawable_tiledSource, 0);
			if (tileId == 0) {
				throw new XmlPullParserException(parser.getPositionDescription()
						+ ": TiledDrawable needs a tiledSource");
			}
			setTile(resources.getDrawable(tileId, theme));
		} finally {
			values.recycle();
		}
	}

	private void setTile(Drawable tile) {
		// The tile's own size, in pixels at the screen's density.
		int width = Math.max(tile.getIntrinsicWidth(), 1);
		int height = Math.max(tile.getIntrinsicHeight(), 1);
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		tile.setBounds(0, 0, width, height);
		tile.draw(new Canvas(bitmap));
		paint.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		canvas.drawRect(getBounds(), paint);
	}

	@Override
	public void setAlpha(int alpha) {
		paint.setAlpha(alpha);
		invalidateSelf();
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
