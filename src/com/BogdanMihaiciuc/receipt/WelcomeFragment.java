package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.FloatingActionButton;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.Utils;

import java.util.ArrayList;

public class WelcomeFragment extends Fragment {

    final static boolean DEBUG = false;
    final static boolean DEBUG_FAKE_UPGRADE = false;
    final static boolean DEBUG_ALTERNATE_SETUP = true;
    final static boolean DEBUG_ALTERNATE_DELAY = false;

    final static String WelcomeKey = "com.BogdanMihaiciuc.receipt.WelcomeV2";

    final static int IntroStage = 0;
    final static int WelcomeStage = 1;
    final static int SetupStage = 2;

    static Paint textPaint = new Paint();

    private ViewGroup root;
    private ViewGroup contentRoot;
    private ViewGroup actionBarRoot;
    private ViewGroup content;

    private ViewGroup welcomeRoot;
    private View welcomeLogo;
    private View welcomeTitle;
    private View setupTitle;
    private ViewGroup setupContainer;
    private boolean welcome;

    private final Handler handler = new Handler();

    private final DisplayMetrics metrics = new DisplayMetrics();
    private float density;

    private boolean detachedSafely;

    private TimeInterpolator standardInterpolator = new DecelerateInterpolator(1.5f);

    private Activity activity;

    private ArrayList<Animator> pendingAnimators = new ArrayList<Animator>();

    private boolean cancelled;
    private int stage = IntroStage;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        welcome = prefs.getBoolean(WelcomeKey, true) || prefs.getBoolean("alwaysIntro", false);

        prefs.edit().putBoolean(WelcomeKey, false).apply();

        setRetainInstance(true);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (cancelled) {
            return;
        }
        if (!DEBUG_FAKE_UPGRADE && !welcome) return;

        activity = getActivity();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        density = metrics.density;

        root = (ViewGroup) activity.getWindow().getDecorView();
        contentRoot = ((Utils.HierarchyController) activity).getContentRoot();
        activity.findViewById(R.id.Backend).setVisibility(View.INVISIBLE);
        if (contentRoot.getId() == R.id.Backend) {
            contentRoot = (ViewGroup) root.getChildAt(1);
        }
        actionBarRoot = (ViewGroup) contentRoot.getChildAt(0);
        content = (ViewGroup) ((com.BogdanMihaiciuc.util.Utils.HierarchyController) activity).getContent();

