package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class FloatingActionButton extends View implements Glyph.GlyphListener {
    final static int DefaultSizeDP = 28;
    final static int DefaultShadowRadiusDP = 6;
    final static int DefaultShadowDistanceDP = 3;
//    final static int DefaultShadowRadiusDP = 12;
//    final static int DefaultShadowDistanceDP = 8;
    final static int DefaultBackgroundColor = 0xFFFFFFFF;
    final static int DefaultGlyphColor = Utils.transparentColor((int) (255 * 0.75), 0);

    final static Paint HighlightPaint;
    final static Paint ButtonPaint;
    final static Paint FlashPaint;

    static {
        HighlightPaint = new Paint();
        HighlightPaint.setAntiAlias(true);
//        HighlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));

        ButtonPaint = new Paint();
        ButtonPaint.setStyle(Paint.Style.FILL);
        ButtonPaint.setAntiAlias(true);

        FlashPaint = new Paint();
        FlashPaint.setAntiAlias(true);
        FlashPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    private int size;
    private int shadowRadius;
    private int shadowDistance;
    private Utils.DPTranslator pixels;

    private int width, height;
    private Path fabPath = new Path();
    private Path highlightRipplePath = new Path();
    private Path punchPath = new Path();
    private float x, y;

    private int backgroundColor = DefaultBackgroundColor;
    private int glyphColor = DefaultGlyphColor;
    private int flashColor;
    private float flashPercentage;
    private float completion = 1f;
    private float glyphCompletion = 1f;
    private boolean visible = true;

    private boolean rippleActive = false;
    private float rippleCompletion;
    private float pressedStateCompletion;
    private float rippleX, rippleY;

    private ValueAnimator rippleAnimator;
    private ValueAnimator colorAnimator;
    private ValueAnimator flashAnimator;
    private ValueAnimator completionAnimator;

    private Bitmap stateBitmap;
    private Canvas stateCanvas;
    private Shader stateShader;
    private final Paint AccentShadowPaint;

    private Glyph glyph;

    private String title;

    // init
    {
        pixels = new Utils.DPTranslator(getResources().getDisplayMetrics().density);
        size = pixels.get(DefaultSizeDP);
        shadowRadius = pixels.get(DefaultShadowRadiusDP);
        shadowDistance = pixels.get(DefaultShadowDistanceDP);

        AccentShadowPaint = new Paint();
        AccentShadowPaint.setMaskFilter(new BlurMaskFilter(shadowRadius / 3f, BlurMaskFilter.Blur.NORMAL));
        AccentShadowPaint.setStyle(Paint.Style.FILL);
        AccentShadowPaint.setColor(Utils.transparentColor(0x25, 0));

        glyph = new Glyph(getContext(), this);

        setLayerType(LAYER_TYPE_SOFTWARE, null);

        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (title != null) {
                    new TooltipPopover(title, null, Popover.anchorWithID(getId())).show((Activity) getContext());
                    return true;
                }

                return false;
            }
        });
    }

    protected void onAttachedToWindow() {
        ((ViewGroup) getParent()).setClipChildren(false);
        ((ViewGroup) getParent()).setClipToPadding(false);

        ((ViewGroup) getParent().getParent()).setClipChildren(false);
        ((ViewGroup) getParent().getParent()).setClipToPadding(false);
    }

    public void onMeasure(int withMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(withMeasureSpec);
        int widthType = MeasureSpec.getMode(withMeasureSpec);

        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightType = MeasureSpec.getMode(heightMeasureSpec);

        int targetWidth, targetHeight;
        int totalSize = size;// + shadowRadius + shadowDistance;

        if (widthType == MeasureSpec.UNSPECIFIED) {
            targetWidth = totalSize;
        }
        else if (widthType == MeasureSpec.AT_MOST) {
            targetWidth = Math.min(widthSize, totalSize);
        }
        else {
            targetWidth = widthSize;
        }

        if (heightType == MeasureSpec.UNSPECIFIED) {
            targetHeight = totalSize;
        }
        else if (heightType == MeasureSpec.AT_MOST) {
            targetHeight = Math.min(heightSize, totalSize);
        }
        else {
            targetHeight = heightSize;
        }

        setMeasuredDimension(targetWidth, targetHeight);
    }

    public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        this.width = width;
        this.height = height;

        fabPath.rewind();
        fabPath.addCircle(width / 2, height / 2, size, Path.Direction.CW);

        punchPath.rewind();
        punchPath.addPath(fabPath);
        punchPath.addRect(0, 0, width, height, Path.Direction.CW);
        punchPath.setFillType(Path.FillType.EVEN_ODD);

        stateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        stateCanvas = new Canvas(stateBitmap);

        glyph.setSize(pixels.get(28));
        glyph.setCenter(width / 2, height / 2);

    }

    public void flushAnimations() {
        if (colorAnimator != null) colorAnimator.end();
        if (completionAnimator != null) completionAnimator.end();
        if (flashAnimator != null) flashAnimator.end();
        if (rippleAnimator != null) rippleAnimator.end();
    }

    public void setColor(int color) {
        setColor(color, true);
    }

    public void setColor(int color, boolean animated) {
        if (colorAnimator != null) {
            colorAnimator.end();
        }

        if (animated) {
            final int StartingColor = this.backgroundColor;
            final int EndingColor = color;
            colorAnimator = ValueAnimator.ofFloat(0f, 1f);
            colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    backgroundColor = Utils.interpolateColors(valueAnimator.getAnimatedFraction(), StartingColor, EndingColor);
                    invalidate();
                }
            });
            colorAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            colorAnimator.start();
        }
        else {
            this.backgroundColor = color;
            invalidate();
        }
    }

    public void setBackgroundAndGlyphColors(int backgroundColor, int glyphColor, boolean animated) {
        if (this.backgroundColor == backgroundColor && this.glyphColor == glyphColor) return;

        if (colorAnimator != null) {
            colorAnimator.end();
        }

        if (animated) {
            final int StartingColor = this.backgroundColor;
            final int EndingColor = backgroundColor;
            final int StartingGlyphColor = this.glyphColor;
            final int EndingGlyphColor = glyphColor;
            colorAnimator = ValueAnimator.ofFloat(0f, 1f);
            colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    FloatingActionButton.this.backgroundColor = Utils.interpolateColors(valueAnimator.getAnimatedFraction(), StartingColor, EndingColor);
                    FloatingActionButton.this.glyphColor = Utils.interpolateColors(valueAnimator.getAnimatedFraction(), StartingGlyphColor, EndingGlyphColor);
                    invalidate();
                }
            });
            colorAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    colorAnimator = null;
                }
            });
            colorAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            colorAnimator.start();
        }
        else {
            this.backgroundColor = backgroundColor;
            this.glyphColor = glyphColor;
            invalidate();
        }
    }

    public void flashColor(int color) {
        if (flashAnimator != null) flashAnimator.end();

        flashColor = color;
        flashAnimator = ValueAnimator.ofFloat(0f, 1f);
        flashAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                flashPercentage = valueAnimator.getAnimatedFraction() / 1.5f;
                invalidate();
            }
        });
        flashAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                flashAnimator = null;
            }
        });
        flashAnimator.setDuration(500).setInterpolator(new Utils.BounceCycleInterpolator(1.5f));
        flashAnimator.start();
    }

    public void setGlyph(int glyph) {
        setGlyph(glyph, true);
    }

    public void setGlyph(int glyph, boolean animated) {
        this.glyph.setGlyph(glyph, animated);
    }

    private boolean touchEventConsumed = false;
    public boolean onTouchEvent(MotionEvent event) {
        if (!visible) {
            if (rippleAnimator != null) rippleAnimator.cancel();
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            x = event.getX();
            y = event.getY();


            if (completion < 1) {
                return super.onTouchEvent(event);
            }
            rippleActive = true;
            touchEventConsumed = false;

            if (rippleAnimator != null) {
                rippleAnimator.cancel();
            }

            rippleAnimator = ValueAnimator.ofFloat(0f, 1f);
            rippleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    rippleCompletion = valueAnimator.getAnimatedFraction();
                    pressedStateCompletion = rippleCompletion;
                    rippleX = Utils.interpolateValues(rippleCompletion, x, width / 2);
                    rippleY = Utils.interpolateValues(rippleCompletion, y, height / 2);
//                    highlightRipplePath.rewind();
//                    highlightRipplePath.addCircle(x, y, rippleCompletion * size * 2, Path.Direction.CCW);
                    invalidate();
                }
            });
            rippleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (rippleAnimator == animation) rippleAnimator = null;
                }
            });
            rippleAnimator.setDuration(400);
            rippleAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
            rippleAnimator.start();
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (!touchEventConsumed) onMouseLeave();
            touchEventConsumed = true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (event.getX() < 0 || event.getY() < 0 || event.getX() > width || event.getY() > height) {
                if (!touchEventConsumed) onMouseLeave();
                touchEventConsumed = true;
            }
        }

        return super.onTouchEvent(event);
    }

    public void onMouseLeave() {
        if (rippleAnimator != null && rippleAnimator.getDuration() == 400) {
            rippleAnimator.setDuration(200);
            rippleAnimator.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        dismissRipple();
                    }
                }
            });
        }
        else {
            dismissRipple();
        }
    }

    public void dismissRipple() {
        if (!rippleActive) return;
        if (rippleAnimator != null) {
            rippleAnimator.end();
        }

        rippleAnimator = ValueAnimator.ofFloat(0f, 1f);

        rippleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                pressedStateCompletion = 1 - valueAnimator.getAnimatedFraction();
                invalidate();
            }
        });

        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (rippleAnimator == animation) {
                    rippleAnimator = null;
                    rippleActive = false;
                }
            }
        });

        rippleAnimator.setDuration(100);
        rippleAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
        rippleAnimator.start();
    }

    public void toggleVisibility() {
        if (completion < 1) {
            show();
        }
        else {
            hide();
        }
    }

    public void show() {
        showDelayed(0, true);
    }

    public void show(boolean animated) {
        showDelayed(0, animated);
    }

    public void showDelayed(long delay) {
        showDelayed(delay, true);
    }

    public void showDelayed(long delay, boolean animated) {
        if (visible) return;
        if (completionAnimator != null) completionAnimator.end();
        if (!animated) {
            completion = 1;
            glyphCompletion = 1;
            visible = true;
            invalidate();
            return;
        }

        completionAnimator = ValueAnimator.ofFloat(0f, 1f);
        completionAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animator) {
                visible = true;
            }

            public void onAnimationEnd(Animator animation) {
                completionAnimator = null;
            }
        });
        completionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                completion = valueAnimator.getAnimatedFraction() * 2.5f / 2f;
                if (completion > 1) completion = 1;

                glyphCompletion = valueAnimator.getAnimatedFraction() > 0.5f ? (valueAnimator.getAnimatedFraction() - 0.5f) * 2 : 0;
                invalidate();
            }
        });
        completionAnimator.setDuration(400).setInterpolator(new DecelerateInterpolator(1.5f));
        completionAnimator.setStartDelay(delay);
        completionAnimator.start();
    }

    public boolean isVisible() {
        return visible;
    }

    public void hide() {
        hide (true) ;
    }

    public void hide(boolean animated) {
        if (!visible && animated) return;
        if (completionAnimator != null) completionAnimator.end();
        if (!visible) return;

        if (!animated) {
            completion = 0f;
            glyphCompletion = 0f;
            visible = false;
            invalidate();
            return;
        }

        completionAnimator = ValueAnimator.ofFloat(0f, 1f);
        completionAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animator) {
                visible = false;
            }
            public void onAnimationEnd(Animator animation) {
                completionAnimator = null;
            }
        });
        completionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = 1 - valueAnimator.getAnimatedFraction();

                completion = fraction * 3f / 2f;
                if (completion > 1) completion = 1;

                glyphCompletion = fraction > 0.5f ? (fraction - 0.5f) * 2 : 0;
                invalidate();
            }
        });
        completionAnimator.setDuration(400).setInterpolator(new AccelerateInterpolator(1.5f));
        completionAnimator.start();
    }

    public void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        int shadowRadius = this.shadowRadius + pixels.get(2 * pressedStateCompletion);

        if (flashPercentage > 0) {
            FlashPaint.setColor(0);
            FlashPaint.setShadowLayer(shadowRadius * flashPercentage, 0, 0, Utils.transparentColor(flashPercentage * completion, flashColor));
            canvas.drawCircle(width / 2, height / 2, size * completion, FlashPaint);
        }

        ButtonPaint.setShadowLayer(shadowRadius, 0, shadowDistance, Utils.transparentColor((int) (0x25 * completion), 000000));
        ButtonPaint.setColor(backgroundColor);

        canvas.drawCircle(width / 2, height / 2 + shadowDistance / 3f, size * completion, AccentShadowPaint);
        canvas.drawCircle(width / 2, height / 2, size * completion, ButtonPaint);

        if (rippleActive) {
            stateCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            HighlightPaint.setColor(Utils.transparentColor((int)(0x15 * pressedStateCompletion * completion), 0));
//            float rippleSize = Math.min(rippleCompletion * size * 1.25f, size);
            float rippleSize = rippleCompletion * size;
            canvas.drawCircle(rippleX, rippleY, rippleSize, HighlightPaint);
//            stateCanvas.drawPath(highlightRipplePath, HighlightPaint);

//            HighlightPaint.setColor(Color.TRANSPARENT);
//            HighlightPaint.setColor(0x00FFFFFF);
//            stateCanvas.drawPath(punchPath, HighlightPaint);
//            canvas.drawBitmap(stateBitmap, 0, 0, null);
        }

        if (glyphCompletion < 1f) {
            canvas.save();
            canvas.scale(glyphCompletion, glyphCompletion, width / 2, height / 2);
        }

        glyph.setColor(glyphColor);
        glyph.render(canvas);

        if (glyphCompletion < 1f) {
            canvas.restore();
        }

    }

    public FloatingActionButton(Context context) {
        super(context);
    }

    public FloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
