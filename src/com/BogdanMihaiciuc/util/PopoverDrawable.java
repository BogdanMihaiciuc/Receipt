package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.DisplayMetrics;
import android.util.Log;

//import com.BogdanMihaiciuc.receipt.R;

public class PopoverDrawable extends Drawable implements Drawable.Callback {

    final static String TAG = PopoverDrawable.class.getName();

    final static boolean DEBUG = false;
    final static boolean USE_DUAL_SHADOWS = true;
    final static boolean USE_SLAVE_DRAWABLE = true;
    // The deadly ART bug refers to a crash that occurs during the Garbage Collection
    // The bug appears to happen after calling canvas.drawColor(0, PorterDuff.Mode.CLEAR);
    final static boolean DEBUG_DEADLY_ART_BUG = false;
    final static boolean DEBUG_PADDING = true;

    final static int SlaveRequiredSizeDP = 48;

    public final static int GravityBelow = 0;
    public final static int GravityAbove = 1;
    public final static int GravityLeftOf = 2;
    public final static int GravityRightOf = 3;
    public final static int GravityCenter = 4;

    final static int PaddingDP = 8;
    final static int BottomPaddingDP = 16;
    final static int IndicatorWidthDP = 32;
    final static int IndicatorHeightDP = 16;
    final static int CornerRadiusDP = 8;

    final static Paint FillPaint;
    final static Paint EdgePaint;
    final static private Paint ShadowPaint;
    final static private Paint AccentShadowPaint;

    // The ClearPaint's purpose is to fix a deadly ART bug
    // which is triggered by a gc after calling canvas.drawColor(0, PorterDuff.Mode.CLEAR);
    final static Paint ClearPaint;

    static {
        FillPaint = new Paint();
        FillPaint.setStyle(Paint.Style.FILL);
        FillPaint.setAntiAlias(true);
        FillPaint.setColor(0xAA000000);

        EdgePaint = new Paint();
        EdgePaint.setStyle(Paint.Style.STROKE);
        EdgePaint.setAntiAlias(true);
        EdgePaint.setColor(0xFF000000);
        EdgePaint.setStrokeWidth(2);

        ShadowPaint = new Paint();
        ShadowPaint.setStyle(Paint.Style.FILL);
        ShadowPaint.setColor(0x2A000000);

        AccentShadowPaint = new Paint();
        AccentShadowPaint.setStyle(Paint.Style.FILL);
        AccentShadowPaint.setColor(0x2A000000);

        ClearPaint = new Paint();
        ClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        ClearPaint.setColor(0);
    }

    private ColorFilter filter;
    private int alpha;

    private Context context;
    private DisplayMetrics metrics;
    private float density;
    private Utils.DPTranslator pixels;

    private float leftPadding;
    private float rightPadding;
    private float topPadding;
    private float bottomPadding;

    private int indicatorWidth;
    private int indicatorHeight;

    private int gravity = GravityAbove;
    private boolean packed;

    private float roundRadius;

    private RectF balloonRect = new RectF();

    private Path balloonPath = new Path();
    private Path shadowPath = new Path();
    private Path accentShadowPath = new Path();

    private Path separateIndicatorPath = new Path();
    private RectF separateBody = new RectF();
    private boolean drawsSeparateIndicator;

    private MaskFilter accentShadowMaskFilter;
    private MaskFilter shadowMaskFilter;

    private int fillColor = 0xAA000000;
    private int edgeColor = 0xFF000000;
    private int shadowColor = 0x2A000000;
    private int accentShadowColor = 0x2A000000;
    private int indicatorColor = 0;

    private float shadowRadius;
    private float shadowDisplacement;
    private float accentShadowRadius;
    private float accentShadowDisplacement;

    private int width;
    private int height;

    public boolean balloonVisible = true;

    public PopoverDrawable(Context context) {
        this(context, false);
    }

    public PopoverDrawable(Context context, boolean white) {
        this.context = context;

        metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        pixels = new Utils.DPTranslator(density);

        leftPadding = pixels.get(PaddingDP);
        rightPadding = leftPadding;
        topPadding = leftPadding;
        bottomPadding = pixels.get(BottomPaddingDP);

        indicatorWidth = pixels.get(IndicatorWidthDP);
        indicatorHeight = pixels.get(IndicatorHeightDP);

        shadowRadius = pixels.get(8);
        shadowDisplacement = pixels.get(3);
        ShadowPaint.setMaskFilter(new BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL));