        welcomeRoot = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.layout_upgrading, root, false);
        welcomeRoot.setBackgroundColor(getResources().getColor(R.color.DashboardBackground));
        welcomeLogo = welcomeRoot.findViewById(R.id.WelcomeLogo);

        welcomeTitle = welcomeRoot.findViewById(R.id.WelcomeTitle);
        ((TextView) welcomeTitle).setTypeface(Receipt.condensedLightTypeface());

        setupTitle = welcomeRoot.findViewById(R.id.SetupTitle);
        setupContainer = (ViewGroup) welcomeRoot.findViewById(R.id.SetupContainer);

        root.addView(welcomeRoot);
        contentRoot.setVisibility(View.INVISIBLE);
        welcomeTitle.setAlpha(0);
        welcomeLogo.setAlpha(0);

        textPaint.setTextSize(((TextView) welcomeTitle).getTextSize());
        textPaint.setTypeface(((TextView) welcomeTitle).getTypeface());
        float textLength = textPaint.measureText(((TextView) welcomeTitle).getText().toString());
        float textScale = 1f;
        if (textLength > metrics.widthPixels) {
            textScale = 0.9f * ((float) metrics.widthPixels) / textLength;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(((TextView) welcomeTitle).getText());
        builder.setSpan(new RelativeSizeSpan(textScale), 0, builder.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
        ((TextView) welcomeTitle).setText(builder);


        if (stage == WelcomeStage) {
            setupContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    setupContainer.removeOnLayoutChangeListener(this);

                    root.setBackgroundColor(0);
                    welcomeLogo.setAlpha(1);
                    welcomeTitle.setAlpha(1);

                    if (setupContainer.getWidth() > 360 * density) {
                        setupContainer.getLayoutParams().width = (int) (360 * density);
                        setupContainer.requestLayout();
                    }

                }
            });
        }

        if (stage == SetupStage) {
            setupContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    setupContainer.removeOnLayoutChangeListener(this);

                    root.setBackgroundColor(0);
                    welcomeRoot.bringToFront();

                    if (setupContainer.getWidth() > 360 * density) {
                        setupContainer.getLayoutParams().width = (int) (360 * density);
                        setupContainer.requestLayout();
                    }

                    welcomeLogo.setAlpha(1);
                    welcomeTitle.setVisibility(View.INVISIBLE);
                    welcomeTitle.setAlpha(0);

                    setupTitle.setVisibility(View.VISIBLE);
                    welcomeLogo.setY(setupContainer.getY() / 3f);

                    float y;
                    if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
                        y = setupContainer.getY() / 3f + welcomeLogo.getHeight() + 32 * density;
                    } else {
                        y = setupContainer.getY() / 3f + welcomeLogo.getHeight() + 64 * density;
                    }
                    setupTitle.setY(y);
                    y = root.getHeight() / 2 - setupContainer.getHeight() / 2;
                    y = y + y / 3f;
                    setupContainer.setY(y);
                    setupContainer.setVisibility(View.VISIBLE);
                    setupContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);


                    View separator = welcomeRoot.findViewById(R.id.SetupSeparator);
                    separator.setVisibility(View.VISIBLE);

                    View buttonStrip = welcomeRoot.findViewById(R.id.SetupButtonStrip);
                    buttonStrip.setVisibility(View.VISIBLE);

                    buttonStrip.findViewById(R.id.FinishSetup).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            welcomeRoot.findViewById(R.id.WelcomeClickBlocker).setOnTouchListener(BackendFragment.NullOnTouchListener);
                            cancelled = true;
                            goToApp();
                        }
                    });

                }
            });
        }
    }

    public void onStart() {
        super.onStart();
        if ((!DEBUG_FAKE_UPGRADE && !welcome) || cancelled) return;

        if (stage == IntroStage) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    welcome();
                }
            }, 1000);
        }
        else if (stage == WelcomeStage) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goToSetup();
                }
            }, 1500);
        }
    }

    public void onDetach() {

        if (!detachedSafely) {
            for (Animator a : pendingAnimators) {
                if (a instanceof ValueAnimator) {
                    ((ValueAnimator) a).removeAllUpdateListeners();
                }
                a.removeAllListeners();
                a.cancel();
            }
        }

        pendingAnimators.clear();
        handler.removeCallbacksAndMessages(null);

        root = null;
        contentRoot = null;
        actionBarRoot = null;
        content = null;

        welcomeRoot = null;
        welcomeLogo = null;
        welcomeTitle = null;
        setupTitle = null;
        setupContainer = null;

        activity = null;

        super.onDetach();
    }

    public void welcome() {

        stage = WelcomeStage;

        root.setBackgroundColor(0);

        welcomeLogo.setTranslationY(64 * density);
        welcomeLogo.animate()
                .translationY(0).alpha(1)
                .setDuration(400)
                .setInterpolator(standardInterpolator);

        final ValueAnimator welcomeAnimator = ValueAnimator.ofInt(1, welcomeTitle.getWidth());
        welcomeAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        welcomeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                welcomeTitle.getLayoutParams().width = (Integer) valueAnimator.getAnimatedValue();
                welcomeTitle.requestLayout();
                welcomeTitle.setAlpha(valueAnimator.getAnimatedFraction());
            }
        });
        welcomeTitle.requestFocus();
        welcomeTitle.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        welcomeAnimator.setStartDelay(800);
        welcomeAnimator.setDuration(800);
        welcomeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        goToSetup();
                    }
                }, 2000);
                stage = WelcomeStage;
                pendingAnimators.remove(welcomeAnimator);
            }
        });
        welcomeAnimator.start();
        pendingAnimators.add(welcomeAnimator);

        if (setupContainer.getWidth() > 360 * density) {
            setupContainer.getLayoutParams().width = (int) (360 * density);
            setupContainer.requestLayout();
        }

    }

    public void goToSetup() {

        if (true) {
            goToApp();
            return;
        }

        stage = SetupStage;

        welcomeLogo.animate()
                .y(setupContainer.getY()/3f).alpha(1)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        welcomeLogo.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        pendingAnimators.add(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        welcomeLogo.setLayerType(View.LAYER_TYPE_NONE, null);
                        pendingAnimators.remove(animation);
                    }
                }).start();

        welcomeTitle.animate()
                .alpha(0)
                .setDuration(300)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        welcomeTitle.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        pendingAnimators.add(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        welcomeTitle.setLayerType(View.LAYER_TYPE_NONE, null);
                        pendingAnimators.remove(animation);
                    }
                }).start();

        setupTitle.setVisibility(View.VISIBLE);
        setupTitle.setAlpha(0);
        float y;
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            y = setupContainer.getY()/3f + welcomeLogo.getHeight() + 32 * density;
        }
        else {
            y = setupContainer.getY()/3f + welcomeLogo.getHeight() + 64 * density;
        }

        float distance = welcomeLogo.getY() - setupContainer.getY()/3f;

        if (DEBUG_ALTERNATE_SETUP) {
            setupTitle.setY(DEBUG_ALTERNATE_DELAY ? y + 1.33f * distance : y + 1.33f * distance);
            setupTitle.animate().setStartDelay(DEBUG_ALTERNATE_DELAY ? 75 : 100);
        }
        else {
            setupTitle.setY(y - 64 * density);
            setupTitle.animate().setStartDelay(500);
        }

        setupTitle.animate()
                .alpha(1).y(y)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
