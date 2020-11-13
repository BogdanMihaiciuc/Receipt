package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

public class CalendarPicker extends ExtendedFragment implements LegacyActionBar.CustomViewProvider, LegacyActionBar.OnLegacyActionSelectedListener {

    final static String TAG = CalendarPicker.class.getName();

    final static boolean USE_RESIZE = false;

    final static int DateSizeDP = 36;

    final static int StateDate = 0;
    final static int StateMonth = 1;

    public final static String ActionBarKey = "CalendarPicker$ActionBar";

    public final static String RootViewKey = "CP$Root";
    public final static String NextRootKey = "CP$NRoot";

    public final static String ActionBarContainerKey = "CP$ActionBarC";

    public final static String HeaderMetadataKey = "CP$HeaderView";
    public final static String DateMetadataKey = "CP$DateView";
    public final static String MonthMetadataKey = "CP$MonthView";
    public final static String YearMetadataKey = "CP$YearView";

    public final static String SelectedKey = "CP$Selected";

    private ViewGroup container;
    private Activity activity;

    private LegacyActionBar actionBar = LegacyActionBar.getAttachableLegacyActionBar();

    private int state = StateDate;

    private Calendar selectedDate;
    private Calendar date;

    public void setDate(long timeInMillis) {
        selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(timeInMillis);

        date = Calendar.getInstance();
        date.setTimeInMillis(timeInMillis);
    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = getActivity();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = getActivity();

        setRetainInstance(true);
        date = date == null ? Calendar.getInstance() : date;

//        actionBar.buildItem()
//                    // TODO
//                    .setId(Utils.UndoID)
//                    .setResource(Utils.UndoIconDark)
//                    .setTitle(getString(Utils.UndoLabel))
//                    .setTitleVisible(false)
//                    .setShowAsIcon(true)
//                .build();

        actionBar.setOnLegacyActionSeletectedListener(this);
//        actionBar.setBackButtonEnabled(false);
        actionBar.setBackMode(LegacyActionBarView.DoneBackMode);
        actionBar.setTitle(Utils.titleFormattedText(date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())));
        actionBar.setTextColor($.color(Utils.DashboardText, activity));
        actionBar.setDoneResource(Utils.CalendarIconDark);

        actionBar.setSeparatorVisible(true);
        actionBar.setSeparatorOpacity(.1f);

