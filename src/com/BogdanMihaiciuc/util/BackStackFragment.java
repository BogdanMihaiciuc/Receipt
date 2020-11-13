package com.BogdanMihaiciuc.util;

import android.os.Bundle;

import java.util.ArrayList;

/**
 * Created by Bogdan on 8/6/15.
 */
public class BackStackFragment extends ExtendedFragment implements Utils.BackStack {
    // *********** BACKSTACK ****************
    private ArrayList<Runnable> backStack = new ArrayList<Runnable>();

    private final Runnable BackDisabler = new Runnable() {
        @Override
        public void run() {
            pushToBackStack(this);
        }
    };

    public void pushToBackStack(Runnable r) {
        backStack.add(r);
    }

    public void disableBackButton() {
        pushToBackStack(BackDisabler);
    }

    public void enableBackButton() {
        for (int i = backStack.size() - 1; i >= 0; i--) {
            if (backStack.get(i) == BackDisabler) {
                backStack.remove(i);
                return;
            }
        }
    }

    public boolean canPopBackStack() {
        return backStack.size() > 0;
    }

    public boolean popBackStack() {

        if (backStack.size() > 0) {
            backStack.remove(backStack.size() - 1).run();
            return true;
        }

        if (getActivity() != null) getActivity().onBackPressed();
        return false;
    }

    @Override
    public Utils.BackStack persistentBackStack() {
        return this;
    }

    public void popBackStackFrom(Runnable r) {

        int insertionPoint = backStack.indexOf(r);
        if (insertionPoint != -1) {
            for (; insertionPoint < backStack.size(); ) {
                backStack.remove(insertionPoint);
            }
        }
    }

    public void rewindBackStackFrom(Runnable r) {

        int insertionPoint = backStack.indexOf(r);
        if (insertionPoint != -1) {
            for (; insertionPoint < backStack.size(); ) {
                backStack.remove(insertionPoint).run();
            }
        }
    }

    public void swipeFromBackStack(Runnable r) {

        backStack.remove(r);
    }

    public int backStackSize() {
        return backStack.size();
    }

    // *********** BACKSTACK ****************


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

}
