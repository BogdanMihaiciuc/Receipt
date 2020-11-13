package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.ListenableEditText;
import com.BogdanMihaiciuc.util.Utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class HistorySearchFragment extends Fragment {

    final static String TAG = HistorySearchFragment.class.getName();
    final static boolean DEBUG = false;
    final static boolean DEBUG_SEARCHRESOLVER = false;

    final static String[] Months, ShortMonths;

    static String[] getOrderedMonthNames(Map<String, Integer> source) {
        String names[] = new String[source.size()];

        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            names[entry.getValue()] = entry.getKey().toLowerCase();
        }

        return names;
    }

    static {
        Calendar calendar = Calendar.getInstance();
        Months = getOrderedMonthNames(calendar.getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.getDefault()));
        ShortMonths = getOrderedMonthNames(calendar.getDisplayNames(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
    }

    final static int ItemToken = 0;
    final static int TagToken = 1;
    final static int CalendarToken = 2;

    // These constants show at which level each of the token operates
    final static int ItemQuery = 0;
    final static int ReceiptQuery = 1;

    private static abstract class Token {
        int tokenType;
        String userString;

        abstract String getQueryClause();
        abstract int getLevel();
        abstract boolean matchesSearch(String string);
        String getArgument() {
            return null;
        }
    }

    static class ItemToken extends Token {
        String matchedName;

        boolean matchesSearch(String string) {
            return string.toLowerCase().startsWith(matchedName.toLowerCase().trim());
        }

        String getQueryClause() {
            return "lower(" + Receipt.DBItemsTable + "." + Receipt.DBNameKey + ") like ?";
        }

        String getArgument() {
            return matchedName + "%";
        }

        int getLevel() {
            return ItemQuery;
        }
    }

    static class TagToken extends Token {
        ArrayList<Integer> matchedTags = new ArrayList<Integer>();

        boolean matchesSearch(String string) {
            return false; // TODO: placeholder
        }

        String getQueryClause() {
            String query = Receipt.DBItemsTable + "." + Receipt.DBItemUIDKey + " in " +
                    "(select distinct " + Receipt.DBItemConnectionUIDKey +
                    " from " + Receipt.DBTagConnectionsTable +
                    " where " + Receipt.DBTagConnectionUIDKey + " in (";
            for (int i = 0; i < matchedTags.size() - 1; i++) {
                query += matchedTags.get(i) + ", ";
            }
            if (matchedTags.size() > 0) {
                query += matchedTags.get(matchedTags.size() - 1);
            }
            query += "))";
            return query;
        }

        int getLevel() {
            return ItemQuery;
        }
    }

    static class CalendarToken extends Token {
        long matchedTime;
        String timeFormat;

        boolean matchesSearch(String string) {
            return false; // TODO: placeholder
        }

        String getQueryClause() {
            return "strftime(" + timeFormat + ", " + Receipt.DBReceiptsTable + "." + Receipt.DBDateKey  + ", 'unixepoch', 'localtime') = " +
                    "strftime(" + timeFormat + ", " + matchedTime + ", 'unixepoch', 'localtime')";
        }

        int getLevel() {
            return ReceiptQuery;
        }
    }

    public static class Query {
        String rawQuery;
        ArrayList<String> args = new ArrayList<String>();

        ArrayList<Token> tokens;

        public boolean matchesSearch(String string) {
            if (tokens != null) {
                if (DEBUG_SEARCHRESOLVER) Log.d(TAG, "SearchResolver: matching " + string);

                for (Token token : tokens) {
                    if (token.matchesSearch(string)) {
                        if (DEBUG_SEARCHRESOLVER) Log.d(TAG, "itemToken " + token.getArgument() + " found a match.");
                        return true;
                    }
                    else {
                        if (DEBUG_SEARCHRESOLVER) Log.d(TAG, "itemToken " + token.getArgument() + " does not match.");
                    }
                }
            }
            else {
                Log.e(TAG, "tokens is NULL!");
            }

            return false;
        }
    }

    private HistoryActivity activity;

    private FrameLayout searchRoot;
    private ListenableEditText searchBox;
    private ArrayList<Token> tokens = new ArrayList<Token>();
    private String searchString;

    private CollectionView.Section currentSection;

    private boolean searchFired;
    private boolean blockedPosition;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity = (HistoryActivity) getActivity();

        searchRoot = (FrameLayout) activity.findViewById(R.id.SearchBoxLayout).getParent();
        searchRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchBox.requestFocus();
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(searchBox, InputMethodManager.SHOW_FORCED);
            }
        });

        searchBox = (ListenableEditText) activity.findViewById(R.id.SearchBox);
        searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                blockedPosition = hasFocus;
                if (hasFocus) {
                    searchRoot.animate().translationY(0);

                    if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
                        activity.collapseNavigationPanel();
                    }
                }
            }
        });
        searchBox.setHint(Utils.echoText(searchBox.getHint(), getResources().getColor(R.color.DashboardTitle)));
        if (searchString != null) {
            searchBox.setText(searchString);
            searchBox.requestFocus();
            searchRoot.findViewById(R.id.ClearSearch).setVisibility(View.VISIBLE);
        }
        searchRoot.findViewById(R.id.ClearSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager imm = (InputMethodManager) searchBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
                searchBox.clearFocus();

                hideClearSearchButton();
                clearSearch();
            }
        });
        searchBox.setOnKeyPreImeListener(new ListenableEditText.OnKeyPreImeListener() {
            @Override
            public boolean onKeyPreIme(int keyCode, KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    searchRoot.findViewById(R.id.ClearSearch).performClick();
                    return true;
                }

                return false;
            }
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                SearchHandler.removeCallbacks(ProcessSearchRunnable);

                boolean wasSearchActive = searchString != null;
                searchString = charSequence.toString().trim();

                if (!TextUtils.isEmpty(searchString)) {
                    SearchHandler.postDelayed(ProcessSearchRunnable, SearchDelay);
                    if (!wasSearchActive) {
                        showClearSearchButton();
                    }
                }
                else {
                    searchString = null;
                    if (wasSearchActive) {
                        hideClearSearchButton();
                    }
                    if (searchFired) {
                        clearSearch();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        /*final TextView FloatingHeader = (TextView) activity.findViewById(R.id.HistoryFloatingHeader);
        FloatingHeader.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {getResources().getColor(R.color.HistoryBackground),
                        getResources().getColor(R.color.HistoryBackgroundTransparent)}));*/

        final boolean phoneUI = getResources().getConfiguration().smallestScreenWidthDp < 600;
        if (!phoneUI) ((CollectionView) activity.findViewById(R.id.History)).addTransactionListener(new CollectionView.TransactionListener() {
            @Override
            public void onTransactionStart() {
                currentSection = null;
                if (activity == null) return;
                activity.onCurrentSectionChanged(null);
            }

            @Override
            public void onTransactionEnd() {
                if (activity == null) return;
                ((CollectionView) activity.findViewById(R.id.History)).getOnScrollListener().onScroll(((CollectionView) activity.findViewById(R.id.History)),
                        activity.findViewById(R.id.History).getScrollY(), 0);
            }
        });

        ((CollectionView) activity.findViewById(R.id.History)).setOnScrollListener(new CollectionView.OnScrollListener() {
            float headerTopLimit;

            @Override
            public void onScroll(CollectionView collectionView, int top, int amount) {
//                if (top > collectionView.getChildAt(0).getHeight() - collectionView.getHeight()) {
////                    Overscroll bump, ignore
//                    return;
//                }
                if (activity == null) return;

                if (top <= 0) {
                    //Top overscroll bump
                    searchRoot.setTranslationY(0);
                    return;
                }
                if (amount > 0) { //content scroll direction is up
                    if (-searchRoot.getTranslationY() < searchRoot.getHeight()) {
                        float translation = searchRoot.getTranslationY() - amount;
                        translation = translation < -searchRoot.getHeight() ? -searchRoot.getHeight() : translation;
                        headerTopLimit = searchRoot.getHeight() + translation;
                        if (!blockedPosition) searchRoot.setTranslationY(translation);
                    }
                } else { // content scroll direction is down
                    float translation = searchRoot.getTranslationY() - amount;
                    translation = translation > 0 ? 0 : translation;
                    headerTopLimit = searchRoot.getHeight() + translation;
                    if (!blockedPosition) searchRoot.setTranslationY(translation);
                }

                // Some operations cause the controller to become dirty while in a transaction
                // this can lead to an infinite transaction loop
                // Additionally, highlighting the current section isn't useful on phones, since the navigation panel
                // is hidden most of the times
                if (collectionView.getController().isInTransaction() || phoneUI) {
                    return;
                }

                final int headerType = getResources().getConfiguration().smallestScreenWidthDp < 600 ?
                        HistoryGridAdapter.ItemTypeHeader : HistoryGridAdapter.ItemTypeBigHeader;

                CollectionView.Section topSection = collectionView.getFirstVisibleSection();
                if (topSection == null) return;
                boolean currentSectionHasChanged = false;
                boolean canCurrentSectionChange = true;
                if (amount < 0 && currentSection != null) {
                    View header = collectionView.getViewForObject(currentSection.getObjectAtIndex(0));
                    if (header != null) {
                        if (((View) header.getParent()).getY() - top < headerTopLimit) {
                            canCurrentSectionChange = false;
                        }
                    }
                }
                if (canCurrentSectionChange) {
                    if (topSection.getViewType() == headerType) {
                        if (topSection != currentSection) {
                            currentSection = topSection;
                            currentSectionHasChanged = true;
                        }
                    } else {
                        int index = collectionView.getController().getIndexOfSection(topSection);
                        while (index > 0) {
                            index--;
                            if ((topSection = collectionView.getController().getSectionAtIndex(index)).getViewType() == headerType) {
                                if (topSection != currentSection) {
                                    currentSection = topSection;
                                    currentSectionHasChanged = true;
                                }
                                break;
                            }
                        }
                    }


                    if (currentSection == null) {
                        // Looking up didn't work, time to look down
                        int index = collectionView.getController().getIndexOfSection(topSection);
                        int count = collectionView.getController().getSectionCount();
                        while (index < count) {
                            index++;
                            if ((topSection = collectionView.getController().getSectionAtIndex(index)).getViewType() == headerType) {
                                if (topSection != currentSection) {
                                    currentSection = topSection;
                                    currentSectionHasChanged = true;
                                }
                                break;
                            }
                        }
                    }

                    // If the current section is still null at this point, it means there are no header sections in the dataset
                }

                if (currentSectionHasChanged) {
                    activity.onCurrentSectionChanged(currentSection);
                }

            }
        });
    }

    public void clearSearchbarFocus() {
        if (searchBox != null) {
            searchBox.clearFocus();
        }
    }

    public boolean handleBackPressed() {
        if (searchString != null) {
            searchRoot.findViewById(R.id.ClearSearch).performClick();
            return true;
        }

        return false;
    }

    public void showClearSearchButton() {
        View clearSearch = searchRoot.findViewById(R.id.ClearSearch);
        clearSearch.setVisibility(View.VISIBLE);
        clearSearch.setTranslationX(clearSearch.getWidth());
        clearSearch.setAlpha(0f);
        clearSearch.animate().alpha(1f).translationX(0f).setListener(null).setInterpolator(new Utils.FrictionInterpolator(1.5f));
    }

    public void hideClearSearchButton() {
        View clearSearch = searchRoot.findViewById(R.id.ClearSearch);
        clearSearch.animate().alpha(0f).translationX(clearSearch.getWidth()).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (searchRoot == null) return;
                searchRoot.findViewById(R.id.ClearSearch).setVisibility(View.INVISIBLE);
            }
        }).setInterpolator(new Utils.FrictionInterpolator(1.5f));
    }

    public void clearSearch() {
        if (searchFired) {
            searchFired = false;
            searchString = null;
            if (activity != null) activity.clearSearch();
        }

        if (searchBox != null) {
            searchBox.setText("");
        }
    }

    final static long SearchDelay = 500;
    final private Handler SearchHandler = new Handler();
    final private Runnable ProcessSearchRunnable = new Runnable() {

        public void run() {
            if (activity != null) {
                searchFired = true;
                activity.search(getCompleteQuery());
            }
        }
    };

    // TODO
    final Pattern splitter = Pattern.compile("[\\s\\/-]*");
    ArrayList<Token> getPossibleTokens(CharSequence source) {
        String text[] = splitter.split(source);

        // Date formats
        if (text.length < 3) {
            int digitFieldCount = 0;
            for (String string : text) {
                if (TextUtils.isDigitsOnly(string)) {
                    digitFieldCount += 1;
                }
            }
            if (digitFieldCount > text.length - 1) {
                // Possible date; one word may be a text month

                // Month
                ArrayList<Integer> months = new ArrayList<Integer>();

                if (digitFieldCount == text.length - 1) {
                    // Find if the non-number is a month
                    int monthPosition = -1;

                    for (int j = 0; j < text.length && monthPosition == -1; j++) {
                        String string = text[j].toLowerCase();
                        for (int i = 0; i < Months.length; i++) {
                            // matching the short month implies matching the long month as well
                            if (Months[i].startsWith(string)) {
                                monthPosition = j;
                                months.add(i);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public Query getCompleteQuery() {
        Query query = new Query();
        query.tokens = new ArrayList<Token>();

        ArrayList<Token> processedTokens = new ArrayList<Token>();
        // combine all tag tokens into one
        TagToken tagToken = new TagToken();
        boolean combinedTokens = false;
        for (Token token : tokens) {
            if (token.getClass() == TagToken.class) {
                tagToken.matchedTags.addAll(((TagToken) token).matchedTags);
                combinedTokens = true;
            }
            else {
                processedTokens.add(token);
            }
        }

        if (combinedTokens) {
            processedTokens.add(tagToken);
            query.tokens.add(tagToken);
        }

        //There's a final item token that's not included in the token list -- the currently edited string
        ItemToken itemToken = new ItemToken();
        itemToken.matchedName = searchString;
        processedTokens.add(itemToken);

        query.tokens.add(itemToken);


        //QueryBase
        StringBuilder rawQuery = new StringBuilder();
        rawQuery.append("select ");
        for (int i = 0; i < Receipt.DBAllReceiptColumns.length; i++) {
            rawQuery.append(Receipt.DBReceiptsTable + ".").append(Receipt.DBAllReceiptColumns[i]);
            if (i != Receipt.DBAllReceiptColumns.length - 1)
                rawQuery.append(", ");
        }
        rawQuery.append(" from " + Receipt.DBReceiptsTable +
                "\n where "  + Receipt.DBFilenameIdKey +
                "\n in (select distinct " + Receipt.DBTargetDBKey +
                "\n\t from " + Receipt.DBItemsTable);

        boolean whereAdded = false;

        for (Token token : processedTokens) {
            if (token.getLevel() == ItemQuery) {
                if (!whereAdded) {
                    rawQuery.append("\n\t where ");
                    whereAdded = true;
                }
                else {
                    rawQuery.append("\n\t\t and ");
                }

                rawQuery.append(token.getQueryClause());

                String arg = token.getArgument();
                if (arg != null) {
                    query.args.add(arg);
                }
            }
        }

        rawQuery.append(")");

        for (Token token : processedTokens) {
            if (token.getLevel() == ReceiptQuery) {
                rawQuery.append("\n\t and ");
                rawQuery.append(token.getQueryClause());

                String arg = token.getArgument();
                if (arg != null) {
                    query.args.add(arg);
                }
            }
        }

        rawQuery.append("\n order by " + Receipt.DBReceiptsTable + "." + Receipt.DBDateKey + " desc");

        query.rawQuery = rawQuery.toString();

        if (DEBUG) {
            Log.d(TAG, "Created query: \n " + query.rawQuery + "\n with args " + query.args);
        }

        return query;
    }

    public void onDetach() {
        super.onDetach();
    }

}
