package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.CollectionPopover;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.DisableableFrameLayout;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.ListenableAutoTextView;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.SwipeToDeleteListener;
import com.BogdanMihaiciuc.util.TagView;
import com.BogdanMihaiciuc.util.Utils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class ItemCollectionFragment extends Fragment implements TagExpander.OnTagDeletedListener {

    final static String TAG = ItemCollectionFragment.class.toString();

    final static boolean DEBUG = false;
    final static boolean DEBUG_SUGGESTIONS = false;
    final static boolean DEBUG_RENAME = false;
    final static boolean DEBUG_EDITOR_RIPPLES = true;

    public final static String ItemMetadataKey = "ItemCollectionFragment$Item";

    static class ViewHolder {
        int id;
        TagView tags;
        TextView title;
        TextView qty;
        TextView price;
        View strikethrough;

        View itemRoot;
        Animator animator;
    }

    final static int EditorModeAll = 0;
    final static int EditorModeTitle = 1;
    final static int EditorModeQty = 2;
    final static int EditorModePrice = 3;

    final static int FocusedTitle = 0;
    final static int FocusedQty = 1;
    final static int FocusedPrice = 2;
    final static int FocusedNone = -1;

    final static int InvalidTarget = -1;
    final static int NewItemTarget = 0;

    final static int SetNone = 0; //Denotes an item being added
    final static int SetTitle = 1;
    final static int SetQty = 2;
    final static int SetPrice = 4;

    final static int NoAnchor = Integer.MAX_VALUE;

    final static int DefaultHeight = -1;

    final static int FlingDirectionLeft = -1;
    final static int FlingDirectionRight = 1;

    final static int ReorderDelay = 4000;

    final static int UIThreadSearchLimit = 300;

    public static class Tag {

        public String name;
        public int color;
        int tagUID;

        static Tag make(String name, int color, int tagUID) {
            Tag tag = new Tag();
            tag.name = name;
            tag.color = color;
            tag.tagUID = tagUID;
            return tag;
        }

        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object object) {
            if (object == null) return false;
            if (object.getClass() == getClass()) {
                Tag tag = (Tag) object;
                if (tag.name.equals(name) && tag.color == color) {
                    return true;
                }
            }
            return false;
        }
    }

    static class Item {

        final static long CurrentVersionUID = 6;

        String name;
        long qty;
        long price;

        boolean crossedOff;
        int flags;

        long estimatedPrice;
        String unitOfMeasurement;

        ArrayList<Tag> tags = new ArrayList<Tag>();

        // These fields are run-time dependent and not flattened into files
        boolean edited;
        boolean selected;
        boolean implicitTags;
        boolean isAnimatingStrikethrough;


        // These fields are run-time dependent, but they are cached into files.
        // The are only used by collaborated lists.

        /**
         * The remoteUID is this item's UID on the server.
         */
        int remoteUID;
        /**
         * The ownerUID is the UID of the user who crossed off this item. In certain cases, this is important when computing the split total.
         */
        int ownerUID;


        // ************************* METHODS **************************

        public Item clipboardCopy() {
            Item copy = new Item();
            copy.name = name;
            copy.qty = qty;
            copy.price = price;
            copy.estimatedPrice = estimatedPrice;
            copy.unitOfMeasurement = unitOfMeasurement;
            copy.flags = flags;
            return copy;
        }

        public Item(Item item) {
            name = item.name;
            qty = item.qty;
            price = item.price;
            crossedOff = item.crossedOff;
            flags = item.flags;
            estimatedPrice = item.estimatedPrice;
            unitOfMeasurement = item.unitOfMeasurement;
            tags = new ArrayList<Tag>(item.tags);
        }

        public Item() {}

        @SuppressWarnings("deprecation")
        public Item (ItemListFragment.Item item, ArrayList<Tag> tags) {
            name = item.name;
            qty = item.qty;
            price = item.price;
            crossedOff = item.crossedOff;
            flags = item.flags;
            estimatedPrice = item.estimatedPrice;
            unitOfMeasurement = item.unitOfMeasurement;
            this.tags = new ArrayList<Tag>();

//            for (int i = 0; i < 4; i++) {
//                if (Math.random() < (1f/((float) (i + 1)) * (0.66f))) {
//                    while (true) {
//                        int index = (int) (Math.random() * tags.size());
//                        if (!this.tags.contains(tags.get(index))) {
//                            this.tags.add(tags.get(index));
//                            break;
//                        }
//                    }
//                }
//                else break;
//            }
        }

        public void addTagToIndex(Tag tag, int position) {
            if (tags.contains(tag)) return;
            tags.add(position, tag);
        }

        public void addTag(Tag tag) {
            if (tags.contains(tag)) return;
            int strength = TagStorage.getColorStrength(tag.color);
            int position = -1;
            for (int i = 0; i < tags.size(); i++) {
                if (strength < TagStorage.getColorStrength(tags.get(i).color)) {
                    position = i;
                    break;
                }
            }

            if (position == -1) position = tags.size();

            addTagToIndex(tag, position);
        }

        public void removeTagAtIndex(int index) {
            tags.remove(index);
        }

        public boolean canAddTags() {
            return tags.size() < 4;
        }

        public boolean hasUncommonTags() {
            return false;
        }

        public boolean canHaveUncommonTags() {
            return false;
        }

        public void flatten(ObjectOutputStream os) throws IOException {
            flatten(os, CurrentVersionUID, false);
        }

        public void flatten(ObjectOutputStream os, long versionUID) throws IOException {
            flatten(os, versionUID, false);
        }

        public void flatten(ObjectOutputStream os, long versionUID, boolean flattenTag) throws IOException {
            //CrashGuard
            if (name != null)
                os.writeUTF(name);
            else
                os.writeUTF("-");

            os.writeLong(qty);
            os.writeLong(price);
            os.writeBoolean(crossedOff);
            os.writeInt(flags);
            os.writeLong(estimatedPrice);

            //CrashGuard
            if (unitOfMeasurement != null)
                os.writeUTF(unitOfMeasurement);
            else
                os.writeUTF("x");

            if (versionUID >= 6) {
                os.writeInt(tags.size());
                if (flattenTag) {
                    for (Tag tag : tags) {
                        os.writeInt(tag.color);
                        os.writeUTF(tag.name);
                    }
                }
                else {
                    for (Tag tag : tags) {
                        os.writeInt(tag.tagUID);
                    }
                }
            }
        }

        public static Item inflate(ObjectInputStream is) throws IOException {
            return inflate(is, CurrentVersionUID, false);
        }

        public static Item inflate(ObjectInputStream is, long versionUID) throws IOException {
            return inflate(is, versionUID, false);
        }

        public static Item inflateFromExternalSource(ObjectInputStream is, long versionUID) throws IOException {
            return inflate(is, versionUID, true);
        }

        public static Item inflate(ObjectInputStream is, long versionUID, boolean inflateTag) throws IOException {
            Item item = new Item();
            item.name = is.readUTF();
            item.qty = is.readLong();
            item.price = is.readLong();
            item.crossedOff = is.readBoolean();
            item.flags = is.readInt();
            item.estimatedPrice = is.readLong();
            item.unitOfMeasurement = is.readUTF();

            if (versionUID >= 6) {
                int tagSize = is.readInt();
                if (inflateTag) {
                    for (int i = 0; i < tagSize; i++) {
                        Tag tag = TagStorage.resolveTag(is.readInt(), is.readUTF());
                        if (tag != null) item.tags.add(tag);
                    }
                }
                else {
                    for (int i = 0; i < tagSize; i++) {
                        Tag tag;

                        tag = TagStorage.findTagWithUID(is.readInt());

                        if (tag != null) item.tags.add(tag);
                    }
                }
            }

            return item;
        }

        // TODO Only used when importing entire history
        public static Item inflateCreatingMissingTagToDatabase(ObjectInputStream is, long versionUID, boolean inflateTag, SQLiteDatabase db) throws IOException {
            Item item = new Item();
            item.name = is.readUTF();
            item.qty = is.readLong();
            item.price = is.readLong();
            item.crossedOff = is.readBoolean();
            item.flags = is.readInt();
            item.estimatedPrice = is.readLong();
            item.unitOfMeasurement = is.readUTF();

            if (versionUID >= 6) {
                int tagSize = is.readInt();
                if (inflateTag) {
                    for (int i = 0; i < tagSize; i++) {
                        int color = is.readInt();
                        String name = is.readUTF();
                        Tag tag = TagStorage.resolveTag(color, name);
                        if (tag != null) item.tags.add(tag);
                        else {
                            tag = new Tag();
                            tag.name = name;
                            tag.color = color;
                            TagStorage.addTagToDatabase(tag, db);

                        }
                    }
                }
                else {
                    for (int i = 0; i < tagSize; i++) {
                        Tag tag;

                        tag = TagStorage.findTagWithUID(is.readInt());

                        if (tag != null) item.tags.add(tag);
                    }
                }
            }

            return item;
        }

        public CharSequence toMenuString(Context context) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(name);
            builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.implicit_text_colors)),
                    name.length(), name.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            builder.append(" - ").append(ReceiptActivity.currentTruncatedLocale).append(ReceiptActivity.longToDecimalString(estimatedPrice));
            return builder;
        }

        public String toString() {
            return name;
        }

    }

    final static Item NullItem = new Item();

    static {
        NullItem.unitOfMeasurement = "";
        NullItem.name = "";
    }

    static class PartialCheckoutItems {
        ArrayList<Item> items = new ArrayList<Item>();
        ArrayList<Integer> positions = new ArrayList<Integer>();
    }

    static class EditorSavedInstance {
        String title;
        String qty;
        String price;

        int focus;
    }

    class Editor {
        View editorRoot;
        View background;

        ListenableAutoTextView titleEditor;
        View titleCompletionHelper;

        EditText qtyEditor;
        View qtyHelper;
        CollectionPopover unitPopover;

        EditText priceEditor;

        TagView tagPlaceholder;

        Item target;
        int mode;

        ViewHolder editorItem;

        View retainedView;

        float minimumUnitSwipeDistance;

        EditorSavedInstance savedInstance = new EditorSavedInstance();

        TextView.OnEditorActionListener doneListener = new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int keyCode, KeyEvent keyEvent) {
                if (keyCode == EditorInfo.IME_ACTION_DONE) {
                    commitAndHideKeyboard(true);
                    return true;
                }
                return false;
            }
        };

        void startEditing(Item item, int mode) {
            startEditingWithKeyboard(item, mode, true);
        }

        void startEditingWithKeyboard(final Item item, final int mode, final boolean showKeyboard) {

            if (target != null) {
                commitAndHideKeyboard(false);
            }

            if (currentExpander != null) {
                currentExpander.compact();
            }

            retainedView = collection.retainViewForObject(item);

            if (retainedView == null) {
                // Trying to edit an inexistent item, look for it again
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        startEditingWithKeyboard(item, mode, showKeyboard);
                    }
                });
                return;
            }

            attach((ViewGroup) retainedView);
            setTarget(item);
            setMode(mode);
            show(showKeyboard);

            delayHandler.retain();
        }

        void saveState() {
            savedInstance.focus = FocusedNone;
            if (titleEditor != null) {
                savedInstance.title = titleEditor.getText().toString();
                if (titleEditor.hasFocus()) savedInstance.focus = FocusedTitle;
            }
            else {
                savedInstance.title = "";
            }

            if (priceEditor != null) {
                savedInstance.price = priceEditor.getText().toString();
                if (priceEditor.hasFocus()) savedInstance.focus = FocusedPrice;
            }
            else {
                savedInstance.price = "";
            }

            if (qtyEditor != null) {
                savedInstance.qty = qtyEditor.getText().toString();
                if (qtyEditor.hasFocus()) savedInstance.focus = FocusedQty;
            }
            else {
                savedInstance.qty = "";
            }
        }

        void restore() {

            retainedView = collection.retainViewForObject(target);

            if (retainedView == null) {
                // Trying to edit an inexistent item, look for it again
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        restore();
                    }
                });
                return;
            }

            attach((ViewGroup) retainedView);
            setTarget(target);
            setMode(mode);
            show(true);

            if (titleEditor.getVisibility() == View.VISIBLE) titleEditor.setText(savedInstance.title);
            if (qtyEditor.getVisibility() == View.VISIBLE) qtyEditor.setText(savedInstance.qty);
            if (priceEditor.getVisibility() == View.VISIBLE) priceEditor.setText(savedInstance.price);

            if (savedInstance.focus == FocusedTitle) titleEditor.requestFocus();
            if (savedInstance.focus == FocusedQty) qtyEditor.requestFocus();
            if (savedInstance.focus == FocusedPrice) priceEditor.requestFocus();
        }

        void attach(ViewGroup editorContainer) {
            editorRoot = getActivity().getLayoutInflater().inflate(R.layout.layout_collection_editor, editorContainer, false);

            editorContainer.addView(editorRoot);

            background = editorRoot.findViewById(R.id.EditorFloatingBackground);

            titleEditor = (ListenableAutoTextView) editorRoot.findViewById(R.id.ItemTitleEditor);
            titleCompletionHelper = editorRoot.findViewById(R.id.TitleCompletionHelper);

            qtyEditor = (EditText) editorRoot.findViewById(R.id.QtyEditor);
            qtyHelper = editorRoot.findViewById(R.id.QtyTouchHelper);

            priceEditor = (EditText) editorRoot.findViewById(R.id.PriceEditor);

            tagPlaceholder = (TagView) editorRoot.findViewById(R.id.ItemTagsSpace);

            editorItem = prepareViewHolder(editorContainer.findViewById(R.id.ItemRoot));

            titleEditor.setOnEditorActionListener(doneListener);
            qtyEditor.setOnEditorActionListener(doneListener);
            priceEditor.setOnEditorActionListener(doneListener);

            qtyEditor.setOnTouchListener(unitSelectorListener);
        }

        class ViewDisplacement {
            View duplicate;
            float position;
            float targetPosition;
        }

        void setTitleAdapter() {
            titleEditor.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(final CharSequence s, int start, int before, int count) {
                    if (DEBUG_SUGGESTIONS) {
                        Log.d(TAG, "onTextChanged() called. Text is " + s);
                    }
                    if (editor.target == null) return;
                    if (s.toString().trim().isEmpty()) titleEditor.dismissDropDown();
                    boolean showDropDown = true;
                    if (!loopForSuggestion(s)) {
                        if (DEBUG_SUGGESTIONS) {
                            Log.d(TAG, "Suggestions were not found!");
                        }
                        if (editor.mode == EditorModeAll) {
                            // For new items, estimated price is committed at the end
                            target.estimatedPrice = 0;
                            priceEditor.setHint(getString(R.string.PriceEditHint));
                            if (target.implicitTags) {
                                target.tags = new ArrayList<Tag>();
                                target.implicitTags = false;
                                editor.tagPlaceholder.setColor(-1);
                            }
                        }
                        else {
                            // If there was previously an estimated price clear it from the estimated total
                            // But only if there wasn't an explicit price set
                            if (target.estimatedPrice != 0 && target.price == 0) {
                                activity.addToEstimatedTotal(target.qty, -target.estimatedPrice);
                                if (target.crossedOff) {
                                    activity.addToTotal(target.qty, -target.estimatedPrice);
                                }
                            }
                            target.estimatedPrice = 0;
                            priceEditor.setHint(getString(R.string.PriceEditHint));
                            if (target.implicitTags) {
                                target.tags = new ArrayList<Tag>();
                                target.implicitTags = false;
                            }
                        }
                    }
                    else {
                        showDropDown = false;
                    }
                    ArrayList<Item> duplicates;
                    if ((duplicates = findDuplicates(s.toString())) != null && mode == EditorModeAll) {
                        titleEditor.dismissDropDown();
                        showDropDown = false;

                        // An already running duplicateRevealer needs to reset positions properly
                        // to ensure this revealer records the correct starting positions
                        if (duplicateRevealer != null) {
                            duplicateRevealer.end();
                        }

                        final float StandardDispalcement = 49 * metrics.density;

                        final ArrayList<ViewDisplacement> DuplicateViews = new ArrayList<ViewDisplacement>();
                        float accumulatedPosition = 49 * metrics.density;
                        for (Item duplicate : duplicates) {
                            ViewDisplacement info = new ViewDisplacement();
                            info.duplicate = collection.retainViewForObject(duplicate);
                            if (info.duplicate != null) {
                                info.targetPosition = accumulatedPosition;
                                accumulatedPosition += 49 * metrics.density;
                                info.position = ((View) info.duplicate.getParent()).getY();
                                DuplicateViews.add(info);
                            }
                        }

                        final float Displacement = accumulatedPosition;

                        final ArrayList<ViewDisplacement> VisibleViews = new ArrayList<ViewDisplacement>();
                        // runForEachViews is called in order of visible views
                        collection.runForEachView(new CollectionView.ViewRunnable() {
                            int index = 0;

                            @Override
                            public void runForView(View view, Object object, int viewType) {
                                if (viewType == 1) return;
                                if (object == target) return;
                                if (((Item) object).name.equalsIgnoreCase(s.toString().trim())) {
                                    return; //duplicate
                                }
                                ViewDisplacement info = new ViewDisplacement();
                                info.duplicate = view;
                                info.position = ((View) info.duplicate.getParent()).getY();
                                info.targetPosition = Displacement + index * StandardDispalcement;
                                index++;
                                VisibleViews.add(info);
                            }
                        });

                        final int FlashColorStart = Utils.transparentColor(0, getResources().getColor(android.R.color.holo_blue_light));
                        final int FlashColorEnd = getResources().getColor(android.R.color.holo_blue_light);

                        duplicateRevealer = ValueAnimator.ofFloat(0f, 1f);
                        duplicateRevealer.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            private TimeInterpolator frictionInterpolator = new Utils.FrictionInterpolator(1.5f);
                            private TimeInterpolator cycleInterpolator = new CycleInterpolator(2);
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                float fraction = valueAnimator.getAnimatedFraction();

                                if (fraction < 0.33f) {
                                    showDuplicates(frictionInterpolator.getInterpolation(Utils.getIntervalPercentage(fraction, 0f, 0.33f)));
                                }
                                else if (fraction < 0.66) {
                                    flashDuplicates(Utils.getIntervalPercentage(fraction, 0.33f, 0.66f));
                                }
                                else {
                                    dismissDuplicates(frictionInterpolator.getInterpolation(Utils.getIntervalPercentage(fraction, 0.66f, 1f)));
                                }
                            }

                            void showDuplicates(float fraction) {
                                for (ViewDisplacement info : VisibleViews) {
                                    ((View) info.duplicate.getParent()).setY(Utils.interpolateValues(fraction, info.position, info.targetPosition));
                                }
                                for (ViewDisplacement info : DuplicateViews) {
                                    ((View) info.duplicate.getParent()).setY(Utils.interpolateValues(fraction, info.position, info.targetPosition));
                                }
                            }

                            void flashDuplicates(float fraction) {
                                fraction = cycleInterpolator.getInterpolation(fraction);
                                if (fraction < 0) fraction = 0;
                                for (ViewDisplacement info : DuplicateViews) {
                                    ((LegacyRippleDrawable) info.duplicate.getBackground()).setBackgroundColor(Utils.interpolateColors(fraction, FlashColorStart, FlashColorEnd), false);
                                }
                            }

                            void dismissDuplicates(float fraction) {
                                for (ViewDisplacement info : VisibleViews) {
                                    ((View) info.duplicate.getParent()).setY(Utils.interpolateValues(fraction, info.targetPosition, info.position));
                                }
                                for (ViewDisplacement info : DuplicateViews) {
                                    ((View) info.duplicate.getParent()).setY(Utils.interpolateValues(fraction, info.targetPosition, info.position));
                                }
                            }
                        });
                        duplicateRevealer.addListener(new AnimatorListenerAdapter() {
                            public void onAnimationStart(Animator animation) {
//                                collection.requestDisableInteractions();
                            }
                            @Override
                            public void onAnimationEnd(Animator animation) {
//                                collection.requestEnableInteractions();
                                duplicateRevealer = null;
                                for (ViewDisplacement info : DuplicateViews) {
                                    collection.releaseView(info.duplicate);
//                                    info.duplicate.setBackgroundResource(R.drawable.list_background_drawable);
                                }
                            }
                        });
                        duplicateRevealer.setInterpolator(new LinearInterpolator());
                        duplicateRevealer.setDuration(1200);
                        duplicateRevealer.start();
                    }
                    showSuggestionsForView(titleEditor, showDropDown);
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count,
                                              int after) {
                    if (DEBUG_SUGGESTIONS) {
                        Log.d(TAG, "beforeTextChanged() called. Text is " + s);
                    }
                }
                @Override
                public void afterTextChanged(Editable s) {
                    if (DEBUG_SUGGESTIONS) {
                        Log.d(TAG, "afterTextChanged() called. Text is " + s);
                    }
                }
            });
        }

        void setTarget(Item target) {

            this.target = target;

            // Name
            titleEditor.setText(target.name);
            setTitleAdapter();

            // Qty and unit of measurement
            if (target.qty != 0)
                qtyEditor.setText(new BigDecimal(target.qty).movePointLeft(4).stripTrailingZeros().toPlainString());
            else
                qtyEditor.setText("");
            qtyEditor.setHint(target.unitOfMeasurement);

            // Price and implicit price
            if (target.price != 0)
                priceEditor.setText(ReceiptActivity.longToDecimalString(target.price));
            else
                priceEditor.setText(null);

            if (target.estimatedPrice != 0)
                priceEditor.setHint(ReceiptActivity.longToFormattedString(target.estimatedPrice, Receipt.hintLightSpan()));
            else
                priceEditor.setHint(R.string.PriceEditHint);
        }

        @SuppressWarnings("MagicConstant")
        void setMode(int mode) {
            if (mode == EditorModeAll) {
                // TODO
                if (DEBUG_EDITOR_RIPPLES) {
                    editorItem.price.setTextColor(0);
                    editorItem.qty.setTextColor(0);
                    editorItem.title.setTextColor(0);
                }
                else {
                    editorItem.price.setVisibility(View.INVISIBLE);
                    editorItem.qty.setVisibility(View.INVISIBLE);
                    editorItem.title.setVisibility(View.INVISIBLE);
                }

                titleEditor.setVisibility(View.VISIBLE);
                qtyEditor.setVisibility(View.VISIBLE);
                priceEditor.setVisibility(View.VISIBLE);
                editorRoot.findViewById(R.id.ItemTagsSpace).setVisibility(View.VISIBLE);
            }
            else {
                int titleMode = View.INVISIBLE;
                int priceMode = View.INVISIBLE;
                int qtyMode = View.INVISIBLE;

                if (mode == EditorModePrice) priceMode = View.VISIBLE;
                if (mode == EditorModeQty) qtyMode = View.VISIBLE;
                if (mode == EditorModeTitle) titleMode = View.VISIBLE;

                titleEditor.setVisibility(titleMode);
                priceEditor.setVisibility(priceMode);
                qtyEditor.setVisibility(qtyMode);
                // The ^ operator (bitwise exclusive OR) generates the opposite of the editor mode
                if (DEBUG_EDITOR_RIPPLES) {
                    if (priceMode == View.VISIBLE) editorItem.price.setTextColor(0);
                    if (qtyMode == View.VISIBLE) editorItem.qty.setTextColor(0);
                    if (titleMode == View.VISIBLE) editorItem.title.setTextColor(0);
                }
                else {
                    editorItem.title.setVisibility(titleMode ^ View.INVISIBLE);
                    editorItem.price.setVisibility(priceMode ^ View.INVISIBLE);
                    editorItem.qty.setVisibility(qtyMode ^ View.INVISIBLE);
                }
                editorRoot.findViewById(R.id.ItemTagsSpace).setVisibility(View.INVISIBLE);
            }

            this.mode = mode;
        }

        void show(boolean showKeyboard) {
            editorRoot.setVisibility(View.VISIBLE);
            View collectionContainer = (View) retainedView.getParent();
            ((FrameLayout.LayoutParams) collectionContainer.getLayoutParams()).topMargin = (int) collectionContainer.getY();
            collectionContainer.requestLayout();
            collectionContainer.setTranslationY(0);

            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

            if (mode == EditorModeAll || mode == EditorModeTitle) {
                titleEditor.requestFocus();
                if (showKeyboard) imm.showSoftInput(titleEditor, InputMethodManager.SHOW_FORCED);
            }
            if (mode == EditorModeQty) {
                qtyEditor.requestFocus();
                if (showKeyboard) imm.showSoftInput(qtyEditor, InputMethodManager.SHOW_FORCED);
            }
            if (mode == EditorModePrice) {
                priceEditor.requestFocus();
                if (showKeyboard) imm.showSoftInput(priceEditor, InputMethodManager.SHOW_FORCED);
            }
        }

        void flashTitle() {
            titleCompletionHelper.setVisibility(View.VISIBLE);
            titleCompletionHelper.setAlpha(0);
            titleCompletionHelper.animate()
                    .alpha(1)
                    .setInterpolator(new CycleInterpolator(2))
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        public void onAnimationEnd(Animator a) {
                            if (activity == null || titleCompletionHelper == null) return;
                            titleCompletionHelper.setVisibility(View.INVISIBLE);
                        }
                    });
        }

        boolean commitAndHideKeyboard(boolean detachKeyboard) {
            if (suggestionsTask != null) {
                suggestionsTask.cancel(false);
            }

            if (target == null)
                return false;

            if (unitPopover != null) {
                unitPopover.dismiss();
            }

            if (detachKeyboard) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(retainedView.getWindowToken(), 0);
            }

            if (mode == EditorModeAll) {
                finishAddingItem();
            }

            if (mode == EditorModePrice) {
                applyPriceChanges();
            }

            if (mode == EditorModeQty) {
                applyQtyChanges();
            }

            if (mode == EditorModeTitle) {
                applyTitleChanges();
            }

            if (target.implicitTags) {
                target.implicitTags = false;
                applyTagChanges();
            }

            target = null;
            detach();

            delayHandler.release(ReorderDelay);

            return true;
        }

        void detach() {
            // Already detached if the editorRoot is null
            if (editorRoot == null) return;
            ((ViewGroup) editorRoot.getParent()).removeView(editorRoot);

            editorItem.title.setVisibility(View.VISIBLE);
            editorItem.price.setVisibility(View.VISIBLE);
            editorItem.qty.setVisibility(View.VISIBLE);

            editorRoot = null;
            background = null;
            titleEditor = null;
            titleCompletionHelper = null;
            qtyEditor = null;
            qtyHelper = null;
            priceEditor = null;
            editorItem = null;
            tagPlaceholder = null;

            collection.releaseView(retainedView);
            retainedView = null;
        }

        View getFocusedField() {
            if (editorRoot != null) {
                // Everything else is also not null in this case
                if (titleEditor.hasFocus()) return titleEditor;
                if (qtyEditor.hasFocus()) return qtyEditor;
                if (priceEditor.hasFocus()) return priceEditor;
            }
            return null;
        }

        private CollectionViewController unitController = new CollectionViewController() {

            {
                Context context = Receipt.getStaticContext();
                addSection().addObjects(
                        context.getString(R.string.Count),
                        context.getString(R.string.Gram),
                        context.getString(R.string.Kilogram),
                        context.getString(R.string.Litre),
                        context.getString(R.string.Ounce),
                        context.getString(R.string.Pound)
                );
                getSectionAtIndex(0).setColumnCount(3);
            }

            @Override
            public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
                TextView unit = new TextView(container.getContext());
                unit.setTextSize(18);
                unit.setTextColor(getResources().getColor(R.color.DashboardText));
                unit.setGravity(Gravity.CENTER);
                unit.setClickable(true);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.ActionBarButtonWidth),
                                                                                getResources().getDimensionPixelSize(R.dimen.LineHeight));

                unit.setLayoutParams(params);

                return unit;
            }

            @Override
            public void configureView(View view, Object item, int viewType) {
                ((TextView) view).setText((CharSequence) item);
                view.setTag(item);
                if (target.unitOfMeasurement.equalsIgnoreCase((String) item)) {
                    view.setBackground(Utils.getSelectedColors(view.getContext()));
                }
                else {
                    view.setBackground(Utils.getDeselectedColors(view.getContext()));
                }
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setUnitOfMeasurement((String) view.getTag());
                        if (unitPopover != null) unitPopover.dismiss();
                    }
                });
            }
        };

        private View.OnTouchListener unitSelectorListener = new View.OnTouchListener() {

            float startX, startY;
            boolean fired;

            @Override
            public boolean onTouch(final View view, MotionEvent event) {
                if (event.getPointerCount() > 1) {
                    view.getParent().getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    fired = false;
                    qtyHelper.setAlpha(0);
                    qtyHelper.setVisibility(View.VISIBLE);
                    view.getParent().getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                }
                if (!fired) {
                    float alpha;
                    alpha = (event.getY() - startY)/minimumUnitSwipeDistance;
                    alpha = alpha < 0 ? 0 : alpha;
                    qtyHelper.setAlpha(alpha);
                    qtyHelper.setY(-(1 - alpha) * qtyHelper.getHeight());
                }
                if (event.getY() - startY > minimumUnitSwipeDistance) {
                    if (Math.abs(event.getY() - startY) > Math.abs(event.getX() - startX) && !fired) {
                        fired = true;
                        qtyHelper.setAlpha(1);
                        qtyHelper.setVisibility(View.GONE);
                        qtyHelper.setTranslationY(0);

                        unitPopover = new CollectionPopover(new Popover.AnchorProvider() {
                            @Override
                            public View getAnchor(Popover popover) {
                                return qtyEditor;
                            }
                        }, unitController);
                        unitPopover.getHeader().setTitle(ReceiptActivity.titleFormattedString(getString(R.string.MeasurementUnit)));
                        unitPopover.setHideKeyboardEnabled(false);
                        unitPopover.setWidth(3 * getResources().getDimensionPixelSize(R.dimen.ActionBarButtonWidth));
                        unitPopover.setOnDismissListener(new Popover.OnDismissListener() {
                            @Override
                            public void onDismiss() {
                                unitPopover = null;
                            }
                        });
                        unitPopover.show((Activity) view.getContext());

                        if (phoneUI && landscape) {
                            InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }

//                        PopupMenu popup = new PopupMenu(activity, view);
//                        popup.inflate(R.menu.unit_of_measurement);
//                        SpannableStringBuilder title = Utils.appendWithSpan(new SpannableStringBuilder(), getString(R.string.MeasurementUnit), new Utils.CustomTypefaceSpan(Receipt.condensedTypeface()));
//                        title.setSpan(new AbsoluteSizeSpan(24, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                        popup.getMenu().getItem(0).setTitle(title);
//                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//                            @Override
//                            public boolean onMenuItemClick(MenuItem item) {
//                                switch(item.getItemId()) {
//                                    case R.id.UnitCount:
//                                    case R.id.UnitGram:
//                                    case R.id.UnitKilogram:
//                                    case R.id.UnitLitre:
//                                    case R.id.UnitOunce:
//                                    case R.id.UnitPound:
//                                        setUnitOfMeasurement(item.getTitle().toString());
//                                        return true;
//                                    default:
//                                        return false;
//                                }
//                            }
//                        });
//                        popup.show();
                        return true;
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
                    if (qtyHelper == null)
                        return false;
                    qtyHelper.setAlpha(1);
                    qtyHelper.setTranslationY(0);
                    qtyHelper.setVisibility(View.GONE);
                    view.getParent().getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }
                //noinspection RedundantIfStatement
                if (!fired)
                    return true;
                return false;
            }

        };

        void setUnitOfMeasurement(String unit) {
            target.unitOfMeasurement = unit;
            qtyEditor.setHint(unit);
        }

    }

    private BigDecimal selectionTotal = new BigDecimal(0);

    private TagExpander currentExpander;
    private Editor editor = new Editor();

    private Item expanderTarget;

    private CollectionView collection;

    private ReceiptActivity activity;

    private DisplayMetrics metrics;
    private float density;
    private boolean phoneUI, landscape;

    private ArrayList<Item> items = new ArrayList<Item>();
    private ArrayList<Item> selectionList = new ArrayList<Item>();

    private SwipeToDeleteListener swipeToDeleteListener;
    private boolean swipeConfirmation;
    private Item confirmingItem;
    private int confirmingPosition;
    private ArrayList<Item> confirmingItems = new ArrayList<Item>();
    private ArrayList<Integer> confirmingPositions = new ArrayList<Integer>();
    private int confirmingDirection;
    private boolean confirmatorUp;
    private View confirmator;

    private ArrayList<Item> unorderedItems = null;
    private boolean orderConfirmatorUp;
    private View orderConfirmator;

    private ValueAnimator emptyAnimator;

    ValueAnimator duplicateRevealer;

