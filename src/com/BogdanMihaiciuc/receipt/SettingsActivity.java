package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;

public class SettingsActivity extends Activity implements LegacyActionBar.OnLegacyActionSelectedListener {
	
	private SettingsFragment fragment;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preferences);
		
		fragment = new SettingsFragment();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		preferences.registerOnSharedPreferenceChangeListener(fragment);
		
		// Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.ContentContainer, fragment)
                .commit();

        LegacyActionBar legacyActionBar = (LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar);
        if (!legacyActionBar.isRetainedInstance()) {
//            legacyActionBar.setLogoResource(R.drawable.logo);
//            legacyActionBar.setCaretResource(R.drawable.caret_up_light);
//            legacyActionBar.setLogoResource(R.drawable.back_light);
//            legacyActionBar.setCaretResource(R.drawable.null_drawable);
            legacyActionBar.setBackMode(LegacyActionBarView.DoneBackMode);
            legacyActionBar.setDoneResource(R.drawable.back_light_centered);

            legacyActionBar.setSeparatorVisible(true);
            legacyActionBar.setSeparatorThickness(2);
            legacyActionBar.setSeparatorOpacity(0.33f);

            legacyActionBar.setBackgroundColor(getResources().getColor(R.color.ActionBar));
        }

//		getActionBar().hide();
		// Show the Up button in the action bar.
//		getActionBar().setDisplayHomeAsUpEnabled(true);
        //ActionBar initialize
//        ActionBar actionBar = getActionBar();
//        actionBar.setDisplayUseLogoEnabled(true);
//        actionBar.setDisplayShowTitleEnabled(false);
        
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		preferences.unregisterOnSharedPreferenceChangeListener(fragment);
	}

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            TagEditor tagEditor = (TagEditor) getFragmentManager().findFragmentByTag(TagEditor.TagEditorKey);
            if (tagEditor != null) {
                tagEditor.handleMenuPressed();
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onActionModeStarted(final ActionMode actionMode) {

        if (getFragmentManager().findFragmentByTag(TagEditor.TagEditorKey) != null) {
            ((TagEditor) getFragmentManager().findFragmentByTag(TagEditor.TagEditorKey)).onActionModeStarted(actionMode);
        }

    }

    @Override
    public void onBackPressed() {

        TagEditor tagEditor = (TagEditor) getFragmentManager().findFragmentByTag(TagEditor.TagEditorKey);
        if (tagEditor != null) {
            if (tagEditor.handleBackPressed());
                return;
        }

        SuperSecretFragment superSecretFragment = (SuperSecretFragment) getFragmentManager().findFragmentByTag("com.BogdanMihaiciuc.DEBUG.SuperSecretFragment");
        if (superSecretFragment != null) {
            if (!superSecretFragment.onBackPressed())
                super.onBackPressed();
        }
        else {
            super.onBackPressed();
        }
    }
	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			super.onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        switch (item.getId()) {
            case android.R.id.home:
                super.onBackPressed();
        }
    }
}
