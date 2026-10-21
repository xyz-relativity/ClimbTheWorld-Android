package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.climbtheworld.app.augmentedreality.ARCore.helpers.ShaderUtil;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * The camera image ARCore tracks with, drawn over the whole view as ARCore crops it for the
 * display. Only to be used on the GL thread.
 */
class CameraBackground {
	private static final String TAG = CameraBackground.class.getSimpleName();
	// The view, as a triangle strip in normalised device coordinates.
	private static final float[] QUAD_COORDS = {-1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f};
	private static final int COORDS_PER_VERTEX = 2;
	private static final int VERTEX_COUNT = QUAD_COORDS.length / COORDS_PER_VERTEX;

	private final FloatBuffer quadCoords = floatBuffer(QUAD_COORDS.length).put(QUAD_COORDS);
	// Where each corner of the view is in the camera image, as ARCore crops and turns it.
	private final FloatBuffer quadTexCoords = floatBuffer(QUAD_COORDS.length);
	private boolean created;
	private int textureId;
	private int program;
	private int positionAttribute;
	private int texCoordAttribute;

	private static FloatBuffer floatBuffer(int size) {
		return ByteBuffer.allocateDirect(size * Float.BYTES).order(ByteOrder.nativeOrder())
				.asFloatBuffer();
	}

	/**
	 * Makes the texture ARCore writes the camera image to, and the program drawing it. Again
	 * whenever the GL context is new.
	 */
	void createOnGlThread(Context context) throws IOException {
		created = false;
		int[] textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		textureId = textures[0];
		int target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
		GLES20.glBindTexture(target, textureId);
		GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

		int vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER,
				"shaders/screenquad.vert");
		int fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER,
				"shaders/screenquad.frag");
		program = GLES20.glCreateProgram();
		GLES20.glAttachShader(program, vertexShader);
		GLES20.glAttachShader(program, fragmentShader);
		GLES20.glLinkProgram(program);
		positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
		texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");
		ShaderUtil.checkGLError(TAG, "Camera background creation");
		created = true;
	}

	boolean isCreated() {
		return created;
	}

	int getTextureId() {
		return textureId;
	}

	void draw(Frame frame) {
		if (frame.hasDisplayGeometryChanged()) {
			quadCoords.position(0);
			quadTexCoords.position(0);
			frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
					quadCoords, Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords);
		}
		// No camera image yet.
		if (frame.getTimestamp() == 0) {
			return;
		}

		quadCoords.position(0);
		quadTexCoords.position(0);
		// Behind everything drawn after it.
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
		GLES20.glDepthMask(false);

		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
		GLES20.glUseProgram(program);
		GLES20.glVertexAttribPointer(positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
				0, quadCoords);
		GLES20.glVertexAttribPointer(texCoordAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
				0, quadTexCoords);
		GLES20.glEnableVertexAttribArray(positionAttribute);
		GLES20.glEnableVertexAttribArray(texCoordAttribute);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);
		GLES20.glDisableVertexAttribArray(positionAttribute);
		GLES20.glDisableVertexAttribArray(texCoordAttribute);

		GLES20.glDepthMask(true);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
	}
}
