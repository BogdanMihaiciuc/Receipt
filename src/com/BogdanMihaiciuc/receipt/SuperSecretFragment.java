package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class SuperSecretFragment extends Fragment {

    private ViewGroup root;
    private ViewGroup contentRoot;
    private ViewGroup actionBarRoot;
    private ViewGroup content;

    private ViewGroup consoleRoot;
    private View consoleLogo;
    private View consoleLines[] = new View[5];
    private View setupTitle;
    private ViewGroup setupContainer;
    private Animator cursorAnimator;
    private boolean welcome;

    private final Handler handler = new Handler();

    private final DisplayMetrics metrics = new DisplayMetrics();
    private float density;

    private TimeInterpolator standardInterpolator = new DecelerateInterpolator(1.5f);

    private Activity activity;

    private ArrayList<Animator> pendingAnimators = new ArrayList<Animator>();

    private boolean cancelled;
    private int stage = -1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (cancelled) {
            return;
        }

        activity = getActivity();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        density = metrics.density;

        root = (ViewGroup) activity.getWindow().getDecorView();
        contentRoot = (ViewGroup) root.getChildAt(0);
        actionBarRoot = (ViewGroup) contentRoot.findViewById(R.id.LegacyActionBar);
        content = (ViewGroup) contentRoot.findViewById(R.id.ContentContainer);

        consoleRoot = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.z_super_secret_developer_console, root, false);
        consoleLogo = consoleRoot.findViewById(R.id.WelcomeLogo);

        consoleLines[0] = consoleRoot.findViewById(R.id.ConsoleLine1);
        consoleLines[1] = consoleRoot.findViewById(R.id.ConsoleLine2);
        consoleLines[2] = consoleRoot.findViewById(R.id.ConsoleLine3Pre);
        consoleLines[3] = consoleRoot.findViewById(R.id.ConsoleLine3Mid);
        consoleLines[4] = consoleRoot.findViewById(R.id.ConsoleLine3Post);
//        consoleLines[5] = consoleRoot.findViewById(R.id.ConsoleLine6);
//        consoleLines[6] = consoleRoot.findViewById(R.id.ConsoleLine7);
//        consoleLines[7] = consoleRoot.findViewById(R.id.ConsoleLine8);
//        consoleLines[8] = consoleRoot.findViewById(R.id.ConsoleLine9);

        setupTitle = consoleRoot.findViewById(R.id.SetupTitle);
        setupContainer = (ViewGroup) consoleRoot.findViewById(R.id.SetupContainer);

        root.addView(consoleRoot);
        consoleRoot.setVisibility(View.INVISIBLE);
        consoleLogo.setAlpha(0);



        if (stage == 0) {
            setupContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    setupContainer.removeOnLayoutChangeListener(this);

                    contentRoot.setBackgroundColor(0);
                    consoleLogo.setAlpha(1);
//                    welcomeTitle.setAlpha(1);

                    if (setupContainer.getWidth() > 360 * density) {
                        setupContainer.getLayoutParams().width = (int) (360 * density);
                        setupContainer.requestLayout();
                    }

                }
            });
        }

        if (stage == 1) {
            setupContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    setupContainer.removeOnLayoutChangeListener(this);

                    contentRoot.setBackgroundColor(0);
                    consoleRoot.bringToFront();

                    if (setupContainer.getWidth() > 360 * density) {
                        setupContainer.getLayoutParams().width = (int) (360 * density);
                        setupContainer.requestLayout();
                    }

                    consoleLogo.setAlpha(1);

                    setupTitle.setVisibility(View.VISIBLE);
                    consoleLogo.setY(setupContainer.getY() / 3f);

                    float y;
                    if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
                        y = setupContainer.getY() / 3f + consoleLogo.getHeight() + 32 * density;
                    } else {
                        y = setupContainer.getY() / 3f + consoleLogo.getHeight() + 64 * density;
                    }
                    setupTitle.setY(y);
                    y = root.getHeight() / 2 - setupContainer.getHeight() / 2;
                    y = y + y / 3f;
                    setupContainer.setY(y);
                    setupContainer.setVisibility(View.VISIBLE);
                    setupContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);


                    View separator = consoleRoot.findViewById(R.id.SetupSeparator);
                    separator.setVisibility(View.VISIBLE);

                    View buttonStrip = consoleRoot.findViewById(R.id.SetupButtonStrip);
                    buttonStrip.setVisibility(View.VISIBLE);

                    buttonStrip.findViewById(R.id.FinishSetup).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            consoleRoot.findViewById(R.id.WelcomeClickBlocker).setOnTouchListener(BackendFragment.NullOnTouchListener);
                            cancelled = true;
                            goToAppTMP();
                        }
                    });

                }
            });
        }
    }

    public void onStart() {
        super.onStart();
        if (cancelled) return;

        consoleRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                consoleRoot.removeOnLayoutChangeListener(this);
                startConsole();
            }
        });

        if (stage == 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startConsole();
                }
            }, 1000);
        }
        else if (stage == 1) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goToSetup();
                }
            }, 1500);
        }
    }

    public void onDetach() {

        for (Animator a : pendingAnimators) {
            if (a instanceof ValueAnimator) {
                ((ValueAnimator) a).removeAllUpdateListeners();
            }
            a.removeAllListeners();
            a.cancel();
        }

        pendingAnimators.clear();
        handler.removeCallbacksAndMessages(null);

        root = null;
        contentRoot = null;
        actionBarRoot = null;
        content = null;

        consoleRoot = null;
        consoleLogo = null;
        setupContainer = null;

        activity = null;

        if (cursorAnimator != null) {
            cursorAnimator.cancel();
            cursorAnimator = null;
        }

        super.onDetach();
    }

    public void startConsole() {

        float lineY;
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            lineY = setupContainer.getY()/3f + consoleLogo.getHeight() + 32 * density;
        }
        else {
            lineY = setupContainer.getY()/3f + consoleLogo.getHeight() + 64 * density;
        }

        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            lineY += 12 * metrics.density;
            consoleLines[1].setY(lineY + 28 * density);
            ((View) consoleLines[2].getParent()).setY(lineY + 84 * density);
