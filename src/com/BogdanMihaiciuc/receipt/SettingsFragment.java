package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.Utils.SingleBounceInterpolator;

import java.util.Currency;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;


public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

    final static boolean DEBUG_SUPER_SECRET_FRAGMENT = false;
	
	final static String CurrencySymbolKey = "currencySymbol";
	final static String UseCurrencySymbolKey = "useCurrencySymbol";
	final static String PlayExceededAleryKey = "playExceededAlert";
	final static String AutoReorderKey = "autoReorder";
    final static String ShakeToSortKey = "shakeToSort";
	final static String SelectedAccountPreferenceKey = "selectedAccountPreference";
	
	final static Semaphore initDone = new Semaphore(1, true);
	
	static CharSequence[] currencyLocaleList;
	static CharSequence[] currencyNameList;
	static CharSequence defaultLocale;
	static boolean init = createCurrencyList();
	
	static boolean createCurrencyList() {
		
		if (currencyLocaleList != null && currencyNameList != null)
			return true;
		
		new Thread() {
			public void run() {
				
				try {
					initDone.acquire();
					
					if (currencyLocaleList != null && currencyNameList != null) {
						initDone.release();
						return;
					}
					
					Locale[] locales = Locale.getAvailableLocales();
					Locale defaultLocale = Locale.getDefault();
					Currency currency;
					
					TreeMap<CharSequence, CharSequence> currencies = new TreeMap<CharSequence, CharSequence>();
					
					for (Locale locale : locales) {
						try {
							currency = Currency.getInstance(locale);
				            String country = locale.getDisplayCountry(defaultLocale);
							CharSequence symbol = currency.getSymbol(defaultLocale);
							if (country.trim().length()>0) {
								currencies.put(locale.getDisplayCountry(defaultLocale) + " - " + symbol, symbol);
							}
						}
						catch (IllegalArgumentException e) {}
					}
					
					final CharSequence[][] result = new CharSequence[2][currencies.size()];

					final Iterator<?> iter = currencies.entrySet().iterator();

					int ii = 0;
					while(iter.hasNext()){
					    final Map.Entry<?, ?> mapping = (Map.Entry<?, ?>) iter.next();

					    result[0][ii] = (CharSequence) mapping.getKey();
					    result[1][ii] = (CharSequence) mapping.getValue();

					    ii++;
					}
					
					currencyLocaleList = result[1];
					currencyNameList = result[0];
					
					
					try {
						SettingsFragment.defaultLocale = defaultLocale.getDisplayCountry(defaultLocale) + " - " + Currency.getInstance(defaultLocale).getSymbol(defaultLocale);
					}
					catch (IllegalArgumentException e) {}

				}
				catch (InterruptedException e) {
					
				}
				
				initDone.release();
			}
		}.start();
		
		return true;
		
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
        if (currencyLocaleList == null)
        	createCurrencyList();
        
        
    }
    
    
	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);

        ListPreference locales = (ListPreference) findPreference(CurrencySymbolKey);
        try {
			initDone.acquire();
	        locales.setDefaultValue(defaultLocale);
	        locales.setEntries(currencyNameList);
	        locales.setEntryValues(currencyNameList);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        initDone.release();
        
        if (locales.getValue() == null)
        	locales.setValue(defaultLocale.toString());
        else if (locales.getValue().isEmpty())
        	locales.setValue(defaultLocale.toString());
        
        onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()), SettingsFragment.CurrencySymbolKey);     
        
        Preference version = findPreference("versionInfo");
        version.setEnabled(true);
        version.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
	        	SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	        	SharedPreferences.Editor globalPrefsEditor = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).edit();
	            globalPrefsEditor.putInt("stepsToFirstRun", globalPrefs.getInt("stepsToFirstRun", 0) + 1).apply();
	            if (globalPrefs.getInt("stepsToFirstRun", 0) >= 5) {
					showAbout();
	            	globalPrefsEditor.putInt("stepsToFirstRun", 0).apply();
	            }
				return true;
			}
		});
        
        final ViewGroup Root = (ViewGroup) getActivity().getWindow().getDecorView();
        final View Content = Root.getChildAt(0);

//        findPreference("editTags").setOnPreferenceClickListener(new OnPreferenceClickListener() {
//            @Override
//            public boolean onPreferenceClick(Preference preference) {
//                TagEditor editor = new TagEditor();
//                getActivity().getFragmentManager().beginTransaction().add(editor, TagEditor.TagEditorKey).commit();
//                return true;
//            }
//        });
        
		Content.setBackgroundResource(R.drawable.gradient_list_background);
		if (getActivity().getFragmentManager().findFragmentByTag(TagEditor.TagEditorKey) == null) Root.setBackgroundColor(0);
    }
    
    
	public void showAbout() {
//        if (!DEBUG_SUPER_SECRET_FRAGMENT) return;

        getActivity().getFragmentManager().beginTransaction().add(new SuperSecretFragment(), "com.BogdanMihaiciuc.DEBUG.SuperSecretFragment").commit();

        if (initDone != null) return;

		final ViewGroup Root = (ViewGroup) getActivity().getWindow().getDecorView();
		final View Content = Root.getChildAt(0);
		final View Welcome = getActivity().getLayoutInflater().inflate(R.layout.layout_welcome, Root, false);
		Root.addView(Welcome);
		
		((TextView) Welcome.findViewById(R.id.WelcomeText)).setTextSize(18);
		((TextView) Welcome.findViewById(R.id.WelcomeText)).setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
		((TextView) Welcome.findViewById(R.id.WelcomeText)).setText("©2013 Mihaiciuc Bogdan");
		
		Root.setBackgroundColor(0xFF000000);
		Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		Welcome.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		Welcome.findViewById(R.id.WelcomePanel).setId(0);
		
		Content.animate()
			.alpha(0.4f).scaleX(0.8f).scaleY(0.8f)
			.setDuration(600)
			.setStartDelay(0)
			.setInterpolator(new AccelerateInterpolator(1f));
		Welcome.setY(-Root.getHeight());
		Welcome.animate()
			.y(0)
			.setDuration(1000)
			.setInterpolator(new SingleBounceInterpolator())
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					Content.setScaleX(0.8f);
					Content.setScaleY(0.8f);
					Content.animate()
						.alpha(1).scaleX(1).scaleY(1)
						.setDuration(500)
						.setStartDelay(1200)
						.setInterpolator(new AccelerateInterpolator(2f));
					Welcome.animate()
						.y(-Root.getHeight())
						.setDuration(700)
						.setStartDelay(1000)
						.setInterpolator(new AccelerateInterpolator(2f))
						.setListener(new AnimatorListenerAdapter() {
							public void onAnimationEnd(Animator a) {
								Root.removeView(Welcome);
								Root.setBackgroundColor(0);
								Content.setLayerType(View.LAYER_TYPE_NONE, null);
							}
						});
				}
			});

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        prefs.edit().putBoolean(WelcomeFragment.WelcomeKey, true).apply();
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(CurrencySymbolKey)) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            if (connectionPref != null) connectionPref.setSummary(sharedPreferences.getString(key, ""));
        }
    }
	
}
