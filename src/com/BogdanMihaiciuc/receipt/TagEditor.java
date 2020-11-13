package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.TagView;

import java.util.ArrayList;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;

public class TagEditor extends Fragment {

    final static String TAG = TagEditor.class.toString();

    final static int AddID = 1;
    final static int ClearID = 2;

    final static int WidthDP = 400;
    final static int TabletWidthDP = 440;
    final static int HeightDP = 500;
    final static int MarginDP = 16;

    public final static String TagEditorKey = "TagEditor";

    private Activity activity;

    private ViewGroup root;
    private FrameLayout container;
    private FrameLayout window;
    private FrameLayout actionBarContainer;
    private LegacyActionBar tagActionBar;
    private CollectionView tagCollection;
    private TagController tagController = new TagController();

    private ArrayList<Tag> tags;
    private Tag editorTarget;
    private String savedEditorText;

    private TagExpander.ColorAdapter colorAdapter;

    private ArrayList<Animator> runningAnimators = new ArrayList<Animator>();

    private DisplayMetrics metrics = new DisplayMetrics();
    private float density;

    private boolean attached;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        tagActionBar = LegacyActionBar.getAttachableLegacyActionBar();
        tagActionBar.setLogoResource(R.drawable.logo_dark);
        tagActionBar.setCaretResource(R.drawable.caret_up);
        tagActionBar.setOverflowResource(R.drawable.ic_action_overflow);
        tagActionBar.setSeparatorVisible(true);
        tagActionBar.setSeparatorOpacity(0.25f);

