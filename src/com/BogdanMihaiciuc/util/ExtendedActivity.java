package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.View;

import com.BogdanMihaiciuc.util.*;
import com.BogdanMihaiciuc.util.Utils;

import java.util.ArrayList;

/**
 * The <strong>ExtendedActivity</strong> class extends the base {@link android.app.Activity} class and adds:<br/>
 *  - a persistent {@link com.BogdanMihaiciuc.util.Utils.BackStack BackStack} implementation<br/>
 *  - the {@link com.BogdanMihaiciuc.util.$ ViewProxy} builder functions, same as the {@link com.BogdanMihaiciuc.util.ExtendedFragment ExtendedFragment}<br/>
 *  - an {@link com.BogdanMihaiciuc.util.Utils.RippleAnimationStack} implementation, allowing you to use {@link LegacyRippleDrawable LegacyRippleDrawables} within this activity.
 */
public class ExtendedActivity extends Activity implements Utils.BackStack, Utils.RippleAnimationStack {

    private BackStackFragment persistentBackStack;
    final static String BackStackKey = "com.BogdanMihaiciuc.Utils.BackStackFramgent";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment backStack = getFragmentManager().findFragmentByTag(BackStackKey);

        if (backStack != null) {
            persistentBackStack = (BackStackFragment) backStack;
        }
        else {
            persistentBackStack = new BackStackFragment();
            getFragmentManager().beginTransaction().add(persistentBackStack, BackStackKey).commit();
        }

    }

    @Override
    public void pushToBackStack(Runnable r) {
        persistentBackStack.pushToBackStack(r);
    }

    @Override
    public void popBackStackFrom(Runnable r) {
        persistentBackStack.popBackStackFrom(r);
    }

    @Override
    public void rewindBackStackFrom(Runnable r) {
        persistentBackStack.rewindBackStackFrom(r);
    }

    @Override
    public void swipeFromBackStack(Runnable r) {
        persistentBackStack.swipeFromBackStack(r);
    }

    @Override
    public boolean popBackStack() {
        return persistentBackStack.popBackStack();
    }

    @Override
    public Utils.BackStack persistentBackStack() {
        return persistentBackStack;
    }

    @Override
    public int backStackSize() {
        return persistentBackStack.backStackSize();
    }



    /**
     * Creates a new ViewProxy wrapper using the supplied query string, using this activity view hierarchy.
     * See {@link com.BogdanMihaiciuc.util.$#find(android.app.Activity, String)} for more info.
     * @param args The query string.
     * @return A new ViewProxy wrapper.
     */
    public $ $(String args) {
        return $.find(this, args);
    }


    /**
     * Creates a new ViewProxy wrapper using the supplied query string, using this activity view hierarchy.
     * The ViewProxy will only find views that are descendands of the supplied view.
     * See {@link com.BogdanMihaiciuc.util.$#find(android.view.View, String)} for more info.
     * @param root The root of the view hierarchy that will be included in the search.
     * @param args The query string.
     * @return A new ViewProxy wrapper.
     */
    public $ $(View root, String args) {
        return $.find(root, args);
    }

    /**
     * Creates an empty ViewProxy wrapper.
     * @return A new empty ViewProxy wrapper.
     */
    public $ $() {
        return $.emptySet(this);
    }

    /**
     * Creates a new ViewProxy wrapper around the views with the specified ids.
     * <strong>NOTE:</strong> This method will use android's own {@link android.view.View#findViewById(int) findViewById()} method, which will find at most 1 view for each id.
     * @param args The ids of views to include.
     * @return A new ViewProxy wrapper around the matching views.
     */
    public $ $(int ... args) {
        return $.find(this, args);
    }

    /**
     * Wraps the supplied views in a new ViewProxy wrapper.
     * @param views The views which will be wrapped.
     * @return A new ViewProxy wrapper around the supplied views.
     */
    public $ $(View ... views) {
        return $.wrap(views);
    }



    private ArrayList<Animator> ripples = new ArrayList<Animator>();

    public void flushRipples() {
        while (ripples.size() > 0) {
            ripples.get(0).end();
        }
    }

    public void addRipple(Animator a) {
        ripples.add(a);
    }

    public void removeRipple(Animator a) {
        ripples.remove(a);
    }

}
