package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.BogdanMihaiciuc.receipt.R;
import com.BogdanMihaiciuc.receipt.Receipt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Bogdan on 7/25/13.
 */
public class Utils {

    public final static float SquareRoot = 1.41421356237f;

    public final static int TopLeftCorner = 0;
    public final static int TopRightCorner = 1;
    public final static int BottomLeftCorner = 2;
    public final static int BottomRightCorner = 3;

    public final static int TopSide[] = {TopLeftCorner, TopRightCorner};
    public final static int BottomSide[] = {BottomLeftCorner, BottomRightCorner};
    public final static int AllCorners[] = {TopLeftCorner, TopRightCorner, BottomLeftCorner, BottomRightCorner};

    public interface HierarchyController {
        public FrameLayout getRoot();
        public ViewGroup getContentRoot();
        public ViewGroup getActionBarRoot();
        public ViewGroup getActionBarContainer();
        public View getContent();
    }

    public interface BackStack {
        public void pushToBackStack(Runnable r);
        public void popBackStackFrom(Runnable r);
        public void rewindBackStackFrom(Runnable r);
        public void swipeFromBackStack(Runnable r);
        public boolean popBackStack();

        public BackStack persistentBackStack();
        public int backStackSize();
    }

    public interface RippleAnimationStack {
        public void flushRipples();
//        public void flushDrawableRipples(LegacyRippleDrawable drawable);
        public void addRipple(Animator a);
        public void removeRipple(Animator a);
    }

    public static class FrictionInterpolator implements TimeInterpolator {
        private TimeInterpolator accelerator, decelerator;

        public FrictionInterpolator() {
            this(1f);
        }

        public FrictionInterpolator(float friction) {
            accelerator = new AccelerateInterpolator(friction);
            decelerator = new DecelerateInterpolator(friction);
        }

        @Override
        public float getInterpolation(float fraction) {
            if (fraction < 0.5f) {
                fraction = accelerator.getInterpolation(fraction * 2f) / 2f;
            } else {
                fraction = decelerator.getInterpolation((fraction - 0.5f) * 2f) / 2f + 0.5f;
            }
            return fraction;
        }
    }

    public static class CompoundInterpolator implements TimeInterpolator {
        private static CompoundInterpolator frictionOvershoot = new CompoundInterpolator(new FrictionInterpolator(1.5f), new OvershootInterpolator(3f));
        public static CompoundInterpolator frictionOvershootInterpolator() { return  frictionOvershoot; }

        private TimeInterpolator[] interpolators;

        public CompoundInterpolator(TimeInterpolator ... interpolators) {
            this.interpolators = interpolators;
        }

        @Override
        public float getInterpolation(float fraction) {
            for (TimeInterpolator interpolator : interpolators) {
                fraction = interpolator.getInterpolation(fraction);
            }

            return fraction;
        }
    }

    public static class GravityInterpolator implements TimeInterpolator {
        private TimeInterpolator accelerator, decelerator;

        public GravityInterpolator() {
            this(1f);
        }

        public GravityInterpolator(float friction) {
            accelerator = new AccelerateInterpolator(friction);
            decelerator = new DecelerateInterpolator(friction);
        }

        @Override
        public float getInterpolation(float fraction) {
            if (fraction < 0.5f) {
                fraction = decelerator.getInterpolation(fraction * 2f) / 2f;
            } else {
                fraction = accelerator.getInterpolation((fraction - 0.5f) * 2f) / 2f + 0.5f;
            }
            return fraction;
        }
    }

    public static class BounceCycleInterpolator implements TimeInterpolator {
        private TimeInterpolator innerInterpolator;

        public BounceCycleInterpolator(float friction) {
            innerInterpolator = new GravityInterpolator(friction);
        }

        @Override
        public float getInterpolation(float v) {
            if (v < 0.5f) {
                return innerInterpolator.getInterpolation(v) * 2;
            }
            else {
                return 2 - innerInterpolator.getInterpolation(v) * 2;
            }
        }
    }