//    private ArrayList<Tag> tags;

    private ItemController controller = new ItemController();

    private DelayHandler delayHandler = new DelayHandler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        controller.addSection();
        controller.addSectionForViewTypeWithTag(1, null).addObject(new Object());
//        tags = TagStorage.getDefaultTags(getResources());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection_items, container);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity = (ReceiptActivity) getActivity();
        metrics = getResources().getDisplayMetrics();
        density = metrics.density;
        phoneUI = getResources().getConfiguration().smallestScreenWidthDp < 600;
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (activity.getState() != BackendFragment.StateOpenList) {
            controller.purgeSavedState();
        }

        editor.minimumUnitSwipeDistance = 50 * density;

        collection = (CollectionView) activity.findViewById(R.id.ItemCollection);
        collection.setController(controller);
        collection.ensureMinimumSupplyForViewType(8, 0);
        collection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
            }

            @Override
            public void resetState(View view, Object object, int viewType) {
            }
        });
        collection.setOnScrollListener(new CollectionView.OnScrollListener() {
            @Override
            public void onScroll(CollectionView collectionView, int top, int amount) {
                delayHandler.redelay(ReorderDelay);
                if (duplicateRevealer != null) {
                    duplicateRevealer.end();
                }
            }
        });

        collection.setOnViewCollectedListener(new CollectionView.OnViewCollectedListener() {
            @Override
            public void onViewCollected(CollectionView collectionView, View view, int viewType) {
                if (viewType == 0) {
                    ((LegacyRippleDrawable) view.getBackground()).flushRipple();

                    view.findViewById(R.id.ItemStrikethrough).animate().cancel();
                }
            }
        });

