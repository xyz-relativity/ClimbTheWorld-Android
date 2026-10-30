package com.climbtheworld.app.utils;

/**
 * Blurs an image's pixels, as Bitmap.getPixels gives them: three passes of a box blur, which come
 * close to a Gaussian one, each pass along the rows then the columns. The colours are averaged
 * premultiplied by their alpha, so that the clear pixels around a shape do not darken its edge.
 * Beyond the edges of the image the nearest pixel is taken, so that nothing bleeds in from
 * outside.
 */
public final class PixelBlur {
	// A box blur this many times over comes close to a Gaussian one.
	static final int PASSES = 3;

	private PixelBlur() {
	}

	/**
	 * @return how far a blur of a radius spreads a pixel, in pixels: the radius of each pass,
	 * PASSES times over
	 */
	public static int reach(int radius) {
		return Math.max(0, radius) * PASSES;
	}

	/**
	 * @param pixels the image, row by row, each pixel ARGB with the colour not premultiplied,
	 *               replaced by the blurred image
	 * @param width  in pixels
	 * @param height in pixels
	 * @param radius how far either side of a pixel each pass averages, in pixels; nothing is
	 *               done for 0 or less
	 */
	public static void blur(int[] pixels, int width, int height, int radius) {
		if (radius <= 0 || width <= 0 || height <= 0) {
			return;
		}
		int count = width * height;
		// Premultiplied channels, as floats to keep the rounding out of the passes.
		float[] alpha = new float[count];
		float[] red = new float[count];
		float[] green = new float[count];
		float[] blue = new float[count];
		for (int i = 0; i < count; i++) {
			int pixel = pixels[i];
			float a = (pixel >>> 24) / 255f;
			alpha[i] = a;
			red[i] = ((pixel >> 16) & 0xff) * a;
			green[i] = ((pixel >> 8) & 0xff) * a;
			blue[i] = (pixel & 0xff) * a;
		}
		float[] scratch = new float[count];
		for (float[] channel : new float[][]{alpha, red, green, blue}) {
			for (int pass = 0; pass < PASSES; pass++) {
				boxBlurRows(channel, scratch, width, height, radius);
				boxBlurColumns(scratch, channel, width, height, radius);
			}
		}
		for (int i = 0; i < count; i++) {
			float a = alpha[i];
			int outAlpha = Math.round(a * 255);
			if (outAlpha <= 0) {
				pixels[i] = 0;
				continue;
			}
			pixels[i] = (outAlpha << 24) | (channel(red[i] / a) << 16)
					| (channel(green[i] / a) << 8) | channel(blue[i] / a);
		}
	}

	private static int channel(float value) {
		return Math.max(0, Math.min(255, Math.round(value)));
	}

	/**
	 * Averages each pixel with the radius either side of it along its row, from source to
	 * target, taking the nearest pixel of the row beyond its ends.
	 */
	private static void boxBlurRows(float[] source, float[] target, int width, int height,
	                                int radius) {
		float divisor = 2 * radius + 1;
		for (int y = 0; y < height; y++) {
			int row = y * width;
			// The window around the first pixel: the radius before it is that pixel again.
			float sum = source[row] * (radius + 1);
			for (int i = 1; i <= radius; i++) {
				sum += source[row + Math.min(i, width - 1)];
			}
			for (int x = 0; x < width; x++) {
				target[row + x] = sum / divisor;
				sum += source[row + Math.min(x + radius + 1, width - 1)]
						- source[row + Math.max(x - radius, 0)];
			}
		}
	}

	/**
	 * As boxBlurRows, along the columns.
	 */
	private static void boxBlurColumns(float[] source, float[] target, int width, int height,
	                                   int radius) {
		float divisor = 2 * radius + 1;
		for (int x = 0; x < width; x++) {
			float sum = source[x] * (radius + 1);
			for (int i = 1; i <= radius; i++) {
				sum += source[Math.min(i, height - 1) * width + x];
			}
			for (int y = 0; y < height; y++) {
				target[y * width + x] = sum / divisor;
				sum += source[Math.min(y + radius + 1, height - 1) * width + x]
						- source[Math.max(y - radius, 0) * width + x];
			}
		}
	}
}