    PointF bezierPoint(float t, Point s, Point c1, Point e, Point c2)
    {
        float u = 1 - t;
        float tt = t*t;
        float uu = u*u;
        float uuu = uu * u;
        float ttt = tt * t;

        PointF p = new PointF(s.x * uuu, s.y * uuu);
        p.x += 3 * uu * t * c1.x;
        p.y += 3 * uu * t * c1.y;
        p.x += 3 * u * tt * c2.x;
        p.y += 3 * u * tt * c2.y;
        p.x += ttt * e.x;
        p.y += ttt * e.y;

        return p;
    }

    public static float bezierX(float fraction, Point source, Point sourceCurve, Point target, Point targetCurve) {
        float u = 1 - fraction;
        float tt = fraction*fraction;
        float uu = u*u;
        float uuu = uu * u;
        float ttt = tt * fraction;

        float x = source.x * uuu;
        x += 3 * uu * fraction * sourceCurve.x;
        x += 3 * u * tt * targetCurve.x;
        x += ttt * target.x;

        return x;
    }

    public static float bezierY(float fraction, Point source, Point sourceCurve, Point target, Point targetCurve) {
        float u = 1 - fraction;
        float tt = fraction*fraction;
        float uu = u*u;
        float uuu = uu * u;
        float ttt = tt * fraction;

        float y = source.y * uuu;
        y += 3 * uu * fraction * sourceCurve.y;
        y += 3 * u * tt * targetCurve.y;
        y += ttt * target.y;

        return y;
    }

    public static float interpolateValues(float fraction, float start, float end) {
        return start + (end - start) * fraction;
    }

    public static float getIntervalPercentage(float fraction, float start, float end) {
        return (fraction - start) / (end - start);
    }

    public static int getClosestMultiple(int value, int multiple) {
        int multiplier = 1;
        if (value < 0) {
            value = - value;
            multiplier = -1;
        }

        int lowerMultiple = value / multiple;
        lowerMultiple *= multiple;
        int higherMultiple = lowerMultiple + multiple;

        if (value - lowerMultiple < higherMultiple - value) {
            return multiplier * lowerMultiple;
        }
        else {
            return multiplier * higherMultiple;
        }
    }

    public static int distanceFromMultiple(int value, int multiple) {
        int multiplier = 1;
        if (value < 0) {
            value = - value;
            multiplier = -1;
        }

        int lowerMultiple = value / multiple * multiple;
        if (multiplier == -1) lowerMultiple += multiple;

        if (multiplier == -1) return (lowerMultiple - value);
        return (value - lowerMultiple);
    }

    public static float constrain(float value, float minimum, float maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }

    public static boolean isWithinInterval(float value, float minimum, float maximum) {
        return value >= minimum && value <= maximum;
    }

    public static float getConstrainedIntervalPercentage(float fraction, float start, float end) {
        return constrain(getIntervalPercentage(fraction, start, end), 0, 1);
    }

    public static float[] obtainScaledArray(float[] source, float factor) {
        float [] scaledArray = new float [source.length];
        System.arraycopy(source, 0, scaledArray, 0, source.length);

        scaleArray(scaledArray, factor);

        return scaledArray;
    }

    public static void scaleArray(float[] source, float factor) {
        int length = source.length;
        for (int i = 0; i < length; i++) {
            source[i] = source[i] * factor;
        }
    }

    public static void outputScaledRect(Rect input, float scaleX, float scaleY, Rect output) {
        output.top = (int) (input.top * scaleY);
        output.bottom = (int) (input.bottom * scaleY);
        output.left = (int) (input.left * scaleX);
        output.right = (int) (input.right * scaleX);
    }

    public static void insetRect(Rect input, int left, int top, int right, int bottom) {
        input.left += left;
        input.top += top;
        input.right -= right;
        input.bottom -= bottom;
    }