        activity.getFragmentManager().beginTransaction().add(actionBar, ActionBarKey).commit();
    }

    public void detach() {
        $.createGlobalQueue();

        getActivity().getFragmentManager().beginTransaction().remove(actionBar).remove(this).commit();
    }

    public void onDetach() {
        super.onDetach();

        activity = null;
        container = null;
    }

    public void onDestroy() {
        super.onDestroy();
    }

    final static int DirectionNext = -1;
    final static int DirectionNone = 0;
    final static int DirectionPrevious = 1;

    private View createView(Context context) {
        final Context activity = this.activity == null ? context : this.activity;

        $.bind(activity);

        final EventTouchListener Dragger = EventTouchListener.listenerInContext(activity);
        Dragger.setEnforceTapThreshold(true);
        Dragger.setHandlesClick(false);
        Dragger.setHandlesLongClick(false);
        Dragger.setDelegate(new EventTouchListener.EventDelegate() {

            private $ collection;
            private $ rootView;
            private int direction = DirectionNext;

            @Override
            public boolean viewShouldPerformClick(EventTouchListener listener, View view) {
                return false;
            }

            @Override
            public boolean viewShouldPerformLongClick(EventTouchListener listener, View view) {
                return false;
            }

            @Override
            public boolean viewShouldStartMoving(EventTouchListener listener, View view) {
                if ($(container, "." + RootViewKey).animated("Scrolling")) return false;

                collection = $(container, "." + RootViewKey + ", ." + NextRootKey);
                rootView = collection.filter("." + RootViewKey);

                return true;
            }

            @Override
            public void viewDidMove(EventTouchListener listener, View view, float distance) {
                collection.property($.TranslateX, distance, $.Op.Add);

                if (rootView.property($.X) > 0) {
                    if (direction != DirectionPrevious) {
                        direction = DirectionPrevious;

                        // Months are all the same
                        if (state == StateDate) {
                            Calendar refactor = Calendar.getInstance();
                            refactor.setTimeInMillis(date.getTimeInMillis());
                            refactor.add(Calendar.MONTH, -1);

                            $.bind(activity);
                            refactorDates(collection.filter("." + NextRootKey), refactor);
                            $.unbind();
                        }

                        collection.filter("." + NextRootKey).property($.X, rootView.property($.X) - rootView.width());
                    }
                }
                else {
                    if (direction != DirectionNext) {
                        direction = DirectionNext;

                        // Months are all the same
                        if (state == StateDate) {
                            Calendar refactor = Calendar.getInstance();
                            refactor.setTimeInMillis(date.getTimeInMillis());
                            refactor.add(Calendar.MONTH, 1);

                            $.bind(activity);
                            refactorDates(collection.filter("." + NextRootKey), refactor);
                            $.unbind();
                        }

                        collection.filter("." + NextRootKey).property($.X, rootView.property($.X) + rootView.width());
                    }
                }
            }

            @Override
            public void viewDidBeginSwiping(EventTouchListener listener, View view, float velocity) {
                onDragStopped(view, (int) Math.signum(velocity));
            }

            @Override
            public void viewDidCancelSwiping(EventTouchListener listener, View view) {
                onDragStopped(view, 0);
            }

            private void onDragStopped(View view, final int Modifier) {
                float value = Modifier * view.getWidth() - $("." + RootViewKey).property($.X);

                if (Modifier != 0) {
                    $(container, "." + SelectedKey).selected(false);

                    if (state == StateDate) {
                        CalendarPicker.this.date.add(Calendar.MONTH, -Modifier);
                        CalendarPicker.this.date.set(Calendar.DATE, 1);
                    }
                    else {
                        CalendarPicker.this.date.add(Calendar.YEAR, -Modifier);
                        CalendarPicker.this.date.set(Calendar.MONTH, 1);
                        CalendarPicker.this.date.set(Calendar.DATE, 1);
                    }

                    SpannableStringBuilder title;
                    if (state == StateDate) {
                        title = Utils.titleFormattedText(
                                "" + date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                                        + (date.get(Calendar.YEAR) != Calendar.getInstance().get(Calendar.YEAR) ? " " + date.get(Calendar.YEAR) : "")
                        );

                        if (date.get(Calendar.YEAR) != Calendar.getInstance().get(Calendar.YEAR)) {
                            title.setSpan(new ForegroundColorSpan($.color(Utils.DashboardTitle, activity)), title.length() - 4, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    else {
                        title = Utils.titleFormattedText(String.valueOf(date.get(Calendar.YEAR)));
                    }

                    actionBar.setTitleAnimated(title, - Modifier);

                    if (USE_RESIZE) {
                        $.bind(activity);

//                        $ pendingContainer = Modifier > 0 ? $(container, "." + PreviousRootKey) : $(container, "." + NextRootKey);
                        $ pendingContainer = $(container, "." + NextRootKey);

                        $.$Animator rootAnimator = pendingContainer.parent().animate();

                        if (pendingContainer.hasMetadata("CP$Tall")) {
                            rootAnimator.layout($.Height, $.dp(DateSizeDP * 7) + $.dimen(Utils.ActionBarSize));
                        } else if (pendingContainer.hasMetadata("CP$Narrow")) {
                            rootAnimator.layout($.Height, $.dp(DateSizeDP * 5) + $.dimen(Utils.ActionBarSize));
                        } else {
                            rootAnimator.layout($.Height, $.dp(DateSizeDP * 6) + $.dimen(Utils.ActionBarSize));
                        }

                        rootAnimator.duration(300).start("Scrolling");

                        $.unbind();
                    }

                }

                collection.animate()
                        .property($.TranslateX, value, $.Op.Add)
                        .duration(200)
                        .interpolator(new DecelerateInterpolator(1.5f))
                        .complete(new $.AnimationCallback() {
                            @Override
                            public void run($ collection) {
                                // TODO implement

                                direction = DirectionNone;

                                $.bind(activity);

                                // If scrolling to next, refactor the previous into the new next and the current into the new previous
                                // If scrolling to previous, refactor the next into the new previous and the current into the new next
                                $ next = $(container, "." + NextRootKey);
                                $ current = $(container, "." + RootViewKey);

                                if (Modifier > 0) {
                                    // Moving backwards
                                    current.metadata(NextRootKey, true).removeMetadata(RootViewKey).property($.X, $.dp(DateSizeDP * 7) + $.dp(8) * 2);
                                    next.metadata(RootViewKey, true).removeMetadata(NextRootKey).property($.X, 0);
                                }
                                else if (Modifier < 0) {
                                    // Moving forward
                                    next.metadata(RootViewKey, true).removeMetadata(NextRootKey).property($.X, 0);
                                    current.metadata(NextRootKey, true).removeMetadata(RootViewKey).property($.X, - ($.dp(DateSizeDP * 7) + $.dp(8) * 2));
                                }

//                                if (Modifier != 0) {
//                                    // First date is always on position 0
//                                    $(container, "." + RootViewKey).find("." + DateMetadataKey).eq(0).click();
//                                }

                                $.unbind();
                            }
                        })
                        .start("Scrolling");
            }

            @Override
            public int getSwipeDistanceThreshold() {
                return $.dp(DateSizeDP * 4 + 8, activity);
            }
        });

        $ layoutRoot = $(new FrameLayout(activity) {

                    private boolean intercepted = false;
                    private float startX;
                    private final int TouchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
                    public boolean onInterceptTouchEvent(MotionEvent event) {

                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                startX = event.getX();
                                intercepted = false;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if (intercepted) return true;

                                if (Math.abs(event.getX() - startX) > TouchSlop) {
                                    intercepted = true;
                                }
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                break;
                        }

                        boolean returnValue = intercepted || super.onInterceptTouchEvent(event);

                        if (returnValue == false) {
                            // Process this event here if it won't be forwarded to the onTouchEvent.
                            Dragger.onTouch(this, event);
                        }

                        return returnValue;

                    }

                    public boolean onTouchEvent(MotionEvent event) {
                        return Dragger.onTouch(this, event);
                    }

                })
                .clips(true)
                .clickable(false)
                .params(new ViewGroup.LayoutParams(
                                $.dp(DateSizeDP * 7) + $.dp(8) * 2,
                                $.dp(DateSizeDP * 7) + $.dimen(Utils.ActionBarSize, activity)
                        )
                );



//        layoutRoot.touch(Dragger);


        $ viewRoot = createViewRoot(layoutRoot);

        $ actionBarContainer = $(new FrameLayout(activity))
                .appendTo((ViewGroup) layoutRoot.get(0))
                .layout($.Width, ViewGroup.LayoutParams.MATCH_PARENT)
                .layout($.Height, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionBar.setContainer((ViewGroup) actionBarContainer.get(0));

        createDateViews(viewRoot, date);
        createMonthViews(viewRoot, date);

        if (state == StateDate) {
            viewRoot.find("." + MonthMetadataKey).visibility($.Invisible);
        }
        else {
            viewRoot.find("." + DateMetadataKey).visibility($.Invisible);
        }

        Calendar pending = Calendar.getInstance();
        pending.add(Calendar.MONTH, 1);
        createDateViews(createViewRoot(layoutRoot).property($.X, layoutRoot.layout($.Width)).removeMetadata(RootViewKey).metadata(NextRootKey, true), pending);
        createMonthViews($(layoutRoot.get(0), "." + NextRootKey), pending).visibility($.Invisible);

        $.unbind();

        return layoutRoot.get(0);
    }

    public $ createViewRoot($ layoutRoot) {
        return $(new FrameLayout(activity))
                .clips(false)
                .clickable(false)
                .appendTo((ViewGroup) layoutRoot.get(0))
                .metadata(RootViewKey, "")
                .layout($.Width, ViewGroup.LayoutParams.MATCH_PARENT)
                .layout($.Height, ViewGroup.LayoutParams.MATCH_PARENT)
                .layout($.MarginTop, $.dimen(Utils.ActionBarSize, activity));
    }

    /**
     * Applies generic styling to date views
     * @param dates The ViewProxy containing the dates that will be styled
     * @return The dates parameter.
     */
    private $ styledDates($ dates) {
        return dates
                .metadata(DateMetadataKey, "")
                .metadata(SelectedKey, "false")
                .layout($.Width, $.dp(DateSizeDP))
                .layout($.Height, $.dp(DateSizeDP))
                .textSize(16)
                .typeface(Utils.DefaultTypeface)
                .textColor($.color(Utils.DashboardTitle))
                .gravity(Gravity.CENTER);
    }

    private $.Each layoutRunnable(final int StartingRow, final boolean Select, final Calendar Date) {return new $.Each() {
        private int row = StartingRow;
        private int column = 1;
        private int i = 1;

        @Override
        public void run(View view, int index) {
            view.setX($.dp(row * DateSizeDP) + $.dp(8));
            view.setY($.dp(column * DateSizeDP));

            view.setBackground(new LegacyRippleDrawable(activity, LegacyRippleDrawable.ShapeCircle));
            ((TextView) view).setText(String.valueOf(i));

            $.metadata(view, "Row", row + 1);
            $.metadata(view, "Column", column);
            $.metadata(view, "Date", i);

            if (Select && i == Date.get(Calendar.DATE)) {
                $.metadata(view, SelectedKey, "true");
                ((TextView) view).setTextColor($.color(Utils.DashboardText));
            }



            if (selectedDate.get(Calendar.YEAR) == Date.get(Calendar.YEAR) && selectedDate.get(Calendar.MONTH) == Date.get(Calendar.MONTH) && i == selectedDate.get(Calendar.DATE)) {
//                $.metadata(view, "true");
                view.setSelected(true);
            }

            row++;
            if (row == 7) {
                row = 0;
                column++;
            }

            i++;
        }
    };}

    private final View.OnClickListener OnDateClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            $(container, "." + SelectedKey).selected(false).removeMetadata(SelectedKey).textColor($.color(Utils.DashboardTitle, activity));
            $.metadata(v, SelectedKey, "true");
            v.setSelected(true);
            ((TextView) v).setTextColor($.color(Utils.DashboardText, activity));
            date.set(Calendar.DATE, (Integer) $.metadata(v, "Date"));
        }
    };

    public $ createDateViews($ layoutRoot, final Calendar date) {
        $ views = $();

        $.bind(activity);

        $ headerViews = $();
        for (int i = 0; i < 7; i++) {
            headerViews.add(new TextView(activity));
        }
        headerViews.appendTo((ViewGroup) layoutRoot.get(0))
                .layout($.Width, $.dp(DateSizeDP))
                .layout($.Height, $.dp(DateSizeDP))
                .metadata(HeaderMetadataKey, "true")
                .textSize(14)
                .textColor(Utils.interpolateColors(.5f, $.color(Utils.DashboardTitle), $.color(Utils.HeaderSeparator)))
                .gravity(Gravity.CENTER)
                .each(new $.Each() {
                    DateFormatSymbols symbols = new DateFormatSymbols();
                    String[] dayNames = symbols.getShortWeekdays();
                    int i = 0;

                    @Override
                    public void run(View view, int index) {
                        // Calendar day names start from 1
                        ((TextView) view).setText(dayNames[i + 1].toUpperCase());
                        view.setX(i * $.dp(DateSizeDP) + $.dp(8));
                        view.setY(0);

                        $.metadata(view, "Row", 0);
                        $.metadata(view, "Column", i);

                        i++;
                    }
                });

        int viewCount = date.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar startingRowGetter = Calendar.getInstance();
        startingRowGetter.setTimeInMillis(date.getTimeInMillis());
        startingRowGetter.set(Calendar.DATE, 1);
        final int StartingRow = startingRowGetter.get(Calendar.DAY_OF_WEEK) - 1;

        for (int i = 0; i < viewCount; i++) {
            views.add($(new TextView(activity)));
        }

        styledDates(views.appendTo((ViewGroup) layoutRoot.get(0)))
                .each(layoutRunnable(StartingRow, true, date))
                .click(OnDateClicked);

        $.unbind();


        boolean tall = StartingRow + viewCount > 5 * 7;
        // Only Februaries starting on sunday are narrow
        boolean narrow = StartingRow == 0 && viewCount == 28;

        if (tall) {
            layoutRoot.metadata("CP$Tall", true);
        }
        else {
            layoutRoot.removeMetadata("CP$Tall");
        }

        if (narrow) {
            layoutRoot.metadata("CP$Narrow", true);
        }
        else {
            layoutRoot.removeMetadata("CP$Narrow");
        }

        return views.add(headerViews);
    }

    public void refactorDates($ layoutRoot, final Calendar date) {
        final int ViewCount = date.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar startingRowGetter = Calendar.getInstance();
        startingRowGetter.setTimeInMillis(date.getTimeInMillis());
        startingRowGetter.set(Calendar.DATE, 1);
        final int StartingRow = startingRowGetter.get(Calendar.DAY_OF_WEEK) - 1;

        $ dates = layoutRoot.find("." + DateMetadataKey);

        if (dates.length() > ViewCount) {
            // Some views have to be removed
            dates.not(dates.filter(new $.Predicate() {
                @Override
                public boolean viewMatches(View view) {

                    return ((Integer) $.metadata(view, "Date")) > ViewCount;
                }
            }).detach());
        }
        else {
            // Some views have to be added
            for (int i = dates.length(); i < ViewCount; i++) {
                dates.add(
                            styledDates($(new TextView(activity))
                                        .appendTo((ViewGroup) layoutRoot.get(0))
                                )
                                .click(OnDateClicked)
                        );
            }
        }

        layoutRoot.children("." + HeaderMetadataKey)
                .property($.Y, 0)
                .distribute($.X, $.dp(8, activity));


        boolean tall = StartingRow + ViewCount > 5 * 7;
        // Only Februaries starting on sunday are narrow
        boolean narrow = StartingRow == 0 && ViewCount == 28;

        if (tall) {
            layoutRoot.metadata("CP$Tall", true);
        }
        else {
            layoutRoot.removeMetadata("CP$Tall");
        }

        if (narrow) {
            layoutRoot.metadata("CP$Narrow", true);
        }
        else {
            layoutRoot.removeMetadata("CP$Narrow");
        }

        if (dates.length() != ViewCount) {
            throw new IllegalStateException("Date length is " + dates.length() + ", should be " + ViewCount + " for month " + date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()));
        }

        // Move the dates into their new positions
        // Prevent selection, as refactoring happens only off-screen on inactive panels.
        dates.each(layoutRunnable(StartingRow, false, date));
    }

    public $ createMonthViews($ layoutRoot, final Calendar date) {
        $ views = $();

        for (int i = 0; i < 12; i++) {
            views.add(new TextView(activity));
        }

        views.appendTo((ViewGroup) layoutRoot.get(0))
                .metadata(MonthMetadataKey, "true")
                .textSize(20)
                .typeface(Utils.DefaultTypeface)
                .textColor($.color(Utils.DashboardTitle, activity))
                .layout($.Width, $.dp(7f / 4f * DateSizeDP, activity))
                .layout($.Height, $.dp(7f / 3f * DateSizeDP, activity))
                .gravity(Gravity.CENTER)
                .click(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        $(container, "." + SelectedKey).selected(false).removeMetadata(SelectedKey);
                        $.metadata(v, SelectedKey, "true");
                        expandDatesFromMonth((Integer) $.metadata(v, "Month"));
                    }
                })
                .each(new $.Each() {
                    int row = 0;
                    int column = 0;

                    int i = 0;

                    DateFormatSymbols symbols = new DateFormatSymbols();
                    String[] monthNames = symbols.getShortMonths();

                    @Override
                    public void run(View view, int index) {
                        view.setX($.dp(8, activity) + row * $.dp(7f / 4f * DateSizeDP, activity));
                        view.setY(column * $.dp(7f / 3f * DateSizeDP, activity));
                        view.setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));

                        ((TextView) view).setText(monthNames[i]);

                        $.metadata(view, "Row", row);
                        $.metadata(view, "Column", column);
                        $.metadata(view, "Month", i);

                        if (i == date.get(Calendar.MONTH)) {
                            $.metadata(view, SelectedKey, "true");
//                            view.setSelected(true);
                        }

                        i++;
                        row++;

                        if (row == 4) {
                            row = 0;
                            column++;
                        }
                    }
                });

        return views;
    }

    @Override
    public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
        this.container = container;

        return createView(container.getContext());
    }

    @Override
    public void onDestroyCustomView(View customView) {
        container = null;
    }

    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        if (item.getId() == android.R.id.home) {
            if (state == StateDate) {
                collapseToMonths();
                actionBar.setBackButtonVisible(false);
            }
            else if (state == StateMonth) {
                // TODO
            }
        }
    }

    final static long SwapAnimationBaseDuration = 500;

    public void expandDatesFromMonth(int month) {

        $.createGlobalQueue();

        actionBar.setBackButtonVisible(true);

        state = StateDate;
        date.set(Calendar.DATE, 1);
        date.set(Calendar.MONTH, month);

        $ months = $(container, "." + RootViewKey).find("." + MonthMetadataKey);

        final $ SelectedMonth = months.filter("." + SelectedKey);

        $.bind(activity);

        refactorDates($("." + RootViewKey), date);

        //********************* OFFSCREEN PAGES REFACTORING *****************

        Calendar nextDate = Calendar.getInstance();
        nextDate.setTimeInMillis(date.getTimeInMillis());
        nextDate.add(Calendar.MONTH, 1);
        refactorDates($(container, "." + NextRootKey), nextDate);

        $ offscreenContainers = $(container, "." + NextRootKey);

        offscreenContainers.find("." + MonthMetadataKey).visibility($.Invisible);
        offscreenContainers.find("." + DateMetadataKey)
                .visibility($.Visible)
                .property($.ScaleX, 1)
                .property($.ScaleY, 1)
                .textColor($.color(Utils.DashboardTitle, activity));
        offscreenContainers.find("." + HeaderMetadataKey)
                .visibility($.Visible)
                .property($.ScaleX, 1)
                .property($.ScaleY, 1)
                .textColor(Utils.interpolateColors(.5f, $.color(Utils.DashboardTitle), $.color(Utils.HeaderSeparator)));

        //********************* ANIMATIONS *****************

        $(container, "." + RootViewKey).find("." + DateMetadataKey + ", ." + HeaderMetadataKey)
                .visibility(View.INVISIBLE)
                .each(new $.Each() {
                    public void run(View view, int index) {
                        $(view).animate()
                                .property($.X, SelectedMonth.property($.X) + SelectedMonth.width() / 2 - $.dp(DateSizeDP, activity) / 2, view.getX())
                                .property($.Y, SelectedMonth.property($.Y) + SelectedMonth.height() / 2 - $.dp(DateSizeDP, activity) / 2, view.getY())
                                .property($.ScaleX, 1.66f, 1)
                                .property($.ScaleY, 1.66f, 1)
                                .color($.TextColor, 0, $.hasMetadata(view, DateMetadataKey)
                                        ? $.color(Utils.DashboardTitle)
                                        : Utils.interpolateColors(.5f, $.color(Utils.DashboardTitle), $.color(Utils.HeaderSeparator)))
                                .visibility(View.VISIBLE)
                                .forcefeed()
                                .duration(SwapAnimationBaseDuration)
                                .delay((long) (300 -
                                                Math.max(
                                                        Math.abs((Integer) $.metadata(view, "Row") - 4),
                                                        Math.abs((Integer) $.metadata(view, "Column") - 3)
                                                ) * 50 + Math.random() * 100 - 50)
                                )
//                                .complete("true".equals($.metadata(view, SelectedKey)) ? new $.AnimationCallback() {
//                                    @Override
//                                    public void run($ collection) {
//                                        collection.filter("." + SelectedKey + "=true").selected(true);
//                                    }
//                                } : null)
                                .start();
                    }
                }).filter("." + SelectedKey).selected(false);


        months.not(SelectedMonth).each(new $.Each() {
            @Override
            public void run(View view, int index) {

                $(view).animate()
                        .property($.X, view.getX() + $.dp(7f / 3f * DateSizeDP, activity) * 3 * ((Integer) $.metadata(view, "Row") - (Integer) SelectedMonth.metadata("Row")))
                        .property($.Y, view.getY() + $.dp(7f / 3f * DateSizeDP, activity) * 3 * ((Integer) $.metadata(view, "Column") - (Integer) SelectedMonth.metadata("Column")))
                        .property($.ScaleX, 3)
                        .property($.ScaleY, 3)
                        .color($.TextColor, 0)
                        .visibility($.Invisible)
//                        .remove(true)
                        .duration(SwapAnimationBaseDuration)
                        .delay((long) (300 -
                                        Math.max(
                                                Math.abs((Integer) $.metadata(view, "Row") - (Integer) SelectedMonth.metadata("Row")),
                                                Math.abs((Integer) $.metadata(view, "Column") - (Integer) SelectedMonth.metadata("Column"))
                                        ) * 100 + Math.random() * 100 - 50)
                        )
                        .start();
            }
        });

        SelectedMonth.selected(false);
        SelectedMonth
                .animate()
                .property($.ScaleY, 3)
                .property($.ScaleX, 3)
                .color($.TextColor, 0)
                .visibility($.Invisible)
//                .remove(true)
                .duration(SwapAnimationBaseDuration)
                .delay(150)
                .start();

        //********************* HEADER *****************

        // Todo split off
        SpannableStringBuilder title =  Utils.titleFormattedText(
                "" + date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                        + (date.get(Calendar.YEAR) != Calendar.getInstance().get(Calendar.YEAR) ? " " + date.get(Calendar.YEAR) : "")
        );

        if (date.get(Calendar.YEAR) != Calendar.getInstance().get(Calendar.YEAR)) {
            title.setSpan(new ForegroundColorSpan($.color(Utils.DashboardTitle, activity)), title.length() - 4, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        actionBar.setTitleAnimated(title, - 1);

        $.unbind();
    }

    /**
     * Move each month view into its correct position.
     * @param months The ViewProxy set containing the months to be aligned.
     */
    private void refactorMonths($ months) {
        months.each(new $.Each() {
            int column;
            int row;

            @Override
            public void run(View view, int index) {
                view.setX($.dp(8, activity) + column * $.dp(7f / 4f * DateSizeDP, activity));
                view.setY(row * $.dp(7f / 3f * DateSizeDP, activity));

                column++;

                if (column == 4) {
                    row++;
                    column = 0;
                }
            }
        });
    }

    public void collapseToMonths() {
        $.createGlobalQueue();
        state = StateMonth;

        if (USE_RESIZE) {
            $.bind(activity);

            $("." + RootViewKey).parent().animate()
                        .layout($.Height, $.dp(DateSizeDP * 7) + $.dimen(Utils.ActionBarSize))
                        .duration(300)
                    .start("Scrolling");

            $.unbind();
        }

        //*********************** OFFSCREEN PAGE REFACTORING **********************

        $ offscreenContainers = $(container, "." + NextRootKey);

        offscreenContainers.find("." + MonthMetadataKey)
                .visibility($.Visible)
                .property($.ScaleX, 1)
                .property($.ScaleY, 1)
                .textColor($.color(Utils.DashboardTitle, activity))
                .distribute($.dp(8, activity), 0, offscreenContainers.parent().layout($.Width));
        offscreenContainers.find("." + DateMetadataKey + ", ." + HeaderMetadataKey).visibility($.Invisible);


        //*********************** ANIMATION **********************

        $ months = $("." + RootViewKey).children("." + MonthMetadataKey).removeMetadata(SelectedKey);
        refactorMonths(months);

        final $ SelectedMonth = months.filter(new $.Predicate() {
            @Override
            public boolean viewMatches(View view) {
                return ((Integer) $.metadata(view, "Month")) == CalendarPicker.this.date.get(Calendar.MONTH);
            }
        }).metadata(SelectedKey, true);

        $("." + RootViewKey).find("." + DateMetadataKey + ", ." + HeaderMetadataKey)
                .each(new $.Each() {
                    @Override
                    public void run(View view, int index) {
                        if (view.getLayoutParams() == null) {
                            throw new IllegalStateException("View " + ((TextView)view).getText() + " has null layout params!");
                        }

                        if (SelectedMonth.params() == null) {
                            throw new IllegalStateException("The Selected Month has null layout params!");
                        }

                        $(view).animate()
                                .property($.X, SelectedMonth.property($.X) + SelectedMonth.params().width / 2 - view.getWidth() / 2)
                                .property($.Y, SelectedMonth.property($.Y) + SelectedMonth.params().height / 2 - view.getHeight() / 2)
                                .property($.ScaleX, 1, 1.66f)
                                .property($.ScaleY, 1, 1.66f)
                                .color($.TextColor, 0)
                                .visibility(View.INVISIBLE)
//                                    .property($.Opacity, 0)
//                                .remove(true)
//                                    .layer(true)
                                .duration(SwapAnimationBaseDuration)
                                .delay((long) (
                                                Math.max(
                                                        Math.abs((Integer) $.metadata(view, "Row") - 4),
                                                        Math.abs((Integer) $.metadata(view, "Column") - 3)
                                                ) * 50 + Math.random() * 100 - 50)
                                )
                                .start();
                    }
                })
                .filter("." + SelectedKey)
                .selected(false);

        months.not(SelectedMonth).each(new $.Each() {
            @Override
            public void run(View view, int index) {

                $(view).property($.ScaleX, 3)
                        .property($.ScaleY, 3)
                        .animate()
                        .property($.X, view.getX() + $.dp(7f / 3f * DateSizeDP, activity) * 3 * ((Integer) $.metadata(view, "Row") - (Integer) SelectedMonth.metadata("Row")), view.getX())
                        .property($.Y, view.getY() + $.dp(7f / 3f * DateSizeDP, activity) * 3 * ((Integer) $.metadata(view, "Column") - (Integer) SelectedMonth.metadata("Column")), view.getY())
                        .property($.ScaleX, 1)
                        .property($.ScaleY, 1)
                        .color($.TextColor, 0, $.color(Utils.DashboardTitle, activity))
                        .visibility($.Visible)
//                                .property($.Opacity, 1)
//                                .layer(true)
                        .duration(SwapAnimationBaseDuration)
                        .forcefeed()
                        .delay((long) (
                                        Math.max(
                                                Math.abs((Integer) $.metadata(view, "Row") - (Integer) SelectedMonth.metadata("Row")),
                                                Math.abs((Integer) $.metadata(view, "Column") - (Integer) SelectedMonth.metadata("Column"))
                                        ) * 100 + Math.random() * 100 - 50)
                        )
                        .start();
            }
        });

        ((LegacyRippleDrawable) SelectedMonth.selected(false).get(0).getBackground()).flushRipple();
        SelectedMonth
                .animate()
                .property($.ScaleY, 3, 1)
                .property($.ScaleX, 3, 1)
                .color($.TextColor, 0, $.color(Utils.DashboardText, activity))
                .color($.RippleWorkingBackgroundColor, 0, 0, new AccelerateInterpolator(2f))
                .visibility($.Visible)
                .duration(SwapAnimationBaseDuration)
//                .complete(new $.AnimationCallback() {
//                    @Override
//                    public void run($ collection) {
//                        if (collection.hasMetadata(SelectedKey)) collection.selected(true);
//                    }
//                })
                .start();

        //**************************** HEADER ************************

        actionBar.setTitleAnimated(Utils.titleFormattedText("" + date.get(Calendar.YEAR)), 1);
    }

}