//        ((ViewGroup) activity.findViewById(R.id.ItemList).getParent()).setVisibility(View.GONE);

        if (editor.target != null) {
            collection.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    //noinspection deprecation
                    collection.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                    editor.restore();
                }
            });
        }
        if (expanderTarget != null) {
            collection.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    //noinspection deprecation
                    collection.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                    final View objectView = collection.retainViewForObject(expanderTarget);
                    delayHandler.retain();
                    currentExpander = TagExpander.fromViewInContainerWithTarget((TagView) objectView.findViewById(R.id.ItemTags), (ViewGroup) objectView, expanderTarget);
                    currentExpander.setOnTagDeletedListener(ItemCollectionFragment.this);
                    currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                        @Override
                        public void onClose() {
                            currentExpander = null;
                            delayHandler.release(ReorderDelay);
                            collection.releaseView(objectView);
                        }
                    });
                    currentExpander.expandAnimated(false);
                    currentExpander.restoreStaticContext();
                }
            });
        }
        if (confirmatorUp) {
            showDeleteConfirmator(false);
        }
        if (orderConfirmatorUp) {
            showOrderConfirmator(false);
        }

        collection.setMoveInterpolator(new Utils.FrictionInterpolator(1.33f));
        initOverScrollListener();

        if (contextWrapper != null) {
            collection.setOverScrollEnabled(false);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (duplicateRevealer != null) {
            duplicateRevealer.end();
        }

        if (currentExpander != null) {
            currentExpander.saveStaticContext();
        }
        if (currentExpander != null && tagWrapper == null) {
            expanderTarget = currentExpander.getTarget();
            currentExpander.destroy(false);
        }
        else {
            expanderTarget = null;
        }
        currentExpander = null;
        confirmator = null;
        orderConfirmator = null;

        editor.saveState();
        editor.detach();

        activity = null;

        collection = null;

//        actionMode = null;
    }

    public boolean handleBackPressed() {
        if (currentExpander != null) {
            currentExpander.compact();
            return true;
        }

        return editor.commitAndHideKeyboard(true);
    }

    public void stopAnimations() {
        if (emptyAnimator != null)
            emptyAnimator.cancel();

        if (duplicateRevealer != null) {
            duplicateRevealer.end();
        }
    }

    public void resumeAnimations() {
        if (emptyAnimator != null)
            emptyAnimator.start();
    }

    public void registerDataForRestore(ArrayList<ItemCollectionFragment.Item> data) {
        items = data;

        if (collection != null) {
            // This also ends all playing animations
            // and implicitly ends all pending transactions
            collection.setAnimationsEnabled(false);
            collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
            collection.scrollTo(0, 0);
            collection.setAnchorCondition(null);
//            collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
        }

        controller.requestBeginTransaction();
        controller.getSectionAtIndex(0).clear();
        controller.getSectionAtIndex(0).addAllObjects(items);
        controller.requestCompleteTransaction();

//        collection.post(new Runnable() {
//            @Override
//            public void run() {
//                controller.requestCompleteTransaction();
//            }
//        });

    }

    public ViewHolder prepareViewHolder(View view) {

        ViewHolder holder = new ViewHolder();
        holder.title = (TextView)view.findViewById(R.id.ItemTitle);
        holder.qty = (TextView)view.findViewById(R.id.QtyTitle);
        holder.price = (TextView)view.findViewById(R.id.PriceTitle);
        holder.strikethrough = view.findViewById(R.id.ItemStrikethrough);
        holder.tags = (TagView) view.findViewById(R.id.ItemTags);

        holder.itemRoot = view.findViewById(R.id.ItemRoot);

        view.setTag(holder);

        return holder;

    }

    protected void configureDeleteTouchListener() {
        swipeToDeleteListener = new SwipeToDeleteListener(getActivity().getApplicationContext());

        SwipeToDeleteListener.OnMoveListener moveListener = new SwipeToDeleteListener.OnMoveListener() {

            float alpha;
            float deleteDistance = metrics.widthPixels / 3f;

            @Override
            public void onMove(View view, float distance, boolean initial) {
                view.setTranslationX(view.getTranslationX() + distance);

                alpha = Math.max(0.1f, 1 - Math.abs(view.getX() - view.getLeft()) / deleteDistance);
                view.setAlpha(alpha);

                if (initial) {
                    collection.retainView(view);
                    collection.requestDisableInteractions();

                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    view.setOnClickListener(null);
                    view.setOnLongClickListener(null);
                    view.setClickable(false);
                    view.setLongClickable(false);
//                    view.setBackgroundColor(0);
                    view.setPressed(false);
                }
            }
        };

        SwipeToDeleteListener.OnReleaseListener releaseListener = new SwipeToDeleteListener.OnReleaseListener() {
            @Override
            public void onRelease(final View view) {
                view.animate()
                        .translationX(0).alpha(1f)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                view.animate().setListener(null);

                                if (collection != null) {
                                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                                    view.setOnClickListener(itemClickListener);
                                    view.setOnLongClickListener(itemLongClickListener);
                                    view.setClickable(true);
                                    view.setLongClickable(true);
//                                    view.setBackgroundResource(R.drawable.unselected_scrap);
                                    collection.requestEnableInteractions();
                                    collection.releaseView(view);
                                    view.setPressed(false);
                                }
                            }
                        });
            }
        };

        SwipeToDeleteListener.OnDeleteListener deleteListener = new SwipeToDeleteListener.OnDeleteListener() {
            @Override
            public void onDelete(final View view, float velocity, float velocityRatio) {

                if (contextWrapper != null) {
                    contextWrapper.dismiss();
                }

                float dr = collection.getWidth() - view.getTranslationX();
                float distanceRatio = dr / view.getWidth();
                if (distanceRatio < 0) distanceRatio = 0.01f;
                float timeRatio = Math.signum(velocityRatio) * 300 * distanceRatio * velocityRatio;
                if (timeRatio > 295f) timeRatio = 295f;

                view.animate().alpha(0f).translationX(Math.signum(velocityRatio) * collection.getWidth())
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .setDuration((long)(timeRatio))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                view.animate().setListener(null);

                                if (collection != null) {
                                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                                    view.setOnClickListener(itemClickListener);
                                    view.setOnLongClickListener(itemLongClickListener);
                                    view.setClickable(true);
                                    view.setLongClickable(true);
//                                    view.setBackgroundResource(R.drawable.unselected_scrap);

                                    view.setPressed(false);
                                }
                            }
                        });

                collection.requestEnableInteractions();
                controller.requestBeginTransaction();


                if (editor != null)
                    editor.commitAndHideKeyboard(true);

                if (currentExpander != null)
                    currentExpander.compact();

                Item target = (Item) collection.getObjectForView(view);
                confirmingItem = target;
                confirmingPosition = items.indexOf(target);
                controller.getSectionAtIndex(0).removeObject(target);
                if (velocityRatio < 0f) {
                    confirmingDirection = FlingDirectionLeft;
                }
                else {
                    confirmingDirection = FlingDirectionRight;
                }
                items.remove(confirmingPosition);
                removeItemFromActivity(confirmingItem);

                swipeConfirmation = true;
                showDeleteConfirmator(true);

                collection.setMoveWithLayersEnabled(false);
                collection.setAnimationsEnabled(true);
                collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
                collection.setAnchorCondition(null);
                collection.setContainerResizeDelay(500);
                collection.setDeleteAnimationStride(0);
                collection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                    @Override
                    public void playAnimation(View view, Object object, int viewType) {
                        view.animate().alpha(0f);
                    }

                    @Override
                    public void resetState(View view, Object object, int viewType) {
                        view.setAlpha(1f);
                    }
                });

                controller.requestCompleteTransaction();

            }
        };

        SwipeToDeleteListener.EnabledListener enabledListener = new SwipeToDeleteListener.EnabledListener() {
            @Override
            public boolean isEnabled() {
                return selectionList.size() == 0;
            }
        };

        swipeToDeleteListener.setEnabledListener(enabledListener);
        swipeToDeleteListener.setOnDeleteListener(deleteListener);
        swipeToDeleteListener.setOnMoveListener(moveListener);
        swipeToDeleteListener.setOnReleaseListener(releaseListener);
        swipeToDeleteListener.setMinimumSwipeDistance(metrics.widthPixels / 3f);

    }

    class ItemController extends CollectionViewController {

        @Override
        public View createEmptyView(ViewGroup container, LayoutInflater inflater) {
            View view = inflater.inflate(R.layout.layout_empty, container, false);

            ((TextView) view.findViewById(R.id.EmptyText)).setTypeface(Receipt.condensedLightTypeface());
            view.findViewById(R.id.EmptyText).setVisibility(View.INVISIBLE);

            final View Graphic = view.findViewById(R.id.EmptyImage);
            Graphic.setAlpha(0f);

            if (emptyAnimator != null) {
                emptyAnimator.cancel();
            }

            emptyAnimator = ValueAnimator.ofFloat(0f, 1f);
            emptyAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    Graphic.setTranslationY(fraction * Graphic.getHeight() / 2 - Graphic.getHeight() / 4);
                    if (fraction < 0.5f) {
                        Graphic.setAlpha(fraction);
                    } else {
                        Graphic.setAlpha(1 - fraction);
                    }
                }
            });
            emptyAnimator.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;

                public void onAnimationStart(Animator animation) { cancelled = false; }

                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        animation.start();
                    }
                }
            });
            emptyAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            emptyAnimator.setDuration(1000);
            emptyAnimator.setStartDelay(500);
            if (activity.getState() == BackendFragment.StateOpenList) {
                emptyAnimator.start();
            }
            return view;
        }

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            if (viewType == 1) {
                View view = new View(container.getContext());

                view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (container.getResources().getDisplayMetrics().density * 24)));

                return view;
            }
            View view =  inflater.inflate(R.layout.layout_collection_item, container, false);

            $.metadata(view, ItemMetadataKey, "");

            ((DisableableFrameLayout) view.findViewById(R.id.ItemRoot)).setForwardPressedStateEnabled(false);

            ViewHolder holder = prepareViewHolder(view);
            view.setBackground(new LegacyRippleDrawable(activity));

            holder.tags.setBackground(new LegacyRippleDrawable(activity));
            holder.price.setBackground(new LegacyRippleDrawable(activity));
            holder.qty.setBackground(new LegacyRippleDrawable(activity));

            holder.tags.setDuplicateParentStateEnabled(false);
            holder.price.setDuplicateParentStateEnabled(false);
            holder.qty.setDuplicateParentStateEnabled(false);

            //Code setting the correct layout margins
            ViewGroup.MarginLayoutParams workParams = (ViewGroup.MarginLayoutParams) holder.price.getLayoutParams();
            ViewGroup.MarginLayoutParams setParams = (ViewGroup.MarginLayoutParams) holder.qty.getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;
            setParams = (ViewGroup.MarginLayoutParams) holder.itemRoot.findViewById(R.id.QtyTouchHelper).getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;

            workParams = (ViewGroup.MarginLayoutParams) holder.qty.getLayoutParams();
            setParams = (ViewGroup.MarginLayoutParams) holder.title.getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;