        tagActionBar.addItem(AddID, getString(R.string.NewTag), R.drawable.content_new_dark, false, true);
        tagActionBar.addItem(ClearID, getString(R.string.ClearTags), 0, true, false);
        tagActionBar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, true, false);

        tagActionBar.setOnLegacyActionSeletectedListener(new LegacyActionBar.OnLegacyActionSelectedListener() {
            @Override
            public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                if (item.getId() == android.R.id.home) {
                    dismiss();
                }
                if (item.getId() == AddID) {
                    addNewTag();
                }
            }
        });

        getActivity().getFragmentManager().beginTransaction().add(tagActionBar, null).commit();

        tags = new ArrayList<Tag>(TagStorage.getDefaultTags(getResources()));
        tagController.addSection().addAllObjects(tags);

        colorAdapter = new TagExpander.ColorAdapter(TagStorage.getAllAvailableColors(getResources())) {
            public View getView(int position, View convertView, ViewGroup container) {
                if (convertView != null) return convertView;
                return new View(container.getContext());
            }

            public View getDropDownView(int position, View convertView, ViewGroup container) {
                return super.getView(position, convertView, container);
            }
        };
        colorAdapter.selection = TagStorage.getNextAvailableColor();
        colorAdapter.checkAvailability = false;
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = getActivity();

        metrics = activity.getResources().getDisplayMetrics();
        density = metrics.density;

        root = (ViewGroup) activity.getWindow().getDecorView();
        container = new FrameLayout(activity);
        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        root.addView(container, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        window = new FrameLayout(activity);
        window.setClickable(true);
        int width = (int) (density * WidthDP);
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) width = (int) (density * TabletWidthDP);
        if (width > metrics.widthPixels - (int) (32 * density)) {
            width = metrics.widthPixels - (int) (32 * density);
        }
        int height = (int) (density * HeightDP);
        if (height > metrics.heightPixels - (int) (57 * density)) {
            height = metrics.heightPixels - (int) (57 * density);
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (height > (int) (metrics.heightPixels * 0.66f)) {
                height = (int) (metrics.heightPixels * 0.66f);
            }
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER;
        int margin = (int) (MarginDP * density);
        params.setMargins(margin, margin + (int) (25 * density), margin, margin);
        window.setBackgroundResource(R.drawable.suggestion_menu);
        container.addView(window, params);

        TypedValue tv = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
        {
            height = TypedValue.complexToDimensionPixelSize(tv.data, metrics);
        }
        else {
            height = (int) (48 * density);
        }

        actionBarContainer = new FrameLayout(activity);
        window.addView(actionBarContainer, ViewGroup.LayoutParams.MATCH_PARENT, height);
        tagActionBar.setContainer(actionBarContainer);

        tagCollection = new CollectionView(activity);
        params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.topMargin = height;
        window.addView(tagCollection, params);
        tagCollection.setController(tagController);

        if (!attached) {
            window.setAlpha(0f);
            window.setScaleY(0.9f);
            window.setScaleX(0.9f);

            final View Window = window;
            Window.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            window.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Window.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            });
            final View ContentRoot = root.getChildAt(0);
            ContentRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            root.getChildAt(0).animate().alpha(0.4f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                }
            });
            root.setBackgroundColor(0xFF000000);
        }
        else {
            final View ContentRoot = root.getChildAt(0);
            ContentRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ContentRoot.setAlpha(0.4f);
            root.setBackgroundColor(0xFF000000);
        }

        if (editorTarget != null) {
            View editor = tagCollection.retainViewForObject(editorTarget);
            editor.findViewById(R.id.TagText).setVisibility(View.INVISIBLE);
            editor.findViewById(R.id.action_edit_tags).setVisibility(View.VISIBLE);
            editor.findViewById(R.id.action_edit_tags).requestFocus();
            ((EditText) editor.findViewById(R.id.action_edit_tags)).setText(savedEditorText);
        }

        attached = true;
    }

    public void onDetach() {
        super.onDetach();

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);

        while (runningAnimators.size() > 0) {
            runningAnimators.get(0).cancel();
        }

        activity = null;

        root = null;
        container = null;
        window = null;
        actionBarContainer = null;
        tagCollection = null;
    }

    public void onActionModeStarted(final ActionMode actionMode) {

        final LegacyActionBar.ContextBarWrapper wrapper = tagActionBar.createActionModeBackedContextMode(actionMode);

        if (wrapper != null) {

            if (Resources.getSystem().getIdentifier("action_mode_bar", "id", "android") != 0) {
                ViewGroup actionModeView = (ViewGroup) activity.findViewById(Resources.getSystem().getIdentifier("action_mode_bar", "id", "android"));
                if (actionModeView != null) {
                    for (int i = 0, size = actionModeView.getChildCount(); i < size; i++) {
                        actionModeView.getChildAt(i).animate().cancel();
                    }
                    ((ViewGroup) actionModeView.getParent()).removeView(actionModeView);
                }
            }

            wrapper.setBackgroundColor(getResources().getColor(R.color.HeaderCanCheckout));
            wrapper.setSeparatorVisible(true);
            wrapper.setBackMode(LegacyActionBarView.DoneBackMode);
            wrapper.setTitle("");
            wrapper.setSubtitle("");
            wrapper.setLandscapeUIEnabled(false);
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    wrapper.start();
                }
            });
        }
    }

    public boolean handleBackPressed() {
        dismiss();
        return true;
    }

    public void handleMenuPressed() {
        tagActionBar.showOverflow();
    }

    public void dismiss() {
        final View Container = container;
        final View Window = window;
        final ViewGroup Root = root;
        Window.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        window.animate().alpha(0f).scaleY(0.9f).scaleX(0.9f).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Window.setLayerType(View.LAYER_TYPE_NONE, null);
                Root.setBackgroundColor(0);
                Root.removeView(Container);
            }
        });
        final View ContentRoot = root.getChildAt(0);
        ContentRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.getChildAt(0).animate().alpha(1f).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ContentRoot.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });

        container.setOnClickListener(null);

        activity.getFragmentManager().beginTransaction().remove(tagActionBar).remove(this).commit();
    }

    class TagController extends CollectionViewController {

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            Context context = container.getContext();
            FrameLayout view = new FrameLayout(context, null, android.R.attr.borderlessButtonStyle);
            ViewGroup.MarginLayoutParams params = new FrameLayout.LayoutParams(0, 0);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = (int) (48 * density);
            view.setLayoutParams(params);
//            view.setPadding((int) (16 * density), 0, (int) (16 * density), 0);

            TagView tag = new TagView(context, null, android.R.attr.borderlessButtonStyle);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.CollectionTagPadding);
            params = new FrameLayout.LayoutParams(0, 0);
            params.width = (int) (48 * density) + 2 * padding;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            tag.setId(R.id.TagColor);

            Spinner spinner = new Spinner(context) {
                public void onDraw(Canvas canvas) {}
            };
            spinner.setBackgroundColor(0);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                view.addView(spinner, (int) (250 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            }
            else {
                spinner.setDropDownWidth((int) (250 * density));
                view.addView(spinner, params);
            }
            view.addView(tag, params);

            TextView title = new TextView(context, null, android.R.attr.borderlessButtonStyle);
            params = new FrameLayout.LayoutParams(0, 0);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.leftMargin = (int) (48 * density) + 2 * padding;
            title.setTextSize(18);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setId(R.id.TagText);
            title.setMaxLines(1);
            view.addView(title, params);

            EditText editor = new EditText(context);
            params = new FrameLayout.LayoutParams(0, 0);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.leftMargin = (int) (48 * density) + 2 * padding;
            editor.setTextSize(18);
            editor.setGravity(Gravity.CENTER_VERTICAL);
            editor.setId(R.id.action_edit_tags);
            editor.setVisibility(View.INVISIBLE);
            editor.setMaxLines(1);
            editor.setHint(getString(R.string.TagNameHint));
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            view.addView(editor, params);

            return view;
        }

        @Override
        public void configureView(View view, Object item, int viewType) {
            ((TagView) view.findViewById(R.id.TagColor)).setColor(((Tag) item).color);
            ((TextView) view.findViewById(R.id.TagText)).setText(((Tag) item).name);

            view.findViewById(R.id.TagColor).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Spinner spinner = (Spinner) ((ViewGroup) view.getParent()).getChildAt(0);
                    if (((TagView) view).isDashedCircleEnabled()) {
                        colorAdapter.selection = TagStorage.getNextAvailableColor();
                    }
                    else {
                        colorAdapter.selection = ((TagView) view).getColors().get(0);
                    }
                    colorAdapter.checkAvailability = false;
                    spinner.setAdapter(colorAdapter);
                    spinner.performClick();
                    spinner.showContextMenu();
                }
            });
        }
    }

    private ArrayList<Tag> selection = new ArrayList<Tag>();
    private LegacyActionBar.ContextBarWrapper selectionBar;
    private LegacyActionBar.ContextBarListener selectionListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {}

        @Override
        public void onContextBarDismissed() {
            selectionBar = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        }
    };

    public void addNewTag() {
        Tag tag = new Tag();
        editorTarget = tag;
        tag.color = -1;
        tag.name = "";
        tag.tagUID = -1;

        tagCollection.setAnimationsEnabled(false);
        tagCollection.setAnimationsEnabled(true);

        tagCollection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                view.setY(- 48 * density);
                view.setAlpha(0f);
                view.animate().alpha(1f);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {}
        });

        tagController.requestBeginTransaction();
        tagController.getSectionAtIndex(0).addObjectToIndex(tag, 0);
        tagController.requestCompleteTransaction();

        final ViewGroup editor = (ViewGroup) tagCollection.retainViewForObject(tag);
        editor.findViewById(R.id.TagText).setVisibility(View.INVISIBLE);
        editor.findViewById(R.id.action_edit_tags).setVisibility(View.VISIBLE);
        editor.findViewById(R.id.action_edit_tags).requestFocus();
        ((EditText) editor.findViewById(R.id.action_edit_tags)).setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    if (onNewTagTitleFinished()) {
                        InputMethodManager imm = (InputMethodManager) textView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            }
        });

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        runningAnimators.add(animator);
        animator.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                runningAnimators.remove(animation);
                if (cancelled) return;

                InputMethodManager imm = (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(editor.findFocus(), InputMethodManager.SHOW_FORCED);
            }
        });
        animator.setDuration(300);
        animator.start();

    }

    protected boolean onNewTagTitleFinished() {
        Tag targetTag = editorTarget;
        if (targetTag == null) return false;

        final ViewGroup Editor = (ViewGroup) tagCollection.getViewForObject(targetTag);

        final EditText TitleEdtior = (EditText) Editor.findViewById(R.id.action_edit_tags);
        String title = TitleEdtior.getText().toString().trim();

        // check to see if the title is indeed valid
        for (Tag tag : tags) {
            if (tag.name.equalsIgnoreCase(title)) return false;
        }

        targetTag.name = title;
        final Spinner spinner = (Spinner) Editor.getChildAt(0);
        colorAdapter.selection = TagStorage.getNextAvailableColor();
        colorAdapter.checkAvailability = true;
        colorAdapter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                colorAdapter.selection = ((TagView) view).getColors().get(0);

                if (!onNewTagColorFinished()) {
                    Editor.findViewById(R.id.action_edit_tags).requestFocus();

                    InputMethodManager imm = (InputMethodManager) Editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(Editor.findFocus(), InputMethodManager.SHOW_FORCED);
                }

            }
        });
        spinner.setAdapter(colorAdapter);
        spinner.performClick();

        return true;
    }

    protected boolean onNewTagColorFinished() {
        Tag targetTag = editorTarget;
        if (TextUtils.isEmpty(targetTag.name)) return false;


        String title = targetTag.name;
        // check to see if the title is indeed valid
        for (Tag tag : tags) {
            if (tag.name.equalsIgnoreCase(title)) return false;
        }

        // invalid colors cannot be selected;
        targetTag.color = colorAdapter.selection;

        // This saves the tag and automatically assigns it a valid tagUID;
        TagStorage.addTag(targetTag);

        ViewGroup editor = (ViewGroup) tagCollection.getViewForObject(targetTag);
        ((TextView) editor.findViewById(R.id.TagText)).setText(targetTag.name);
        editor.findViewById(R.id.TagText).setVisibility(View.VISIBLE);
        editor.findViewById(R.id.action_edit_tags).setVisibility(View.INVISIBLE);

        tagCollection.refreshViews();

        return true;
    }

}
