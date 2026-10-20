package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.Context;
import android.opengl.GLES20;

import com.climbtheworld.app.augmentedreality.ARCore.helpers.ShaderUtil;
import com.climbtheworld.app.augmentedreality.placement.LineVertices;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Draws coloured lines of the session's world over the camera image, whatever is in front of
 * them. Only to be used on the GL thread.
 */
class LineRenderer {
	private static final String TAG = LineRenderer.class.getSimpleName();
	private static final int POSITION_FLOATS = 3;
	private static final int COLOR_FLOATS = 4;
	private static final int STRIDE_BYTES = LineVertices.FLOATS_PER_VERTEX * Float.BYTES;

	private FloatBuffer buffer = floatBuffer(LineVertices.FLOATS_PER_VERTEX * 1024);
	private boolean created;
	private int program;
	private int positionAttribute;
	private int colorAttribute;
	private int viewProjectionUniform;

	private static FloatBuffer floatBuffer(int size) {
		return ByteBuffer.allocateDirect(size * Float.BYTES).order(ByteOrder.nativeOrder())
				.asFloatBuffer();
	}

	/**
	 * Makes the program drawing the lines. Again whenever the GL context is new.
	 */
	void createOnGlThread(Context context) throws IOException {
		created = false;
		int vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER,
				"shaders/lines.vert");
		int fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER,
				"shaders/lines.frag");
		program = GLES20.glCreateProgram();
		GLES20.glAttachShader(program, vertexShader);
		GLES20.glAttachShader(program, fragmentShader);
		GLES20.glLinkProgram(program);
		positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
		colorAttribute = GLES20.glGetAttribLocation(program, "a_Color");
		viewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ViewProjection");
		ShaderUtil.checkGLError(TAG, "Line program creation");
		created = true;
	}

	/**
	 * @param viewProjection the camera's projection times its view, column-major
	 * @param widthPixels    how wide the lines are
	 */
	void draw(float[] viewProjection, LineVertices vertices, float widthPixels) {
		int count = vertices.getCount();
		if (!created || count == 0) {
			return;
		}
		int floats = count * LineVertices.FLOATS_PER_VERTEX;
		if (buffer.capacity() < floats) {
			buffer = floatBuffer(floats * 2);
		}
		buffer.clear();
		buffer.put(vertices.getFloats(), 0, floats);

		GLES20.glUseProgram(program);
		GLES20.glUniformMatrix4fv(viewProjectionUniform, 1, false, viewProjection, 0);
		buffer.position(0);
		GLES20.glVertexAttribPointer(positionAttribute, POSITION_FLOATS, GLES20.GL_FLOAT, false,
				STRIDE_BYTES, buffer);
		buffer.position(POSITION_FLOATS);
		GLES20.glVertexAttribPointer(colorAttribute, COLOR_FLOATS, GLES20.GL_FLOAT, false,
				STRIDE_BYTES, buffer);
		GLES20.glEnableVertexAttribArray(positionAttribute);
		GLES20.glEnableVertexAttribArray(colorAttribute);

		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
		GLES20.glLineWidth(widthPixels);
		GLES20.glDrawArrays(GLES20.GL_LINES, 0, count);

		GLES20.glDisable(GLES20.GL_BLEND);
		GLES20.glDisableVertexAttribArray(positionAttribute);
		GLES20.glDisableVertexAttribArray(colorAttribute);
	}
}