//            workParams = (ViewGroup.MarginLayoutParams) holder.tags.getLayoutParams();
//            setParams.leftMargin = workParams.leftMargin + workParams.width;
//            setParams.leftMargin += holder.tags.getPaddingLeft() + holder.tags.getPaddingRight();
//            workParams.width += holder.tags.getPaddingLeft() + holder.tags.getPaddingRight();

            return view;
        }

        @Override
        public void configureView(View view, Object object, int viewType) {
            if (viewType == 1) return;
            if (swipeToDeleteListener == null) configureDeleteTouchListener();

            ViewHolder holder = (ViewHolder) view.getTag();

            Item item = (Item) object;

            holder.title.setText(item.name);

            if ((item.flags & SetQty) == 0) {
                holder.qty.setText("1.0" + item.unitOfMeasurement);
                holder.qty.setTextColor(getResources().getColor(R.color.implicit_text_colors));
            }
            else {
                holder.qty.setText(ReceiptActivity.quantityFormattedString(activity, item.qty, item.unitOfMeasurement));
                holder.qty.setTextColor(getResources().getColor(R.color.ItemSetValue));
            }
            if ((item.flags & SetPrice) == 0) {
                if (item.estimatedPrice == 0) holder.price.setText(ReceiptActivity.currentLocale);
                else holder.price.setText(ReceiptActivity.longToFormattedString(item.estimatedPrice, Receipt.hintLightSpan()));
                holder.price.setTextColor(getResources().getColor(R.color.implicit_text_colors));
            }
            else {
                holder.price.setText(ReceiptActivity.longToFormattedString(item.price, Receipt.textLightSpan()));
                holder.price.setTextColor(getResources().getColor(R.color.ItemSetValue));
            }

            if (item.crossedOff) {
                holder.title.setTextColor(getResources().getColor(R.color.ItemSetValue));
                holder.qty.setTextColor(getResources().getColor(R.color.ItemImplicitValue));
                holder.price.setTextColor(getResources().getColor(R.color.ItemImplicitValue));
                holder.strikethrough.setVisibility(View.VISIBLE);
            }
            else {
                holder.title.setTextColor(getResources().getColor(R.color.ItemText));
                holder.strikethrough.setVisibility(View.INVISIBLE);
            }

            holder.tags.setTags(item.tags);

            if (selectionList.size() > 0) {
                holder.tags.setOnClickListener(null);
                holder.qty.setOnClickListener(null);
                holder.price.setOnClickListener(null);

                holder.tags.setClickable(false);
                holder.qty.setClickable(false);
                holder.price.setClickable(false);

                holder.tags.setEnabled(false);
                holder.qty.setEnabled(false);
                holder.price.setEnabled(false);

//                holder.price.setBackground(null);
//                holder.qty.setBackground(null);
//                holder.tags.setBackground(null);
            }
            else {
                holder.tags.setOnClickListener(tagClickListener);
                holder.qty.setOnClickListener(qtyEditListener);
                holder.price.setOnClickListener(priceEditListener);

                holder.tags.setClickable(true);
                holder.qty.setClickable(true);
                holder.price.setClickable(true);

                holder.tags.setEnabled(true);
                holder.qty.setEnabled(true);
                holder.price.setEnabled(true);

//                holder.tags.setBackground(new LegacyRippleDrawable(activity));
//                holder.price.setBackground(new LegacyRippleDrawable(activity));
//                holder.qty.setBackground(new LegacyRippleDrawable(activity));
            }

//            if (item.selected) {
////                view.setBackgroundResource(R.drawable.selected_scrap);
//                view.setSelected(true);
//            }
//            else {
////                view.setBackgroundResource(R.drawable.unselected_scrap);
//                view.setSelected(false);
//            }
//            if (!(view.getBackground() instanceof LegacyRippleDrawable)) view.setBackground(new LegacyRippleDrawable(getActivity()));

            if (view.isSelected() != item.selected) {
                if (!isRefreshingViews()) {
                    ((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
                }
                view.setSelected(item.selected);
            }

            if (item.flags == SetNone) {
                holder.itemRoot.setVisibility(View.INVISIBLE);
                view.setOnClickListener(null);
                view.setOnLongClickListener(null);
                ((LegacyRippleDrawable) view.getBackground()).setForwardListener(swipeToDeleteListener);
            }
            else {
                holder.itemRoot.setVisibility(View.VISIBLE);

                view.setOnClickListener(itemClickListener);
                view.setOnLongClickListener(itemLongClickListener);
                ((LegacyRippleDrawable) view.getBackground()).setForwardListener(swipeToDeleteListener);
            }
        }

        @Override
        public void requestCompleteTransaction() {
            if (items.size() == 0) {
                getSectionAtIndex(1).clear();
            }
            else if (getSectionAtIndex(1).getSize() == 0) {
                getSectionAtIndex(1).addObject(new Object());
            }
            super.requestCompleteTransaction();

            if (duplicateRevealer != null) {
                duplicateRevealer.end();
            }

            if (items.size() > 0) {
                if (emptyAnimator != null) {
                    emptyAnimator.cancel();
                    emptyAnimator = null;
                }
            }
        }
    }

    private View.OnClickListener tagClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            editor.commitAndHideKeyboard(true);
            if (currentExpander != null) currentExpander.compact();
            collection.retainView((View) view.getParent().getParent());
            delayHandler.retain();
            currentExpander = TagExpander.fromViewInContainerWithTarget((TagView) view, (ViewGroup) view.getParent().getParent(), (Item) collection.getObjectForView((ViewGroup) view.getParent().getParent()));
            currentExpander.setOnTagDeletedListener(ItemCollectionFragment.this);
            currentExpander.expand();
            currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                @Override
                public void onClose() {
                    currentExpander = null;
                    collection.releaseView((View) view.getParent().getParent());
                    delayHandler.release(ReorderDelay);
                }
            });
        }
    };

    private View.OnClickListener qtyEditListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            editor.startEditing((Item) collection.getObjectForView((View) view.getParent().getParent()), EditorModeQty);
        }
    };

    private View.OnClickListener priceEditListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            editor.startEditing((Item) collection.getObjectForView((View) view.getParent().getParent()), EditorModePrice);
        }
    };

    private View.OnClickListener itemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            editor.commitAndHideKeyboard(true);
            if (currentExpander != null) currentExpander.compact();

            if (selectionList.size() == 0) {
                onItemClicked((Item) collection.getObjectForView(view));
            }
            else {
                onItemLongClicked((Item) collection.getObjectForView(view));
            }
        }
    };

    private View.OnLongClickListener itemLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            editor.commitAndHideKeyboard(true);
            if (currentExpander != null) currentExpander.compact();

            onItemLongClicked((Item) collection.getObjectForView(view));
            return true;
        }
    };

    /**
     * Updates the associated view for the specified item if that item has a view bound to it
     * @param item The item whose view will be updated
     * @return The item's bound view if it exists, null otherwise
     */
    protected View refreshViewForItemIfVisible(Item item) {

        View itemView = collection.getViewForObject(item);
        if (itemView != null) {
            // Refresh the view to match the new item
            controller.configureView(itemView, item, 0);
        }

        return itemView;

    }

    protected void updateStrikethroughForItem(final Item TargetItem, boolean animated) {

        View itemView = refreshViewForItemIfVisible(TargetItem);

        if (animated && itemView != null) {

            final View Strikethrough =  itemView.findViewById(R.id.ItemStrikethrough);

            if (TargetItem.isAnimatingStrikethrough) {
               Strikethrough.animate().cancel();
            }

            TargetItem.isAnimatingStrikethrough = true;

            if (!TargetItem.crossedOff) {
                Strikethrough.setVisibility(View.VISIBLE);
            }

            final float InitialValue = TargetItem.crossedOff ? 0f : 1f;
            final float TargetValue = 1f - InitialValue;

//            Strikethrough.setAlpha(InitialValue);
            Strikethrough.setScaleX(InitialValue);

            float clickPointX = ((LegacyRippleDrawable) itemView.getBackground()).lastXCoordinate() - Strikethrough.getLeft();
            Strikethrough.setPivotX(clickPointX);

            Strikethrough.animate().scaleX(TargetValue)
                    .setDuration(200)
                    .setInterpolator(new Utils.FrictionInterpolator(1.5f))
//                    .withLayer()
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            TargetItem.isAnimatingStrikethrough = false;

//                            Strikethrough.setAlpha(TargetValue);
                            Strikethrough.setScaleX(1f);

                            if (!TargetItem.crossedOff) {
                                Strikethrough.setVisibility(View.INVISIBLE);
                            }
                        }
                    });

        }

    }

    protected void onItemClicked(Item item) {

        if (item.crossedOff) {
            item.crossedOff = false;
            activity.addToCrossedOffCount(-1);
            if (item.price == 0) {
                activity.addToTotal(item.qty, -item.estimatedPrice);
            }
            else {
                activity.addToTotal(item.qty, -item.price);
            }
        }
        else {
            item.crossedOff = true;
            activity.addToCrossedOffCount(1);
            if (item.price == 0) {
                activity.addToTotal(item.qty, item.estimatedPrice);
            }
            else {
                activity.addToTotal(item.qty, item.price);
            }
        }

        updateStrikethroughForItem(item, true);

        delayHandler.registerCallback(ReorderRunnable, ReorderDelay);

    }

    public void invertSelection() {
        for (Item item : items) {
            onItemLongClicked(item);
        }
    }

    public void flashItem() {

        Resources Res = getResources();

        final int[] ColorTable[] = new int[][] {
                new int[] {Res.getColor(R.color.TagLightRed), Res.getColor(R.color.TagRed), Res.getColor(R.color.TagDarkRed)},
                new int[] {Res.getColor(R.color.TagLightOrange), Res.getColor(R.color.TagOrange), Res.getColor(R.color.TagDarkOrange)},
                new int[] {Res.getColor(R.color.TagLightGreen), Res.getColor(R.color.TagGreen), Res.getColor(R.color.TagDarkGreen)},
                new int[] {Res.getColor(R.color.TagLightBlue), Res.getColor(R.color.TagBlue), Res.getColor(R.color.TagDarkBlue)},
                new int[] {Res.getColor(R.color.TagLightPurple), Res.getColor(R.color.TagPurple), Res.getColor(R.color.TagDarkPurple)},
                new int[] {Res.getColor(R.color.TagWhite), Res.getColor(R.color.TagGray), Res.getColor(R.color.TagBlack)}
        };

        if (items.size() > 0) {
            int max = items.size();

            int index = (int) (Math.random() * max);

            Item item = items.get(index);

            View view = collection.getViewForObject(item);
            if (view != null) {
                ((LegacyRippleDrawable) view.getBackground()).flashColor(ColorTable[((int) (Math.random() * 6))][((int) (Math.random() * 3))], 600, 1f);
            }
            else {
                Log.e(TAG, "The item view for item " + index + " is NULL!");
            }
        }
    }

    protected void onItemLongClicked(Item item) {

        int selectionSizePre = selectionList.size();

        if (item.selected = !item.selected) {
            selectionList.add(item);
            addToSelectionTotal(item);
        }
        else {
            selectionList.remove(item);
            subtractFromSelectionTotal(item);
        }

        if (contextWrapper == null) {
            contextWrapper = ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).createContextMode(contextListener);

            contextWrapper.addItem(R.id.action_delete, getString(R.string.ItemDelete), R.drawable.ic_action_delete, false, true);
//            contextWrapper.addItem(R.id.action_cut, getString(R.string.ItemCut), R.drawable.ic_action_cut, false, true);
            contextWrapper.addItem(R.id.action_copy, getString(R.string.ItemCopy), R.drawable.ic_action_copy, false, true);
            contextWrapper.addItem(R.id.action_rename, getString(R.string.ItemRename), R.drawable.content_edit, false, true);

            contextWrapper.start();
        }

        if (tagWrapper != null) {
            final LegacyActionBar.ContextBarWrapper ContextWrapper = contextWrapper;
            contextWrapper = null;
            tagWrapper.dismiss();
            contextWrapper = ContextWrapper;
        }

        boolean changingSelection = multipleSelection;
        multipleSelection = selectionList.size() > 1;
        if (changingSelection != multipleSelection) {
            if (multipleSelection) {
                contextWrapper.removeItemWithId(R.id.action_rename);
                contextWrapper.addItemToIndex(R.id.action_edit_tags, getString(R.string.ItemEditTags), R.drawable.ic_action_edit_tags, false, true, 2);
            }
            else {
                contextWrapper.removeItemWithId(R.id.action_edit_tags);
                contextWrapper.addItemToIndex(R.id.action_rename, getString(R.string.ItemRename), R.drawable.content_edit, false, true, 2);
            }
        }