//            consoleLines[5].setY(lineY + 132 * density);
//            consoleLines[6].setY(lineY + 188 * density);
//            consoleLines[7].setY(lineY + 216 * density);
//            consoleLines[8].setY(lineY + 272 * density);
        }
        else {
            consoleLines[1].setY(lineY);
            consoleLines[1].setX(consoleLines[0].getLeft() + consoleLines[0].getWidth());
            ((View) consoleLines[2].getParent()).setY(lineY + 56 * density);

//            consoleLines[5].setY(lineY + 132 * density);
//            consoleLines[6].setY(lineY + 188 * density);
//            consoleLines[7].setY(lineY + 188 * density);
//            consoleLines[7].setX(consoleLines[6].getLeft() + consoleLines[6].getWidth());
//            consoleLines[8].setY(lineY + 244 * density);
        }

        consoleLines[0].setY(lineY);

        final float x = 12 * density;
        final Rect ActionBarRect = new Rect();
        actionBarRoot.getGlobalVisibleRect(ActionBarRect);
        final float y = ActionBarRect.top + actionBarRoot.getHeight()/2 - consoleLogo.getHeight()/2;

        consoleRoot.removeView(consoleLogo);
        root.addView(consoleLogo);

        consoleLogo.setX(x);
        consoleLogo.setY(y);
        consoleLogo.setAlpha(1f);

        final float X = root.getWidth() / 2f - consoleLogo.getWidth() / 2f;
        final float Y = setupContainer.getY()/3f;

        ValueAnimator.AnimatorUpdateListener listener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = (Float) valueAnimator.getAnimatedValue();
                float yPercent = fraction;
                float xPercent = (float) Math.sqrt(1 - Math.pow(fraction - 1, 2));
                consoleLogo.setX(x - (x - X) * xPercent);
                consoleLogo.setY(y - (y - Y) * yPercent);
            }
        };

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(listener);
        animator.setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pendingAnimators.remove(animation);
                showConsoleMessage(0);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                pendingAnimators.add(animation);
            }
        });
        animator.start();

        consoleRoot.setVisibility(View.VISIBLE);
        consoleRoot.setY(ActionBarRect.bottom - consoleRoot.getHeight());

        consoleRoot.animate().translationY(0)
                .setDuration(600)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        root.removeView(consoleLogo);
                        consoleRoot.addView(consoleLogo);
                    }
                });
        content.animate().translationY(- actionBarRoot.getBottom() + consoleRoot.getHeight())
                .setDuration(600);

    }

    protected void showConsoleMessage(final int line) {

        contentRoot.setBackgroundColor(0);

        if (line == 5) {
            startCursorBlink();
            return;
        }

        final int DPWidth = (int) (consoleLines[line].getWidth()/density);
        final int Width = consoleLines[line].getWidth();

        ((TextView) consoleLines[line]).setHorizontallyScrolling(true);
        consoleLines[line].requestFocus();
        consoleLines[line].getLayoutParams().width = 1;
        consoleLines[line].requestLayout();

        ValueAnimator animator = ValueAnimator.ofInt(1, Width);
        animator.setDuration(DPWidth);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                consoleLines[line].getLayoutParams().width = (Integer) valueAnimator.getAnimatedValue();
                consoleLines[line].requestLayout();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pendingAnimators.remove(animation);
                showConsoleMessage(line + 1);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                pendingAnimators.add(animation);
                consoleLines[line].setVisibility(View.VISIBLE);
            }
        });

        if (line == 0)
            animator.setStartDelay(400);

        animator.start();

    }

    public void startCursorBlink() {

        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
        animator.setDuration(800);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (valueAnimator.getAnimatedFraction() < 0.5f) {
                    consoleRoot.findViewById(R.id.Cursor).setVisibility(View.VISIBLE);
                } else {
                    consoleRoot.findViewById(R.id.Cursor).setVisibility(View.INVISIBLE);
                }
            }
        });
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();

        consoleLines[3].setFocusable(false);
        consoleLines[3].setFocusableInTouchMode(false);
        consoleLines[3].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConsole();
                if (cursorAnimator != null)
                    cursorAnimator.end();
            }
        });

        cursorAnimator = animator;
    }

    @Deprecated
    protected void showConsoleWarning(final int line) {

        if (line == 8) {
            return;
        }


        final int DPWidth = (int) (consoleLines[line].getWidth()/density);
        final int Width = consoleLines[line].getWidth();

        ((TextView) consoleLines[line]).setHorizontallyScrolling(true);
        consoleLines[line].requestFocus();
        consoleLines[line].getLayoutParams().width = 1;
        consoleLines[line].requestLayout();

        ValueAnimator animator = ValueAnimator.ofInt(1, Width);
        animator.setDuration(DPWidth);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                consoleLines[line].getLayoutParams().width = (Integer) valueAnimator.getAnimatedValue();
                consoleLines[line].requestLayout();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pendingAnimators.remove(animation);
                showConsoleWarning(line + 1);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                pendingAnimators.add(animation);
                consoleLines[line].setVisibility(View.VISIBLE);
            }
        });

        if (line == 5 || line == 6)
            animator.setStartDelay(400);

        animator.start();

    }

    public boolean onBackPressed() {
        root.removeView(consoleRoot);
        content.setTranslationY(0);
        activity.getFragmentManager().beginTransaction().remove(this).commit();
        Fragment settingsFragment = activity.getFragmentManager().findFragmentByTag("com.BogdanMihaiciuc.DEBUG.SecretSettingsFragment");
        if (settingsFragment != null)
            activity.getFragmentManager().beginTransaction().remove(settingsFragment).commit();
        return true;
    }

    protected void showConsole() {

        for (int i = 0; i < 2; i++) {
            consoleLines[i].animate()
                    .yBy(- 64f * density)
                    .alpha(0f)
                    .setDuration(400);
        }

        ((View) consoleLines[2].getParent()).animate()
                .yBy(- 64f * density)
                .alpha(0f)
                .setDuration(400);

        setupTitle.setVisibility(View.VISIBLE);
        setupTitle.setAlpha(0);
        float y;
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            y = setupContainer.getY()/3f + consoleLogo.getHeight() + 32 * density;
        }
        else {
            y = setupContainer.getY()/3f + consoleLogo.getHeight() + 64 * density;
        }

        float distance = consoleLogo.getY() - setupContainer.getY()/3f;

        setupTitle.setY(y + 64 * density);
        setupTitle.animate().setStartDelay(100);

        setupTitle.animate()
                .alpha(1).y(y)
                .setDuration(400)
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
                });

        activity.getFragmentManager().beginTransaction().add(R.id.ConsoleContainer, new SecretSettingsFragment(), "com.BogdanMihaiciuc.DEBUG.SecretSettingsFragment").commit();

        View consoleContainer = consoleRoot.findViewById(R.id.ConsoleContainer);

        consoleContainer.getLayoutParams().height = root.getHeight() - (int) (y + setupTitle.getHeight() + 64 * density);
        consoleContainer.requestLayout();
        consoleContainer.setTranslationY(root.getHeight() - (int) (y + setupTitle.getHeight() + 64 * density));
        consoleContainer.setVisibility(View.VISIBLE);
        consoleContainer.animate().translationY(0)
                .setDuration(400)
                .setStartDelay(100);

    }

    public void goToSetup() {

        consoleLogo.animate()
                .y(setupContainer.getY()/3f).alpha(1)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        consoleLogo.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        pendingAnimators.add(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        consoleLogo.setLayerType(View.LAYER_TYPE_NONE, null);
                        pendingAnimators.remove(animation);
                    }
                });

        setupTitle.setVisibility(View.VISIBLE);
        setupTitle.setAlpha(0);
        float y;
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            y = setupContainer.getY()/3f + consoleLogo.getHeight() + 32 * density;
        }
        else {
            y = setupContainer.getY()/3f + consoleLogo.getHeight() + 64 * density;
        }

        float distance = consoleLogo.getY() - setupContainer.getY()/3f;

        setupTitle.setY(y + 1.33f * distance);
        setupTitle.animate().setStartDelay(100);

        setupTitle.animate()
                .alpha(1).y(y)
                .setDuration(400)
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
                });

        setupContainer.setVisibility(View.VISIBLE);
        setupContainer.setAlpha(0);
        y = root.getHeight()/2 - setupContainer.getHeight()/2;
        y = y + y/3f;

        setupContainer.setY(y + 1.66f * distance);
        setupContainer.animate().setStartDelay(150);

        setupContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setupContainer.animate()
                .alpha(1).y(y)
                .setDuration(400)
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
                });

        View separator = consoleRoot.findViewById(R.id.SetupSeparator);
        separator.setTranslationY(64 * density);
        separator.setVisibility(View.VISIBLE);
        separator.animate()
                .translationY(0)
                .setStartDelay(250);

        View buttonStrip = consoleRoot.findViewById(R.id.SetupButtonStrip);
        buttonStrip.setTranslationY(64 * density);
        buttonStrip.setVisibility(View.VISIBLE);
        buttonStrip.animate()
                .translationY(0)
                .setStartDelay(250);

        buttonStrip.findViewById(R.id.FinishSetup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                consoleRoot.findViewById(R.id.WelcomeClickBlocker).setOnTouchListener(BackendFragment.NullOnTouchListener);
                goToAppTMP();
                cancelled = true;
            }
        });

    }

    public void goToAppTMP() {
        if (getActivity() == null) return;

        final View ActionBar = activity.findViewById(R.id.BackendActionBar);
        final View Logo = null; //ActionBar.findViewById(R.id.Logo);
        final View Backend = activity.findViewById(R.id.Backend);

        Backend.setVisibility(View.INVISIBLE);
        ActionBar.setVisibility(View.INVISIBLE);
        Logo.setVisibility(View.INVISIBLE);
        contentRoot.setVisibility(View.VISIBLE);
        contentRoot.bringToFront();

        final ActionBar bar = activity.getActionBar();
        bar.setBackgroundDrawable(getResources().getDrawable(R.drawable.null_drawable));

        final Rect rct = new Rect();
        consoleLogo.getGlobalVisibleRect(rct);

        final float X = 12 * density;
        final float Y = actionBarRoot.getTop() + ActionBar.getHeight()/2 - consoleLogo.getHeight()/2;

        consoleRoot.removeView(consoleLogo);
        root.addView(consoleLogo);

        ValueAnimator.AnimatorUpdateListener listener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = (Float) valueAnimator.getAnimatedValue();
                float xPercent = fraction;
                float yPercent = (float) Math.sqrt(1 - Math.pow(fraction - 1, 2));
                consoleLogo.setX(rct.left - (rct.left - X) * xPercent);
                consoleLogo.setY(rct.top - (rct.top - Y) * yPercent);
            }
        };

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(listener);
        animator.setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator());
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

