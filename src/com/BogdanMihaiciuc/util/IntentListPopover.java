package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class IntentListPopover extends CollectionPopover {

    public interface IntentListPopoverDelegate {
        public void popoverWillLaunchActivityFromIntent(IntentListPopover popover, ResolveInfo activity, Intent intent);
        public void popoverDidLaunchActivityFromIntent(IntentListPopover popover, ResolveInfo activity, Intent intent);
    }

    final static int RowHeightDP = 48;
    final static int IconID = LegacyActionBarView.generateViewId();
    final static int LabelID = LegacyActionBarView.generateViewId();

    static class IntentApplication {
        String name;
        Drawable icon;

        ResolveInfo intentInfo;
    }

    private List<IntentApplication> applications;

    private Utils.DPTranslator pixels;

    private Intent intent;
    private CollectionViewController controller = new CollectionViewController() {
        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            Context context = container.getContext();
            FrameLayout layout = new FrameLayout(context);

            ImageView icon = new ImageView(context);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(context.getResources().getDimensionPixelSize(Utils.SecondaryKeyline), pixels.get(RowHeightDP));
            icon.setId(IconID);
            icon.setPadding(0, pixels.get(8), 0, pixels.get(8));
            layout.addView(icon, layoutParams);

            TextView label = new TextView(context);
            label.setId(LabelID);
            label.setTextSize(16);
            label.setTextColor(context.getResources().getColor(Utils.DashboardText));
            label.setGravity(Gravity.CENTER_VERTICAL);
            layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.leftMargin = context.getResources().getDimensionPixelSize(Utils.SecondaryKeyline);
            layout.addView(label, layoutParams);

            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pixels.get(RowHeightDP));
            layout.setBackground(new LegacyRippleDrawable(context));
            layout.setLayoutParams(params);

            layout.setClickable(true);

            layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    IntentApplication application = (IntentApplication) getCollectionView().getObjectForView(v);

                    dismiss();

                    intent.setComponent(new ComponentName(application.intentInfo.activityInfo.applicationInfo.packageName, application.intentInfo.activityInfo.name));
                    getCollectionView().getContext().startActivity(intent);
                }
            });

            return layout;
        }

        @Override
        public void configureView(View view, Object item, int viewType) {
            IntentApplication application = (IntentApplication) item;

            ((ImageView) view.findViewById(IconID)).setImageDrawable(application.icon);
            ((TextView) view.findViewById(LabelID)).setText(application.name);
        }
    };

    public IntentListPopover(AnchorProvider anchor, Intent intent) {
        super(anchor, null);

        this.intent = intent;
        super.setController(controller);
    }

    public void setIntent(Intent intent, Context context) {
        this.intent = intent;

        List intentResult = context.getPackageManager().queryIntentActivities(intent, 0);
        applications = new ArrayList(intentResult.size());

        controller.requestBeginNewDataSetTransaction();


        CollectionView.Section section = controller.addSection();

        for (Object result : intentResult) {

            IntentApplication application = new IntentApplication();
            application.intentInfo = (ResolveInfo) result;
            application.name = application.intentInfo.activityInfo.applicationInfo.loadLabel(context.getPackageManager()).toString();
            application.icon = application.intentInfo.activityInfo.applicationInfo.loadIcon(context.getPackageManager());

            applications.add(application);
            section.addObject(application);

        }

        controller.requestCompleteTransaction();
    }

    public IntentListPopover show(Activity activity) {

        List intentResult = activity.getPackageManager().queryIntentActivities(intent, 0);
        applications = new ArrayList(intentResult.size());

        pixels = new Utils.DPTranslator(activity.getResources().getDisplayMetrics().density);

        // TODO BackGround Thread

        CollectionView.Section section = controller.addSection();

        for (Object result : intentResult) {

            IntentApplication application = new IntentApplication();
            application.intentInfo = (ResolveInfo) result;
            application.name = application.intentInfo.activityInfo.applicationInfo.loadLabel(activity.getPackageManager()).toString();
            application.icon = application.intentInfo.activityInfo.applicationInfo.loadIcon(activity.getPackageManager());

            applications.add(application);
            section.addObject(application);

        }

        super.show(activity);

        return this;
    }

}