//        actionMode.invalidate();

        if (selectionList.size() == 0) {
            contextWrapper.dismiss();
        }
        else {
            // TODO
            contextWrapper.setTitleAnimated(selectionList.size() + " selected", selectionList.size() - selectionSizePre);
            contextWrapper.setSubtitle(ReceiptActivity.currentLocale + selectionTotal
                    .add(selectionTotal
                            .multiply(new BigDecimal(activity.getTax())
                                    .movePointLeft(4)))
                    .setScale(2, RoundingMode.HALF_EVEN) + " total");
        }

        collection.refreshViews();
    }

    public void refreshActionMode() {
        if (contextWrapper != null) {
            contextWrapper.setSubtitle(ReceiptActivity.currentLocale + selectionTotal
                    .add(selectionTotal
                            .multiply(new BigDecimal(activity.getTax())
                                    .movePointLeft(4)))
                    .setScale(2, RoundingMode.HALF_EVEN) + " total");
        }
    }

    final static boolean AlternateEditorAnimation = true;

    private CollectionView.ReversibleAnimation insertAnimation = new CollectionView.ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            view.setAlpha(0f);
            if (AlternateEditorAnimation) {
                ((ViewGroup) view).getChildAt(0).setTranslationY(- 48f * density);
            }
            view.animate().alpha(1f);
            if (AlternateEditorAnimation) {
                ((ViewGroup) view).getChildAt(0).animate().translationY(0f).setDuration(300).start();
            }
        }

        @Override
        public void resetState(View view, Object object, int viewType) { }
    };

    public void addNewItemToList() {

        if (contextWrapper != null) {
            contextWrapper.dismiss();
        }

        if (editor.target != null && editor.mode == EditorModeAll) {
            if (editor.titleEditor.getText().toString().trim().isEmpty()) {
                editor.flashTitle();
                return;
            }
        }
        else {
            editor.commitAndHideKeyboard(false);
        }
        if (currentExpander != null) currentExpander.compact();

        final Item newItem = new Item();
        newItem.flags = SetNone;
        newItem.unitOfMeasurement = getString(R.string.Count);

        // This instantly ends any playing animations
        collection.setAnimationsEnabled(false);
        collection.setAnimationsEnabled(true);

        controller.requestBeginTransaction();
        controller.getSectionAtIndex(0).addObjectToIndex(newItem, 0);

        // Scroll to the top to reveal the new item
        collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
        collection.setInsertAnimator(insertAnimation);
        controller.requestCompleteTransaction();

        editor.startEditingWithKeyboard(newItem, EditorModeAll, false);

        ValueAnimator delayAnimator = ValueAnimator.ofInt(0, 300);
        delayAnimator.setDuration(250);
        delayAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (editor.target == newItem && activity != null) {
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(editor.getFocusedField(), InputMethodManager.SHOW_FORCED);
                }
            }
        });
        delayAnimator.start();

    }

    protected void finishAddingItem() {

        collection.setDeleteAnimator(null);
        collection.setDeleteAnimationDuration(200);

        String title = editor.titleEditor.getText().toString().trim();

        if (title.isEmpty()) {
            // This instantly ends any playing animations
            // There are no animations that may play during editing
            collection.setAnimationsEnabled(false);
            collection.setAnimationsEnabled(true);

            controller.requestBeginTransaction();
            controller.getSectionAtIndex(0).removeObject(editor.target);

            collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
            controller.requestCompleteTransaction();

            return;
        }

        Item item = editor.target;

        item.name = title;
        item.flags |= SetTitle;
        activity.addToItemCount(1);

        long qty;
        try {
            qty = new BigDecimal(editor.qtyEditor.getText().toString()).movePointRight(4).longValue();
            if (qty > 9999999) //That is to say 999.9999
                qty = 9999999;
        }
        catch (NumberFormatException exception) {
            qty = 0;
        }
        if (qty != 0)
            item.flags |= SetQty;
        item.qty = qty;

        long price;
        try {
            price = new BigDecimal(editor.priceEditor.getText().toString()).movePointRight(2).longValue();
        }
        catch (NumberFormatException exception) {
            price = 0;
        }
        if (price != 0) {
            item.flags |= SetPrice;
            if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("autoCross", true)) {
                // TODO
                delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
                item.crossedOff = true;
                activity.addToCrossedOffCount(1);
                activity.addToTotal(qty, price);
            }
        }
        item.price = price;

        if (item.price != 0)
            activity.addToEstimatedTotal(item.qty, item.price);
        else
            activity.addToEstimatedTotal(item.qty, item.estimatedPrice);

        items.add(0, item);

        refreshViewForItemIfVisible(item);

    }

    protected void applyQtyChanges() {
        final Item item = editor.target;
        final long oldQty = item.qty;
        long qty;
        try {
            qty = new BigDecimal(editor.qtyEditor.getText().toString()).movePointRight(4).longValue();
            if (qty > 9999999) //That is to say, 999.9999
                qty = 9999999;
        }
        catch (NumberFormatException exception) {
            qty = 0;
        }
        if (qty != 0)
            item.flags |= SetQty;
        else
            item.flags &= ~SetQty;
        item.qty = qty;

        long qtyDifference = qty - oldQty;
        if (qty == 0) qtyDifference += 10000;
        if (oldQty == 0) qtyDifference -= 10000;

        // These "direct" methods do not set the quantity to one if it was zero
        if (item.crossedOff)  {
            if (item.price == 0)
                activity.directAddToTotal(qtyDifference, item.estimatedPrice);
            else
                activity.directAddToTotal(qtyDifference, item.price);
        }
        if (item.price == 0)
            activity.directAddToEstimatedTotal(qtyDifference, item.estimatedPrice);
        else
            activity.directAddToEstimatedTotal(qtyDifference, item.price);


        refreshViewForItemIfVisible(item);
    }

    protected void applyPriceChanges() {

        final Item item = editor.target;
        final long oldPrice = item.price;
        final boolean wasCrossedOff = item.crossedOff;
        final boolean autoCross = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("autoCross", true);
        long price;
        try {
            price = new BigDecimal(editor.priceEditor.getText().toString()).movePointRight(2).longValue();
        }
        catch (NumberFormatException exception) {
            price = 0;
        }
        if (price != 0) {
            if (autoCross) {
                item.crossedOff = true;
                delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
            }
            item.flags |= SetPrice;
        }
        else
            item.flags &= ~SetPrice;
        item.price = price;

        long priceDifference = price - oldPrice;

        activity.addToEstimatedTotal(item.qty, priceDifference);
        if (oldPrice == 0 && price != 0)
            activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
        if (item.price == 0 && oldPrice != 0)
            activity.addToEstimatedTotal(item.qty, item.estimatedPrice);

        if (wasCrossedOff) {
            activity.addToTotal(item.qty, priceDifference);
            if (oldPrice == 0 && price != 0)
                activity.addToTotal(item.qty, -item.estimatedPrice);
            if (item.price == 0 && oldPrice != 0)
                activity.addToTotal(item.qty, item.estimatedPrice);
        }
        else if (autoCross) {
            if (item.crossedOff) {
                delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
                activity.addToCrossedOffCount(1);
                if (item.price != 0)
                    activity.addToTotal(item.qty, item.price);
                else
                    activity.addToTotal(item.qty, item.estimatedPrice);
            }
        }

        refreshViewForItemIfVisible(item);
    }

    protected void applyTitleChanges() {
        // Secondary changes brought on by title changes are handled while the item is being edited
        final Item item = editor.target;

        String newName = editor.titleEditor.getText().toString().trim();

        if (!TextUtils.isEmpty(newName))
            item.name = newName;

        refreshViewForItemIfVisible(item);
    }

    protected void applyTagChanges() {
        refreshViewForItemIfVisible(editor.target);
    }

    public void finalizeChanges() {
        editor.commitAndHideKeyboard(true);
        if (currentExpander != null) currentExpander.compact();
    }

    public void finalizeChangesInstantly() {
        collection.getOnOverScrollListener().onOverScrollStopped(collection);

        editor.commitAndHideKeyboard(true);
        if (currentExpander != null && tagWrapper == null) {
            currentExpander.dismissPopover();
            currentExpander.destroy();
        }

        // This ends all in progress animations
        collection.setAnimationsEnabled(false);

        delayHandler.unregisterCallback();
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item lhs, Item rhs) {
                if (lhs.crossedOff == rhs.crossedOff) return 0;
                if (lhs.crossedOff) return 1;
                else return -1;
            }
        });

        if (confirmatorUp)
            finalizeDelete();

        if (orderConfirmatorUp)
            finalizeOrder();

        collection.setAnimationsEnabled(true);
    }

    public void dismissContextModes(boolean animated) {
        if (tagWrapper != null) {
            tagWrapper.dismiss(animated);
        }
        if (contextWrapper != null) {
            contextWrapper.dismiss(animated);
        }
        if (deleteWrapper != null) {
            deleteWrapper.dismiss(animated);
        }
    }

    class ClipboardAnimation implements CollectionView.ReversibleAnimation {
        int pasteSize;
        int index = 0;

        ClipboardAnimation(int pasteSize) {
            this.pasteSize = pasteSize;
        }

        @Override
        public void playAnimation(View view, Object object, int viewType) {
//            view.setAlpha(0f);
//            view.animate().alpha(1f);
//            if (true) return;
//            ((ViewGroup) view).setClipChildren(false);
//            ((ViewGroup) view.getParent()).setClipChildren(false);
//            ((ViewGroup) view).getChildAt(0).setTranslationY(- (2 * pasteSize - items.indexOf(object)) * 48 * density);
//            ((ViewGroup) view).getChildAt(0).animate().translationY(0).start();

            // Displace the view appropriately; moveViewsAndCleanup() will handle moving the view back into its usual position
            // Also destroy the hardware layer; it doesn't help here
            //noinspection SuspiciousMethodCalls
            view.setTranslationY(- (1.5f * pasteSize - /*items.indexOf(object)*/ index) * 48 * density);
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            index++;
        }

        @Override
        public void resetState(View view, Object object, int viewType) {}
    }

    public void addPendingItems(ArrayList<ItemCollectionFragment.Item> items) {
        collection.setAnimationsEnabled(false);
        collection.setAnimationsEnabled(true);

        controller.requestBeginTransaction();

        editor.commitAndHideKeyboard(true);
        if (currentExpander != null)
            currentExpander.compact();

        int index = 0;
        for (ItemCollectionFragment.Item item : items) {
            controller.getSectionAtIndex(0).addObjectToIndex(item, index);
            this.items.add(index, item);
            index++;

            activity.addToItemCount(1);

            //Pending items never start off crossed off and their prices are usually estimates
            item.crossedOff = false;
            item.selected = false;
            if (item.price == 0)
                activity.addToEstimatedTotal(item.qty, item.estimatedPrice);
            else
                activity.addToEstimatedTotal(item.qty, item.price);
        }

        collection.setMoveWithLayersEnabled(false);
        collection.setInsertAnimator(new ClipboardAnimation(items.size()));
        collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        controller.requestCompleteTransaction();

        delayHandler.redelay(ReorderDelay);
    }

    public ArrayList<Item> getItems() {
        return items;
    }

    public ArrayList<View> obtainTopCrossedOffItems() {
        ArrayList<View> topViews = new ArrayList<View>();
        return null;
    }

    public PartialCheckoutItems deleteCrossedOffItems() {
        return deleteCrossedOffItems(false);
    }

    public PartialCheckoutItems deleteCrossedOffItems(boolean animated) {

        editor.commitAndHideKeyboard(true);
        if (currentExpander != null) currentExpander.compact();

        PartialCheckoutItems partialCheckoutItems = new PartialCheckoutItems();

        collection.setAnimationsEnabled(animated);
        if (animated) {
            collection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                @Override
                public void playAnimation(View view, Object object, int viewType) {
                    view.setAlpha(0f);
                }

                @Override
                public void resetState(View view, Object object, int viewType) {
                    view.setAlpha(1f);
                }
            });
        }

        controller.requestBeginTransaction();
        CollectionView.Section section = controller.getSectionAtIndex(0);

        int position = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).crossedOff) {
                Item item = items.get(i);

                section.removeObject(items.get(i));
                partialCheckoutItems.items.add(items.remove(i));
                partialCheckoutItems.positions.add(position);

                activity.fastAddToTotal(item.qty, -(item.price == 0 ? item.estimatedPrice : item.price));
                activity.fastAddToEstimatedTotal(item.qty, -(item.price == 0 ? item.estimatedPrice : item.price));

                i--;
            }

            position++;
        }

        activity.addToCrossedOffCount(-partialCheckoutItems.items.size());
        activity.addToItemCount(-partialCheckoutItems.items.size());
        activity.addToTotal(0, 0);
        activity.addToEstimatedTotal(0, 0);

        controller.requestCompleteTransaction();

        return partialCheckoutItems;
    }

    public void restoreCrossedOffItems(PartialCheckoutItems partialCheckoutItems) {
        collection.setAnimationsEnabled(false);

        controller.requestBeginTransaction();
        CollectionView.Section section = controller.getSectionAtIndex(0);

        for (int i = 0; i < partialCheckoutItems.items.size(); i++) {
            Item item = partialCheckoutItems.items.get(i);

            section.addObjectToIndex(partialCheckoutItems.items.get(i), partialCheckoutItems.positions.get(i));
            items.add(partialCheckoutItems.positions.get(i), partialCheckoutItems.items.get(i));

            activity.fastAddToTotal(item.qty, (item.price == 0 ? item.estimatedPrice : item.price));
            activity.fastAddToEstimatedTotal(item.qty, (item.price == 0 ? item.estimatedPrice : item.price));
        }

        activity.addToItemCount(partialCheckoutItems.items.size());
        activity.addToCrossedOffCount(partialCheckoutItems.items.size());
        activity.addToTotal(0, 0);
        activity.addToEstimatedTotal(0, 0);

        controller.requestCompleteTransaction();
    }

    // ************ REORDERING ***************

    static class DelayHandler {

        private Handler handler = new Handler();
        Runnable callback;
        int retainCount = 0;

        public void registerCallback(Runnable r, long delay) {
            if (callback != null)
                handler.removeCallbacks(callback);
            callback = r;
            if (retainCount == 0)
                handler.postDelayed(r, delay);
        }

        public void unregisterCallback() {
            if (callback != null) {
                handler.removeCallbacks(callback);
                callback = null;
            }
        }

        public void redelay(long delay) {
            if (retainCount != 0) return;
            if (callback != null) {
                handler.removeCallbacks(callback);
                handler.postDelayed(callback, delay);
            }
        }

        @Deprecated
        public void delayIndefinitely() {
            if (callback != null) {
                handler.removeCallbacks(callback);
            }
        }

        public void retain() {
            retainCount++;
            if (callback != null) {
                handler.removeCallbacks(callback);
            }
        }

        public void release(long delay) {
            retainCount--;
            if (retainCount == 0 && callback != null) {
                handler.postDelayed(callback, delay);
            }
        }

        @Deprecated
        public boolean postDelayed(Runnable r, long delay) {
            return handler.postDelayed(r, delay);
        }

        @Deprecated
        public void removeCallbacks(Runnable r) {
            handler.removeCallbacks(r);
        }

    }

    final Runnable ReorderRunnable = new Runnable() {
        public void run() {

            boolean reorder = PreferenceManager.getDefaultSharedPreferences(Receipt.getStaticContext()).getBoolean(SettingsFragment.AutoReorderKey, true);
            if (!reorder) return;

            // This shouldn't run during an editing or other transient state

            controller.requestBeginTransaction();

            Collections.sort(items, new Comparator<Item>() {
                @Override
                public int compare(Item lhs, Item rhs) {
                    if (lhs.crossedOff == rhs.crossedOff) return 0;
                    if (lhs.crossedOff) return 1;
                    else return -1;
                }
            });

            controller.getSectionAtIndex(0).sortWithComparator(new Comparator<Item>() {
                @Override
                public int compare(Item lhs, Item rhs) {
                    if (lhs.crossedOff == rhs.crossedOff) return 0;
                    if (lhs.crossedOff) return 1;
                    else return -1;
                }
            });

            collection.setAnimationsEnabled(true);
            collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
            collection.setAnchorCondition(new CollectionView.AnchorInspector() {
                @Override
                public boolean isAnchor(Object object, int viewType) {
                    return viewType == 0 && !((Item) object).crossedOff;
                }
            });
            controller.requestCompleteTransaction();
        }
    };



    // **************************** ACTIONMODE RELATED CALLS ******************************

    // selection handlers
