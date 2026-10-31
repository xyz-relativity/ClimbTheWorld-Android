package com.climbtheworld.app.tutorial;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ARCore.ArCoreInstall;
import com.climbtheworld.app.configs.Configs;

/**
 * Tells about ARCore, as its notice does, and offers to install it from Google Play, or as the APK
 * from Google's GitHub page, for phones without Google Play, so the A.R. views need not ask for
 * it later. Non-FOSS build only.
 */
public class ArCoreFragment extends TutorialFragment implements DefaultLifecycleObserver {
	private ViewGroup view;

	public ArCoreFragment(AppCompatActivity parent, int viewID) {
		super(parent, viewID);
	}

	@Override
	public void onCreate(ViewGroup view) {
		super.onCreate(view);
		this.view = view;
		setMessage(view, getString(R.string.arcore_notice_message));

		view.findViewById(R.id.buttonPlayStore).setOnClickListener(v -> {
			noticeShown();
			ArCoreInstall.openPlayStore(parent.get());
		});
		view.findViewById(R.id.buttonApk).setOnClickListener(v -> {
			noticeShown();
			ArCoreInstall.openApkPage(parent.get());
		});
		// Back from Google Play or the APK's install.
		parent.get().getLifecycle().addObserver(this);
		updateButtons();
	}

	@Override
	public void onDestroy(ViewGroup view) {
		parent.get().getLifecycle().removeObserver(this);
		this.view = null;
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		updateButtons();
	}

	/**
	 * The page tells about ARCore as its notice does, which the A.R. views then leave out.
	 */
	private void noticeShown() {
		Configs.instance(parent.get()).setBoolean(Configs.ConfigKey.showArCoreNotice, false);
	}

	/**
	 * Offers to install ARCore while it is missing or too old: the page only comes where it runs,
	 * see ArCoreFeatures.tutorialPage.
	 */
	private void updateButtons() {
		AppCompatActivity activity = parent.get();
		if (view == null || activity == null) {
			return;
		}
		view.findViewById(R.id.arCoreInstall).setVisibility(
				ArCoreInstall.needsInstall(activity) ? View.VISIBLE : View.GONE);
		view.findViewById(R.id.buttonPlayStore).setVisibility(
				ArCoreInstall.hasPlayStore(activity) ? View.VISIBLE : View.GONE);
	}
}