    public static int interpolateColors(float fraction, int start, int end) {
        return Color.argb(
                (int) (interpolateValues(fraction, Color.alpha(start), Color.alpha(end))),
                (int) (interpolateValues(fraction, Color.red(start), Color.red(end))),
                (int) (interpolateValues(fraction, Color.green(start), Color.green(end))),
                (int) (interpolateValues(fraction, Color.blue(start), Color.blue(end)))
        );
    }

    public static int transparentColor(float alpha, int color) {
        return Color.argb((int) (alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int transparentColor(int alpha, int color) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int interpolatedTransparentColor(float alpha, int color) {
        return Color.argb((int) (alpha * Color.alpha(color)), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int overlayColors(int source, int overlay) {
        float overlayAlpha = Color.alpha(overlay) / 255f;

        return Color.argb(
                (int) (Color.alpha(overlay) + Color.alpha(source) * (1 - overlayAlpha)),
                (int) Utils.interpolateValues(overlayAlpha, Color.red(source), Color.red(overlay)),
                (int) Utils.interpolateValues(overlayAlpha, Color.green(source), Color.green(overlay)),
                (int) Utils.interpolateValues(overlayAlpha, Color.blue(source), Color.blue(overlay))
        );
    }

    public static boolean arrayContainsInt(int[] array, int value) {
        for (int i : array) {
            if (i == value) return true;
        }

        return false;
    }

    /**
     * This class contains a pair of two integers, which represent the beginning and ending of a range.
     */
    public static class Range {
        int start;
        int end;

        /**
         * Creates a new range.
         * @param start The start of the range.
         * @param end The end of the range.
         * @return The newly created range.
         */
        public static Range make(int start, int end) {
            Range range = new Range();
            range.start = start;
            range.end = end;
            return range;
        }
    }

    public static SpannableStringBuilder appendWithSpan(SpannableStringBuilder target, CharSequence text, Object span) {
        target.append(text);
        target.setSpan(span, target.length() - text.length(), target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return target;
    }



    /**
     * Appends a new block text to the supplied {@link android.text.SpannableStringBuilder SpannableStringBuilder}.
     * Portions of this text may be enclosed within square brackets (e.g.: This text has a <strong>[spanned]</strong> region.), which indicates that a certain span will be applied to them.
     * Spanned regions may be nested.
     * @param target The {@link android.text.SpannableStringBuilder SpannableStringBuilder} to which the text will be added.
     * @param text The text to be added.
     * @param spans The ordered list of spans that will be applied. The order of these spans must match the order of the span regions within the text.
     * @throws ArrayIndexOutOfBoundsException If there are fewer spans than span regions, or if the brackets aren't balanced.
     * @throws IllegalArgumentException If the brackets aren't balanced.
     * @return The target {@link android.text.SpannableStringBuilder SpannableStringBuilder}.
     */
    public static SpannableStringBuilder appendWithEscapedSpan(SpannableStringBuilder target, CharSequence text, Object ... spans) {
        return appendWithEscapedSpan(target, text, '[', ']', spans);
    }

    /**
     * Appends a new block text to the supplied {@link android.text.SpannableStringBuilder SpannableStringBuilder}.
     * Portions of this text may be enclosed within special characters, which indicates that a certain span will be applied to them.
     * Spanned regions may be nested.
     * @param target The {@link android.text.SpannableStringBuilder SpannableStringBuilder} to which the text will be added.
     * @param text The text to be added.
     * @param startLimiter The special character that marks the beginning of a spanned region.
     * @param endLimiter The special character that marks the end of a spanned region.
     * @param spans The ordered list of spans that will be applied. The order of these spans must match the order of the span regions within the text.
     * @throws ArrayIndexOutOfBoundsException If there are fewer spans than span regions, or if the start and end limiters aren't balanced.
     * @throws IllegalArgumentException If the start and end limiters aren't balanced.
     * @return The target {@link android.text.SpannableStringBuilder SpannableStringBuilder}.
     */
    public static SpannableStringBuilder appendWithEscapedSpan(SpannableStringBuilder target, CharSequence text, char startLimiter, char endLimiter, Object ... spans) {
        ArrayList<Character> limiterStack = new ArrayList<Character>();
        ArrayList<Range> spanStack = new ArrayList<Range>();

        // TODO


        return target;
    }

    public static SpannableStringBuilder titleFormattedText(CharSequence text) {
        SpannableStringBuilder title = Utils.appendWithSpan(new SpannableStringBuilder(), text, new AbsoluteSizeSpan(24, true));
        title.setSpan(new Utils.CustomTypefaceSpan(Utils.CondesedTypeface), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return title;
    }

    public static SpannableStringBuilder coloredItalicText(CharSequence text, int color) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        ssb.setSpan(new StyleSpan(Typeface.ITALIC), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(color), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }

    public static CharSequence echoText(CharSequence text, int color) {
        return text;
    }

    public static class CustomTypefaceSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        public CustomTypefaceSpan(final Typeface typeface)
        {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(final TextPaint drawState)
        {
            apply(drawState);
        }

        @Override
        public void updateMeasureState(final TextPaint paint)
        {
            apply(paint);
        }

        private void apply(final Paint paint)
        {
            final Typeface oldTypeface = paint.getTypeface();
            final int oldStyle = oldTypeface != null ? oldTypeface.getStyle() : 0;
            final int fakeStyle = oldStyle & ~typeface.getStyle();

            if ((fakeStyle & Typeface.BOLD) != 0)
            {
                paint.setFakeBoldText(true);
            }

            if ((fakeStyle & Typeface.ITALIC) != 0)
            {
                paint.setTextSkewX(-0.25f);
            }

            paint.setTypeface(typeface);
        }
    }

    public static class OnTextChangedListener implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }

    public static class DPTranslator {
        float density;

        public DPTranslator(float density) {this.density = density;}

        public int get(float source) {
            return (int) (source * density + 0.5f);
        }

        public float getDensity() { return density; }
    }

    public static class ViewUtils {
        public static void cancelAllAnimationsInViewGroup(ViewGroup root) {
            for (int i = 0, size = root.getChildCount(); i < size; i++) {
                View view = root.getChildAt(i);
                view.animate().setListener(null).cancel();
                if (view instanceof ViewGroup) {
                    cancelAllAnimationsInViewGroup((ViewGroup) view);
                }
            }
        }

        public static void centerViewOnPoint(View view, float x, float y) {
            view.setX(x - view.getWidth() / 2f);
            view.setY(y - view.getHeight() / 2f);
        }

        private final static Rect rect = new Rect();
        public static void centerViewOnWindowPoint(View view, float x, float y) {
            // todo partially invisible views

            view.setTranslationX(0);
            view.setTranslationY(0);

            view.getGlobalVisibleRect(rect);

            view.setX(view.getX() + (x - rect.centerX()));
            view.setY(view.getY() + (y - rect.centerY()));
        }

        public static void positionViewOnWindowPoint(View view, int x, int y) {
            // todo partially invisible views

            view.setTranslationX(0);
            view.setTranslationY(0);

            view.getGlobalVisibleRect(rect);

            view.setX(view.getX() + (x - rect.left));
            view.setY(view.getY() + (y - rect.top));
        }

        public static void displaceView(View view, float x, float y) {
            view.setTranslationX(view.getTranslationX() + x);
            view.setTranslationY(view.getTranslationY() + y);
        }

        public static void centerViewMatrixOnPoint(View view, Matrix matrix, float x, float y) {
            matrix.setTranslate(
                    x - view.getWidth() / 2f - view.getLeft(),
                    y - view.getHeight() / 2f - view.getTop()
            );
        }

        public static void resetViewProperties(View view) {
            view.setTranslationY(0f);
            view.setTranslationX(0f);
            view.setAlpha(1f);
            view.setRotation(0f);
            view.setRotationY(0f);
            view.setRotationX(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
        }

        public static View screenshotView(View source) {
            return screenshotView(source, source.getWidth(), source.getHeight());
        }

        public static View screenshotView(View source, int width, int height) {
            if (width <= 0) {
                Log.e("", "Trying to create bitmap with width = 0");
                width = source.getLayoutParams().width;
            }
            if (width <= 0) width = 1;
            if (height <= 0) {
                Log.e("", "Trying to create bitmap with height = 0");
                height = source.getLayoutParams().height;
            }
            if (height <= 0) height = 1;
            View screenshotView = new View(source.getContext());

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            source.draw(new Canvas(bitmap));
            screenshotView.setBackgroundDrawable(new BitmapDrawable(bitmap));

            return screenshotView;
        }

        public static final View.OnTouchListener DisablerTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        };
    }

    static class RoundedCornerDrawable extends Drawable {

        private int[] corners;
        private float radius;
        private int color;

        private Rect squareComponents[];
        private RectF arcs[];

        final static Paint RoundPaint;

        static {
            RoundPaint = new Paint();
            RoundPaint.setAntiAlias(true);
            RoundPaint.setStyle(Paint.Style.FILL);
        }

        public static RoundedCornerDrawable roundedCornersWithRadiusAndColor(int[] corners, float radius, int color) {
            return new RoundedCornerDrawable(corners, radius, color);
        }

        private RoundedCornerDrawable(int[] corners, float radius, int color) {
            arcs = new RectF[corners.length];

            this.corners = corners;
            this.radius = radius / 2;
            this.color = color;

            squareComponents = new Rect[3];
        }

        public void onBoundsChange(Rect bounds) {
            squareComponents[0] = new Rect((int) (bounds.left + radius), bounds.top, (int) (bounds.right - radius), bounds.bottom);

            int topDisplacement = Utils.arrayContainsInt(corners, TopLeftCorner) ? (int) (bounds.top + radius) : bounds.top;
            int bottomDisplacement = Utils.arrayContainsInt(corners, BottomLeftCorner) ? (int) (bounds.bottom - radius) : bounds.bottom;

            squareComponents[1] = new Rect(bounds.left, topDisplacement, (int) (bounds.left + radius), bottomDisplacement);

            topDisplacement = Utils.arrayContainsInt(corners, TopRightCorner) ? (int) (bounds.top + radius) : bounds.top;
            bottomDisplacement = Utils.arrayContainsInt(corners, BottomRightCorner) ? (int) (bounds.bottom - radius) : bounds.bottom;

            squareComponents[2] = new Rect((int) (bounds.right - radius), topDisplacement, bounds.right, bottomDisplacement);

            int arcIndex = 0;
            if (Utils.arrayContainsInt(corners, TopLeftCorner)) {
                arcs[arcIndex] = new RectF(bounds.left, bounds.top, (int) (bounds.left + radius), (int) (bounds.top + radius));
                arcs[arcIndex].right += arcs[arcIndex].width();
                arcs[arcIndex].bottom += arcs[arcIndex].height();
                arcIndex++;
            }
            if (Utils.arrayContainsInt(corners, BottomLeftCorner)) {
                arcs[arcIndex] = new RectF(bounds.left, (int) (bounds.bottom - radius), (int) (bounds.left + radius), bounds.bottom);
                arcs[arcIndex].right += arcs[arcIndex].width();
                arcs[arcIndex].top -= arcs[arcIndex].height();
                arcIndex++;
            }
            if (Utils.arrayContainsInt(corners, TopRightCorner)) {
                arcs[arcIndex] = new RectF((int) (bounds.right - radius), bounds.top, bounds.right, (int) (bounds.top + radius));
                arcs[arcIndex].left -= arcs[arcIndex].width();
                arcs[arcIndex].bottom += arcs[arcIndex].height();
                arcIndex++;
            }
            if (Utils.arrayContainsInt(corners, BottomRightCorner)) {
                arcs[arcIndex] = new RectF((int) (bounds.right - radius), (int) (bounds.bottom - radius), bounds.right, bounds.bottom);
                arcs[arcIndex].left -= arcs[arcIndex].width();
                arcs[arcIndex].top -= arcs[arcIndex].height();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            RoundPaint.setColor(color);

            for (Rect r : squareComponents) {
                canvas.drawRect(r, RoundPaint);
            }

            int arcIndex = 0;
            if (Utils.arrayContainsInt(corners, TopLeftCorner)) {
                canvas.drawArc(arcs[arcIndex], 180, 90, true, RoundPaint);
                arcIndex++;
            }
            if (Utils.arrayContainsInt(corners, BottomLeftCorner)) {
                canvas.drawArc(arcs[arcIndex], 90, 90, true, RoundPaint);
                arcIndex++;
            }
            if (Utils.arrayContainsInt(corners, TopRightCorner)) {
                canvas.drawArc(arcs[arcIndex], 270, 90, true, RoundPaint);
                arcIndex++;
            }
            if (Utils.arrayContainsInt(corners, BottomRightCorner)) {
                canvas.drawArc(arcs[arcIndex], 0, 90, true, RoundPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter cf) {

        }

        @Override
        public int getOpacity() {
            return 0;
        }
    }

    static public LegacyRippleDrawable getSelectedColors(Context context) {
//        StateListDrawable selectedColors;
//        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT) {
//            selectedColors = new StateListDrawable();
//            selectedColors.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Resources.getSystem().getColor(android.R.color.holo_blue_dark)));
//            selectedColors.addState(StateSet.WILD_CARD, new ColorDrawable(Resources.getSystem().getColor(android.R.color.holo_blue_light)));
//        }
//        else {
//            selectedColors = (new StateListDrawable());
//            selectedColors.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Utils.transparentColor(50, 0)));
//            selectedColors.addState(StateSet.WILD_CARD, new ColorDrawable(Utils.transparentColor(25, 0)));
//        }

        LegacyRippleDrawable selectedColors = new LegacyRippleDrawable(context);
        selectedColors.setColors(Utils.transparentColor(0.75f, Resources.getSystem().getColor(android.R.color.holo_blue_light)),
                Utils.transparentColor(0.80f, Resources.getSystem().getColor(android.R.color.holo_blue_dark)));

        return selectedColors;
    }

    static public LegacyRippleDrawable getDeselectedColors(Context context) {
//        StateListDrawable deselectedColors;
//        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT) {
//            deselectedColors = (new StateListDrawable());
//            deselectedColors.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Resources.getSystem().getColor(android.R.color.holo_blue_dark)));
//            deselectedColors.addState(StateSet.WILD_CARD, new ColorDrawable(Resources.getSystem().getColor(android.R.color.transparent)));
//        }
//        else {
//            deselectedColors = (new StateListDrawable());
//            deselectedColors.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Utils.transparentColor(50, 0)));
//            deselectedColors.addState(StateSet.WILD_CARD, new ColorDrawable(Resources.getSystem().getColor(android.R.color.transparent)));
//        }

        return new LegacyRippleDrawable(context);
    }

    public static class DisableableRelativeLayout extends RelativeLayout {

        private boolean disabled;
        private boolean isTransient;

        public boolean isDisabled() {
            return disabled;
        }

        public void disable() {
            disabled = true;
        }

        public void enable() {
            disabled = false;
        }

        public void setTransientEnabled(boolean enabled) {
            isTransient = enabled;
        }

        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (isTransient) return false;
            if (disabled) return true;
            else return super.onInterceptTouchEvent(event);
        }

        public boolean onTouchEvent(MotionEvent event) {
            if (isTransient) return false;
            if (disabled) return true;
            else return super.onTouchEvent(event);
        }

        public DisableableRelativeLayout(Context context) {
            super(context);
        }

        public DisableableRelativeLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DisableableRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }
    }


    public static class ClippedLayout extends DisableableFrameLayout {

        private ArrayList<Rect> clips = new ArrayList<Rect>();
        private ArrayList<Rect> punches = new ArrayList<Rect>();
        private boolean dirty;

        public void addDrawArea(Rect rect) {
            clips.add(rect);
        }

        public void addPunchedArea(Rect rect) {
            dirty = true;
            punches.add(rect);
        }

        public void removeClips() {
            clips.clear();
        }

        /**
         * Optimizes the clips and allows the use addPunchedArea().
         * The current implementation only supports punching one rect out of one other rect.
         */
        public void buildClips() {
            dirty = false;

            ArrayList<Rect> finalClips = new ArrayList<Rect>();

            Rect drawArea = clips.get(0);
            Rect punchArea = punches.get(0);

            if (drawArea.left < punchArea.left) {
                finalClips.add(new Rect(drawArea.left, drawArea.top, punchArea.left, drawArea.bottom));
            }

            if (drawArea.right > punchArea.right) {
                finalClips.add(new Rect(punchArea.right, drawArea.top, drawArea.right, drawArea.bottom));
            }

            if (drawArea.top < punchArea.top) {
                finalClips.add(new Rect(Math.max(drawArea.left, punchArea.left), drawArea.top,
                                        Math.min(drawArea.right, punchArea.right), punchArea.top));
            }

            if (drawArea.bottom > punchArea.bottom) {
                finalClips.add(new Rect(Math.max(drawArea.left, punchArea.left), punchArea.bottom,
                                Math.min(drawArea.right, punchArea.right), drawArea.bottom));
            }

            this.clips = finalClips;
        }

        public void dispatchDraw(Canvas canvas) {
            if (dirty) {
                throw new IllegalStateException("ClippedLayout.buildClips() must be called after addPunchedArea()");
            }

            if (clips.size() > 0) {
                canvas.clipRect(clips.get(0));
            }

            if (clips.size() > 1) {
                for (Rect r : clips){
                    canvas.clipRect(r, Region.Op.UNION);
                }
            }

            super.dispatchDraw(canvas);
        }


        public ClippedLayout(Context context) {
            super(context);
        }

        public ClippedLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public ClippedLayout(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        public ClippedLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

    }


    public static NinePatch createFixedNinePatch(Resources res, Bitmap bitmap, int top, int left, int bottom, int right, String srcName){
        ByteBuffer buffer = getByteBufferFixed(top, left, bottom, right);
        NinePatch patch = new NinePatch(bitmap, buffer.array(), srcName);
        return patch;
    }

    public static ByteBuffer getByteBufferFixed(int top, int left, int bottom, int right) {
        //Docs check the NinePatchChunkFile
        ByteBuffer buffer = ByteBuffer.allocate(84).order(ByteOrder.nativeOrder());
        //was translated
        buffer.put((byte)0x01);
        //divx size
        buffer.put((byte)0x02);
        //divy size
        buffer.put((byte)0x02);
        //color size
        buffer.put(( byte)0x09);

        //skip
        buffer.putInt(0);
        buffer.putInt(0);

        //padding
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);

        //skip 4 bytes
        buffer.putInt(0);

        buffer.putInt(left);
        buffer.putInt(right);
        buffer.putInt(top);
        buffer.putInt(bottom);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        return buffer;
    }

    // ************************************ VIEWPROXY EXTENSIONS ******************************

    public final static String Completion = "U$Completion";

    static {
        $.extendProperty(Completion, new $.Property<Float>() {
            @Override
            public void set(View view, Float value) {
                if (view instanceof GraphView) {
                    ((GraphView) view).setCompletion(value);
                }
                else if (view instanceof PieChartView) {
                    ((PieChartView) view).setCompletion(value);
                }
            }

            @Override
            public Float get(View view) {
                if (view instanceof GraphView) {
                    ((GraphView) view).getCompletion();
                }
                else if (view instanceof PieChartView) {
                    ((PieChartView) view).getCompletion();
                }
                return 0f;
            }
        });
    }


    // ************************************ STANDARD RESOURCES ******************************

    public static final int PrimaryKeyline = R.dimen.PrimaryKeyline;
    public final static int SecondaryKeyline = R.dimen.SecondaryKeyline;
    public final static int LineHeight = R.dimen.LineHeight;
    public final static int TextSize = R.dimen.TextSize;
    public final static int GenericHeaderHeight = R.dimen.GenericHeaderHeight;
    public final static int ActionBarSize = R.dimen.ActionBarSize;
    public final static int DashboardText = R.color.DashboardText;
    public final static int DashboardTitle = R.color.DashboardTitle;
    public final static int DashboardSeparator = R.color.DashboardSeparator;
    public final static int DashboardSeparatorTransparent = R.color.DashboardSeparatorTransparent;
    public final static int HistoryHeader = R.color.HistoryHeader;
    public final static int HeaderSeparator = R.color.HeaderSeparator;

    public final static int AccentColor = R.color.HeaderCanCheckout;
    public final static int ActionModeColor = R.color.SelectionBar;
    public final static int ConfirmationTitlebar = R.drawable.actionbar_confirmation_round;

    // Generic actionbar icons
    public final static int CaretUpLight = R.drawable.caret_up_light;
    public final static int OverflowLight = R.drawable.ic_action_overflow_light;
    public final static int Done = R.drawable.ic_action_done;
    public final static int DoneDark = R.drawable.ic_action_done_dark;
    public final static int Spinner = R.drawable.menu_dropdown_panel_receipt;
    public final static int Cut = R.drawable.ic_action_cut;
    public final static int Copy = R.drawable.ic_action_copy;
    public final static int Paste = R.drawable.paste;
    public final static int SelectAll = R.drawable.ic_action_select_all;
    public final static int BackLight = R.drawable.back_light_centered;
    public final static int BackDark = R.drawable.back_dark_centered;
    public final static int UndoIcon = R.drawable.undo_arrow;
    public final static int UndoIconDark = R.drawable.undo_arrow_dark;
    public final static int CalendarIconDark = R.drawable.ic_grouper_dark;

    public final static int ConfirmOKID = R.id.ConfirmOK;
    public final static int UndoID = R.id.Undo;

    public final static int MetadataKey = R.id.MetadataKey;
    public final static int CollectionTagKey = R.id.CollectionTagKey;

    public final static int ConfirmatorLayout = R.layout.delete_overlay;
    public final static int ConfirmatorBackground = R.drawable.undo_panel;

    public final static int DoneLabel = R.string.ActionModeDone;
    public final static int OKLabel = R.string.OKButtonLabel;
    public final static int UndoLabel = R.string.UndoLabel;

    // Window animation styles
    public final static int PopoverDialogAnimation = R.style.popover_dialog_animation;
    public final static int PopoverDialogAnimationLand = R.style.popover_dialog_animation_land;
    public final static int ModalDialogAnimation = R.style.modal_dialog_animation;

    public final static int SpinnerItemLayout = R.layout.layout_spinner_item;

    public final static Typeface DefaultTypeface = Receipt.defaultTypeface();
    public final static Typeface CondesedTypeface = Receipt.condensedTypeface();
    public final static Typeface CondensedBoldTypeface = Receipt.condensedBoldTypeface();
    public final static Typeface CondensedLightTypeface = Receipt.condensedLightTypeface();
    public final static Typeface MediumTypeface = Receipt.mediumTypeface();


    protected final static int Dragon = R.drawable.dragon_head;
    protected final static int DragonWindow = R.drawable.dragon_window;

}