//    private ActionMode actionMode = null;
    private boolean multipleSelection;

    private LegacyActionBar.ContextBarWrapper contextWrapper;
    private LegacyActionBar.ContextBarListener contextListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
            delayHandler.retain();
            activity.dismissBackendContextModes();
        }

        public void onContextBarActivated(LegacyActionBar.ContextBarWrapper wrapper) {
            if (collection != null) {
                collection.setOverScrollEnabled(false);
            }
        }

        @Override
        public void onContextBarDismissed() {
            if (collection != null) {
                collection.setOverScrollEnabled(true);
            }
            if (selectionList.size() != 0) {
                deselect();
                collection.refreshViews();
            }
            selectionTotal = new BigDecimal(0);
            contextWrapper = null;
            multipleSelection =  false;
            delayHandler.release(ReorderDelay);
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            switch (item.getId()) {
                case R.id.action_delete:
                    deleteSelection(false);
                    contextWrapper.dismiss();
                    break;
                case R.id.action_cut:
                    cutSelection();
                    contextWrapper.dismiss();
                    break;
                case R.id.action_copy:
                    copySelection();
                    contextWrapper.dismiss();
                    break;
                case R.id.action_crosoff:
                    contextWrapper.dismiss();
                    break;
                case R.id.action_rename:
                    Item target = selectionList.get(0);
                    contextWrapper.dismiss();
                    editTitleForSelection(target);
                    break;
                case R.id.action_edit_tags:
                    editTagsForSelection();
                    break;
            }
        }
    };

    public void deselect() {
        selectionList.clear();

        for (Item item : items) {
            if (item.selected) item.selected = false;
        }
    }

    public void addToSelectionTotal(Item item) {
        if (item.price != 0) {
            if (item.qty == 0) {
                selectionTotal = selectionTotal.add(new BigDecimal(item.price).movePointLeft(2));
            }
            else {
                selectionTotal = selectionTotal.add(new BigDecimal(item.price).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
            }
        }
        else {
            if (item.qty == 0) {
                selectionTotal = selectionTotal.add(new BigDecimal(item.estimatedPrice).movePointLeft(2));
            }
            else {
                selectionTotal = selectionTotal.add(new BigDecimal(item.estimatedPrice).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
            }
        }
    }

    public void subtractFromSelectionTotal(Item item) {
        if (item.price != 0) {
            if (item.qty == 0) {
                selectionTotal = selectionTotal.subtract(new BigDecimal(item.price).movePointLeft(2));
            }
            else {
                selectionTotal = selectionTotal.subtract(new BigDecimal(item.price).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
            }
        }
        else {
            if (item.qty == 0) {
                selectionTotal = selectionTotal.subtract(new BigDecimal(item.estimatedPrice).movePointLeft(2));
            }
            else {
                selectionTotal = selectionTotal.subtract(new BigDecimal(item.estimatedPrice).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
            }
        }
    }

    public void resetSelectionTotal() {
        selectionTotal = new BigDecimal(0);
    }

    public void flashScreen() {
        final ViewGroup Root = (ViewGroup) activity.getWindow().getDecorView();
        final View Flash = new View(activity);
        Flash.setBackgroundColor(getResources().getColor(android.R.color.white));
        Flash.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Flash.setAlpha(0);
        Flash.setClickable(true);
        Root.addView(Flash);
        Flash.animate()
                .alpha(1)
                .setDuration(50)
                .setInterpolator(new AccelerateInterpolator(2))
                .setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (activity == null) return;
                        Flash.animate()
                                .alpha(0)
                                .setDuration(250)
                                .setInterpolator(new AccelerateInterpolator(1))
                                .setListener(new  AnimatorListenerAdapter() {
                                    public void onAnimationEnd(Animator a) {
                                        if (activity == null) return;
                                        Root.removeView(Flash);
                                    }
                                });
                    }
                });
    }

    private Handler handler = new Handler();

    public void cutSelection() {
        copySelection();
        deleteSelection(true);
    }

    public void copySelection() {
        flashScreen();

        // Item instances must be unique for CollectionView
        ArrayList<Item> selection = new ArrayList<Item>(selectionList.size());
        for (Item item : selectionList) {
            selection.add(new Item(item));
        }
    	activity.appendToClipboard(selection);
    }

    public void deleteSelection(boolean cut) {

        handler.post(new Runnable() {
            @Override
            public void run() {
                collection.refreshViews();
            }
        });

        collection.setAnimationsEnabled(false);
        collection.setAnimationsEnabled(true);

        controller.requestBeginTransaction();

        if (!cut) {
            confirmingItems = new ArrayList<Item>();
            confirmingPositions = new ArrayList<Integer>();

            for (Item item : selectionList) {
                // The selection should be sorted by index
                item.selected = false;
                int targetIndex = 0;
                int sourceIndex = items.indexOf(item);
                if (confirmingPositions.size() > 0) {
                    for (Integer i : confirmingPositions) {
                        if (sourceIndex < i) break;
                        targetIndex++;
                    }
                }
                confirmingPositions.add(targetIndex, sourceIndex);
                confirmingItems.add(targetIndex, item);
            }
        }

        for (Item item : selectionList) {

            controller.getSectionAtIndex(0).removeObject(item);
            items.remove(item);

            activity.addToItemCount(-1);

            if (item.price == 0) {
                activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
            }
            else {
                activity.addToEstimatedTotal(item.qty, -item.price);
            }

            if (item.crossedOff) {
                if (item.price == 0) {
                    activity.addToTotal(item.qty, -item.estimatedPrice);
                }
                else {
                    activity.addToTotal(item.qty, -item.price);
                }
                activity.addToCrossedOffCount(-1);
            }
        }


        // The collection's view layout will call playAnimation in the view's visible order on the screen
        // The second phase of the completed transaction will only be called after the last animation finishes
        if (!cut)
            collection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                @Override
                public void playAnimation(View view, Object object, int viewType) {
                    view.animate().x(view.getWidth()).alpha(0f);
                }

                @Override
                public void resetState(View view, Object object, int viewType) {
                    view.setAlpha(1f);
                }
            });
        else
            collection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                @Override
                public void playAnimation(View view, Object object, int viewType) {
                    view.animate().alpha(0f);
                }

                @Override
                public void resetState(View view, Object object, int viewType) {
                    view.setAlpha(1f);
                }
            });
        collection.setMoveWithLayersEnabled(false);
        collection.setDeleteAnimationDuration(300);
        collection.setDeleteAnimationStride(50);
        collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
        collection.setAnchorCondition(null);

        selectionList = new ArrayList<Item>();

        controller.requestCompleteTransaction();

        if (!cut) {
            swipeConfirmation = false;
            showDeleteConfirmator(true);
        }

    }
    public void editTitleForSelection(Item item) {
        editor.startEditingWithKeyboard(item, EditorModeTitle, true);
    }

    class ProxyItem extends Item {

        ArrayList<Tag> allTags = new ArrayList<Tag>();
        ArrayList<Integer> tagCounts = new ArrayList<Integer>();

        ArrayList<Item> targets;

        int freeTagSlots = 4;

        public void addTagToIndex(Tag tag, int index) {
            super.addTagToIndex(tag, index);
            // This tag is no longer uncommon
            allTags.remove(tag);
            freeTagSlots = 4;
            for (Item item : targets) {
                item.addTag(tag);
                if (freeTagSlots > 4 - item.tags.size()) {
                    freeTagSlots = 4 - item.tags.size();
                }
            }
            collection.refreshViews();
        }

        public void removeTagAtIndex(int index) {
            Tag tag = tags.get(index);
            super.removeTagAtIndex(index);
            freeTagSlots = 4;
            // A color of -1 indicates uncommon tags
            if (tag.color == -1) {
                for (Item item : targets) {
                    item.tags.removeAll(allTags);
                    if (freeTagSlots > 4 - item.tags.size()) {
                        freeTagSlots = 4 - item.tags.size();
                    }
                }
            }
            else {
                for (Item item : targets) {
                    item.tags.remove(tag);
                    if (freeTagSlots > 4 - item.tags.size()) {
                        freeTagSlots = 4 - item.tags.size();
                    }
                }
            }
            collection.refreshViews();
        }

        public boolean hasUncommonTags() {
            return allTags.size() > 0;
        }

        public boolean canHaveUncommonTags() {
            return true;
        }

        public boolean canAddTags() {
            return freeTagSlots > 0;
        }

    }

    protected ProxyItem createProxyItem(Context context) {
        ProxyItem proxyItem = new ProxyItem();

        ArrayList<Tag> allTags = proxyItem.allTags;
        ArrayList<Integer> tagCounts = proxyItem.tagCounts;

        int index;

        proxyItem.freeTagSlots = 4;
        for (Item item : selectionList) {
            for (Tag tag : item.tags) {
                if ((index = allTags.indexOf(tag)) == -1) {
                    allTags.add(tag);
                    tagCounts.add(1);
                }
                else {
                    tagCounts.set(index, tagCounts.get(index) + 1);
                }
            }
            if (proxyItem.freeTagSlots > 4 - item.tags.size()) {
                proxyItem.freeTagSlots = 4 - item.tags.size();
            }
        }

        //Determine which tags are uncommon
        for (int i = 0, tagCountsSize = tagCounts.size(); i < tagCountsSize; i++) {
            int count = tagCounts.get(i);
            if (count == selectionList.size()) {
                proxyItem.tags.add(allTags.remove(i));
                tagCounts.remove(i);
                i--;
                tagCountsSize--;
            }
        }

        //If there are tags still left in the allTags array it means that there are uncommon tags
        if (allTags.size() != 0) {
            Tag uncommonTag = new Tag();
            uncommonTag.name = context.getResources().getString(R.string.UncommonTags);
            uncommonTag.color = -1;
            proxyItem.tags.add(0, uncommonTag);
        }

        proxyItem.targets = selectionList;

        return proxyItem;
    }

    private LegacyActionBar.ContextBarWrapper tagWrapper;
    private TagExpander contextExpander;
    private LegacyActionBar.ContextBarListener tagListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
            delayHandler.retain();
        }

        @Override
        public void onContextBarDismissed() {
            if (currentExpander == contextExpander) {
                if (contextExpander != null) {
                    contextExpander.compact();
                }
            }

            if (contextWrapper != null) {
                contextWrapper.dismissInstantly();
            }

            delayHandler.release(ReorderDelay);
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {

        }
    };

    public void editTagsForSelection() {

        tagWrapper = ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).createContextMode(tagListener);
        tagWrapper.setCustomView(new LegacyActionBar.CustomViewProvider() {
            boolean initial = true;

            @Override
            public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
                TagView proxy = new TagView(container.getContext());
                Item proxyItem = createProxyItem(container.getContext());
                proxy.setTags(proxyItem.tags);
//                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                currentExpander = TagExpander.fromViewInContainerWithProxyTarget(proxy, container, proxyItem);
                currentExpander.setOnTagDeletedListener(ItemCollectionFragment.this);
                contextExpander = currentExpander;
                if (!activity.isSidebar()) currentExpander.setInvertedModeEnabled(true);
                currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                    @Override
                    public void onClose() {
                        currentExpander = null;
                    }
                });
                if (initial) {
                    currentExpander.expand();
                    initial = false;
                }
                else {
                    currentExpander.expandAnimated(false);
                    final View Container = container;
                    container.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            //noinspection deprecation
                            Container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                            currentExpander.restoreStaticContext();
                        }
                    });
                }
                return null;
            }

            @Override
            public void onDestroyCustomView(View customView) {
            }
        });

        if (activity.isSidebar()) {
            tagWrapper.setBackgroundColor(getResources().getColor(R.color.DashboardBackground));
            tagWrapper.setDoneResource(R.drawable.ic_action_done_dark);
            tagWrapper.setTextColor(getResources().getColor(R.color.DashboardText));
            tagWrapper.setSeparatorVisible(false);
            tagWrapper.setInnerSeparatorOpacity(0.10f);
        }
        else {
            tagWrapper.setBackgroundColor(getResources().getColor(R.color.ActionBar));
            tagWrapper.setDoneResource(R.drawable.ic_action_done);
            tagWrapper.setTextColor(getResources().getColor(android.R.color.white));
            tagWrapper.setSeparatorVisible(false);
            tagWrapper.setInnerSeparatorOpacity(0.66f);
            tagWrapper.setRippleHighlightColors(LegacyRippleDrawable.DefaultLightPressedColor, LegacyRippleDrawable.DefaultLightRippleColor);
        }