        roundRadius = pixels.get(CornerRadiusDP);

        if (white) {
            packed = true;
            setFillColor(context.getResources().getColor(android.R.color.white));
            setEdgeColor(0x20000000);
            setShadowAlpha(0.25f);
            setShadowRadius(16, 8, true);
            setGravity(gravity);
            setRoundness(CornerRadiusDP, true);
        }

    }

    public void setFillColor(int fillColor) {
        this.fillColor = fillColor;
        if (USE_SLAVE_DRAWABLE) {
            if (getBounds() != null) if (getBounds().width() > 0 && getBounds().height() > 0) softwareDraw(false);
        }
        invalidateSelf();
    }

    public void setEdgeColor(int edgeColor) {
        this.edgeColor = edgeColor;
        invalidateSelf();
    }

    public void setShadowAlpha(float alpha) {
        if (USE_DUAL_SHADOWS) this.shadowColor = Utils.transparentColor((int) (130 * alpha), 0);
        else this.shadowColor = Utils.transparentColor((int) (255 * alpha), 0);

        if (USE_DUAL_SHADOWS) this.accentShadowColor = Utils.transparentColor((int) (100 * alpha), 0);
    }

    public PopoverDrawable setShadowRadius(float radius, float displacement, boolean dp) {
        this.shadowRadius = dp ? pixels.get(radius) : radius;
        if (USE_DUAL_SHADOWS) this.accentShadowRadius = this.shadowRadius / 3.5f;
        shadowMaskFilter = new BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL);
        if (USE_DUAL_SHADOWS) accentShadowMaskFilter = new BlurMaskFilter(accentShadowRadius, BlurMaskFilter.Blur.NORMAL);

        this.shadowDisplacement = dp ? pixels.get(displacement) : displacement;
        if (USE_DUAL_SHADOWS) this.accentShadowDisplacement = this.shadowDisplacement / 3.5f;

        leftPadding = (int) (shadowRadius + 0.5f);
        rightPadding = leftPadding;
        topPadding = leftPadding;
        bottomPadding = (int) (shadowRadius + shadowDisplacement + 0.5f);

        onBoundsChange(getBounds());

        invalidateSelf();

        return this;
    }

    public void prepareIndicatorColor(int color) {
        if (color != fillColor && color != 0) {
            drawsSeparateIndicator = true;
            indicatorColor = color;
        }
        else {
            drawsSeparateIndicator = false;
        }
    }

    public void setIndicatorColor(int color) {
        if (color != fillColor && color != 0) {
            drawsSeparateIndicator = true;
            indicatorColor = color;
        }
        else {
            drawsSeparateIndicator = false;
        }

        if (getBounds() != null && getBounds().width() > 0) {
            //Ensure we have a surface to draw on before refreshing the bitmap

            computeBalloonTranslationRectsForTranslation(getBalloonTranslation());

            fastSoftwareDraw();
            invalidateSelf();
        }

//        if (!RectF.intersects(currentIndicatorRect, balloonRect)) {
//            // Invalidation is required if the indicator rect is not fully contained in the bounds
//
//            if (currentIndicatorRect.left == currentIndicatorRect.right) {
//                // Uninitialized balloonRect
//                computeBalloonTranslationRectsForTranslation(getBalloonTranslation());
//                Log.d(TAG, "Balloon translations have been computed: " + previousIndicatorRect + " and " + currentIndicatorRect);
//            }
//
//            if (LegacyActionBar.DEBUG_COMMANDED_POPOVER) {
//                Log.d(TAG, "Fast software draw running on balloon translations: " + previousIndicatorRect + " and " + currentIndicatorRect);
//                Log.d(TAG, "Balloon translation is " + getBalloonTranslation());
//            }
//        }
    }

    public void setPadding(int left, int right, int top, int bottom) {
        leftPadding = pixels.get(left);
        rightPadding = pixels.get(right);
        topPadding = pixels.get(top);
        bottomPadding = pixels.get(bottom);

        onBoundsChange(getBounds());

        invalidateSelf();
    }

    public int getLeftPadding() {
        return (int) (gravity == GravityRightOf ? leftPadding + indicatorHeight : leftPadding);
    }

    public int getRightPadding() {
        return (int) (gravity == GravityLeftOf ? rightPadding + indicatorHeight : rightPadding);
    }

    public int getTopPadding() {
        return (int) (gravity == GravityBelow ? topPadding + indicatorHeight : topPadding);
    }

    public int getBottomPadding() {
        return (int) (gravity == GravityAbove ? bottomPadding + indicatorHeight : bottomPadding);
    }

    public void setGravity(int gravity) {
        this.gravity = gravity;
        onBoundsChange(getBounds(), true);
        invalidateSelf();
    }

    public void setIndicatorSize(int width, int height) {
        this.indicatorWidth = width;
        this.indicatorHeight = height;

        onBoundsChange(getBounds());
        invalidateSelf();
    }

    public int getIndicatorWidth() {
        return indicatorWidth;
    }

    public int getIndicatorHeight() {
        return indicatorHeight;
    }

    public void setRoundness(float roundness) {
        this.roundRadius = (int) (roundness + 0.5f);
        invalidateSelf();
    }

    public void setRoundness(int roundness, boolean dp) {
        if (dp) setRoundness(pixels.get(roundness));
        else setRoundness(roundness);
    }

    public boolean getPadding(Rect outRect) {
        if (!packed) {
            outRect.left = pixels.get(24);
            outRect.right = pixels.get(24);
            outRect.bottom = pixels.get(40);
            outRect.top = pixels.get(20);
        }
        else {
            outRect.left = (int) leftPadding;
            outRect.right = (int) rightPadding;
            outRect.bottom = gravity == GravityAbove ?  (int) (indicatorHeight + bottomPadding) : (int) (bottomPadding);
            outRect.top = gravity == GravityBelow ? (int) (indicatorHeight + topPadding) : (int) (topPadding);
        }
        return true;
    }

    private float balloonTranslation;