//                .setInterpolator(standardInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        setupTitle.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        pendingAnimators.add(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setupTitle.setLayerType(View.LAYER_TYPE_NONE, null);
                        pendingAnimators.remove(animation);
                    }
                }).start();

        setupContainer.setVisibility(View.VISIBLE);
        setupContainer.setAlpha(0);
        y = root.getHeight()/2 - setupContainer.getHeight()/2;
        y = y + y/3f;

        if (DEBUG_ALTERNATE_SETUP) {
            setupContainer.setY(DEBUG_ALTERNATE_DELAY ? y + 1.66f * distance : y + 1.66f * distance);
            setupContainer.animate().setStartDelay(DEBUG_ALTERNATE_DELAY ? 150 : 150);
        }
        else {
            setupContainer.setY(y - 64 * density);
            setupContainer.animate().setStartDelay(550);
        }

        setupContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setupContainer.animate()
                .alpha(1).y(y)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        pendingAnimators.add(animation);
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        pendingAnimators.remove(animation);
                        setupContainer.findViewById(R.id.SetupBudgetEditor).requestFocus();
                    }
                }).start();

        View separator = welcomeRoot.findViewById(R.id.SetupSeparator);
        separator.setTranslationY(64 * density);
        separator.setVisibility(View.VISIBLE);
        separator.animate()
                .translationY(0)
                .setStartDelay(DEBUG_ALTERNATE_SETUP ? 350 : 650);

        View buttonStrip = welcomeRoot.findViewById(R.id.SetupButtonStrip);
        buttonStrip.setTranslationY(64 * density);
        buttonStrip.setVisibility(View.VISIBLE);
        buttonStrip.animate()
                .translationY(0)
                .setStartDelay(DEBUG_ALTERNATE_SETUP ? 350 : 650);

        buttonStrip.findViewById(R.id.FinishSetup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                welcomeRoot.findViewById(R.id.WelcomeClickBlocker).setOnTouchListener(BackendFragment.NullOnTouchListener);
                goToApp();
                cancelled = true;
            }
        });

    }

    public void goToApp() {
        if (getActivity() == null) return;

//        final SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

//        final SharedPreferences.Editor globalPrefsEditor = globalPrefs.edit();
        // Save budget related values
//        globalPrefsEditor.putString(BackendFragment.GlobalBudgetKey, ((TextView) setupContainer.findViewById(R.id.SetupBudgetEditor)).getText().toString());
//        globalPrefsEditor.putInt(BackendFragment.BudgetResetTypeKey, ((Spinner) setupContainer.findViewById(R.id.RepeatTypeSelector)).getSelectedItemPosition());
//        globalPrefsEditor.putInt(BackendFragment.BudgetResetSubtypeKey, ((Spinner) setupContainer.findViewById(R.id.RepeatSubtypeSelector)).getSelectedItemPosition());
//        globalPrefsEditor.putInt(BackendFragment.BudgetResetValueKey, ((Spinner) setupContainer.findViewById(R.id.RepeatDateSelector)).getSelectedItemPosition() + 1);
//        globalPrefsEditor.putString(BackendFragment.CarryoverBudgetKey, "0");
//        globalPrefsEditor.apply();
//        ((BackendFragment) activity.getFragmentManager().findFragmentByTag(ReceiptActivity.BackendFragmentKey)).loadGlobalBudget(globalPrefs);
//        ((BackendFragment) activity.getFragmentManager().findFragmentByTag(ReceiptActivity.BackendFragmentKey)).updateBalanceDisplay();
//        ((BackendFragment) activity.getFragmentManager().findFragmentByTag(ReceiptActivity.BackendFragmentKey)).refreshBudget();


        final View ActionBar = activity.findViewById(R.id.BackendActionBar);
        final View Backend = activity.findViewById(R.id.Backend);
        final View Logo = Backend.findViewById(LegacyActionBarView.LogoID);
        final ViewGroup root = this.root;
        final ViewGroup content = this.content;
        final ViewGroup contentRoot = this.contentRoot;
        final ViewGroup welcomeRoot = this.welcomeRoot;
        final View welcomeLogo = this.welcomeLogo;
        final View setupTitle = this.setupTitle;
        final View welcomeTitle = this.welcomeTitle;
        final Activity activity = this.activity;
        final FloatingActionButton BackendFAB = (FloatingActionButton) activity.findViewById(R.id.BackendFAB);

        final boolean Landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        detachedSafely = true;
        getActivity().getFragmentManager().beginTransaction().remove(this).commit();

        Backend.setVisibility(View.INVISIBLE);
        ActionBar.setVisibility(View.INVISIBLE);
        Logo.setVisibility(View.INVISIBLE);
        contentRoot.setVisibility(View.VISIBLE);
        Backend.bringToFront();
        contentRoot.bringToFront();

        BackendFAB.setEnabled(false);
        BackendFAB.hide(false);

//        final ActionBar bar = activity.getActionBar();
//        bar.setBackgroundDrawable(getResources().getDrawable(R.drawable.null_drawable));

        final Rect rct = new Rect();
        welcomeLogo.getGlobalVisibleRect(rct);

        final float X = 12 * density;
        if (DEBUG) Log.d("", "Actionbar top is " + actionBarRoot.getTop() + ", actionbar height is " + ActionBar.getHeight());
//        final Rect ActionBarRect = new Rect();
//        actionBarRoot.getGlobalVisibleRect(ActionBarRect);
        final float Y = ((View) activity.findViewById(R.id.ContentContainer).getParent()).getTop() + ActionBar.getHeight()/2 - welcomeLogo.getHeight()/2;

        welcomeRoot.removeView(welcomeLogo);
        root.addView(welcomeLogo);

        ValueAnimator.AnimatorUpdateListener listener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = (Float) valueAnimator.getAnimatedValue();
                float xPercent = fraction;
                float yPercent = (float) Math.sqrt(1 - Math.pow(fraction - 1, 2));
                welcomeLogo.setX(rct.left - (rct.left - X) * xPercent);
                welcomeLogo.setY(rct.top - (rct.top - Y) * yPercent);
            }
        };

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(listener);
        animator.setDuration(800)
                .setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pendingAnimators.add(animation);

            }

            @Override
            public void onAnimationStart(Animator animation) {
                pendingAnimators.remove(animation);
            }
        });
        animator.start();

        welcomeTitle.animate()
                .alpha(0)
                .setDuration(600)
                .setStartDelay(0)
                .setInterpolator(new Utils.FrictionInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        welcomeTitle.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        pendingAnimators.add(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        welcomeTitle.setLayerType(View.LAYER_TYPE_NONE, null);
                        pendingAnimators.remove(animation);
                    }
                }).start();

        setupTitle.animate()
                .alpha(0)
                .setDuration(300)
                .setStartDelay(0);

        final View BackendList = Backend.findViewById(R.id.BackendCollection);
        final View Dashboard = Backend.findViewById(R.id.DashboardContainer);

        ActionBar.setVisibility(View.VISIBLE);
        ActionBar.setTranslationY(- ActionBar.getHeight());
        ActionBar.animate()
                .translationY(0)
                .setDuration(400)
                .setStartDelay(400)
                .setInterpolator(standardInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        pendingAnimators.add(animation);
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        root.removeView(welcomeLogo);
                        Logo.setVisibility(View.VISIBLE);
                        pendingAnimators.add(animation);
                    }
                });

        BackendList.setVisibility(View.INVISIBLE);
        Dashboard.setVisibility(View.VISIBLE);
        Dashboard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Dashboard.setAlpha(0);

        Backend.setVisibility(View.VISIBLE);
        root.setBackgroundColor(getResources().getColor(R.color.GradientStart));
        Backend.setBackgroundColor(0x0);

        final Rect DashboardRect = new Rect();
        Dashboard.getGlobalVisibleRect(DashboardRect);
        Dashboard.setY(metrics.heightPixels);

        welcomeRoot.removeView(setupContainer);
        root.addView(setupContainer);

        Dashboard.animate()
                .alpha(1).translationY(0).rotation(0)
                .setDuration(500)
                .setStartDelay(500);

        BackendList.setVisibility(View.VISIBLE);
        BackendList.setY(root.getHeight());
