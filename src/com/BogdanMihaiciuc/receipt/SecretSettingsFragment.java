package com.BogdanMihaiciuc.receipt;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;


public class SecretSettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.super_secret_preferences);
        
    }
    
    
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);

    }
    

    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("SOMEKEY")) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            if (connectionPref != null) connectionPref.setSummary(sharedPreferences.getString(key, ""));
        }
    }
	
}
