package com.BogdanMihaiciuc.util;

import android.app.Fragment;
import android.view.View;

/**
 * The <strong>ExtendedFragment</strong> provides convenient access to several {@link com.BogdanMihaiciuc.util.$ ViewProxy} builder methods.
 */
public class ExtendedFragment extends Fragment {

    /**
     * Creates a new ViewProxy wrapper using the supplied query string, using this fragment's attached activity.
     * See {@link com.BogdanMihaiciuc.util.$#find(android.app.Activity, String)} for more info.
     * @param args The query string.
     * @return A new ViewProxy wrapper.
     */
    public $ $(String args) {
        return $.find(getActivity(), args);
    }


    /**
     * Creates a new ViewProxy wrapper using the supplied query string, using this fragment's attached activity.
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
        return $.emptySet(getActivity());
    }

    /**
     * Creates a new ViewProxy wrapper around the views with the specified ids.
     * <strong>NOTE:</strong> This method will use android's own {@link android.view.View#findViewById(int) findViewById()} method, which will find at most 1 view for each id.
     * @param args The ids of views to include.
     * @return A new ViewProxy wrapper around the matching views.
     */
    public $ $(int ... args) {
        return $.find(getActivity(), args);
    }

    /**
     * Wraps the supplied views in a new ViewProxy wrapper.
     * @param views The views which will be wrapped.
     * @return A new ViewProxy wrapper around the supplied views.
     */
    public $ $(View ... views) {
        return $.wrap(views);
    }

}