//    private Path updatePath = new Path();
    private RectF previousIndicatorRect = new RectF();
    private RectF currentIndicatorRect = new RectF();

    public void setBalloonTranslation(float translation) {
        if (LegacyActionBar.DEBUG_COMMANDED_POPOVER) {
            Log.d(TAG, "Balloon translation will change from " + balloonTranslation + " to " + translation);
        }

        if (translation != balloonTranslation) {

            computeBalloonTranslationRectsForTranslation(translation);

            balloonTranslation = translation;
            if (width == 0 || height == 0) {
                return;
            }
            createPathsWithTranslation(translation);
//            softwareDraw(false);
            fastSoftwareDraw();
            invalidateSelf();
//            createSlave(false);
//            getCallback().invalidateDrawable(this);
        }
    }

    private void computeBalloonTranslationRectsForTranslation(float translation) {
        if (gravity == GravityBelow) {
//                updatePath.rewind();

            previousIndicatorRect.top = (int) (balloonRect.top - shadowRadius - indicatorHeight);
            previousIndicatorRect.bottom = (int) balloonRect.top;
            previousIndicatorRect.left = (int) (balloonRect.centerX() - indicatorWidth / 2 + balloonTranslation - shadowRadius);
            previousIndicatorRect.right = (int) (previousIndicatorRect.left + indicatorWidth + 2 * shadowRadius);

            currentIndicatorRect.top = (int) (balloonRect.top - shadowRadius);
            currentIndicatorRect.bottom = (int) balloonRect.top;
            currentIndicatorRect.left = (int) (balloonRect.centerX() - indicatorWidth / 2 + translation - shadowRadius);
            currentIndicatorRect.right = (int) (previousIndicatorRect.left + indicatorWidth + 2 * shadowRadius);

//                updatePath.addRect(previousIndicatorRect, Path.Direction.CW);
//                updatePath.addRect(currentIndicatorRect, Path.Direction.CW);
        }

        if (gravity == GravityAbove) {

            previousIndicatorRect.top = (int) (balloonRect.bottom);
            previousIndicatorRect.bottom = (int) balloonRect.bottom + shadowRadius + shadowDisplacement + indicatorHeight;
            previousIndicatorRect.left = (int) (balloonRect.centerX() - indicatorWidth / 2 + balloonTranslation - shadowRadius);
            previousIndicatorRect.right = (int) (previousIndicatorRect.left + indicatorWidth + 2 * shadowRadius);

            currentIndicatorRect.top = (int) (balloonRect.bottom);
            currentIndicatorRect.bottom = (int) (balloonRect.bottom + shadowRadius + shadowDisplacement + indicatorHeight);
            currentIndicatorRect.left = (int) (balloonRect.centerX() - indicatorWidth / 2 + translation - shadowRadius);
            currentIndicatorRect.right = (int) (previousIndicatorRect.left + indicatorWidth + 2 * shadowRadius);
        }

        if (gravity == GravityLeftOf) {
            previousIndicatorRect.top = (int) (balloonRect.centerY() - indicatorWidth / 2 + balloonTranslation - shadowRadius);
            previousIndicatorRect.bottom = (int) (previousIndicatorRect.top + 2 * shadowRadius + indicatorWidth + shadowDisplacement);
            previousIndicatorRect.left = (int) balloonRect.right;
            previousIndicatorRect.right = (int) (previousIndicatorRect.right + shadowRadius + indicatorHeight);

            currentIndicatorRect.top = (int) (balloonRect.centerY() - indicatorWidth / 2 + translation - shadowRadius);
            currentIndicatorRect.bottom = (int) (currentIndicatorRect.top + 2 * shadowRadius + indicatorWidth + shadowDisplacement);
            currentIndicatorRect.left = (int) balloonRect.right;
            currentIndicatorRect.right = (int) (previousIndicatorRect.right + shadowRadius + indicatorHeight);
        }

        if (gravity == GravityRightOf) {
            previousIndicatorRect.top = (int) (balloonRect.centerY() - indicatorWidth / 2 + balloonTranslation - shadowRadius);
            previousIndicatorRect.bottom = (int) (previousIndicatorRect.top + 2 * shadowRadius + indicatorWidth + shadowDisplacement);
            previousIndicatorRect.left = (int) (balloonRect.left - shadowRadius - indicatorHeight);
            previousIndicatorRect.right = (int) (balloonRect.left);

            currentIndicatorRect.top = (int) (balloonRect.centerY() - indicatorWidth / 2 + translation - shadowRadius);
            currentIndicatorRect.bottom = (int) (currentIndicatorRect.top + 2 * shadowRadius + indicatorWidth + shadowDisplacement);
            currentIndicatorRect.left = (int) (balloonRect.left - shadowRadius - indicatorHeight);
            currentIndicatorRect.right = (int) (balloonRect.left);
        }

    }

    public float getBalloonTranslation() {
        return balloonTranslation;
    }

    public void setBalloonVisible(boolean visible) {
        if (balloonVisible != visible) {
            balloonVisible = visible;
            if (width == 0 || height == 0) {
                return;
            }
            createPathsWithTranslation(balloonTranslation);
//            createSlave(false);
            getCallback().invalidateDrawable(this);
        }
    }

    protected void createPathsWithTranslation(float translation) {
        balloonPath.rewind();
        separateIndicatorPath.rewind();
//        balloonPath.addRoundRect(balloonRect, pixels.get(4), pixels.get(4), Path.Direction.CW);
        // TOP LEFT CORNER
        balloonPath.moveTo(balloonRect.left, balloonRect.top + roundRadius);
        balloonPath.cubicTo(balloonRect.left, balloonRect.top, balloonRect.left, balloonRect.top, balloonRect.left + roundRadius, balloonRect.top);

        if (gravity == GravityBelow && balloonVisible) {
            balloonPath.lineTo(balloonRect.left + balloonRect.width() / 2 - indicatorWidth / 2 + translation, balloonRect.top);
            separateIndicatorPath.moveTo(balloonRect.left + balloonRect.width() / 2 - indicatorWidth / 2 + translation, balloonRect.top);

            balloonPath.rLineTo(indicatorWidth / 2f, -indicatorHeight);
            balloonPath.rLineTo(indicatorWidth / 2f, indicatorHeight);

            separateIndicatorPath.rLineTo(indicatorWidth / 2f, -indicatorHeight);
            separateIndicatorPath.rLineTo(indicatorWidth / 2f, indicatorHeight);
            separateIndicatorPath.close();
        }
        // TOP EDGE
        balloonPath.lineTo(balloonRect.right - roundRadius, balloonRect.top);
        balloonPath.cubicTo(balloonRect.right, balloonRect.top, balloonRect.right, balloonRect.top, balloonRect.right, balloonRect.top + roundRadius);

        if (gravity == GravityLeftOf && balloonVisible) {
            balloonPath.lineTo(balloonRect.right, balloonRect.top + balloonRect.height() / 2 - indicatorWidth / 2 + translation);
            separateIndicatorPath.moveTo(balloonRect.right, balloonRect.top + balloonRect.height() / 2 - indicatorWidth / 2 + translation);

            balloonPath.rLineTo(indicatorHeight, indicatorWidth / 2f);
            balloonPath.rLineTo(- indicatorHeight, indicatorWidth / 2f);

            separateIndicatorPath.rLineTo(indicatorHeight, indicatorWidth / 2f);
            separateIndicatorPath.rLineTo(- indicatorHeight, indicatorWidth / 2f);
            separateIndicatorPath.close();
        }

        // RIGHT EDGE
        balloonPath.lineTo(balloonRect.right, balloonRect.bottom - roundRadius);
        balloonPath.cubicTo(balloonRect.right, balloonRect.bottom, balloonRect.right, balloonRect.bottom, balloonRect.right - roundRadius, balloonRect.bottom);

        if (gravity == GravityAbove && balloonVisible) {
            // BOTTOM EDGE RIGHT
            balloonPath.lineTo(balloonRect.left + balloonRect.width() / 2 + indicatorWidth / 2 + translation, balloonRect.bottom);
            separateIndicatorPath.moveTo(balloonRect.left + balloonRect.width() / 2 + indicatorWidth / 2 + translation, balloonRect.bottom);

//        balloonPath.moveTo(balloonRect.left + balloonRect.width() / 2f - indicatorWidth / 2f, balloonRect.bottom - 1);
//        balloonPath.rLineTo(indicatorWidth, 0);
            // BALLOON
            balloonPath.rLineTo(-indicatorWidth / 2f, indicatorHeight);
            balloonPath.rLineTo(-indicatorWidth / 2f, -indicatorHeight);

            separateIndicatorPath.rLineTo(-indicatorWidth / 2f, indicatorHeight);
            separateIndicatorPath.rLineTo(-indicatorWidth / 2f, -indicatorHeight);
            separateIndicatorPath.close();
        }

        // BOTTOM EDGE LEFT
        float balloonBottomStart = balloonRect.bottom - balloonRect.height() / 2 + indicatorWidth / 2 + translation;
        balloonPath.lineTo(balloonRect.left + roundRadius, balloonRect.bottom);
        if (balloonBottomStart > balloonRect.bottom - roundRadius && gravity == GravityRightOf && balloonVisible) {
            balloonPath.lineTo(balloonRect.left, balloonRect.bottom);
        }
        else {
            balloonPath.cubicTo(balloonRect.left, balloonRect.bottom, balloonRect.left, balloonRect.bottom, balloonRect.left, balloonRect.bottom - roundRadius);
        }

        if (gravity == GravityRightOf && balloonVisible) {
            balloonPath.lineTo(balloonRect.left, balloonRect.bottom - balloonRect.height() / 2 + indicatorWidth / 2 + translation);
            separateIndicatorPath.moveTo(balloonRect.left, balloonRect.bottom - balloonRect.height() / 2 + indicatorWidth / 2 + translation);

            balloonPath.rLineTo(-indicatorHeight, -indicatorWidth / 2f);
            balloonPath.rLineTo(indicatorHeight, -indicatorWidth / 2f);

            separateIndicatorPath.rLineTo(-indicatorHeight, -indicatorWidth / 2f);
            separateIndicatorPath.rLineTo(indicatorHeight, - indicatorWidth / 2f);
            separateIndicatorPath.close();
        }

        // LEFT EDGE
        balloonPath.lineTo(balloonRect.left, balloonRect.top + roundRadius);
        balloonPath.close();

        shadowPath.rewind();
        balloonPath.offset(0, shadowDisplacement, shadowPath);
        accentShadowPath.rewind();
        balloonPath.offset(0, accentShadowDisplacement, accentShadowPath);
    }

    protected NinePatchDrawable createSlave() {
        return createSlave(true);
    }

    protected NinePatchDrawable createSlave(boolean refreshBitmap) {
        createPathsWithTranslation(balloonTranslation);
        softwareDraw(refreshBitmap);
        // Slave Drawable coordinates: top left bottom right
        if (gravity == GravityAbove || gravity == GravityBelow) {
            slaveDrawable = new NinePatchDrawable(context.getResources(), Utils.createFixedNinePatch(context.getResources(), softwareSurface,
                    (int) (balloonRect.top + pixels.get(SlaveRequiredSizeDP) / 2 - 1), 0,
                    (int) (balloonRect.top + pixels.get(SlaveRequiredSizeDP) / 2 + 1), width, null));
        }
        else if (gravity == GravityLeftOf || gravity == GravityRightOf) {
            slaveDrawable = new NinePatchDrawable(context.getResources(), Utils.createFixedNinePatch(context.getResources(), softwareSurface,
                    height / 2 - 1, (int) (balloonRect.left + pixels.get(SlaveRequiredSizeDP) / 2 - 1),
                    height / 2 + 1, (int) (balloonRect.left + pixels.get(SlaveRequiredSizeDP) / 2 + 1), null));
        }
        else { // centergravity
            slaveDrawable = new NinePatchDrawable(context.getResources(), Utils.createFixedNinePatch(context.getResources(), softwareSurface,
                    (int) (balloonRect.top + pixels.get(SlaveRequiredSizeDP) / 2 - 1), (int) (balloonRect.left + pixels.get(SlaveRequiredSizeDP) / 2 - 1),
                    (int) (balloonRect.top + pixels.get(SlaveRequiredSizeDP) / 2 + 1), (int) (balloonRect.left + pixels.get(SlaveRequiredSizeDP) / 2 + 1), null));
        }
        slaveDrawable.setCallback(this);
        return slaveDrawable;
    }

    protected void onBoundsChange(Rect bounds) {
        onBoundsChange(bounds, false);
    }

    protected void onBoundsChange(Rect bounds, boolean regenerateBitmap) {
        boolean needsNewBitmap = regenerateBitmap;

        if (USE_SLAVE_DRAWABLE) {
            if (gravity == GravityBelow || gravity == GravityAbove) {
                if (width != bounds.width()) needsNewBitmap = true;
            }
            if (gravity == GravityLeftOf || gravity == GravityRightOf) {
                if (height != bounds.height()) needsNewBitmap = true;
            }
            if (gravity == GravityCenter) {
                if (softwareSurface == null) needsNewBitmap = true;
            }
            width = bounds.width();
            height = bounds.height();
        }

        // Nothing to draw in this case
        if (width == 0 || height == 0) return;

        if (USE_SLAVE_DRAWABLE) {
            if (needsNewBitmap) {
                if (gravity == GravityBelow || gravity == GravityAbove) {
                    balloonRect.left = (int) (bounds.left + leftPadding + 0.5f);
                    balloonRect.right = (int) (bounds.right - rightPadding + 0.5f);
                    balloonRect.top = gravity == GravityBelow ? bounds.top + topPadding + indicatorHeight : bounds.top + topPadding;
                    balloonRect.bottom = balloonRect.top + pixels.get(SlaveRequiredSizeDP);
                }
                if (gravity == GravityLeftOf || gravity == GravityRightOf) {
                    balloonRect.left = gravity == GravityRightOf ? bounds.left + leftPadding + indicatorHeight : bounds.left + leftPadding;
                    balloonRect.right = balloonRect.left + pixels.get(SlaveRequiredSizeDP);
                    balloonRect.top = bounds.top + topPadding;
                    balloonRect.bottom = bounds.bottom - bottomPadding;
                }
                if (gravity == GravityCenter) {
                    balloonRect.left =  bounds.left + leftPadding;
                    balloonRect.right = balloonRect.left + pixels.get(SlaveRequiredSizeDP) - rightPadding + leftPadding;
                    balloonRect.top = bounds.top + topPadding;
//                    balloonRect.bottom = balloonRect.top + pixels.get(SlaveRequiredSizeDP) - bottomPadding + 2 * topPadding;
                    balloonRect.bottom = balloonRect.top + pixels.get(SlaveRequiredSizeDP);
                }
                if (slaveDrawable != null) {
                    slaveDrawable.setCallback(null);
                }

                slaveDrawable = createSlave();
                slaveDrawable.setBounds(bounds);
            }
            else {
                if (slaveDrawable == null) {
                    slaveDrawable = createSlave();
                }
                slaveDrawable.setBounds(bounds);
            }
        }
        else {
            balloonRect.left = bounds.left + leftPadding;
            balloonRect.right = bounds.right - rightPadding;
            balloonRect.top = gravity == GravityBelow ? bounds.top + topPadding + indicatorHeight : bounds.top + topPadding;
            balloonRect.bottom = gravity == GravityAbove ? bounds.bottom - bottomPadding - indicatorHeight : bounds.bottom - bottomPadding;

            createPathsWithTranslation(balloonTranslation);
        }

        separateBody = new RectF(balloonRect);
    }

    private boolean enabled;
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    @Override
    public void draw(Canvas canvas) {
        if (USE_SLAVE_DRAWABLE) {
            if (width == 0 || height == 0) {
                return;
            }
            if (DEBUG) Log.d(TAG, "Putting slave to work!");
            slaveDrawable.draw(canvas);
        }
        else {
            drawPopover(canvas);
        }
    }

    public void updatePopoverTranslation() {
//        softwareCanvas.clipPath(updatePath, Region.Op.REPLACE);
        softwareCanvas.clipRect(previousIndicatorRect, Region.Op.REPLACE);
//        softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
//        drawPopover(softwareCanvas);
        softwareCanvas.clipRect(currentIndicatorRect, Region.Op.UNION);
        softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR); // TODO Watch for possible ART Deadly Bug
        drawPopover(softwareCanvas);
