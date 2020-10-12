package com.climbtheworld.app;

import android.app.Application;
import android.content.Context;

public class ClimbTheWorld extends Application {

	private static ClimbTheWorld mContext;

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = this;
	}

	public static Context getContext() {
		return mContext.getApplicationContext();
	}
}