//        setupTitle.animate()
//                .alpha(0)
//                .setDuration(250)
//                .setStartDelay(0);

        View separator = consoleRoot.findViewById(R.id.SetupSeparator);
        separator.setTranslationY(64 * density);
        separator.animate()
                .translationY(64 * density)
                .setStartDelay(0);

        View buttonStrip = consoleRoot.findViewById(R.id.SetupButtonStrip);
        buttonStrip.setTranslationY(64 * density);
        buttonStrip.animate()
                .translationY(64 * density)
                .setStartDelay(0);

        final View BackendList = Backend.findViewById(R.id.BackendCollection);
        final View Dashboard = Backend.findViewById(R.id.DashboardContainer);

        ActionBar.setVisibility(View.VISIBLE);
        ActionBar.setTranslationY(- ActionBar.getHeight());
        ActionBar.animate()
                .translationY(0)
                .setDuration(400)
                .setStartDelay(0)
                .setInterpolator(standardInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        pendingAnimators.add(animation);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        root.removeView(consoleLogo);
                        Logo.setVisibility(View.VISIBLE);
                        pendingAnimators.add(animation);
                    }
                });

        BackendList.setVisibility(View.INVISIBLE);
        Dashboard.setVisibility(View.VISIBLE);
        Dashboard.setAlpha(0);
        Dashboard.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        Backend.setVisibility(View.VISIBLE);

        final Rect DashboardRect = new Rect();
        Dashboard.getGlobalVisibleRect(DashboardRect);
        Dashboard.setY(setupContainer.getY() - DashboardRect.top);

        consoleRoot.removeView(setupContainer);
        root.addView(setupContainer);

        final View DashboardText = Dashboard.findViewById(R.id.BalanceText);

        float x = DashboardText.getX();
        setupContainer.animate()
                .y(DashboardRect.top).x(x).alpha(0)
                .setStartDelay(0)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        pendingAnimators.add(animation);
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        pendingAnimators.remove(animation);
                        root.removeView(setupContainer);
                    }
                });

        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            DashboardText.setX(setupContainer.getX());
            DashboardText.animate()
                    .translationX(0)
                    .setDuration(400);
        }

        Dashboard.animate()
                .alpha(1).translationY(0)
                .setDuration(400)
                .setStartDelay(0);

        BackendList.setVisibility(View.VISIBLE);
        BackendList.setY(root.getHeight());
        root.setBackgroundColor(getResources().getColor(R.color.ActionBar));
        BackendList.animate()
                .translationY(0)
                .setDuration(400)
                .setStartDelay(300);

        consoleRoot.animate()
                .yBy(- root.getHeight() + DashboardRect.bottom)
                .setDuration(400)
                .setStartDelay(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        pendingAnimators.add(animation);
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        pendingAnimators.remove(animation);
                        root.removeView(consoleRoot);
                        bar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.ActionBar)));
                    }
                });

    }

    public void goToApp() {

        final DisplayMetrics Metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(Metrics);

        final ImageView LogoDark = new ImageView(activity);
        LogoDark.setImageResource(R.drawable.logo_dark);

        root.addView(LogoDark, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final Rect rct = new Rect();
        consoleLogo.getGlobalVisibleRect(rct);
        LogoDark.setY(rct.top);
        LogoDark.setX(rct.left);

        final float Y = actionBarRoot.getTop() + (actionBarRoot.getHeight() - LogoDark.getDrawable().getIntrinsicHeight())/2;

        ValueAnimator.AnimatorUpdateListener listener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = (Float) valueAnimator.getAnimatedValue();
                float xPercent = fraction;
                float yPercent = (float) Math.sqrt(1 - Math.pow(fraction - 1, 2));
                float scale = 1 + 0.25f * (0.5f - Math.abs(0.5f - fraction));
                LogoDark.setX(rct.left - (rct.left - 12 * Metrics.density) * xPercent);
                LogoDark.setY(rct.top - (rct.top - Y) * yPercent);
                LogoDark.setScaleX(scale);
                LogoDark.setScaleY(scale);
            }
        };

        ValueAnimator animator = ValueAnimator.ofFloat(0, 0.5f);
        animator.addUpdateListener(listener);
        animator.setDuration(500)
                .setInterpolator(new AccelerateInterpolator(1.5f));

        ValueAnimator animator2 = ValueAnimator.ofFloat(0.5f, 1f);
        animator2.addUpdateListener(listener);
        animator2.setDuration(500)
                .setInterpolator(new DecelerateInterpolator(1.5f));

        AnimatorSet set = new AnimatorSet();
        set.play(animator2).after(animator);
        set.start();

        consoleLogo.setVisibility(View.INVISIBLE);

        actionBarRoot.setTranslationY(-actionBarRoot.getHeight());
        ActionBar actionBar = activity.getActionBar();
        actionBar.setIcon(R.drawable.null_drawable);
        actionBar.setTitle("");
        actionBar.setDisplayUseLogoEnabled(false);
        actionBarRoot.setVisibility(View.VISIBLE);
        actionBarRoot.animate()
                .translationY(0)
                .setDuration(400)
                .setStartDelay(900)
                .setInterpolator(new AccelerateDecelerateInterpolator());

        final View ActionBar = actionBarRoot.getChildAt(0);
        ActionBar.setY(-ActionBar.getHeight());
        ActionBar.animate()
                .translationY(0)
                .setDuration(300)
                .setStartDelay(1300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        root.removeView(LogoDark);
                        getActivity().getActionBar().setDisplayUseLogoEnabled(true);
                    }
                });
    }

}