//        softwareCanvas.clipRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight());
    }

    public void drawPopover(Canvas canvas) {
        if (canvas.isHardwareAccelerated()) {
            canvas.drawBitmap(softwareDraw(), 0, 0, null);
            return;
        }

        if (DEBUG_DEADLY_ART_BUG) System.gc();
        if (USE_DUAL_SHADOWS) {
            AccentShadowPaint.setMaskFilter(accentShadowMaskFilter);
            AccentShadowPaint.setColor(accentShadowColor);
            canvas.drawPath(accentShadowPath, AccentShadowPaint);
        }
        if (DEBUG_DEADLY_ART_BUG) System.gc();
        ShadowPaint.setMaskFilter(shadowMaskFilter);
        ShadowPaint.setColor(shadowColor);
        canvas.drawPath(shadowPath, ShadowPaint);
        if (DEBUG_DEADLY_ART_BUG) System.gc();
        EdgePaint.setColor(edgeColor);
        canvas.drawPath(balloonPath, EdgePaint);

        FillPaint.setColor(fillColor);
        if (drawsSeparateIndicator) {

            // TODO ideally, this should already be initialized by the time drawing starts..........
//            if (separateBody.width() == 0) {
                Rect bounds = getBounds();
                separateBody.left = balloonRect.left;
                separateBody.right = balloonRect.right;
                separateBody.top = balloonRect.top;
                separateBody.bottom = balloonRect.bottom;
//            }
            canvas.drawRoundRect(separateBody, roundRadius / 2f, roundRadius / 2f, FillPaint);

            if (balloonVisible) {
                FillPaint.setColor(Utils.overlayColors(fillColor, indicatorColor));
                canvas.drawPath(separateIndicatorPath, FillPaint);
            }
        }
        else {
            canvas.drawPath(balloonPath, FillPaint);
        }

//        if (DEBUG_PADDING) {
//            FillPaint.setColor(Color.RED);
//            canvas.drawRect(balloonRect, FillPaint);
//        }
    }

    // In the new drawing format, PopoverDrawable acts as a master drawable, intercepting events from both the callback view
    // and its slave drawable; It creates the main ninepatch bitmap, but the slave drawable is responsible for the actual drawing
    private NinePatchDrawable slaveDrawable;
    private Bitmap softwareSurface;
    private Canvas softwareCanvas;
    private float drawnTranslation;

    public Bitmap softwareDraw() {
        return softwareDraw(true);
    }

    public Bitmap softwareDraw(boolean rebuildBitmap) {
        return softwareDraw(rebuildBitmap, false);
    }

    public Bitmap fastSoftwareDraw() {
        return softwareDraw(false, true);
    }

    public Bitmap softwareDraw(boolean rebuildBitmap, boolean fast) {
        if (USE_SLAVE_DRAWABLE) {
            // With a slave drawable enabled, this is only called when the bitmap needs to be updated
            if (gravity == GravityAbove || gravity == GravityBelow) {
                if (rebuildBitmap || softwareSurface == null) {
                    softwareSurface = Bitmap.createBitmap(width, (int) (pixels.get(SlaveRequiredSizeDP) + leftPadding + bottomPadding + indicatorHeight), Bitmap.Config.ARGB_8888);
                    fast = false;
                }
                if (softwareCanvas == null) softwareCanvas = new Canvas(softwareSurface);
                else softwareCanvas.setBitmap(softwareSurface);

                if (fast) {
                    updatePopoverTranslation();
                }
                else {
                    softwareCanvas.clipRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), Region.Op.REPLACE);
                    if (!rebuildBitmap) {
                        // TODO This call destroys ART
//                        softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                        softwareCanvas.drawRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), ClearPaint);
                    }
                    if (DEBUG_DEADLY_ART_BUG) System.gc();
                    drawPopover(softwareCanvas);
                }
            }
            if (gravity == GravityLeftOf || gravity == GravityRightOf) {
                if (rebuildBitmap || softwareSurface == null) {
                    softwareSurface = Bitmap.createBitmap((int) (pixels.get(SlaveRequiredSizeDP) + leftPadding * 2 + indicatorHeight), height, Bitmap.Config.ARGB_8888);
                    fast = false;
                }
                if (softwareCanvas == null) softwareCanvas = new Canvas(softwareSurface);
                else softwareCanvas.setBitmap(softwareSurface);

                if (fast) {
                    updatePopoverTranslation();
                }
                else {
                    softwareCanvas.clipRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), Region.Op.REPLACE);
                    if (!rebuildBitmap) {
                        // TODO This call destroys ART
//                        softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                        softwareCanvas.drawRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), ClearPaint);
                    }
                    if (DEBUG_DEADLY_ART_BUG) System.gc();
                    drawPopover(softwareCanvas);
                }
            }
            if (gravity == GravityCenter) {
                if (rebuildBitmap || softwareSurface == null) {
                    softwareSurface = Bitmap.createBitmap((int) (pixels.get(SlaveRequiredSizeDP) + leftPadding * 2),
                            (int) (pixels.get(SlaveRequiredSizeDP) + leftPadding + bottomPadding), Bitmap.Config.ARGB_8888);
                    fast = false;
                }
                if (softwareCanvas == null) softwareCanvas = new Canvas(softwareSurface);
                else softwareCanvas.setBitmap(softwareSurface);

                if (fast) {
                    updatePopoverTranslation();
                }
                else {
                    softwareCanvas.clipRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), Region.Op.REPLACE);
                    if (!rebuildBitmap) {
                        // TODO This call destroys ART
//                        softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                        softwareCanvas.drawRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), ClearPaint);
                    }
                    if (DEBUG_DEADLY_ART_BUG) System.gc();
                    drawPopover(softwareCanvas);
                }
            }
            return  softwareSurface;
        }
        else {
            if (softwareSurface == null) {
                softwareCanvas = new Canvas();
                softwareSurface = Bitmap.createBitmap(getBounds().width(), getBounds().height(), Bitmap.Config.ARGB_8888);
                softwareCanvas.setBitmap(softwareSurface);
//                softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                softwareCanvas.drawRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), ClearPaint);
                if (DEBUG_DEADLY_ART_BUG) System.gc();

                drawnTranslation = balloonTranslation;
                draw(softwareCanvas);
            }
            if (softwareSurface.getWidth() != getBounds().width() || softwareSurface.getHeight() != getBounds().height()) {
                softwareSurface = Bitmap.createBitmap(getBounds().width(), getBounds().height(), Bitmap.Config.ARGB_8888);
                softwareCanvas.setBitmap(softwareSurface);
//                softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                softwareCanvas.drawRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), ClearPaint);
                if (DEBUG_DEADLY_ART_BUG) System.gc();

                drawnTranslation = balloonTranslation;
                draw(softwareCanvas);
            }

            if (balloonTranslation != drawnTranslation) {
                softwareCanvas.setBitmap(softwareSurface);
//                softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                softwareCanvas.drawRect(0, 0, softwareCanvas.getWidth(), softwareCanvas.getHeight(), ClearPaint);
                if (DEBUG_DEADLY_ART_BUG) System.gc();

                drawnTranslation = balloonTranslation;
                draw(softwareCanvas);
            }

            // It's only necessary to update the bitmap if the bounds change

            return softwareSurface;
        }
    }

    public void dealloc() {
        softwareSurface = null;
        softwareCanvas = null;
    }

    @Override
    public void setAlpha(int i) {
        alpha = i;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.filter = colorFilter;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidateDrawable(Drawable drawable) {
        getCallback().invalidateDrawable(this);
    }

    @Override
    public void scheduleDrawable(Drawable drawable, Runnable runnable, long l) {
        getCallback().scheduleDrawable(this, runnable, l);
    }

    @Override
    public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
        getCallback().unscheduleDrawable(this, runnable);
    }
}