//        root.setBackgroundResource(R.color.ActionBar);
        BackendList.animate()
                .translationY(0).rotation(0)
                .setDuration(500)
                .setStartDelay(DEBUG_ALTERNATE_DELAY ? 700 : 700);

        int welcomeRootY = Landscape ? 0 : -welcomeRoot.getHeight();
        int welcomeRootX = Landscape ? -welcomeRoot.getWidth() + Backend.findViewById(R.id.DashboardContainer).getWidth() : 0;

        if (!Landscape) {
            final View BackendSeparator = Backend.findViewById(R.id.DashboardShadow);

            BackendSeparator.setTranslationY(metrics.heightPixels - BackendSeparator.getTop() + Backend.findViewById(R.id.DashboardContainer).getHeight());
            BackendSeparator.animate()
                    .translationY(0)
                    .setDuration(500)
                    .setStartDelay(DEBUG_ALTERNATE_DELAY ? 500 : 500)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            pendingAnimators.add(animation);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            pendingAnimators.remove(animation);
                            BackendSeparator.animate().setListener(null);
                        }
                    });
        }
        if (Landscape) {
            final View BackendSeparator = Backend.findViewById(R.id.DashboardShadow);

            BackendSeparator.setTranslationX(metrics.widthPixels - BackendSeparator.getLeft());
            BackendSeparator.animate()
                    .translationX(0)
                    .setDuration(500)
                    .setStartDelay(DEBUG_ALTERNATE_DELAY ? 700 : 700)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            pendingAnimators.add(animation);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            pendingAnimators.remove(animation);
                            BackendSeparator.animate().setListener(null);
                        }
                    });

            final View BackendTransparentStrip = Backend.findViewById(R.id.BackendTransparentStrip);
            BackendTransparentStrip.setAlpha(0f);
            BackendTransparentStrip.animate()
                    .alpha(0.01f)
                    .setDuration(500)
                    .setStartDelay(DEBUG_ALTERNATE_DELAY ? 700 : 700)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            pendingAnimators.add(animation);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            pendingAnimators.remove(animation);
                            BackendTransparentStrip.setAlpha(1f);
                            BackendTransparentStrip.animate().setListener(null);
                        }
                    });
        }

        welcomeRoot.animate()
                .yBy(welcomeRootY)
                .xBy(welcomeRootX)
                .setDuration(500)
                .setStartDelay(DEBUG_ALTERNATE_DELAY ? 700 : 700)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        pendingAnimators.add(animation);

                        BackendFAB.setEnabled(true);
                        BackendFAB.show();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        pendingAnimators.remove(animation);
                        root.removeView(welcomeRoot);

                        root.setBackgroundColor(0);
                        Backend.setBackgroundColor(Backend.getResources().getColor(R.color.GradientStart));

//                        getActivity().getFragmentManager().beginTransaction().remove(WelcomeFragment.this).commit();
//                        bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.ActionBar)));
                    }
                });

    }

}