//        tagWrapper.setSeparatorOpacity(0.25f);
        tagWrapper.setBackButtonPosition(LegacyActionBarView.BackButtonPositionRight);

        tagWrapper.start();

    }

    @Override
    public void onTagDeleted(Tag tag) {
        activity.onTagDeleted(tag);

        if (collection != null)
            collection.refreshViews();
    }


    //************************** HISTORY BASED SUGGESTIONS ************************


    static class Suggestion {
        String name;
        long price;
        String measurement;
        ArrayList<Tag> tags = new ArrayList<Tag>();

        public String toString() {
            return name;
        }

        public static Suggestion make(Cursor cursor) {
            Suggestion suggestion = new Suggestion();
            suggestion.name = cursor.getString(0);
            suggestion.price = cursor.getLong(1);
            suggestion.measurement = cursor.getString(2);

            return suggestion;
        }

        public Item toItem() {
            Item item = new Item();
            item.name = name;
            item.estimatedPrice = price;
            item.unitOfMeasurement = measurement;
            item.tags = tags;
            item.flags = SetTitle;
            return item;
        }

        public CharSequence toMenuString(Context context) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(name);
            builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.implicit_text_colors)),
                    name.length(), name.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            builder.append(" - ").append(ReceiptActivity.currentTruncatedLocale).append(ReceiptActivity.longToDecimalString(price));
            return builder;
        }

    }

    class FindSuggestionsAsyncTask extends AsyncTask<String, Void, Suggestion[]> {
        boolean showDropdownWhenDone;

        public FindSuggestionsAsyncTask(boolean showDropdownWhenDone) {
            this.showDropdownWhenDone = showDropdownWhenDone;
        }

        @Override
        protected Suggestion[] doInBackground(String... arg0) {
            synchronized (Receipt.DatabaseLock) {
                SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();

                if (isCancelled()) {
                    db.close();
                    return null;
                }

                String text = arg0[0].toLowerCase(Locale.getDefault()) + "%" ;
                Cursor query = db.query(Receipt.DBItemsTable,		 																					//FROM
                        new String[] {Receipt.DBNameKey, Receipt.DBPriceKey, Receipt.DBUnitOfMeasurementKey, "_id"}, 									//SELECT
                        "lower(" + Receipt.DBNameKey + ") like ?", 																						//WHERE
                        new String[]{text}, 																											//ARGS
                        Receipt.DBNameKey, null, "count(" + Receipt.DBNameKey + ") desc",																//GROUPBY, HAVING, ORDERBY
                        "5");																															//LIMIT

                Suggestion result[] = null;

                if (isCancelled()) {
                    query.close();
                    db.close();
                    return null;
                }

                int index = 0;
                if (query.getCount() != 0) {

                    result = new Suggestion[query.getCount()];

                    while (query.moveToNext()) {

                        if (isCancelled()) {
                            query.close();
                            db.close();
                            return null;
                        }

                        result[index] = Suggestion.make(query);

                        int uid = query.getInt(3);
                        Cursor tagConnections = db.query(Receipt.DBTagConnectionsTable,
                                new String[] {Receipt.DBTagConnectionUIDKey},
                                Receipt.DBItemConnectionUIDKey + " = " + uid,
                                null,
                                null, null, null);

                        while (tagConnections.moveToNext()) {
                            Tag tag = TagStorage.findTagWithUID(tagConnections.getInt(0));
                            if (tag != null) result[index].tags.add(tag);
                        }

                        index++;
                    }
                }

                query.close();
                db.close();
                return result;
            }
        }

        protected void onCancelled(Suggestion[] result) {
            if (DEBUG_SUGGESTIONS) Log.d(TAG, "SuggestionFinder cancelled!");
        }

        @Override
        protected void onPostExecute(Suggestion[] results) {
            preparePopupMenu(results, showDropdownWhenDone);
        }

    }

    @SuppressWarnings("UnusedDeclaration")
    class EstimatedPriceFinder extends AsyncTask<Void, Void, Suggestion> {

        Item item;

        EstimatedPriceFinder(Item item) {
            this.item = item;
        }

        @Override
        protected Suggestion doInBackground(Void... arg0) {
            synchronized (Receipt.DatabaseLock) {
                SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();

                if (isCancelled()) {
                    db.close();
                    return null;
                }

                String text = item.name.toLowerCase(Locale.getDefault());
                Cursor query = db.query(Receipt.DBItemsTable,		 																					//FROM
                        new String[] {Receipt.DBNameKey, Receipt.DBPriceKey, Receipt.DBUnitOfMeasurementKey, "_id"}, 	            					//SELECT
                        "lower(" + Receipt.DBNameKey + ") like ?", 																						//WHERE
                        new String[]{text}, 																											//ARGS
                        Receipt.DBNameKey, null, "count(" + Receipt.DBNameKey + ") desc",																//GROUPBY, HAVING, ORDERBY
                        null);

                if (isCancelled()) {
                    query.close();
                    db.close();
                    return null;
                }

                Suggestion suggestion = null;

                if (query.getCount() != 0) {

                    query.moveToFirst();

                    if (isCancelled()) {
                        query.close();
                        db.close();
                        return null;
                    }

                    suggestion = Suggestion.make(query);
                }

                query.close();
                db.close();
                return suggestion;
            }
        }

        @Override
        protected void onPostExecute(Suggestion result) {

            // Only do this of the item's name hasn't changed in the meantime
            if (result != null) {
                if (TextUtils.equals(item.name, result.name)) {
                    if (item.price == 0) {
                        activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
                        activity.addToEstimatedTotal(item.qty, result.price);
                        if (item.crossedOff) {
                            activity.addToTotal(item.qty, -item.estimatedPrice);
                            activity.addToTotal(item.qty, result.price);
                        }
                    }

                    item.estimatedPrice = result.price;
                    item.unitOfMeasurement = result.measurement;
                }
            }


            if (editor.retainedView != null && editor.target != null)
                controller.configureView(editor.retainedView, editor.target, 0);
//            collection.refreshViews();

        }

    }

    private FindSuggestionsAsyncTask suggestionsTask;

    public void preparePopupMenu(Suggestion[] suggestions, boolean showDropDown) {

        if (activity == null || editor.target == null) return;
        if (editor.mode != EditorModeAll && editor.mode != EditorModeTitle) return;

        if (DEBUG_SUGGESTIONS) {
            if (suggestions != null) Log.d(TAG, "The suggestion finder has returned with " + suggestions.length + " results.");
            else Log.d(TAG, "The suggestion finder has returned with no results.");
        }

        //noinspection unchecked
        final ArrayAdapter<Suggestion> suggestionsAdapter = new ArrayAdapter<Suggestion>(activity, R.layout.layout_suggestion, R.id.Suggestion);
        if (suggestions != null) {
            suggestionsAdapter.clear();
            for (Suggestion suggestion : suggestions) {
                int sizePre = suggestionsAdapter.getCount();
                suggestionsAdapter.add(suggestion);
                int sizePost = suggestionsAdapter.getCount();

                if (sizePost == sizePre) {
                    Log.e(TAG, "Adapter add had no effect, retrying...");
                    suggestionsAdapter.add(suggestion);
                }
                if (DEBUG_SUGGESTIONS) {
                    Log.d(TAG, "Added " + suggestion.name + " to adapter.");
                    Log.d(TAG, "Last adapter item is " + suggestionsAdapter.getItem(suggestionsAdapter.getCount() - 1));
                }
            }
//            suggestionsAdapter.addAll(suggestions);
            if (DEBUG_SUGGESTIONS) Log.d(TAG, "Adding " + suggestions.length + " items to adapter.");
        }
        if (editor.titleEditor.getAdapter() != suggestionsAdapter) editor.titleEditor.setAdapter(suggestionsAdapter);
        if (DEBUG_SUGGESTIONS) Log.d(TAG, "Adapter has " + editor.titleEditor.getAdapter().getCount() + " items in it.\n" +
                                            "SuggestionsAdapter has " + suggestionsAdapter.getCount() + " items in it.");
        loopForSuggestion(editor.titleEditor.getText());
        if (showDropDown) editor.titleEditor.showDropDown();

//        collection.refreshViews();
        controller.configureView(editor.retainedView, editor.target, 0);

    }

    public boolean loopForSuggestion(CharSequence s) {
        if (editor.titleEditor.getAdapter() != null) {
            @SuppressWarnings("unchecked")
            ArrayAdapter<Suggestion> suggestionsAdapter = (ArrayAdapter<Suggestion>) editor.titleEditor.getAdapter();
            int adapterSize = suggestionsAdapter.getCount();
            if (DEBUG_SUGGESTIONS) Log.d(TAG, "Adapter has " + adapterSize + " items in it.");
            for (int i = 0; i < adapterSize; i++) {
                Suggestion suggestion = suggestionsAdapter.getItem(i);
                if (DEBUG_SUGGESTIONS) Log.d(TAG, "Matching " + s + " with" + suggestion.name);
                if (s.toString().trim().equalsIgnoreCase(suggestion.name)) {
                    if (editor.mode == EditorModeAll) {
                        editor.qtyEditor.setHint(suggestion.measurement);

                        Item item = editor.target;

                        item.unitOfMeasurement = suggestion.measurement;

                        // For new items, estimated price is set and added on completion

                        if (suggestion.price > 0) {
                            editor.priceEditor.setHint(ReceiptActivity.longToFormattedString(suggestion.price, Receipt.hintLightSpan()));
                        }
                        else {
                            editor.priceEditor.setHint(getString(R.string.PriceEditHint));
                        }
                        item.estimatedPrice = suggestion.price;

                        if (item.implicitTags || item.tags.size() == 0) {
                            item.tags = suggestion.tags;
                            editor.tagPlaceholder.setTags(item.tags);
                            item.implicitTags = true;
                        }
                    }
                    else {

                        Item item = editor.target;

                        item.unitOfMeasurement = suggestion.measurement;
                        if (item.price == 0) {
                            activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
                            activity.addToEstimatedTotal(item.qty, suggestion.price);
                            if (item.crossedOff) {
                                if (DEBUG_RENAME) Log.d(TAG, "Activity total: " + activity.getTotal());
                                activity.addToTotal(item.qty, -item.estimatedPrice);
                                if (DEBUG_RENAME) Log.d(TAG, "Activity total (post removal): " + activity.getTotal());
                                activity.addToTotal(item.qty, suggestion.price);
                                if (DEBUG_RENAME) Log.d(TAG, "Activity total (post adding): " + activity.getTotal());
                            }
                        }

                        editor.qtyEditor.setHint(suggestion.measurement);
                        item.unitOfMeasurement = suggestion.measurement;
                        if (suggestion.price > 0) {
                            editor.priceEditor.setHint(ReceiptActivity.longToFormattedString(suggestion.price, Receipt.hintLightSpan()));
                        }
                        else {
                            editor.priceEditor.setHint(getString(R.string.PriceEditHint));
                        }
                        item.estimatedPrice = suggestion.price;

                        if (item.implicitTags || item.tags.size() == 0) {
                            item.tags = suggestion.tags;
                            editor.tagPlaceholder.setTags(item.tags);
                            item.implicitTags = true;
                        }
                    }

                    return true;
                }
            }
        }
        else if (DEBUG_SUGGESTIONS) Log.d(TAG, "Title Editor has no adapter set!");
        if (DEBUG_SUGGESTIONS) Log.d(TAG, "Loop for suggestions found nothing.");
        return false;

    }

    protected ArrayList<Item> findDuplicates(String s) {
        ArrayList<Item> duplicates = null;
        for (Item item : items) {
            if (item.name.equalsIgnoreCase(s.trim())) {
                if (duplicates == null) duplicates = new ArrayList<Item>();

                duplicates.add(item);
            }
        }

        return duplicates;
    }

    public void showSuggestionsForView(TextView view, boolean withDropdown) {
        if (DEBUG_SUGGESTIONS) Log.d(TAG, "About to fire a suggestion finder.");

        if (view.getText().toString().isEmpty()) return;
        if (suggestionsTask != null) suggestionsTask.cancel(false);
        suggestionsTask = new FindSuggestionsAsyncTask(withDropdown);
        suggestionsTask.execute(view.getText().toString().trim());
        if (DEBUG_SUGGESTIONS) Log.d(TAG, "Fired off a suggestion finder.");

    }

    static class CommonlyUsedItem {
        Suggestion suggestion;
        int usageCount;

        CommonlyUsedItem make(Cursor cursor) {
            CommonlyUsedItem item = new CommonlyUsedItem();
            item.suggestion = Suggestion.make(cursor);
            item.usageCount = cursor.getInt(4);
            return item;
        }
    }

    static ArrayList<CommonlyUsedItem> commonlyUsedItems = new ArrayList<CommonlyUsedItem>();

    static class FindMostUsedItemsAsyncTask extends AsyncTask<Receipt, Void, CommonlyUsedItem[]> {

        @Override
        protected CommonlyUsedItem[] doInBackground(Receipt ... applications) {
            synchronized (Receipt.DatabaseLock) {
                SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();

                if (isCancelled()) {
                    db.close();
                    return null;
                }

                Cursor query = db.query(Receipt.DBItemsTable,		 																					//FROM
                        new String[] {Receipt.DBNameKey, Receipt.DBPriceKey, Receipt.DBUnitOfMeasurementKey, "_id", "count(*)"},		        		//SELECT
                        null, 																						                                    //WHERE
                        null, 																											                //ARGS
                        Receipt.DBNameKey, null, "count(" + Receipt.DBNameKey + ") desc",																//GROUPBY, HAVING, ORDERBY
                        "6");																															//LIMIT

                CommonlyUsedItem result[] = null;

                if (isCancelled()) {
                    query.close();
                    db.close();
                    return null;
                }

                int index = 0;
                if (query.getCount() != 0) {

                    result = new CommonlyUsedItem[query.getCount()];

                    while (query.moveToNext()) {

                        if (isCancelled()) {
                            query.close();
                            db.close();
                            return null;
                        }

                        result[index] = new CommonlyUsedItem();

                        result[index].suggestion = Suggestion.make(query);


                        int uid = query.getInt(3);
                        Cursor tagConnections = db.query(Receipt.DBTagConnectionsTable,
                                new String[] {Receipt.DBTagConnectionUIDKey},
                                Receipt.DBItemConnectionUIDKey + " = " + uid,
                                null,
                                null, null, null);

                        while (tagConnections.moveToNext()) {
                            Tag tag = TagStorage.findTagWithUID(tagConnections.getInt(0));
                            if (tag != null) result[index].suggestion.tags.add(tag);
                        }

                        result[index].usageCount = query.getInt(4);

                        index++;
                    }
                }

                query.close();
                db.close();
                return result;
            }
        }

        @Override
        protected void onPostExecute(CommonlyUsedItem[] results) {
            if (results != null) {
                commonlyUsedItems = new ArrayList<CommonlyUsedItem>(results.length);
                Collections.addAll(commonlyUsedItems, results);
            }
        }

    }

    static void findMostUsedItems(Receipt application) {
        new FindMostUsedItemsAsyncTask().execute(application);
    }


    protected void removeItemFromActivity(Item item) {
        activity.addToItemCount(-1);

        if (item.price == 0)
            activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
        else
            activity.addToEstimatedTotal(item.qty, -item.price);

        if (item.crossedOff) {
            if (item.price == 0)
                activity.addToTotal(item.qty, -item.estimatedPrice);
            else
                activity.addToTotal(item.qty, -item.price);
            activity.addToCrossedOffCount(-1);
        }
    }

    protected void addItemToActivity(Item item) {
        activity.addToItemCount(1);

        if (item.price == 0)
            activity.addToEstimatedTotal(item.qty, item.estimatedPrice);
        else
            activity.addToEstimatedTotal(item.qty, item.price);

        if (item.crossedOff) {
            if (item.price == 0)
                activity.addToTotal(item.qty, item.estimatedPrice);
            else
                activity.addToTotal(item.qty, item.price);
            activity.addToCrossedOffCount(1);
        }
    }

    public void onLocaleChanged() {
        collection.refreshViews();
    }


    /****************** LIST DISCARD CONFIRMATION *********************/

    public void handleDiscard() {
        if (deleteWrapper == null) {
            deleteWrapper = activity.getLegacyActionBar().createActionConfirmationContextMode(getString(R.string.DiscardConfirmation), getString(R.string.DiscardOK), R.drawable.ic_action_delete, deleteListener);

            deleteWrapper.start();
        }
    }

    private LegacyActionBar.ContextBarWrapper deleteWrapper;
    private LegacyActionBar.ContextBarListener deleteListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {}

        @Override
        public void onContextBarDismissed() {
            deleteWrapper = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() == R.id.ConfirmOK) {
                activity.handleDiscard();
            }
            deleteWrapper.postDismiss();
        }
    };

    /****************** CONFIRMATOR     *******************/

    private void showDeleteConfirmator(boolean animated) {

        ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView();

        if (!confirmatorUp) {
            confirmatorUp = true;
            delayHandler.retain();
        }

        confirmator = activity.getLayoutInflater().inflate(R.layout.delete_overlay, root, false);

        if (swipeConfirmation) {
            ((TextView) confirmator.findViewById(R.id.DeleteTitle)).setText(
                    String.format(getResources().getString(R.string.DeleteItemOverlayTitle), confirmingItem.name));
        }
        else {
            if (confirmingItems.size() > 1) {
                ((TextView) confirmator.findViewById(R.id.DeleteTitle)).setText(getString(R.string.DeleteItemOverlayTitleMultiple, confirmingItems.size()));
            }
            else {
                ((TextView) confirmator.findViewById(R.id.DeleteTitle)).setText(
                        String.format(getResources().getString(R.string.DeleteItemOverlayTitle), confirmingItems.get(0).name));
            }
        }

        final View undo = confirmator.findViewById(R.id.Undo);
        LegacyRippleDrawable background = new LegacyRippleDrawable(activity, LegacyRippleDrawable.ShapeRoundRect);
        background.setColors(0x00FFFFFF, 0x22FFFFFF);
        background.setRippleColor(0x40FFFFFF);
        undo.setBackground(background);

        root.addView(confirmator);
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmator.setOnTouchListener(null);
                undo.setOnClickListener(null);
                undo.setClickable(false);
                undoDelete();
            }
        });

        confirmator.animate().setDuration(100);

        if (animated) {
            confirmator.setAlpha(0);
            confirmator.animate()
                    .alpha(1).withLayer();
        }

        confirmator.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getY() < ((ViewGroup) confirmator).getChildAt(0).getTop() - metrics.density * 16) {
                        confirmator.setOnTouchListener(null);
                        undo.setOnClickListener(null);
                        undo.setClickable(false);
                        finalizeDelete();
                        return false;
                    }
                }
                return true;
            }
        });

        View scrapFAB = activity.findViewById(R.id.ScrapFAB);
        if (animated) {
            scrapFAB.animate().translationY(-getResources().getDisplayMetrics().density * 72).setInterpolator(new Utils.FrictionInterpolator(1.5f)).setDuration(400);
        }
        else {
            scrapFAB.setTranslationY(- getResources().getDisplayMetrics().density * 72);
        }

    }

    public void undoDelete() {
        if (swipeConfirmation) {
            addItemToActivity(confirmingItem);
        }
        else {
            for (Item item : confirmingItems) {
                addItemToActivity(item);
            }
        }

        final ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView();

        if (confirmatorUp) {
            final View Confirmator = confirmator;
            confirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    if (activity == null) return;
                    root.removeView(Confirmator);
                }
            });
            confirmator = null;
            confirmatorUp = false;
            delayHandler.release(ReorderDelay);
        }

        if (swipeConfirmation) {
            items.add(confirmingPosition, confirmingItem);
        }
        else {
            for (int i = 0; i < confirmingItems.size(); i++) {
                items.add(confirmingPositions.get(i), confirmingItems.get(i));
            }
            confirmingDirection = 1;
        }
        controller.requestBeginTransaction();

        controller.getSectionAtIndex(0).clear();
        controller.getSectionAtIndex(0).addAllObjects(items);

        collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
        collection.setAnimationsEnabled(true);
        final Item ConfirmingItem = confirmingItem;
        collection.setMoveWithLayersEnabled(true);
        collection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                view.setX(collection.getWidth() * confirmingDirection);
                view.setAlpha(0f);
                view.animate().alpha(1f);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {}
        });
        if (swipeConfirmation) {
            collection.setAnchorCondition(new CollectionView.AnchorInspector() {
                @Override
                public boolean isAnchor(Object object, int viewType) {
                    return object == ConfirmingItem;
                }
            });
        }
        else {
            collection.setAnchorCondition(null);
        }
        controller.requestCompleteTransaction();

        if (!swipeConfirmation) {
            confirmingItems = null;
            confirmingPositions = null;
        }

        View scrapFAB = activity.findViewById(R.id.ScrapFAB);
        scrapFAB.animate().translationY(0);
    }

    public void finalizeDelete() {
        finalizeDelete(true);
    }

    public void finalizeDelete(boolean animated) {
        final ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView();

        if (confirmatorUp) {
            final View Confirmator = confirmator;
            if (animated)
                confirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (activity == null) return;
                        root.removeView(Confirmator);
                    }
                });
            else
                root.removeView(confirmator);
            confirmator = null;
            confirmatorUp = false;
            delayHandler.release(ReorderDelay);
        }

        View scrapFAB = activity.findViewById(R.id.ScrapFAB);
        if (animated) {
            scrapFAB.animate().translationY(0);
        }
        else {
            scrapFAB.setTranslationY(0);
        }

        confirmingItem = null;

        if (!swipeConfirmation) {
            confirmingItems = null;
            confirmingPositions = null;
        }
    }




    /****************** ORDER CONFIRMATOR *******************/

    public void order() {
        if (!PreferenceManager.getDefaultSharedPreferences(Receipt.getStaticContext()).getBoolean(SettingsFragment.ShakeToSortKey, true)) {
            return;
        }


        if (orderConfirmatorUp) {
            //There is already a pending reorder, ignore
            return;
        }

        if (editor.target != null || currentExpander != null) {
            //Prevent accidental shakes from messing up editing
            return;
        }

        finalizeChangesInstantly();

        unorderedItems = items;

        items = new ArrayList<Item>(unorderedItems);
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item item, Item item2) {
                if (item.crossedOff == item2.crossedOff) return item.name.compareTo(item2.name);
                if (item.crossedOff) return 1;
                else return -1;
            }
        });

        controller.requestBeginTransaction();

        controller.getSectionAtIndex(0).clear();
        controller.getSectionAtIndex(0).addAllObjects(items);

        collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        collection.setAnimationsEnabled(true);
        controller.requestCompleteTransaction();

        showOrderConfirmator(true);
    }

    private void showOrderConfirmator(boolean animated) {

        ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView();

        if (!orderConfirmatorUp) {
            orderConfirmatorUp = true;
            delayHandler.retain();
        }

        orderConfirmator = activity.getLayoutInflater().inflate(R.layout.delete_overlay, root, false);

        ((TextView) orderConfirmator.findViewById(R.id.DeleteTitle)).setText(getResources().getString(R.string.OrderOverlayTitle));

        final View undo = orderConfirmator.findViewById(R.id.Undo);
        LegacyRippleDrawable background = new LegacyRippleDrawable(activity, LegacyRippleDrawable.ShapeRoundRect);
        background.setColors(0x00FFFFFF, 0x22FFFFFF);
        background.setRippleColor(0x40FFFFFF);
        undo.setBackground(background);

        root.addView(orderConfirmator);
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                orderConfirmator.setOnTouchListener(null);
                undo.setOnClickListener(null);
                undo.setClickable(false);
                undoOrder();
            }
        });

        orderConfirmator.animate().setDuration(100);

        if (animated) {
            orderConfirmator.setAlpha(0);
            orderConfirmator.animate()
                    .alpha(1).withLayer();
        }

        orderConfirmator.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getY() < ((ViewGroup) orderConfirmator).getChildAt(0).getTop() - metrics.density * 16) {
                        orderConfirmator.setOnTouchListener(null);
                        undo.setOnClickListener(null);
                        undo.setClickable(false);
                        finalizeOrder();
                        return false;
                    }
                }
                return true;
            }
        });

        View scrapFAB = activity.findViewById(R.id.ScrapFAB);
        if (animated) {
            scrapFAB.animate().translationY(- getResources().getDisplayMetrics().density * 72).setInterpolator(new Utils.FrictionInterpolator(1.5f)).setDuration(400);
        }
        else {
            scrapFAB.setTranslationY(- getResources().getDisplayMetrics().density * 72);
        }

    }

    public void undoOrder() {
        if (unorderedItems != null) {
            items = unorderedItems;
        }
        final ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView();

        if (orderConfirmatorUp) {
            final View Confirmator = orderConfirmator;
            orderConfirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    if (activity == null) return;
                    root.removeView(Confirmator);
                }
            });
            orderConfirmator = null;
            orderConfirmatorUp = false;
            delayHandler.release(ReorderDelay);
        }

        controller.requestBeginTransaction();

        controller.getSectionAtIndex(0).clear();
        controller.getSectionAtIndex(0).addAllObjects(items);

        collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        collection.setAnimationsEnabled(true);
        controller.requestCompleteTransaction();

        View scrapFAB = activity.findViewById(R.id.ScrapFAB);
        scrapFAB.animate().translationY(0);

    }

    public void finalizeOrder() {
        finalizeOrder(true);
    }

    public void finalizeOrder(boolean animated) {
        final ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView();

        if (orderConfirmatorUp) {
            final View Confirmator = orderConfirmator;
            if (animated)
                orderConfirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (activity == null) return;
                        root.removeView(Confirmator);
                    }
                });
            else
                root.removeView(orderConfirmator);
            orderConfirmator = null;
            orderConfirmatorUp = false;
            delayHandler.release(ReorderDelay);
        }

        View scrapFAB = activity.findViewById(R.id.ScrapFAB);
        if (animated) {
            scrapFAB.animate().translationY(0);
        }
        else {
            scrapFAB.setTranslationY(0);
        }

        unorderedItems = null;
    }




    /****************** OVER SCROLLER *******************/

    protected void initOverScrollListener() {
        collection.setOnOverScrollListener(new CollectionView.OnOverScrollListener() {
            boolean started;
            int lastAmount;
            boolean canRelease;
            View newItemPlaceholder;

            @Override
            public void onOverScroll(CollectionView collectionView, int amount, int direction) {
                lastAmount = amount;
                if (!started) {
                    started = true;

                    newItemPlaceholder = LayoutInflater.from(getActivity()).inflate(R.layout.hint_pulldown_mini, (ViewGroup) collection.getParent(), false);
                    newItemPlaceholder.findViewById(R.id.PulldownPlus).setAlpha(0f);
                    newItemPlaceholder.findViewById(R.id.PulldownPlus).setRotation(-180);
                    ((ViewGroup) collection.getParent()).addView(newItemPlaceholder, 0);

                    collection.setOverScrollMode(View.OVER_SCROLL_NEVER);
                    delayHandler.retain();
                }

                collection.setTranslationY(amount / 2);
                newItemPlaceholder.setY(collection.getY() - newItemPlaceholder.getLayoutParams().height);

                if (amount > 96 * metrics.density && !canRelease) {
                    canRelease = true;
                    newItemPlaceholder.findViewById(R.id.PulldownArrow).animate().rotation(180).alpha(0f).setInterpolator(new Utils.FrictionInterpolator(1.33f));
                    newItemPlaceholder.findViewById(R.id.PulldownPlus).animate().rotation(0).alpha(1f).setInterpolator(new Utils.FrictionInterpolator(1.33f));
                }
                else if (canRelease && amount <= 96 * metrics.density) {
                    canRelease = false;
                    newItemPlaceholder.findViewById(R.id.PulldownArrow).animate().rotation(0).alpha(1f).setInterpolator(new Utils.FrictionInterpolator(1.33f));
                    newItemPlaceholder.findViewById(R.id.PulldownPlus).animate().rotation(-180).alpha(0f).setInterpolator(new Utils.FrictionInterpolator(1.33f));
                }
            }

            @Override
            public void onOverScrollStopped(CollectionView collectionView) {

                if (activity == null || !started) {
                    // Most likely, screen rotation has occured while the user was over scrolling
                    // Views have been detached from the window and can't be used anymore
                    return;
                }

                if (started) {
                    delayHandler.release(ReorderDelay);
                }

                started = false;
                canRelease = false;
                collection.animate().translationY(0f).setInterpolator(collection.getMoveInterpolator());

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f).setDuration(400);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (collection != null)
                            collection.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                    }
                });
                animator.start();

                if (lastAmount > 96 * metrics.density && activity.getState() == BackendFragment.StateOpenList) {
                    final View NewItemPlaceholder = newItemPlaceholder;
                    newItemPlaceholder.animate().cancel();
                    newItemPlaceholder.findViewById(R.id.PulldownArrow).animate().cancel();
                    newItemPlaceholder.findViewById(R.id.PulldownPlus).animate().cancel();
                    newItemPlaceholder.findViewById(R.id.PulldownArrow).animate().rotation(180).alpha(0f);
                    newItemPlaceholder.findViewById(R.id.PulldownPlus).animate().rotation(0).alpha(0f);

                    NewItemPlaceholder.animate().y(collection.getTop())
                            .setInterpolator(collection.getMoveInterpolator())
                            .setDuration(collection.getMoveAnimationDuration())
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    ((ViewGroup) NewItemPlaceholder.getParent()).removeView(NewItemPlaceholder);
                                }
                            })
                            .start();

                    newItemPlaceholder = null;

                    addNewItemToList();
                    collection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
                        @Override
                        public void playAnimation(View view, Object object, int viewType) {
                            view.setAlpha(0f);
                            view.animate().alpha(1f);
                        }

                        @Override
                        public void resetState(View view, Object object, int viewType) {

                        }
                    });
                }
                else {
                    if (newItemPlaceholder != null) {
                        final View NewItemPlaceholder = newItemPlaceholder;
                        newItemPlaceholder.findViewById(R.id.PulldownArrow).animate().rotation(0).alpha(0f);
                        newItemPlaceholder.findViewById(R.id.PulldownPlus).animate().rotation(-180).alpha(0f);

                        NewItemPlaceholder.animate().y(collection.getTop() - 48 * metrics.density)
                                .setInterpolator(collection.getMoveInterpolator())
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        ((ViewGroup) NewItemPlaceholder.getParent()).removeView(NewItemPlaceholder);
                                    }
                                });

                        newItemPlaceholder = null;
                    }
                }
            }
        });
    }

}
