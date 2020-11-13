package com.BogdanMihaiciuc.util;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.IntDef;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import android.os.Handler;

import org.json.JSONObject;

import static com.BogdanMihaiciuc.util.Utils.MetadataKey;

public class $ {

    final static String TAG = $.class.getName();

    final static boolean DEBUG_QUERY = false;
    final static boolean DEBUG_LAYOUT = false;
    final static boolean DEBUG_EVENT_LISTENER = false;
    final static boolean DEBUG_XHR = false;

    public enum Op {
        Set, Add, Scale, Replace
    }

    public interface Predicate {
        boolean viewMatches(View view);
    }

    /**
     * The root of the Resources class file
     */
    private static Class RClass;

    /**
     * The root of the IDs class file
     */
    private static Class IDClass;

    /**
     * Allows ViewProxy to use the actual names of View IDs when using query strings.
     * Must be called before any query string with readable IDs is used, preferably in the App Delegate's onCreate callback.
     * This method only needs to be called once.
     * @param R The R class specific to your aplication e.g. <strong>com.example.App.R.class</strong>
     */
    public static void initializeResources(Class R) {
        RClass = R;

        Class IDClasses[] = RClass.getDeclaredClasses();
        for (int i = 0; i < IDClasses.length; i++) {
            if (IDClasses[i].getSimpleName() == "id") {
                IDClass = IDClasses[i];
                break;
            }
        }
    }

    // region Predicates

    /**
     * A metadata key that retrieves the view's tag via {@link View#getTag()}.
     */
    public final static String TagMetadataKey = "$Tag";

    /**
     * Constructs a new Predicate from the supplied query string.
     * The query string is a collection of expression strings joined by ",".
     * All spaces within the query string are removed.
     * At least one of the expression strings must evaluate to true for the matching view to be considered.
     * @param string The query string.
     * @return A new corresponding predicate.
     */
    private static Predicate predicateFromQueryString(String string) {
        string = string.replaceAll("\\s+","");

        String orComponents[] = string.split(",");

        if (orComponents.length > 1) {
            Predicate components[] = new Predicate[orComponents.length];

            for (int i = 0; i < orComponents.length; i++) {
                components[i] = predicateFromExpression(orComponents[i].trim());
            }

            return or(components);
        }
        else {
            if (DEBUG_QUERY) Log.d(TAG, "Query is a simple predicate!");
            return predicateFromExpression(string);
        }
    }

    /**
     * Constructs a new predicate from the supplied expression string.
     * The expression string must be [[@]ViewClass][#id][.Metadata[=Value]][.Metadata[=Value]]...
     * The conditions must all be true for the matching view to be considered.
     * @param expression The expression string.
     * @return A new corresponding predicate.
     */
    private static Predicate predicateFromExpression(String expression) {
        ArrayList<Predicate> components = new ArrayList<Predicate>();

        // ClassType condition
        if (!expression.startsWith(".") && !expression.startsWith("#")) {

            if (DEBUG_QUERY) Log.d(TAG, "Checking className clause");

            int bounds = Math.min(expression.indexOf('.'), expression.indexOf('#'));
            boolean strict = expression.startsWith("@");


            if (bounds == -1) {
                if (DEBUG_QUERY) Log.d(TAG, "Adding className clause " + expression);
                return strict
                        ? strictClassPredicate(expression.substring(1))
                        : classPredicate(expression);
            }
            else {
                if (DEBUG_QUERY) Log.d(TAG, "Adding className clause " + expression.substring(0, bounds));
                components.add(strict
                                ? strictClassPredicate(expression.substring(1, bounds))
                                : classPredicate(expression.substring(0, bounds))
                );

                expression = expression.substring(bounds);
            }
        }

        if (expression.startsWith("#")) {
            if (DEBUG_QUERY) Log.d(TAG, "Checking id");

            int bounds = expression.indexOf('.');

            if (bounds == -1) {
                if (DEBUG_QUERY) Log.d(TAG, "Adding id clause " + Integer.valueOf(
                        expression.substring(1, expression.length())
                ));



                components.add(idPredicate(
                                Integer.valueOf(
                                        expression.substring(1, expression.length())
                                )
                        )
                );
            }
            else {
                if (DEBUG_QUERY) Log.d(TAG, "Adding id clause " + Integer.valueOf(
                        expression.substring(1, bounds)
                ));
                components.add(idPredicate(
                                Integer.valueOf(
                                        expression.substring(1, bounds)
                                )
                        )
                );

                expression = expression.substring(bounds);
            }
        }

        if (expression.startsWith(".")) {
            String metadataComponents[] = expression.split("\\.");

            if (DEBUG_QUERY) Log.d(TAG, "Checking metadata(" + expression + "): " + metadataComponents.length + " clauses!");

            for (String metadataComponent : metadataComponents) {
                if (metadataComponent.length() == 0) continue;

                int valueDelimiter = metadataComponent.indexOf('=');
                if (valueDelimiter != -1) {
                    if (DEBUG_QUERY) Log.d(TAG, "Adding metadata+value clause " + metadataComponent);
                    components.add(metadataValuePredicate(
                            metadataComponent.substring(0, valueDelimiter),
                            metadataComponent.substring(valueDelimiter + 1, metadataComponent.length())
                    ));
                }
                else {
                    if (DEBUG_QUERY) Log.d(TAG, "Adding metadata clause " + metadataComponent);
                    components.add(metadataPredicate(metadataComponent));
                }
            }
        }

        if (DEBUG_QUERY) Log.d(TAG, "Components found: " + components.size());
        return and(components.toArray(new Predicate[components.size()]));
    }

    private static Predicate idPredicate(final int ... IDs) {
        return view -> IDs.length == 0 || Utils.arrayContainsInt(IDs, view.getId());
    }

    private static Predicate metadataPredicate(final String Metadata) {
        return view -> $.hasMetadata(view, Metadata);
    }

    private static Predicate metadataValuePredicate(final String Key, final Object Value) {
        return view -> Value.equals($.metadata(view, Key));
    }

    private static Predicate strictClassPredicate(final String ClassName) {
        return view -> {
            try {
                return Class.forName("android.view." + ClassName) == view.getClass();
            } catch (ClassNotFoundException e) {
                try {
                    return Class.forName("android.widget." + ClassName) == view.getClass();
                } catch (ClassNotFoundException e2) {
                    if (DEBUG_QUERY) e2.printStackTrace();
                    return false;
                }
            }
        };
    }

    private static Predicate classPredicate(final String ClassName) {
        return view -> {
            try {
                return Class.forName("android.view." + ClassName).isInstance(view);
            } catch (ClassNotFoundException e) {
                try {
                    return Class.forName("android.widget." + ClassName).isInstance(view);
                } catch (ClassNotFoundException e2) {
                    if (DEBUG_QUERY) e2.printStackTrace();
                    return false;
                }
            }
        };
    }

    private static Predicate and(final Predicate ... predicates) {
        return view -> {
            for (Predicate predicate : predicates) {
                if (!predicate.viewMatches(view)) return false;
            }
            return true;
        };
    }

    private static Predicate or(final Predicate ... predicates) {
        return view -> {
            for (Predicate predicate : predicates) {
                if (predicate.viewMatches(view)) return true;
            }
            return false;
        };
    }
    //endregion

    //region View Properties

    public final static String X = "x";
    public final static String TranslateX = "translationX";
    public final static String Y = "y";
    public final static String TranslateY = "translationY";

    public final static String CenterX = "$centerX";
    public final static String CenterY = "$centerY";

    public final static String RotateX = "rotationX";
    public final static String RotateY = "rotationY";
    public final static String RotateZ = "rotation";
    public final static String Rotate = RotateZ;

    public final static String ScaleX = "scaleX";
    public final static String ScaleY = "scaleY";

    public final static String Z = "elevation";
    public final static String TranslateZ = "translationZ";
    public final static String Opacity = "opacity";

    public final static String TargetWidth = "targetWidth";
    public final static String TargetHeight = "targetHeight";

    public final static String TargetWidthParam = "targetWidthParam";
    public final static String TargetHeightParam = "targetHeightParam";

    public final static String TargetX = "targetX";
    public final static String TargetY = "targetY";

    /**
     * The <strong>TransformOriginX</strong> property controls where the x coordinate of the rotation and scale centers is (the pivotX property).
     * This value is relative to the view's top-left corner.
     */
    public final static String TransformOriginX = "pivotX";

    /**
     * The <strong>TransformOriginY</strong> property controls where the y coordinate of the rotation and scale centers is (the pivotY property).
     * This value is relative to the view's top-left corner.
     */
    public final static String TransformOriginY = "pivotY";

    public final static String CameraDistance = "$cameraDistance";

    /**
     * Define a custom property which will be accessible in all collection property getter/setters as well as any animation involving properties.
     * @param identifier The identifier through which the property will be accessed.
     * @param property The interface implementation defining how this property will be retrieved or updated. The property must be a float type.
     */
    public static void extendProperty(String identifier, Property<Float> property) {
        if (identifier.startsWith("$")) {
            throw new IllegalArgumentException("Property identifiers starting with '$' are reserved.");
        }

        Properties.put(identifier, property);
    }

    /**
     * A special reserved end-value for animators that instructs the animator to replace this with the current property value for each view.
     */
    public final static float CurrentPropertyValue = Float.NaN;

    public interface Property<T> {
        void set(View view, T value);
        T get(View view);
    }

    final private static HashMap<String, Property<Float>> Properties = new HashMap<String, Property<Float>>() {{
        put(X, new Property<Float>() {
            public void set(View view, Float value) { view.setX(value); }
            public Float get(View view) { return view.getX(); }
        });

        put(Y, new Property<Float>() {
            public void set(View view, Float value) { view.setY(value); }
            public Float get(View view) { return view.getY(); }
        });

        put(Z, new Property<Float>() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void set(View view, Float value) { view.setElevation(value); }
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public Float get(View view) { return view.getElevation(); }
        });

        put(TranslateZ, new Property<Float>() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void set(View view, Float value) { view.setTranslationZ(value); }
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public Float get(View view) { return view.getTranslationZ(); }
        });

        put(CenterX, new Property<Float>() {
            public void set(View view, Float value) { view.setX(value - view.getWidth() / 2); }
            public Float get(View view) { return view.getX() + view.getWidth() / 2; }
        });

        put(CenterY, new Property<Float>() {
            public void set(View view, Float value) { view.setY(value - view.getHeight() / 2); }
            public Float get(View view) { return view.getY() + view.getHeight() / 2; }
        });

        put(TranslateX, new Property<Float>() {
            public void set(View view, Float value) { view.setTranslationX(value); }
            public Float get(View view) { return view.getTranslationX(); }
        });

        put(TranslateY, new Property<Float>() {
            public void set(View view, Float value) { view.setTranslationY(value); }
            public Float get(View view) { return view.getTranslationY(); }
        });

        put(TransformOriginX, new Property<Float>() {
            public void set(View view, Float value) { view.setPivotX(value); }
            public Float get(View view) { return view.getPivotX(); }
        });

        put(TransformOriginY, new Property<Float>() {
            public void set(View view, Float value) { view.setPivotY(value); }
            public Float get(View view) { return view.getPivotY(); }
        });

        put(RotateX, new Property<Float>() {
            public void set(View view, Float value) { view.setRotationX(value); }
            public Float get(View view) { return view.getRotationX(); }
        });

        put(RotateY, new Property<Float>() {
            public void set(View view, Float value) { view.setRotationY(value); }
            public Float get(View view) { return view.getRotationY(); }
        });

        put(RotateZ, new Property<Float>() {
            public void set(View view, Float value) { view.setRotation(value); }
            public Float get(View view) { return view.getRotation(); }
        });

        put(ScaleX, new Property<Float>() {
            public void set(View view, Float value) { view.setScaleX(value); }
            public Float get(View view) { return view.getScaleX(); }
        });

        put(ScaleY, new Property<Float>() {
            public void set(View view, Float value) { view.setScaleY(value); }
            public Float get(View view) { return view.getScaleY(); }
        });

        put(Opacity, new Property<Float>() {
            public void set(View view, Float value) { view.setAlpha(value); }
            public Float get(View view) { return view.getAlpha(); }
        });

        put(CameraDistance, new Property<Float>() {
            public void set(View view, Float value) { view.setCameraDistance(value); }
            public Float get(View view) { return view.getCameraDistance(); }
        });
    }};
    //endregion

    //region Layout Properties

    public final static String Width = "width";
    public final static String Height = "height";

    public final static String MarginLeft = "$marginLeft";
    public final static String MarginTop = "$marginTop";
    public final static String MarginRight = "$marginRight";
    public final static String MarginBottom = "$marginBottom";

    public final static String PaddingLeft = "$paddingLeft";
    public final static String PaddingTop = "$paddingTop";
    public final static String PaddingRight = "$paddingRight";
    public final static String PaddingBottom = "$paddingBottom";

    public final static String ScrollX = "$scrollX";
    public final static String ScrollY = "$scrollY";


    /**
     * Define a custom layout property which will be accessible in all collection layout property getter/setters as well as any animation involving layout properties.
     * @param identifier The identifier through which the layout property will be accessed.
     * @param property The interface implementation defining how this layout property will be retrieved or updated. The property must be an integer type.
     */
    public static void extendLayout(String identifier, Property<Integer> property) {
        if (identifier.startsWith("$")) {
            throw new IllegalArgumentException("Property identifiers starting with '$' are reserved.");
        }

        LayoutProperties.put(identifier, property);
    }

    /**
     * A special reserved end-value for animators that instructs the animator to replace this with the current layout property value for each view.
     */
    public final static int CurrentLayoutValue = Integer.MIN_VALUE;

//    public final static String Gravity = "$gravity";

    final private static HashMap<String, Property<Integer>> LayoutProperties = new HashMap<String, Property<Integer>>() {{
        // Sizes

        put(Width, new Property<Integer>() {
            public void set(View view, Integer value) {

                view.getLayoutParams().width = value;
                view.setLayoutParams(view.getLayoutParams());
            }
            public Integer get(View view) { return view.getLayoutParams().width; }
        });

        put(Height, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.getLayoutParams().height = value;
                view.setLayoutParams(view.getLayoutParams());
            }
            public Integer get(View view) { return view.getLayoutParams().height; }
        });

        // Margins

        put(MarginLeft, new Property<Integer>() {
            public void set(View view, Integer value) {
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).leftMargin = value;
                view.setLayoutParams(view.getLayoutParams());
            }
            public Integer get(View view) { return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).leftMargin ; }
        });

        put(MarginTop, new Property<Integer>() {
            public void set(View view, Integer value) {
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin = value;
                view.setLayoutParams(view.getLayoutParams());
            }
            public Integer get(View view) { return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin ; }
        });

        put(MarginBottom, new Property<Integer>() {
            public void set(View view, Integer value) {
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin = value;
                view.setLayoutParams(view.getLayoutParams());
            }
            public Integer get(View view) { return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).bottomMargin ; }
        });

        put(MarginRight, new Property<Integer>() {
            public void set(View view, Integer value) {
                ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).rightMargin = value;
                view.setLayoutParams(view.getLayoutParams());
            }
            public Integer get(View view) { return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).rightMargin ; }
        });

        // Paddings

        put(PaddingLeft, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.setPadding(value, view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
            }
            public Integer get(View view) { return view.getPaddingLeft(); }
        });

        put(PaddingTop, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.setPadding(view.getPaddingLeft(), value, view.getPaddingRight(), view.getPaddingBottom());
            }
            public Integer get(View view) { return view.getPaddingTop(); }
        });

        put(PaddingRight, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), value, view.getPaddingBottom());
            }
            public Integer get(View view) { return view.getPaddingRight(); }
        });

        put(PaddingBottom, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), value);
            }
            public Integer get(View view) { return view.getPaddingBottom(); }
        });

        put(ScrollX, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.setScrollX(value);
            }
            public Integer get(View view) { return view.getScrollX(); }
        });

        put(ScrollY, new Property<Integer>() {
            public void set(View view, Integer value) {
                view.setScrollY(value);
            }
            public Integer get(View view) { return view.getScrollY(); }
        });
    }};
    //endregion

    //region Color properties

    public final static String TextColor = "$TextColor";
    public final static String RippleColor = "$RippleColor";
    public final static String BackgroundColor = "$BackgroundColor";
    public final static String RippleSelectedColor = "$RippleSelectedColor";
    public final static String RippleWorkingBackgroundColor = "$RippleWorkingBackgroundColor";


    /**
     * Define a custom color property which will be accessible in all collection color property getter/setters as well as any animation involving color properties.
     * @param identifier The identifier through which the color property will be accessed.
     * @param property The interface implementation defining how this color property will be retrieved or updated. This property must be an integer type.
     *                 Unlike layout properties, when interpolating two color values, the four 8-bit <strong>A R G B</strong> properties will be interpolated independently of each other,
     *                 rather than interpolating the entire value a whole integer.
     */
    public static void extendColor(String identifier, Property<Integer> property) {
        if (identifier.startsWith("$")) {
            throw new IllegalArgumentException("Property identifiers starting with '$' are reserved.");
        }

        ColorProperties.put(identifier, property);
    }

    /**
     * A special reserved end-value for animators that instructs the animator to replace this with the current property color value for each view.
     */
    public final static int CurrentColorValue = 0x00000001;

    final private static HashMap<String, Property<Integer>> ColorProperties = new HashMap<String, Property<Integer>>() {{
        // Sizes

        put(TextColor, new Property<Integer>() {
            public void set(View view, Integer value) {
                if (view instanceof TextView) ((TextView) view).setTextColor(value);
            }
            public Integer get(View view) { return view instanceof TextView ? ((TextView) view).getCurrentTextColor() : 0; }
        });

        put(RippleColor, new Property<Integer>() {
            public void set(View view, Integer value) {
                if (view.getBackground() != null && view.getBackground() instanceof LegacyRippleDrawable) {
                    ((LegacyRippleDrawable) view.getBackground()).setRippleColor(value);
                }
            }
            public Integer get(View view) { return (view.getBackground() != null && view.getBackground() instanceof LegacyRippleDrawable) ? ((LegacyRippleDrawable) view.getBackground()).getRippleColor() : 0; }
        });

        put(BackgroundColor, new Property<Integer>() {
            public void set(View view, Integer value) {
                if (view.getBackground() != null) {
                    if (view.getBackground() instanceof ColorDrawable) ((ColorDrawable) view.getBackground()).setColor(value);
                    if (view.getBackground() instanceof LegacyRippleDrawable) ((LegacyRippleDrawable) view.getBackground()).setColors(value, ((LegacyRippleDrawable) view.getBackground()).getPressedColor());
                }
            }
            public Integer get(View view) {
                if (view.getBackground() != null) {
                    if (view.getBackground() instanceof ColorDrawable) return ((ColorDrawable) view.getBackground()).getColor();
                    if (view.getBackground() instanceof LegacyRippleDrawable) return ((LegacyRippleDrawable) view.getBackground()).getBackgroundColor();
                }
                return 0;
            }
        });

        put(RippleSelectedColor, new Property<Integer>() {
            public void set(View view, Integer value) {
                if (view.getBackground() != null) {
                    if (view.getBackground() instanceof LegacyRippleDrawable) {
                        ((LegacyRippleDrawable) view.getBackground()).setSelectedColors(value, ((LegacyRippleDrawable) view.getBackground()).getSelectedPressedColor());
                    }
                }
            }
            public Integer get(View view) {
                if (view.getBackground() != null) {
                    if (view.getBackground() instanceof LegacyRippleDrawable) return ((LegacyRippleDrawable) view.getBackground()).getSelectedColor();
                }
                return 0;
            }
        });

        put(RippleWorkingBackgroundColor, new Property<Integer>() {
            public void set(View view, Integer value) {
                if (view.getBackground() != null) {
                    if (view.getBackground() instanceof LegacyRippleDrawable) {
                        ((LegacyRippleDrawable) view.getBackground()).setBackgroundColor(value, false);
                    }
                }
            }
            public Integer get(View view) {
                if (view.getBackground() != null) {
                    if (view.getBackground() instanceof LegacyRippleDrawable) return ((LegacyRippleDrawable) view.getBackground()).getCurrentColor();
                }
                return 0;
            }
        });
    }};
    //endregion

    //region Event handlers
    // TODO
    // *************************** Events ****************************

    public final static String Click = "$Click";
    public final static String Touch = "$Touch";
    public final static String LongClick = "$LongClick";

    private final static Map<String, Each> ViewEventRemovers = new HashMap<String, Each>() {{
        put(Click, new Each() {
            @Override
            public void run(View view, int index) {
                view.setOnClickListener(null);
            }
        });

        put(LongClick, new Each() {
            @Override
            public void run(View view, int index) {
                view.setOnLongClickListener(null);
            }
        });

        put(Touch, new Each() {
            @Override
            public void run(View view, int index) {
                view.setOnTouchListener(null);
            }
        });
    }};

    final private static class ViewWrapper {
        private View view;
//        private Map<String, Object> metadata;

        public ViewWrapper(View view) {
            this.view = view;
//            metadata = new HashMap<String, Object>();
        }
    }
    //endregion

    //region Resource loaders
    // ************************* RESOURCES *************************

    /**
     * The stack of bound contexts used for short-hand notations of resource getters.
     */
    static private final ArrayList<Context> BoundContexts = new ArrayList<Context>();

    /**
     * Statically binds the $ namespace to the specified context, allowing you to use short-hand resource getters.
     * To prevent memory leaks, you must unbind this context when you are done with resource getters.
     * @param context The context to which the $ namespace will be bound.
     */
    public static void bind(Context context) {
        BoundContexts.add(context);
    }

    /**
     * Unbinds the $ namespace from the context to which it was last bound. You must call this method once for each context you bind.
     */
    public static void unbind() {
        if (BoundContexts.size() > 0) BoundContexts.remove(BoundContexts.size() - 1);
    }

    /**
     * Retrieves the decor view of the currently bound activity context.
     * @return The decor view of the currently bound activity context.
     */
    public static FrameLayout root() {
        return (FrameLayout) ((Activity) BoundContexts.get(BoundContexts.size() - 1)).getWindow().getDecorView();
    }

    /**
     * Retrieves the decor view of the currently bound activity context, wrapped in a ViewProxy set.
     * @return A new ViewProxy wrapper, containing the decor view of the currently bound activity context.
     */
    public static $ body() {
        return $.wrap(((Activity) BoundContexts.get(BoundContexts.size() - 1)).getWindow().getDecorView());
    }

    /**
     * Returns the corresponding pixel value of the specified <strong>density independent pixel</strong> value.
     * @param dp The size to convert to pixels.
     * @param context The display context in which the conversion will take place.
     * @return The corresponding pixel value, rounded up for fractions exceeding .5, or down otherwise.
     */
    public static int dp(float dp, Context context) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Returns the corresponding pixel value of the specified <strong>density independent pixel</strong> value.
     * @param dp The size to convert to pixels.
     * @return The corresponding pixel value, rounded up for fractions exceeding .5, or down otherwise.
     */
    public static int dp(float dp) {
        return (int) (dp * BoundContexts.get(BoundContexts.size() - 1).getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Returns the corresponding pixel value of the specified <strong>scaled pixel</strong> value.
     * @param sp The size to convert to pixels.
     * @param context The display context in which the conversion will take place.
     * @return The corresponding pixel value, rounded up for fractions exceeding .5, or down otherwise.
     */
    public static int sp(float sp, Context context) {
        return (int) (sp * context.getResources().getDisplayMetrics().scaledDensity + 0.5f);
    }

    /**
     * Returns the corresponding pixel value of the specified <strong>scaled pixel</strong> value.
     * @param sp The size to convert to pixels.
     * @return The corresponding pixel value, rounded up for fractions exceeding .5, or down otherwise.
     */
    public static int sp(float sp) {
        return (int) (sp * BoundContexts.get(BoundContexts.size() - 1).getResources().getDisplayMetrics().scaledDensity + 0.5f);
    }

    /**
     * Returns the value of the specified color resource.
     * @param resource The id of the resource to load.
     * @param context The context in which the resource will be loaded.
     * @return The color identified by the supplied resource id.
     */
    public static int color(int resource, Context context) {
        return context.getResources().getColor(resource);
    }

    /**
     * Returns the value of the specified color resource.
     * @param resource The id of the resource to load.
     * @return The color identified by the supplied resource id.
     */
    public static int color(int resource) {
        return BoundContexts.get(BoundContexts.size() - 1).getResources().getColor(resource);
    }

    /**
     * Returns the value of the specified dimension resource.
     * @param resource The id of the resource to load.
     * @param context The context in which the resource will be loaded.
     * @return The dimension identified by the supplied resource id.
     */
    public static int dimen(int resource, Context context) {
        return context.getResources().getDimensionPixelSize(resource);
    }

    /**
     * Returns the value of the specified dimension resource.
     * @param resource The id of the resource to load.
     * @return The dimension identified by the supplied resource id.
     */
    public static int dimen(int resource) {
        return BoundContexts.get(BoundContexts.size() - 1).getResources().getDimensionPixelSize(resource);
    }

    /**
     * Retrieves the specified drawable resource.
     * @param resource The id of the resource to load.
     * @param context The context in which the resource will be loaded.
     * @return The drawable identified by the supplied resource id.
     */
    public static Drawable drawable(int resource, Context context) {
        return context.getResources().getDrawable(resource);
    }

    /**
     * Retrieves the specified drawable resource.
     * @param resource The id of the resource to load.
     * @return The drawable identified by the supplied resource id.
     */
    public static Drawable drawable(int resource) {
        return BoundContexts.get(BoundContexts.size() - 1).getResources().getDrawable(resource);
    }


    /**
     * Renders the supplied view into a bitmap, then returns a static clone of that view using the previously rendered bitmap as the background.
     * The View returned by this method is not attached to any parent; it is up to you to attach and position it correctly.
     * <br\><br\>
     * This method is an alias to {@link com.BogdanMihaiciuc.util.Utils.ViewUtils#screenshotView(View) Utils.ViewUtils.screenshotView(view)}
     * @param source The view which will be cloned.
     * @return A new static clone of the supplied view, using a bitmap render as the background.
     */
    public static View screenshot(View source) {
        return Utils.ViewUtils.screenshotView(source);
    }


    /**
     * Triggers a blocking layout pass of the currently bound context.
     * When this method returns, all the views within the current context will have been measured and laid out.
     */
    public static void layout() {
        layout(BoundContexts.get(BoundContexts.size() - 1));
    }

    final static Method DoTraversal;
    static {
        Method doTraversal;
        try {
            Class viewRootImpl = Class.forName("android.view.ViewRootImpl");
            doTraversal = viewRootImpl.getDeclaredMethod("doTraversal");
            doTraversal.setAccessible(true);
        }
        catch (Exception e) {
            doTraversal = null;
            e.printStackTrace();
        }
        DoTraversal = doTraversal;
    }
    /**
     * Triggers a blocking layout pass of the supplied context.
     * When this method returns, all the views within the target context will have been measured and laid out.
     */
    public static void layout(Context context) {
        View root = ((Activity) context).getWindow().getDecorView();
        root.requestLayout();

        ViewParent viewRootImpl = root.getParent();
        if (DoTraversal != null) {
            try {
                DoTraversal.invoke(viewRootImpl);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //endregion

    //region AJAX
    //****************************** AJAX *******************************

    public interface OnReadyStateChangeListener {
        void onReadyStateChange(XMLHttpRequest request);
    }

    /**
     * Loads the content at the specified address, then posts a callback on the main thread with that content.
     * @param location The remote location from which to load the content.
     * @return An object representing the request. You may subscribe to its state change by calling {@link XMLHttpRequest#onReadyStateChange(OnReadyStateChangeListener)}.
     */
    public static XMLHttpRequest ajax(String location) {
        return $.ajax("GET", location);
    }


    /**
     * Loads the content at the specified address, then posts a callback on the main thread with that content.
     * @param method The HTTP method to use when performing the request.
     * @param location The remote location from which to load the content.
     * @return An object representing the request. You may subscribe to its state change by calling {@link XMLHttpRequest#onReadyStateChange(OnReadyStateChangeListener)}.
     */
    public static XMLHttpRequest ajax(String method, String location) {
//        return $.ajax(method, location, null);
        return new XMLHttpRequest(method, location);
    }

//    /**
//     * Loads the content at the specified address, then posts a callback on the main thread with that content.
//     * @param method The HTTP method to use when performing the request.
//     * @param location The remote location from which to load the content.
//     * @param params A map of parameters to send to the server.
//     * @return An object representing the request. You may subscribe to its state change by calling {@link XMLHttpRequest#onReadyStateChange(OnReadyStateChangeListener)}.
//     */
//    @Deprecated
//    public static XMLHttpRequest ajax(String method, String location, Map<String, ? extends Object> params) {
//        return new XMLHttpRequest(method, location, params);
//    }


    @IntDef({ReadyStateNotInitialized, ReadyStateConnectionEstablished, ReadyStateRequestSent, ReadyStateProcessingRequest, ReadyStateRequestFinished})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ReadyState {}
    /**
     * Indicates that the XMLHttpRequest hasn't yet begun connecting to the server.
     */
    public final static int ReadyStateNotInitialized = 0;
    /**
     * Indicates that the XMLHttpRequest has connected to the server, but hasn't yet sent the request or the data.
     */
    public final static int ReadyStateConnectionEstablished = 1;
    /**
     * Indicates that the XMLHttpRequest has connected to the server and sent the request.
     */
    public final static int ReadyStateRequestSent = 2;
    /**
     * Indicates that the XMLHttpRequest is currently processing the response from the server.
     */
    public final static int ReadyStateProcessingRequest = 3;
    /**
     * Indicates that the XMLHttpRequest has completed the request.
     * At this point you may call {@link XMLHttpRequest#status()} to retrieve the status code and {@link XMLHttpRequest#responseText()} to retrieve the response.
     */
    public final static int ReadyStateRequestFinished = 4;

    /**
     * The <strong>Ajax</strong> class represents an HTTP request that's handled on a background thread.
     * When the request completes, the Ajax object will schedule a callback on the UI thread for the content.
     */
    public static class XMLHttpRequest {
        final private String request;
        final private String method;

        private Map<String, String> headers = new HashMap<String, String>();

        public XMLHttpRequest(String method, String request) {
            this.request = request;
            this.method = method;
        }

        OnReadyStateChangeListener onReadyStateChangeListener;

        private AsyncTask httpRequest;

        private int _readyState,
                _statusCode;
        private String response;

        private String user, password;

        /**
         * Set to true if the underlying HTTPURLConnection is processing the request on a background thread.
         */
        private boolean started;

        private Handler handler = new Handler();

        /**
         * Retrieves the current ready state of this request.
         * @return The current ready state of this request.
         */
        @ReadyState
        public synchronized int readyState() {
            return _readyState;
        }

        /**
         * Retrieves the status code of this request.
         * @return The status code of this request. <br/>If the ready state is different than {@link $#ReadyStateRequestFinished}, the status code is undefined.
         */
        public synchronized int status() {
            return _statusCode;
        }

        /**
         * Retrieves the response text from the server.
         * @return The response text from the server, or null if the ready state is different than {@link $#ReadyStateRequestFinished}.
         */
        public String responseText() {
            return response;
        }

//        public byte[] response() {
//            return response;
//        }

        private final Runnable ReadyStateNotifier = new Runnable() {
            @Override
            public void run() {
                onReadyStateChangeListener.onReadyStateChange($.XMLHttpRequest.this);
            }
        };

        /**
         * Sets the status code.
         * @param status The status code
         */
        private synchronized void status(int status) {
            _statusCode = status;
        }

        /**
         * Sets the ready state; if the ready state changes, the onReadyStateChange listener will be notified on the UI thread.
         * @param readyState The ready state.
         */
        private synchronized void readyState(int readyState) {
            if (_readyState != readyState) {
                _readyState = readyState;

                if (onReadyStateChangeListener != null) {
                    // Remove any previous pending ready state notifications
                    handler.removeCallbacks(ReadyStateNotifier);
                    handler.post(ReadyStateNotifier);
                }
            }
        }

        /**
         * Adds or removes a request header for this request.
         * @param name The name of the header.
         * @param value The value of the header. If the value is null, the header is removed, otherwise it will be added.
         * @return This XMLHttpRequest.
         * @throws IllegalStateException if the request is already being processed.
         * @throws IllegalArgumentException if the header name is <strong>Content-length</strong>
         */
        public XMLHttpRequest header(String name, String value) {
            if ("Content-length".equals(name)) throw new IllegalArgumentException("The Content-length header cannot be set manually");
            if (started) throw new IllegalStateException("Can't modify this request's properties while it's being processed!");

            if (value == null) {
                headers.remove(name);
            }
            else {
                headers.put(name, value);
            }
            return this;
        }

        /**
         * Removes a request header from this request.
         * @param name The name of the header.
         * @return This XMLHttpRequest.
         * @throws IllegalStateException if the request is already being processed.
         */
        public XMLHttpRequest removeHeader(String name) {
            if (started) throw new IllegalStateException("Can't modify this request's properties while it's being processed!");

            headers.remove(name);
            return this;
        }

        /**
         * Gets the value of the specified request header.
         * @param name The name of the header.
         * @return The value of the specified request header, or null if the header doesn't exist.
         * @throws IllegalStateException if the request is already being processed.
         * @throws IllegalArgumentException if the header name is <strong>Content-length</strong>
         */
        public String header(String name) {
            if ("Content-length".equals(name)) throw new IllegalArgumentException("The Content-length header can only be computed when this request is sent.");
            if (started) throw new IllegalStateException("Can't get this request's headers while it's being processed!");

            return headers.get(name);
        }

        /**
         * For servers that require authentication, this method, in conjunction with {@link com.BogdanMihaiciuc.util.$.XMLHttpRequest#password(String)} lets you set up the authentication details.
         * @param user The username.
         * @return This XMLHttpRequest.
         * @throws IllegalStateException if the request is already being processed.
         */
        public XMLHttpRequest username(String user) {
            this.user = user;
            if (started) throw new IllegalStateException("Can't modify this request's properties while it's being processed!");
            return this;
        }


        /**
         * For servers that require authentication, this method, in conjunction with {@link com.BogdanMihaiciuc.util.$.XMLHttpRequest#username(String)} lets you set up the authentication details.
         * @param password The password.
         * @return This XMLHttpRequest.
         * @throws IllegalStateException if the request is already being processed.
         */
        public XMLHttpRequest password(String password) {
            this.password = password;
            if (started) throw new IllegalStateException("Can't modify this request's properties while it's being processed!");
            return this;
        }

        /**
         * Replace the handler that will post the callback, allowing it to execute on a different thread.
         * @param handler The handler that will post the callback.
         * @return This XMLHttpRequest instance.
         * @throws IllegalStateException if the request is already being processed.
         */
        public XMLHttpRequest handler(Handler handler) {
            this.handler = handler;

            if (started) throw new IllegalStateException("Can't modify this request's properties while it's being processed!");

            return this;
        }

        /**
         * Sets a handler that will be called <strong>on the UI Thread</strong> when this connection's status changes.
         * You may then retrieve the ready state, status code and server response from the supplied {@link XMLHttpRequest $.Ajax} object.
         * @param listener The handler that will be invoked when the connection's ready state changes.
         * @return This XMLHttpRequest instance.
         */
        public synchronized XMLHttpRequest onReadyStateChange(OnReadyStateChangeListener listener) {
            this.onReadyStateChangeListener = listener;

            return this;
        }

        /**
         * Sends the request to the server. If this request is already being processed, this call is ignored.
         * @return This XMLHttpRequest instance.
         */
        public XMLHttpRequest send() {
            return send(null);
        }


        /**
         * Sends the request to the server. If this request is already being processed, this call is ignored.
         * @param Data May be null. This represents a block of data that will be sent to the server as part of this request.<br/>
         *             It may be:<br/>
         *             <ul>
         *              <li>A string, which will be encoded as a JSON, or an urlencoded form, if it starts with ?</li>
         *              <li>A map of string keys and values that will be encoded as an urlencoded form.</li>
         *              <li>A byte array which will be encoded as a blob.</li>
         *             </ul>
         * @return This XMLHttpRequest instance.
         */
        public XMLHttpRequest send(final Object Data) {
            return send(Data, null);
        }

        /**
         * Sends the request to the server. If this request is already being processed, this call is ignored.
         * @param Data May be null. This represents a block of data that will be sent to the server as part of this request.<br/>
         *             It must correspond to the supplied mime type, or if the mime type isn't specified, it will be determined based on the Data:<br/>
         *             <ul>
         *              <li>A {@link org.json.JSONObject JSONObject}, which will be encoded as <strong>application/JSON</strong>.</li>
         *              <li>A string, which will be encoded as <strong>application/JSON</strong> or <strong>application/x-www-form-urlencoded</strong> if it starts with ?.</li>
         *              <li>A map of string keys and string values that will be encoded as <strong>application/x-www-form-urlencoded</strong>.</li>
         *              <li>A byte array which will be encoded as a blob.</li>
         *             </ul>
         * @param MimeType The mime type specifying how to treat the data to be sent. If this is non-null, the class of the Data object must match the supplied mime type:
         *                 <ul>
         *                  <li><strong>application/x-www-form-urlencoded</strong>: an URL encoded {@link String} or a {@link Map} of String keys and values.</li>
         *                  <li><strong>multipart/form-data</strong>: a {@link Map} of String keys and any object values.</li>
         *                  <li><strong>application/json</strong> or <strong>text/json</strong>: a JSON string or a {@link org.json.JSONObject JSONObject}.</li>
         *                  <li><strong>all other mime types</strong>: a byte array</li> TODO not implemented; use multipart/form-data for now
         *                 </ul>
         * @return This XMLHttpRequest instance.
         * @throws ClassCastException If the Data object does not match the supplied MimeType
         */
        public XMLHttpRequest send(final Object Data, final String MimeType) {

            if (httpRequest != null) return this;
            if (started) return this;
            started = true;

            httpRequest = new AsyncTask<Void, Void, Void>() {

                private String serverResponse;

                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        // The request, method and params are constant and immutable and thus thread safe
                        URL url = new URL(request);
                        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
                        if (isCancelled()) return null;
                        readyState(1);

                        httpConnection.setRequestMethod(method);
                        httpConnection.setDoInput(true);
                        httpConnection.setConnectTimeout(10000);

                        // push the user defined headers to the request
                        for (Map.Entry<String, String> header : headers.entrySet()) {
                            httpConnection.setRequestProperty(header.getKey(), header.getValue());
                        }

                        // Perform authentication if needed
                        if (user != null) {
                            final byte[] EncodedBytes = Base64.encode((user + ":" + password).getBytes(), Base64.DEFAULT);
                            final String EncodedAuthDetails = new String(EncodedBytes);

                            httpConnection.setRequestProperty("Authorization", "Basic " + EncodedAuthDetails);
                        }

                        if (DEBUG_XHR) Log.d(TAG, "HTTP request set up!");

                        //region If there is data to be sent then send it
                        // Perform output if some Data is specified
                        if (Data != null) {
                            httpConnection.setRequestProperty("Connection", "Keep-Alive");

                            // Resolve the mime-type dinamically if not specified
                            String resolvedMimeType = MimeType;
                            if (resolvedMimeType == null) {
                                if (Data instanceof String) {
                                    if (((String) Data).startsWith("?")) {
                                        resolvedMimeType = "application/x-www-form-urlencoded";
                                    }
                                    else {
                                        resolvedMimeType = "application/JSON";
                                    }
                                }
                                else if (Data instanceof Map) {
                                    resolvedMimeType = "application/x-www-form-urlencoded";
                                }
                                else if (Data instanceof JSONObject) {
                                    resolvedMimeType = "application/JSON";
                                }
                                else {
                                    resolvedMimeType = "application/octet-stream";
                                }
                            }

                            // Prepare for output
                            httpConnection.setDoOutput(true);
                            OutputStream httpOutput = null;

                            // region x-www-form-urlencoded
                            if (resolvedMimeType.equals("application/x-www-form-urlencoded")) {
                                httpConnection.setRequestProperty("Content-Type", resolvedMimeType);

                                httpConnection.connect();
                                readyState(2);

                                httpOutput = httpConnection.getOutputStream();

                                byte[] formData;

                                if (Data instanceof String) {
                                    if (((String) Data).startsWith("?")) {
                                        formData = ((String) Data).substring(1).getBytes();
                                    }
                                    else {
                                        formData = ((String) Data).getBytes();
                                    }
                                }
                                // The only other option is Map<String, String> or a badly formatted map, which throws an exception
                                else {
                                    //noinspection unchekced
                                    Map<String, String> formValues = (Map<String, String>) Data;

                                    StringBuilder builder = new StringBuilder();
                                    boolean firstEntry = true;
                                    for (Map.Entry<String, String> entry : formValues.entrySet()) {
                                        if (firstEntry) {
                                            firstEntry = false;
                                        }
                                        else {
                                            builder.append('&');
                                        }

                                        builder.append(entry.getKey()).append('=').append(entry.getValue());
                                    }

                                    formData = builder.toString().getBytes();
                                }

                                httpOutput.write(formData);
                            }
                            //endregion
                            // region application/JSON and text/json
                            else if (resolvedMimeType.equals("application/JSON") || resolvedMimeType.equals("text/JSON")) {
                                httpConnection.setRequestProperty("Content-Type", resolvedMimeType);

                                httpConnection.connect();
                                readyState(2);

                                httpOutput = httpConnection.getOutputStream();

                                if (Data instanceof String) {
                                    httpOutput.write(((String) Data).getBytes());
                                }
                                // The data must be a String or a JSONObject, otherwise an exception is thrown
                                else {
                                    JSONObject json = (JSONObject) Data;
                                    httpOutput.write(json.toString().getBytes());
                                }
                            }
                            //endregion
                            //region multipart/form-data
                            else if (resolvedMimeType.equals("multipart/form-data")) {

                                // Multipart must be a map of String keys and any type of values.
                                // TODO any type of values
                                //noinspection unchecked
                                Map map = (Map) Data;

                                String crlf = "\r\n";
                                String lineEnd = crlf;
                                String twoHyphens = "--";
                                String boundary =  "*****";

                                httpConnection.setRequestProperty("Content-Type", resolvedMimeType + ";boundary=" + boundary);
                                httpConnection.setRequestProperty("Cache-Control", "no-cache");

                                httpConnection.connect();
                                readyState(2);

                                httpOutput = httpConnection.getOutputStream();

                                DataOutputStream outputStream = new DataOutputStream(httpOutput);

                                outputStream.writeBytes(twoHyphens + boundary + crlf);
                                // noinspection unchecked
                                for (Map.Entry entry : (Set<Map.Entry>) map.entrySet()) {
                                    String keyName = (String) entry.getKey();

                                    outputStream.writeBytes("Content-Disposition: form-data; name=\"" + keyName + "\"" + lineEnd);

                                    Object value = entry.getValue();
                                    // TODO other data types
                                    if (value instanceof CharSequence) {
                                        String stringValue = value.toString();

                                        if (stringValue.startsWith("{") && stringValue.endsWith("}")) {
                                            // The string is likely a JSON
                                            outputStream.writeBytes("Content-Type: application/JSON;charset=UTF-8" + lineEnd);
                                            outputStream.writeBytes(lineEnd);
                                            outputStream.writeBytes(stringValue);
                                        }
                                        else {
                                            // The string is plain text
                                            outputStream.writeBytes("Content-Type: text/plain;charset=UTF-8" + lineEnd);
                                            outputStream.writeBytes(lineEnd);
                                            outputStream.writeBytes(stringValue);
                                        }
                                    }
                                    else if (value instanceof JSONObject) {
                                        outputStream.writeBytes("Content-Type: application/JSON;charset=UTF-8" + lineEnd);
                                        outputStream.writeBytes(lineEnd);
                                        outputStream.writeBytes(value.toString());
                                    }
                                    else if (value instanceof byte[]) {
                                        outputStream.write((byte[]) value);
                                    }

                                    outputStream.writeBytes(lineEnd);
                                    outputStream.writeBytes(twoHyphens + boundary + lineEnd);
                                }
//                                request.writeBytes("Content-Disposition: form-data; name=\"" + this.attachmentName + "\";filename=\"" + this.attachmentFileName + "\"" + this.crlf);
                                outputStream.writeBytes(crlf);

                            }
                            //endregion
                            // TODO

                            if (httpOutput != null) {
                                httpOutput.flush();
                                httpOutput.close();
                            }
                        }
                        //endregion
                        else {
                            if (DEBUG_XHR) Log.d(TAG, "HTTP about to connect!");
                            httpConnection.connect();
                            readyState(2);
                        }

                        if (DEBUG_XHR) Log.d(TAG, "HTTP request connected!");

                        status(httpConnection.getResponseCode());

                        readyState(3);

//                        InputStream in = new BufferedInputStream(httpConnection.getInputStream());
                        int contentLength = Integer.parseInt(httpConnection.getHeaderField("Content-Length"));

                        // Handle GZIP compressed response
                        InputStream stream = httpConnection.getInputStream();
                        if ("gzip".equals(httpConnection.getContentEncoding())) {
                            stream = new GZIPInputStream(stream);
                        }

                        Reader reader = new InputStreamReader(stream, "UTF-8");
                        char[] buffer = new char[contentLength];

                        // The Reader doesn't always fully read the response from the server, so a loop is needed to ensure that all the bytes have been read
                        int bytesRead = 0;
                        while (bytesRead < contentLength) {
                            bytesRead += reader.read(buffer, bytesRead, contentLength - bytesRead);
                        }

//                        reader.read(buffer);
                        serverResponse = new String(buffer);

                        if (DEBUG_XHR) Log.d(TAG, "HTTP response received!");

                        httpConnection.disconnect();


                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    return null;
                }

                protected void onPostExecute(Void result) {
                    response = serverResponse;

                    readyState(4);
                }

            }.execute();

            return this;
        }

        /**
         * Stops this request.
         * If it hasn't already been fired, the onReadyStateChange callback will not be invoked by an aborted request, even if the underlying HTTP request has already been completed.
         * @return This XMLHttpRequest instance.
         */
        public XMLHttpRequest abort() {
            if (!started) return this;

            if (httpRequest != null) {
                httpRequest.cancel(true);
            }

            return this;
        }

    }

    //endregion

    //region Factory
    // ************************* FACTORY *************************

    public static $ find(Activity activity, String query) {
        return new $(activity, query);
    }

    public static $ find(Activity activity, int... ids) {
        return new $(activity, ids);
    }

    public static $ find(View container, String query) {
        return new $(container, query);
    }

    public static $ find(View container, int... ids) {
        return new $(container, ids);
    }

    public static $ wrap(View ... views) {
        return new $(views);
    }

    public static $ emptySet(Activity activity) {
        return new $(activity);
    }

    private static $ $(Activity activity, Predicate predicate) {
        $ $ = new $(activity);

        $.traverseViewsUsingPredicate(predicate);

        return $;
    }

    /**
     * Creates a ViewProxy wrapper around a new layout inflated from the specified resource id.
     * @param activity The activity from which the {@link android.view.LayoutInflater} will be obtained.
     * @param resource The layout resource to inflate.
     * @return A ViewProxy wrapper around the newly inflated layout.
     * @throws NullPointerException if activity is null.
     */
    public static $ inflate(Activity activity, int resource) {
        return wrap(activity.getLayoutInflater().inflate(resource, null, false));
    }


    /**
     * Creates a ViewProxy wrapper around a new layout inflated from the specified resource id.
     * @param root The ViewGroup that will be used to generate the inflated layout's {@link android.view.ViewGroup.LayoutParams}.
     * @param resource The layout resource to inflate.
     * @return A ViewProxy wrapper around the newly inflated layout.
     * @throws NullPointerException if root is null.
     */
    public static $ inflate(ViewGroup root, int resource) {
        return wrap(LayoutInflater.from(root.getContext()).inflate(resource, root, false));
    }


    /**
     * Retrieves the metadata value of the supplied view for the given key.
     * See also {@link $#metadata(String)}
     * @param view The view for which to retrieve the metadata.
     * @param key The key coresponding to the value which will be retrieved.
     * @return The requested metadata value, or an empty string if the view has no metadata value associated to the given key.
     * @throws NullPointerException if view is null or if key is null.
     */
    public static Object metadata(View view, String key) {
        if (view.getTag(MetadataKey) != null) {
            if (((Map) view.getTag(MetadataKey)).containsKey(key)) return ((Map) view.getTag(MetadataKey)).get(key);
        }

        if (key.equals(TagMetadataKey)) {
            return view.getTag();
        }

        return "";
    }

    /**
     * Adds a metadata to the supplied view, either creating a new key-value pair, or if the key already exists, replacing its associated value with the one supplied to this method.
     * See also {@link $#metadata(String, Object)}.
     * @param view The view to which the metadata key-value pair will be attached.
     * @param key The key.
     * @param value The value.
     * @throws NullPointerException if view is null or if key is null.
     */
    public static void metadata(View view, String key, Object value) {
        if (view.getTag(Utils.MetadataKey) == null) {
            Map<String, Object> metadata = new HashMap<String, Object>();
            view.setTag(Utils.MetadataKey, metadata);

            metadata.put(key, value);
        }
        else {
            //noinspection unchecked
            ((Map) view.getTag(MetadataKey)).put(key, value);
        }
    }

    public static boolean removeMetadata(View view, String key) {
        if (view.getTag(Utils.MetadataKey) == null) {
            return false;
        }
        else {
            //noinspection unchecked
            return ((Map) view.getTag(MetadataKey)).remove(key) != null;
        }
    }

    public static boolean hasMetadata(View view, String key) {
        if (view.getTag(MetadataKey) != null) {
            if (((Map) view.getTag(MetadataKey)).containsKey(key)) return true;
        }

        return false;
    }

    public static Map dumpMetadata(View view) {
        return (Map) view.getTag(MetadataKey);
    }

    public static void loadMetadata(View view, Map metadata) {
        view.setTag(MetadataKey, metadata);
    }

    //endregion

    //region Constructors
    // ************************* CONSTRUCTORS *************************

    private $(Activity activity) {
        this.activity = activity;
    }

    private $(Activity activity, String query) {
        this.activity = activity;

        traverseViewsUsingPredicate(predicateFromQueryString(query));
    }

    private $(View container, String query) {
        this.activity = (Activity) container.getContext();

        traverseViewsUsingPredicate(predicateFromQueryString(query), container);
    }

    private $(Activity activity, int[] ids) {
        this.activity = activity;

        add(ids);
    }

    private $(View container, int[] ids) {
        this.activity = (Activity) container.getContext();

        for (int id : ids) {
            View view = container.findViewById(id);

            if (view != null) {
                views.add(new ViewWrapper(view));
            }
        }
    }

    private $(View ... views) {
        if (views.length == 0) throw new IllegalArgumentException("No views supplied to wrap.");


        for (View view : views) {
            if (view == null) continue;

            this.activity = (Activity) view.getContext();

            this.views.add(new ViewWrapper(view));
        }

        if (this.activity == null) throw new IllegalArgumentException("No non-null views supplied to wrap.");
    }

    //endregion

    private ArrayList<ViewWrapper> views = new ArrayList<ViewWrapper>();
    private Activity activity;

    //region Generators
    // **************** GENERATORS ****************

    /**
     * Creates a new set that contains only the first subviews of each view that have the supplied ids.
     * The views are found using {@link android.view.View#findViewById(int) findViewById}, so for each view in the set, at most one subview will be found for each of the specified ids.
     * @param ids The ids to look for.
     * @return A new set that contains the appropriate views.
     */
    public $ filter(int ... ids) {
        $ result = new $(activity);

        for (ViewWrapper wrapper : views) {
            for (int id : ids) {
                View view = wrapper.view.findViewById(id);

                if (view != null) {
                    result.views.add(new ViewWrapper(view));
                }
            }
        }

        return result;
    }

    /**
     * Creates a new set that contains only the views from this set that match the supplied selector.
     * @param query The selector against which the views will be tested.
     * @return A new set that contains the appropriate views.
     */
    public $ filter(String query) {
        $ result = new $(activity);

        Predicate predicate = predicateFromQueryString(query);

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (predicate.viewMatches(view)) {
                result.views.add(new ViewWrapper(view));
            }
        }

        return result;
    }


    /**
     * Creates a new set that contains only the views from this set that match the supplied predicate.
     * @param predicate The predicate against which the views will be tested.
     * @return A new set that contains the appropriate views.
     */
    public $ filter(Predicate predicate) {
        $ result = new $(activity);

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (predicate.viewMatches(view)) {
                result.views.add(new ViewWrapper(view));
            }
        }

        return result;
    }

    public $ find(int ... ids) {
        $ result = new $(activity);

        Predicate predicate = idPredicate(ids);

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (predicate.viewMatches(view)) result.views.add(new ViewWrapper(view));

            if (view instanceof ViewGroup) {
                result.traverseSubviewsUsingPredicate((ViewGroup) view, predicate);
            }
        }

        return result;
    }

    /**
     * Creates and returns a new ViewProxy around all descendants of each view in the current set that match the specified condition.
     * @param query The condition against which to check children.
     * @return A new ViewProxy set of the matching views.
     */
    public $ find(String query) {
        $ set = new $(activity);

        for (ViewWrapper wrapper : views) {
            if (wrapper.view instanceof ViewGroup) {
                set.traverseSubviewsUsingPredicate((ViewGroup) wrapper.view, predicateFromQueryString(query));
            }
        }

        return set;
    }

    public $ children(int ... ids) {
        $ result = new $(activity);

        for (ViewWrapper wrapper : views) {

            if (wrapper.view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) wrapper.view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View view = group.getChildAt(i);

                    if (ids.length == 0 || Utils.arrayContainsInt(ids, view.getId())) {
                        result.views.add(new ViewWrapper(view));
                    }
                }
            }

        }

        return result;
    }


    /**
     * Creates a new ViewProxy wrapper containing each view's direct descendants.
     * @return A new ViewProxy containing the parents of this collection's views.
     */
    public $ children() {
        $ set = new $(activity);

        for (ViewWrapper wrapper : views) {
            if (wrapper.view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) wrapper.view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    set.views.add(new ViewWrapper(group.getChildAt(i)));
                }
            }
        }

        return set;
    }


    /**
     * Creates a new ViewProxy wrapper containing each view's direct descendants, filtered by the specified query string.
     * @param query The condition against which to check children.
     * @return A new ViewProxy containing the parents of this collection's views.
     */
    public $ children(String query) {
        $ set = new $(activity);

        Predicate predicate = predicateFromQueryString(query);

        for (ViewWrapper wrapper : views) {
            if (wrapper.view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) wrapper.view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    if (predicate.viewMatches(group.getChildAt(i))) {
                        set.views.add(new ViewWrapper(group.getChildAt(i)));
                    }
                }
            }
        }

        return set;
    }


    /**
     * Creates a new ViewProxy wrapper containing each view's parent.
     * Duplicate parents are not added twice.
     * @return A new ViewProxy containing the parents of this collection's views.
     */
    public $ parent() {
        $ result = new $(activity);

        for (ViewWrapper wrapper : views) {

            if (wrapper.view.getParent() instanceof View) {
                View view = (View) wrapper.view.getParent();

                if (!result.hasView(view)) {
                    result.views.add(new ViewWrapper(view));
                }
            }

        }

        return result;
    }

    /**
     * Checks whether the supplied view is in this collection.
     * @param view The view to look for.
     * @return True if the view is in this collection, false otherwise.
     */
    public boolean hasView(View view) {
        for (ViewWrapper wrapper : views) {

            if (wrapper.view == view) return true;

        }

        return false;
    }

    public $ parents(int ... ids) {
        $ result = new $(activity);

        for (ViewWrapper wrapper : views) {

            if (wrapper.view.getParent() instanceof View) {
                View view = (View) wrapper.view.getParent();

                if (ids.length == 0 || Utils.arrayContainsInt(ids, view.getId())) {
                    result.views.add(new ViewWrapper(view));
                }
            }

        }

        return result;
    }

    //endregion

    //region Wrapper Modifiers
    // **************** WRAPPER MODIFIERS ****************

    /**
     * Traverses all the views in the set's context, adding all views that match the predicate.
     * @param predicate The predicate.
     * @return This ViewProxy instance.
     */
    private $ traverseViewsUsingPredicate(Predicate predicate) {
        View root = activity.getWindow().getDecorView();

        return traverseViewsUsingPredicate(predicate, root);
    }

    /**
     * Traverses all the views beginning from the supplied root view, adding it and all subviews that match the predicate.
     * @param predicate The predicate.
     * @param root The root view.
     * @return This ViewProxy instance.
     */
    private $ traverseViewsUsingPredicate(Predicate predicate, View root) {
        if (predicate.viewMatches(root)) {
            views.add(new ViewWrapper(root));
        }

        if (root instanceof ViewGroup) {
            if (DEBUG_QUERY) Log.d(TAG, "Supplied view is a group, traversing...");
            return traverseSubviewsUsingPredicate((ViewGroup) root, predicate);
        }

        return this;
    }


    /**
     * Traverses all the views beginning from the supplied root view, adding all subviews that match the predicate, but not the root view.
     * @param root The root view.
     * @param predicate The predicate.
     * @return This ViewProxy instance.
     */
    private $ traverseSubviewsUsingPredicate(ViewGroup root, Predicate predicate) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View view = root.getChildAt(i);

            if (predicate.viewMatches(view)) {
                views.add(new ViewWrapper(view));
            }

            if (view instanceof ViewGroup) {
                traverseSubviewsUsingPredicate((ViewGroup) view, predicate);
            }
        }

        return this;
    }

    public $ add(int ... ids) {
        for (int id : ids) {
            View view = activity.findViewById(id);

            if (view != null) {
                views.add(new ViewWrapper(view));
            }
        }

        return this;
    }

    /**
     * Adds all the views from the specified collection into this collection.
     * @param collection The collection from which views will be added.
     * @return This ViewProxy instance.
     */
    public $ add($ collection) {
        for (ViewWrapper wrapper : collection.views) {
            views.add(wrapper);
        }

        return this;
    }


    /**
     * Adds all the views matching the specified query into this collection.
     * @param query The query against which views will be checked.
     * @return This ViewProxy instance.
     */
    public $ add(String query) {
        Predicate predicate = predicateFromQueryString(query);

        traverseViewsUsingPredicate(predicate);

        return this;
    }

    /**
     * Adds all the views matching the specified query into this collection.
     * The views must be descendants of the specified root view.
     * @param root The root of the hierarchy from which views will be added.
     * @param query The query against which views will be checked.
     * @return This ViewProxy instance.
     */
    public $ add(View root, String query) {
        Predicate predicate = predicateFromQueryString(query);

        traverseViewsUsingPredicate(predicate, root);

        return this;
    }

    /**
     * Adds all the specified views to this collection.
     * @param views The views that will be added. Null views are ignored.
     * @return This ViewProxy instance.
     */
    public $ add(View ... views) {
        for (View view : views) {
            if (view != null) this.views.add(new ViewWrapper(view));
        }

        return this;
    }

    /**
     * Reverses the order of the views in this collection.
     * @return This ViewProxy instance.
     */
    public $ reverse() {
        Collections.reverse(views);
        return this;
    }

    /**
     * Remove all views in the specified collection from this collection.
     * @param collection The collection from which to check views.
     * @return This ViewProxy instance.
     */
    public $ not($ collection) {
        for (ViewWrapper wrapper : collection.views) {
            int index = indexOfView(wrapper.view);

            if (index != -1) views.remove(index);
        }

        return this;
    }

    private int indexOfId(int id) {
        int index = 0;


        for (ViewWrapper wrapper : views) {
            if (wrapper.view.getId() == id) return index;

            index++;
        }

        return -1;
    }

    /**
     * Returns the position of the specified within this collection.
     * @param view The view whose position will be obtained.
     * @return This view's position within the collection, or -1 if the view is not part of the collection.
     */
    public int indexOfView(View view) {
        int index = 0;


        for (ViewWrapper wrapper : views) {
            if (wrapper.view == view) return index;

            index++;
        }

        return -1;
    }

    /**
     * Removes all the views from this set that have one of the specified ids.
     * @param ids The ids of views to remove.
     * @return This ViewProxy instance.
     */
    public $ not(int... ids) {
        for (int id : ids) {
            int index;
            while ((index = indexOfId(id)) != -1) {
                views.remove(index);
            }
        }

        return this;
    }

    /**
     * Removes all the views from this collection that match the specified query string.
     * @param query The string against which to check views.
     * @return This ViewProxy instance.
     */
    public $ not(String query) {
        Predicate predicate = predicateFromQueryString(query);

        for (int i = 0; i < views.size(); i++) {
            View view = views.get(i).view;

            if (predicate.viewMatches(view)) {
                views.remove(i);
                i--;
            }
        }

        return this;
    }

    //endregion

    // ************************* GETTERS *************************

    /**
     * Gets the View instance at the specified index.
     * @param index The view's index in the set.
     * @return The View instance at the specified index.
     * @throws ArrayIndexOutOfBoundsException if the index is negative or greater than the set's size - 1.
     */
    public View get(int index) {
        return views.get(index).view;
    }

    /**
     * Creates a new ProxyView wrapper around the view at the specified index.
     * @param index The view's index in the set.
     * @return A new ProxyView wrapper around the view at the specified index.
     * @throws ArrayIndexOutOfBoundsException if the index is negative or greater than the set's size - 1.
     */
    public $ eq(int index) {
        return $.wrap(views.get(index).view);
    }

    // **************** PROPERTIES ****************

    /**
     * Sets the supplied drawable as each view's background.<br/>
     * <strong>Note:</strong> if this drawable can change its state as the view's state changes, this change will be reflected in all the views in the set as they will all share the same drawable instance.
     * @param background The background drawable to set.
     */
    public $ background(Drawable background) {
        for (ViewWrapper view : views) {
            view.view.setBackground(background);
        }

        return this;
    }


    /**
     * Sets the supplied drawable resource as each view's background.<br/>
     * @param background The background drawable resource to set.
     */
    public $ background(int background) {
        for (ViewWrapper view : views) {
            view.view.setBackgroundResource(background);
        }

        return this;
    }

    /**
     * Sets the background color on all views in the set.
     * @param color The color that will be used as the views' background.
     */
    public $ backgroundColor(int color) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setBackgroundColor(color);
        }

        return this;
    }

    public static interface Each {
        void run(View view, int index);
    }

    public $ each(Each runnable) {
        int index = 0;
        for (ViewWrapper view : views) {
            runnable.run(view.view, index);
            index++;
        }

        return this;
    }

    public Object metadata(String key) {
        if (views.size() > 0) return $.metadata(views.get(0).view, key);

        return null;
    }

    public boolean hasMetadata(String key) {
        if (views.size() > 0) return $.hasMetadata(views.get(0).view, key);

        return false;
    }

    public $ metadata(String key, Object value) {
        for (ViewWrapper view : views) {
            $.metadata(view.view, key, value);
        }

        return this;
    }

    public $ removeMetadata(String key) {
        for (ViewWrapper view : views) {
            $.removeMetadata(view.view, key);
        }

        return this;
    }

    /**
     * Gets the index of the first view in the set.
     * @return The view's index in its' parent's child list, or 0 if this set is empty.
     * @throws ClassCastException If this view is at the top of the View hierarchy.
     * @throws NullPointerException If this view isn't attached to a parent.
     */
    public int index() {
        if (views.size() > 0) return ((ViewGroup) views.get(0).view.getParent()).indexOfChild(views.get(0).view);

        return 0;
    }

    public float property(String property) {
        if (views.size() > 0) return Properties.get(property).get(views.get(0).view);

        return 0;
    }

    public $ property(String property, float value) {
        return property(property, value, Op.Set);
    }

    public $ property(String property, float value, Op op) {
        //noinspection unchecked
        Property<Float> controller = Properties.get(property);

        for (ViewWrapper wrapper : views) {

            View view = wrapper.view;

            switch (op) {
                case Add:
                    controller.set(view, controller.get(view) + value);
                    break;
                case Scale:
                    controller.set(view, controller.get(view) * value);
                    break;
                case Replace:
                case Set:
                    controller.set(view, value);
                    break;
            }
        }

        return this;
    }

    /**
     * Resets all view properties to their default values.
     * @return This ViewProxy instance.
     */
    public $ resetProperties() {
        for (ViewWrapper wrapper : views) {
            Utils.ViewUtils.resetViewProperties(wrapper.view);
        }

        return this;
    }

    /**
     * Distribute the views in the set one after the other.
     * @param property Must be $.X to distribute horizontally or $.Y to distribute vertically.
     * @param start The initial position from which to start distributing views.
     * @return This ViewProxy instance.
     */
    public $ distribute(String property, float start) {
        if ($.X.equals(property)) {
            for (ViewWrapper wrapper : views) {
                wrapper.view.setX(start);

                start += wrapper.view.getWidth();
            }
        }
        else if ($.Y.equals(property)) {
            for (ViewWrapper wrapper : views) {
                wrapper.view.setY(start);

                start += wrapper.view.getHeight();
            }
        }

        return this;
    }

    /**
     * Distribute the views in the set horizontally one after the other.
     * @param xStart The initial X position from which to start distributing views horizontally.
     * @param yStart The initial Y pozition from which to start distributing views.
     * @param limit The maximum horizontal space the views are allowed to occupy. When this limit is exceeded, the views will wrap, creating a new row.
     * @return This ViewProxy instance.
     */
    public $ distribute(final float xStart, float yStart, float limit) {
        float caret = xStart;
        float y = yStart;

        for (ViewWrapper wrapper : views) {

            if (caret + wrapper.view.getWidth() >= limit) {
                caret = xStart;
                y += wrapper.view.getHeight();
            }

            wrapper.view.setX(caret);
            wrapper.view.setY(y);

            caret += wrapper.view.getWidth();
        }

        return this;
    }

    @IntDef({Visible, Invisible, Gone, View.VISIBLE, View.INVISIBLE, View.GONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Visibility {}

    /**
     * Alias to {@link android.view.View#VISIBLE}
     */
    public final static int Visible = View.VISIBLE;
    /**
     * Alias to {@link android.view.View#INVISIBLE}
     */
    public final static int Invisible = View.INVISIBLE;
    /**
     * Alias to {@link android.view.View#GONE}
     */
    public final static int Gone = View.GONE;

    /**
     * Sets the visibility of each view in the set. See {@link android.view.View#setVisibility(int) View.setVisibility()} for more info.
     * @param visibility Must be View.VISIBLE, View.INVISIBLE or View.GONE.
     * @return This ViewProxy instance.
     */
    public $ visibility(@Visibility int visibility) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setVisibility(visibility);
        }

        return this;
    }

    /**
     * Get the visibility of the first view in the set.
     * @return The visibility of the first view in the set, or {@link android.view.View#INVISIBLE} if the set is empty.
     */
    @Visibility
    public int visibility() {
        if (views.size() > 0) return views.get(0).view.getVisibility();

        return View.INVISIBLE;
    }

    /**
     * Gets the layout params of the first view in the set.
     * @return The layout params of the first view in the set, or null if the set is empty.
     */
    public ViewGroup.LayoutParams params() {
        if (views.size() > 0) return views.get(0).view.getLayoutParams();

        return null;
    }

    /**
     * Sets the layout params of all the views in the set.
     * @param params The params which will be set.
     * @return This ViewProxy instance.
     */
    public $ params(ViewGroup.LayoutParams params) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setLayoutParams(params);
        }

        return this;
    }

    /**
     * Gets the current width of the first view in the set.
     * @return The width of the first view in the set, or 0 if the set is empty.
     */
    public int width() {
        return views.size() > 0 ? views.get(0).view.getWidth() : 0;
    }

    /**
     * Gets the current height of the first view in the set.
     * @return The height of the first view in the set, or 0 if the set is empty.
     */
    public int height() {
        return views.size() > 0 ? views.get(0).view.getHeight() : 0;
    }



    /**
     * Gets the current inner width of the first view in the set. The inner width is the width of the view minus its horizontal padding.
     * @return The width of the first view in the set, or 0 if the set is empty.
     */
    public int innerWidth() {
        return views.size() > 0 ? views.get(0).view.getWidth() - views.get(0).view.getPaddingLeft() - views.get(0).view.getPaddingRight() : 0;
    }

    /**
     * Gets the current inner height of the first view in the set. The inner height is the height of the view minus its vertical padding.
     * @return The height of the first view in the set, or 0 if the set is empty.
     */
    public int innerHeight() {
        return views.size() > 0 ? views.get(0).view.getHeight() - views.get(0).view.getPaddingTop() - views.get(0).view.getPaddingBottom() : 0;
    }

    /**
     * Gets the value of the specified layout property from the first view in the set, or 0 if the set is empty.
     * @param property The layout property to return.
     * @return The value of the specified property.
     */
    public int layout(String property) {
        if (views.size() > 0) return LayoutProperties.get(property).get(views.get(0).view);

        return 0;
    }


    /**
     * Sets the value of the specified layout property for all the views in the set.
     * @param property The layout property to set.
     * @param value The value which will be set.
     * @return This ProxyView instance.
     * @throws NullPointerException if the property affects the layout params, but any of the views doesn't have layout params set.
     */
    public $ layout(String property, int value) {
        return layout(property, value, Op.Set);
    }

    /**
     * Sets the value of the specified layout property for all the views in the set.
     * @param property The layout property to set.
     * @param value The value which will be set.
     * @param op The operation used to mix the new value with the old value.
     * @return This ProxyView instance.
     * @throws NullPointerException if the property affects the layout params, but any of the views doesn't have layout params set.
     */
    public $ layout(String property, int value, Op op) {
        //noinspection unchecked
        Property<Integer> controller = LayoutProperties.get(property);

        for (ViewWrapper wrapper : views) {

            View view = wrapper.view;

            switch (op) {
                case Add:
                    controller.set(view, controller.get(view) + value);
                    break;
                case Scale:
                    controller.set(view, controller.get(view) * value);
                    break;
                case Replace:
                case Set:
                    controller.set(view, value);
                    break;
            }
        }

        return this;
    }

    /**
     * Positions the top left corner of the supplied view to the supplied coordinates.
     * @param view The view to be positioned.
     * @param x The left coordinate.
     * @param y The top coordinate.
     */
    private static void position(View view, int x, int y) {
        Utils.ViewUtils.positionViewOnWindowPoint(view, x, y);
    }


    /**
     * Centers the view on the specified point, using the TranslationX and TranslationY properties.<br/>
     * This is the low level implementation of {@link $#center(Point)}.
     * @param view The view that will be centered.
     * @param point The point on which the view will be centered.
     */
    public static void center(View view, Point point) {
        Utils.ViewUtils.centerViewOnWindowPoint(view, point.x, point.y);
    }


    /**
     * Centers the view on the specified point, using the TranslationX and TranslationY properties.<br/>
     * This is the low level implementation of {@link $#center(int, int)}.
     * @param view The view that will be centered.
     * @param left The left coordinate.
     * @param top The top coordinate.
     */
    public static void center(View view, int left, int top) {
        Utils.ViewUtils.centerViewOnWindowPoint(view, left, top);
    }

    /**
     * Centers all the views in this set on the specified point, using the TranslationX and TranslationY properties.
     * @param left The left coordinate.
     * @param top The top coordinate.
     * @return This ViewProxy instance.
     */
    public $ center(int left, int top) {
        for (ViewWrapper wrapper : views) {
            $.center(wrapper.view, left, top);
        }

        return this;
    }

    /**
     * Centers all the views in this set on the specified point, using the TranslationX and TranslationY properties.
     * @param point The point on which the views will be centered.
     * @return This ViewProxy instance.
     */
    public $ center(Point point) {
        return center(point.x, point.y);
    }

    /**
     * Retrieves the center position of the first view in the set. This position is relative to the containing window.
     * @return The center position of the first view in the set or null if the set is empty.
     */
    public Point center() {
        if (views.size() == 0) return null;

        View view = views.get(0).view;

        return $.center(view);
    }


    private final static Rect LocationRect = new Rect();
    /**
     * Retrieves the center position of the specified view. This position is relative to the containing window.
     * @return The center position of the view.
     */
    public static Point center(View view) {
        view.getGlobalVisibleRect(LocationRect);

        return new Point(LocationRect.left + view.getWidth() / 2, LocationRect.top + view.getHeight() / 2);
    }

    /**
     * Retrieves the top left position of the specified view in the set.
     * @return The top left position of the view.
     */
    public static Point offset(View view) {
        view.getGlobalVisibleRect(LocationRect);

        return new Point(LocationRect.left, LocationRect.top);
    }

    /**
     * Retrieves the top left position of the first view in the set. This position is relative to the containing window.
     * @return The top left position of the first view in the set or null if the set is empty.
     */
    public Point offset() {
        if (views.size() == 0) return null;

        View view = views.get(0).view;

        return $.offset(view);
    }

    // *************************** TEXT VIEW SPECIFIC PROPERTIES ***********************************

    /**
     * Sets the typeface of all the TextView and subclasses in this set.
     * @param typeface The typeface that will be set
     * @return This viewProxy instance.
     */
    public $ typeface(Typeface typeface) {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                ((TextView) view).setTypeface(typeface);
            }
        }

        return this;
    }

    /**
     * Gets the typeface of the first TextView in the set.
     * @return The typeface of the first TextView in the set, or null if there are no TextViews in this set.
     */
    public Typeface typeface() {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                return ((TextView) view).getTypeface();
            }
        }

        return null;
    }

    /**
     * Sets the text color on all TextViews in this set.
     * @param color The hex color that will be set.
     * @return This ViewProxy instance.
     */
    public $ textColor(int color) {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                ((TextView) view).setTextColor(color);
            }
        }

        return this;
    }

    /**
     * Gets the text color of the first TextView in the set.
     * @return The text color of the first TextView in the set, or 0 if there are no TextViews in this set.
     */
    public int textColor() {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                return ((TextView) view).getCurrentTextColor();
            }
        }

        return 0;
    }

    /**
     * Sets the text size on all TextViews in this set.
     * @param size The size of the text. The size is interpreted as a <strong>scaled pixel</strong> value and will be converted into the appropriate pixel value.
     * @return This ViewProxy instance.
     */
    public $ textSize(float size) {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                ((TextView) view).setTextSize(size);
            }
        }

        return this;
    }

    /**
     * Gets the text size expressed in <strong>pixels</strong> of the first TextView in the set.
     * @return The text size expressed in pixels of the first TextView in the set, or 0 if there are no TextViews in this set.
     */
    public float textSize() {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                return ((TextView) view).getTextSize();
            }
        }

        return 0;
    }

    /**
     * Sets the gravity on all TextViews in this set.
     * @param gravity The gravity that will be set.
     * @return This ViewProxy instance.
     */
    public $ gravity(int gravity) {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                ((TextView) view).setGravity(gravity);
            }
        }

        return this;
    }

    /**
     * Gets the gravity of the first TextView in the set.
     * @return The gravity of the first TextView in the set, or 0 if there are no TextViews in this set.
     */
    public int gravity() {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) {
                return ((TextView) view).getGravity();
            }
        }

        return 0;
    }

    /**
     * Sets the clipsChildren property on all instances of ViewGroup in the set.
     * @param clips Whether the ViewGroups will clip their children or not.
     * @return This ViewProxy instance.
     */
    public $ clips(boolean clips) {
        for (ViewWrapper wrapper : views) {
            if (wrapper.view instanceof ViewGroup) {
                ((ViewGroup) wrapper.view).setClipChildren(clips);
            }
        }

        return this;
    }

    /**
     * Prevents these views' immediate parents from clipping their children
     * @return This ViewProxy instance.
     */
    public $ unclip() {
        return unclip(1);
    }

    /**
     * Prevents these views' parents from clipping their children
     * @param depth How far up the view hierarchy this setting should be applied.
     * @return This ViewProxy instance.
     * @throws ClassCastException if the depth exceeds the view hierarchy's bounds
     */
    public $ unclip(int depth) {
        if (depth == 0) return this;

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;
            ViewGroup parent = (ViewGroup) view.getParent();
            parent.setClipChildren(false);

            for (int i = 1; i < depth; i++) {
                parent = (ViewGroup) parent.getParent();
                parent.setClipChildren(false);
            }
        }

        return this;
    }


    /**
     * Enables these views' immediate parents to clip their children
     * @return This ViewProxy instance.
     */
    public $ clip() {
        return clip(1);
    }

    /**
     * Enables these views' parents to clip their children
     * @param depth How far up the view hierarchy this setting should be applied.
     * @return This ViewProxy instance.
     * @throws ClassCastException if the depth exceeds the view hierarchy's bounds
     */
    public $ clip(int depth) {
        if (depth == 0) return this;

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;
            ViewGroup parent = (ViewGroup) view.getParent();
            parent.setClipChildren(false);

            for (int i = 1; i < depth; i++) {
                parent = (ViewGroup) parent.getParent();
                parent.setClipChildren(true);
            }
        }

        return this;
    }

    /**
     * Sets the selected property for each view in the set.
     * @param selected Whether the views are selected or not.
     * @return This ViewProxy instance.
     */
    public $ selected(boolean selected) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setSelected(selected);
        }

        return this;
    }

    /**
     * Gets the selected property of the first view in the set.
     * @return True if the view is selected, false if not or if the set is empty.
     */
    public boolean selected() {
        return views.size() > 0 && views.get(0).view.isSelected();
    }

    public int length() {
        return views.size();
    }

    public $ interpolate(String property, float fraction, float value) {
        //noinspection unchecked
        Property<Float> controller = Properties.get(property);

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (!$.hasMetadata(wrapper.view, property)) $.metadata(wrapper.view, property, controller.get(view));

            Properties.get(property).set(view, Utils.interpolateValues(fraction, (Float) $.metadata(wrapper.view, property), value));
        }

        return this;
    }

    public $ interpolate(String property, float fraction, String key) {
        //noinspection unchecked
        Property<Float> controller = Properties.get(property);

        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (!$.hasMetadata(wrapper.view, property)) {
                if (DEBUG_LAYOUT) Log.e(TAG, "View is missing the interpolated value, seeding...");
                $.metadata(wrapper.view, property, controller.get(view));
            }

            Properties.get(property).set(view, Utils.interpolateValues(fraction, (Float) $.metadata(wrapper.view, property), (Float) $.metadata(view, key)));
//            view.setX();

            if (DEBUG_LAYOUT) Log.d(TAG, "Interpolating view from " + $.metadata(wrapper.view, property) + " to " + $.metadata(view, key) + "; current value: " + view.getX());
        }

        return this;
    }

    public $ layer(int layer) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setLayerType(layer, null);
        }

        return this;
    }

    public $ layer(int layer, Paint paint) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setLayerType(layer, paint);
        }

        return this;
    }

    public int layer() {
        if (views.size() > 0) return views.get(0).view.getLayerType();

        return View.LAYER_TYPE_NONE;
    }

    public $ buildLayer() {
        for (ViewWrapper wrapper : views) {
            wrapper.view.buildLayer();
        }

        return this;
    }

    public float x() {
        if (views.size() > 0) return views.get(0).view.getX();

        return 0;
    }

    //****************************** EVENTS ***************************

    // All the listeners only have setter methods, so their values must be retrieved using reflection.
    private static Field FieldListenerInfo;
    private static Field FieldOnClickListener;
    private static Field FieldOnLongClickListener;
    private static Field FieldOnTouchListener;

    static {
        try {

            FieldListenerInfo = View.class.getDeclaredField("mListenerInfo");
            FieldListenerInfo.setAccessible(true);

            FieldOnClickListener = FieldListenerInfo.getType().getDeclaredField("mOnClickListener");
            FieldOnClickListener.setAccessible(true);

            FieldOnLongClickListener = FieldListenerInfo.getType().getDeclaredField("mOnLongClickListener");
            FieldOnLongClickListener.setAccessible(true);

            FieldOnTouchListener = FieldListenerInfo.getType().getDeclaredField("mOnTouchListener");
            FieldOnTouchListener.setAccessible(true);
        }
        catch (NoSuchFieldException e) {
            // This will crash the app on incompatible source codes, TODO handle more elegantly
            FieldListenerInfo = null;
            FieldOnClickListener = null;
            FieldOnLongClickListener = null;
            FieldOnTouchListener = null;

            e.printStackTrace();

            throw new UnsupportedOperationException();
        }
    }

    /**
     * Sets whether the views in this set can respond to click events or not.
     * @param clickable True if the views will respond to click events, false if not.
     * @return This ViewProxy instance.
     */
    public $ clickable(boolean clickable) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setClickable(clickable);
        }

        return this;
    }

    /**
     * Gets the clickable property of the first view in the set.
     * @return True if the first view in the set is clickable, false if it is not or if the set is empty.
     */
    public boolean clickable() {
        if (views.size() > 0) return views.get(0).view.isClickable();

        return false;
    }

    /**
     * Simulates a click on each view in the set.
     * @return Thie ViewProxy instance.
     */
    public $ click() {
        for (ViewWrapper wrapper : views) {
            wrapper.view.performClick();
        }

        return this;
    }

    /**
     * Sets the OnClickListener on all views in the set.
     * @param listener The listener which will be set.
     * @return This ViewProxy instance.
     */
    public $ click(View.OnClickListener listener) {
        for (ViewWrapper wrapper : views) {
            $.bindClickEvent(wrapper.view, null, listener);
        }

        return this;
    }

    /**
     * Binds a click listener identified by a string to all the views in the set.
     * @param identifier The string identifying this listener.
     *                   If each view already has a click listener with this identifier it will be replaced by the supplied listener.
     *                   Otherwise, a new listener will be added to this view.
     * @param listener The listener which will respond to click events.
     * @return This ViewProxy instance.
     */
    public $ click(String identifier, View.OnClickListener listener) {
        for (ViewWrapper wrapper : views) {
            $.bindClickEvent(wrapper.view, identifier, listener);
        }

        return this;
    }

    private final static View.OnClickListener ProxyClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            //noinspection unchecked
            Map<String, View.OnClickListener> eventMap = (Map<String, View.OnClickListener>) $.metadata(view, $.Click);

            for (View.OnClickListener listener : eventMap.values()) {
                listener.onClick(view);
            }

        }
    };

    // TODO Generic events listener ?
    /**
     * Binds a click listener identified by a string to the specified view.
     * @param view The view on which the listener will be set.
     * @param identifier The string identifying this listener.
     *                   If this view already has a click listener with this identifier it will be replaced by the supplied listener.
     *                   Otherwise, a new listener will be added to this view.
     * @param listener The listener which will respond to click events.
     */
    public static void bindClickEvent(View view, String identifier, View.OnClickListener listener) {
        if (DEBUG_EVENT_LISTENER) Log.d(TAG, "Binding click event...");

        try {
            Object listenerInfo = FieldListenerInfo.get(view);
            View.OnClickListener currentListener = listenerInfo != null ? (View.OnClickListener) FieldOnClickListener.get(listenerInfo) : null;

            if (currentListener != ProxyClickListener) {
                view.setOnClickListener(ProxyClickListener);
//                currentListener = null;
            }
            else {
                currentListener = null;
            }

            Map<String, View.OnClickListener> eventMap;

            if (!$.hasMetadata(view, $.Click)) {
                if (identifier == null) {
                    if (DEBUG_EVENT_LISTENER) Log.d(TAG, "Click listener has been set directly on the view!");
                    view.setOnClickListener(listener);
                    return;
                }

                $.metadata(view, $.Click, eventMap = new HashMap<String, View.OnClickListener>());

                if (currentListener != null) {
                    if (DEBUG_EVENT_LISTENER) Log.d(TAG, "Current click listener has been added to the event map!");
                    eventMap.put($.Click, currentListener);
                }
            }
            else {
                //noinspection unchecked
                eventMap = (Map<String, View.OnClickListener>) $.metadata(view, $.Click);
            }

            identifier = identifier == null ? $.Click : identifier;
            if (DEBUG_EVENT_LISTENER) Log.d(TAG, "Click listener has been added to the event map!");
            eventMap.put(identifier, listener);

        }
        catch (IllegalAccessException e) {
            // Will not happen, as the field is made accessible at init.
            e.printStackTrace();
        }


    }

    /**
     * Sets the OnTouch on all views in the set.
     * @param listener The listener which will be set.
     * @return This ViewProxy instance.
     * TODO unimplemented namespaces
     */
    public $ touch(View.OnTouchListener listener) {
        for (ViewWrapper wrapper : views) {
            wrapper.view.setOnTouchListener(listener);
        }

        return this;
    }

    /**
     * Removes all listeners bound to the specified event.
     * @param event The event for which listeners will be removed.
     * @return This ViewProxy instance.
     */
    public $ off(String event) {
        int index = 0;
        for (ViewWrapper wrapper : views) {
            ViewEventRemovers.get(event).run(wrapper.view, index);
            index++;
        }
        return this;
    }

//    public void x(float x) {
//        x(x, Op.Set);
//    }
//
//    public $ x(float x, Op op) {
//        for (ViewWrapper view : views) {
//            if (op != Op.Replace && !$.hasMetadata(view.view, X)) $.metadata(view.view, X, view.view.getX());
//
//            switch (op) {
//                case Add:
//                    view.view.setX((Float) $.metadata(view.view, X) + x);
//                    break;
//                case Scale:
//                    view.view.setX((Float) $.metadata(view.view, X) * x);
//                    break;
//                case Replace:
//                    $.metadata(view.view, X, x);
//                case Set:
//                    view.view.setX(x);
//                    break;
//            }
//        }
//
//        return this;
//    }
//
//    public $ interpolateX(float x, float fraction) {
//        for (ViewWrapper view : views) {
//            if (!$.hasMetadata(view.view, X)) $.metadata(view.view, X, view.view.getX());
//
//            view.view.setX(Utils.interpolateValues(fraction, (Float) $.metadata(view.view, X), x));
//        }
//
//        return this;
//    }

    /**
     * Gets the "value" of the first view in the set. What this means exactly depends on the type of view:
     * <b>TextView</b> and descendants return the text.
     * <b>Spinners, Drop-down menus, progress bars and sliders</b> return their boxed numeric value.
     * <b>PrecisionRangeSliders</b> can return an array of two numbers.
     * </b>Checkboxes<b> return boxed booleans of their checked state.
     * Other types of views return null.
     * @return The corresponding value of the first view in the set.
     */
    public Object value() {
        if (views.size() > 0) {
            View view = views.get(0).view;

            if (view instanceof TextView) {
                return ((TextView) view).getText();
            }
            else if (view instanceof Picker) {
                return ((Picker) view).getValue();
            }
            else if (view instanceof RatingBar) {
                return ((RatingBar) view).getRating();
            }
            else if (view instanceof ProgressBar) {
                return ((ProgressBar) view).getProgress();
            }
            else if (view instanceof CheckBox) {
                return ((CheckBox) view).isChecked();
            }
        }

        return null;
    }

    public $ value(Object value) {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (value instanceof CharSequence) {
                if (view instanceof TextView) {
                    ((TextView) view).setText((CharSequence) value);
                }
                else if (view instanceof Picker) {
                    ((Picker) view).setCurrentValue((CharSequence) value);
                }
            }
            else if (value instanceof Number) {
                if (view instanceof Picker) {
                    ((Picker) view).setCurrentValue((Integer) value);
                }
                else if (view instanceof RatingBar) {
                    ((RatingBar) view).setRating((Float) value);
                }
                else if (view instanceof ProgressBar) {
                    ((ProgressBar) view).setProgress((Integer) value);
                }
                else if (view instanceof CheckBox) {
                    ((CheckBox) view).setChecked((Boolean) value);
                }
            }
        }

        return this;
    }

    public CharSequence text() {
        if (views.size() > 0) {
            View view = views.get(0).view;

            if (view instanceof TextView) return ((TextView) view).getText();
        }

        return null;
    }

    public $ text(CharSequence text) {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof TextView) ((TextView) view).setText(text);
        }

        return this;
    }

    //region layout
    //********************* LAYOUT AFFECTING MODIFICATIONS ********************

    /**
     * Backs up the current values of selected view properties into each views' metadata
     * @param properties The properties whose values will be stored
     * @return This ProxyView instance
     */
    public $ storeProperties(String ... properties) {
        for (String property : properties) {
            for (ViewWrapper wrapper : views) {
                $.metadata(wrapper.view, property, Properties.get(property).get(wrapper.view));
            }
        }

        return this;
    }
    /**
     * Backs up the current values of selected layout properties into each views' metadata
     * @param properties The properties whose values will be stored
     * @return This ProxyView instance
     */
    public $ storeLayout(String ... properties) {
        for (String property : properties) {
            for (ViewWrapper wrapper : views) {
                $.metadata(wrapper.view, property, LayoutProperties.get(property).get(wrapper.view));
            }
        }

        return this;
    }

    public static void saveLayout(View view) {
        $.metadata(view, Width, view.getWidth());
        $.metadata(view, Height, view.getHeight());
        $.metadata(view, X, view.getX());
        $.metadata(view, Y, view.getY());

        if (DEBUG_LAYOUT) Log.d(TAG, "Layout has been saved: (" + $.metadata(view, X) + ", " + $.metadata(view, Y) + ") -> (" + $.metadata(view, Width) + ", " + $.metadata(view, Height) + ")");
    }

    public $ saveLayout() {
        for (ViewWrapper wrapper : views) {
            $.saveLayout(wrapper.view);
        }

        return this;
    }

    // TODO queue animation, TODO animation params
    public $ applyLayout() {
        if (views.size() > 0) {
            final View FirstView = views.get(0).view;
            FirstView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    //noinspection deprecation
                    FirstView.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                    for (ViewWrapper wrapper : views) {
                        View view = wrapper.view;

                        $.metadata(wrapper.view, TargetWidth, wrapper.view.getWidth());
                        $.metadata(wrapper.view, TargetHeight, wrapper.view.getHeight());
                        $.metadata(wrapper.view, TargetWidthParam, wrapper.view.getLayoutParams().width);
                        $.metadata(wrapper.view, TargetHeightParam, wrapper.view.getLayoutParams().height);

                        $.metadata(wrapper.view, TargetX, wrapper.view.getX());
                        $.metadata(wrapper.view, TargetY, wrapper.view.getY());

                        if (DEBUG_LAYOUT) Log.d(TAG, "Applying layout! Moving from (" + $.metadata(view, X) + ", " + $.metadata(view, Y) + ") -> (" + $.metadata(view, Width) + ", " + $.metadata(view, Height) + ") to ("
                                + $.metadata(view, TargetX) + ", " + $.metadata(view, TargetY) + ") -> (" + $.metadata(view, TargetWidth) + ", " + $.metadata(view, TargetHeight) + ")");
                    }

                    $.this.finish("$LayoutQueue")
                            .animate()
                            .property($.X, $.propertyMetadataGetter($.X), $.propertyMetadataGetter($.TargetX))
                            .property($.Y, $.propertyMetadataGetter($.Y), $.propertyMetadataGetter($.TargetY))
                            .layout($.Width, $.layoutMetadataGetter($.Width), $.layoutMetadataGetter($.TargetWidth))
                            .layout($.Height, $.layoutMetadataGetter($.Height), $.layoutMetadataGetter($.TargetHeight))
                            .complete(new AnimationCallback() {
                                @Override
                                public void run($ collection) {
                                    for (ViewWrapper wrapper : views) {
                                        wrapper.view.getLayoutParams().width = (Integer) $.metadata(wrapper.view, $.TargetWidthParam);
                                        wrapper.view.getLayoutParams().height = (Integer) $.metadata(wrapper.view, $.TargetHeightParam);

                                        wrapper.view.setLayoutParams(wrapper.view.getLayoutParams());
                                    }
                                }
                            })
                            .start("$LayoutQueue");

//                    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
//
//                    each(new EachViewRunnable() {
//                        @Override
//                        public void run(View view, Activity activity) {
//                            view.getLayoutParams().width = (Integer) $.metadata(view, Width);
//                            view.getLayoutParams().height = (Integer) $.metadata(view, Height);
//
//                            view.setLayoutParams(view.getLayoutParams());
//                        }
//                    });
//
//                    for (ViewWrapper wrapper : views) {
//                        View view = wrapper.view;
//
//                        $.metadata(wrapper.view, TargetWidth, wrapper.view.getWidth());
//                        $.metadata(wrapper.view, TargetHeight, wrapper.view.getHeight());
//                        $.metadata(wrapper.view, TargetWidthParam, wrapper.view.getLayoutParams().width);
//                        $.metadata(wrapper.view, TargetHeightParam, wrapper.view.getLayoutParams().height);
//
//                        $.metadata(wrapper.view, TargetX, wrapper.view.getX());
//                        $.metadata(wrapper.view, TargetY, wrapper.view.getY());
//
//                        if (DEBUG_LAYOUT) Log.d(TAG, "Applying layout! Moving from (" + $.metadata(view, X) + ", " + $.metadata(view, Y) + ") -> (" + $.metadata(view, Width) + ", " + $.metadata(view, Height) + ") to ("
//                                + $.metadata(view, TargetX) + ", " + $.metadata(view, TargetY) + ") -> (" + $.metadata(view, TargetWidth) + ", " + $.metadata(view, TargetHeight) + ")");
//                    }
//
//                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//                        @Override
//                        public void onAnimationUpdate(ValueAnimator animation) {
//                            float fraction = animation.getAnimatedFraction();
//
//                            for (ViewWrapper wrapper : views) {
//                                wrapper.view.getLayoutParams().width = (int) Utils.interpolateValues(fraction, (Integer) $.metadata(wrapper.view, Width), (Integer) $.metadata(wrapper.view, TargetWidth));
//                                wrapper.view.getLayoutParams().height = (int) Utils.interpolateValues(fraction, (Integer) $.metadata(wrapper.view, Height), (Integer) $.metadata(wrapper.view, TargetHeight));
//
//                                wrapper.view.setLayoutParams(wrapper.view.getLayoutParams());
//                            }
//
//                            interpolate(X, fraction, TargetX);
//                            interpolate(Y, fraction, TargetY);
//                        }
//                    });
//
//                    animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
//                    animator.setDuration(200);
//
//                    animator.addListener(new AnimatorListenerAdapter() {
//                        @Override
//                        public void onAnimationEnd(Animator animation) {
//                            if (DEBUG_LAYOUT) for (ViewWrapper wrapper : views) {
//                                View view = wrapper.view;
//                                Log.d(TAG, "Layout applied! Moved from (" + $.metadata(view, X) + ", " + $.metadata(view, Y) + ") -> (" + $.metadata(view, Width) + ", " + $.metadata(view, Height) + ") to ("
//                                        + $.metadata(view, TargetX) + ", " + $.metadata(view, TargetY) + ") -> (" + $.metadata(view, TargetWidth) + ", " + $.metadata(view, TargetHeight) + ")\n"
//                                        + "Real values (" + view.getX() + ", " + view.getY() + ") -> (" + view.getWidth() + ", " + view.getHeight() + ")"
//                                );
//                            }
//                        }
//                    });
//
//                    animator.start();

                }
            });

            // skip the next frame, which contains the wrong layout
            if (false) FirstView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    FirstView.getViewTreeObserver().removeOnPreDrawListener(this);
                    return false;
                }
            });
        }

        return this;
    }

    public $ remove() {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            view.animate()
                    .alpha(0f)
                    .scaleX(0.5f).scaleY(0.5f)
                    .setDuration(200)
                    .setStartDelay(0)
                    .withLayer()
                    .setInterpolator(new Utils.FrictionInterpolator(1.5f))
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (DEBUG_LAYOUT) Log.d(TAG, "Detaching removed view!");
                            detach();
                        }
                    })
                    .start();
        }

        return this;
    }

    /**
     * Detaches all views in this set from their parents, effectively removing them from the view hierarchy.
     * @return This ViewProxy instance.
     */
    public $ detach() {
        for (ViewWrapper wrapper : views) {
            if (wrapper.view.getParent() != null) {
                ((ViewGroup) wrapper.view.getParent()).removeView(wrapper.view);
            }
        }

        return this;
    }

    /**
     * Adds each view in this set as the last child to the specified ViewGroup.
     * If necessary, each view will first be detached from its current parent.
     * @param parent The ViewGroup to which the views in this set will be added.
     * @return This ViewProxy instance.
     */
    public $ appendTo(ViewGroup parent) {
        for (ViewWrapper wrapper : views) {
            if (wrapper.view.getParent() != null) {
                ((ViewGroup) wrapper.view.getParent()).removeView(wrapper.view);
            }

            parent.addView(wrapper.view);
        }

        return this;
    }

    /**
     * Adds each view in this set as the last child to the first ViewGroup in the supplied set.
     * If necessary, each view will first be detached from its current parent.
     * If there is no parent in the supplied set, the views will not be attached.
     * @param parentSet The ViewProxy containing the ViewGroup to which the views in this set will be added.
     * @return This ViewProxy instance.
     */
    public $ appendTo($ parentSet) {
        ViewGroup parent = null;
        for (ViewWrapper wrapper : parentSet.views) {
            if (wrapper.view instanceof ViewGroup) {
                parent = (ViewGroup) wrapper.view;
            }
        }

        if (parent == null) return this;

        return appendTo(parent);
    }

    /**
     * Inserts the specified view as the last child to the first ViewGroup in the set.
     * If this view is already attached to a parent, it is first detached from it.
     * @param child The view that will be added.
     * @return This ViewProxy instance.
     */
    public $ append(View child) {
        for (ViewWrapper wrapper : views) {
            if (wrapper.view instanceof ViewGroup) {
                if (child.getParent() != null) {
                    ((ViewGroup) child.getParent()).removeView(child);
                }
                ((ViewGroup) wrapper.view).addView(child);
                return this;
            }
        }

        return this;
    }

    /**
     * Inserts each of the views in the supplied set as the last child to the first ViewGroup in the set.
     * If any of these views are already attached to a parent, they are first detached from it.
     * @param children The ViewProxy set containing the views that will be added.
     * @return This ViewProxy instance.
     */
    public $ append($ children) {
        for (ViewWrapper wrapper : views) {
            if (wrapper.view instanceof ViewGroup) {
                for (ViewWrapper childWrapper : children.views) {
                    if (childWrapper.view.getParent() != null) {
                        ((ViewGroup) childWrapper.view.getParent()).removeView(childWrapper.view);
                    }
                    ((ViewGroup) wrapper.view).addView(childWrapper.view);
                }
                return this;
            }
        }

        return this;
    }


    /**
     * Inserts the specified view to the parent of the first view in the set, before it.
     * If the first view is not attached to a parent, the child will be added before the first view in the set that does have a parent.
     * If this view is already attached to a parent, it is first detached from it.
     * @param child The view that will be added.
     * @return This ViewProxy instance.
     */
    public $ before(View child) {
        for (ViewWrapper wrapper : views) {
            View $this = wrapper.view;
            if ($this.getParent() == null) continue;

            if (child.getParent() != null) {
                ((ViewGroup) child.getParent()).removeView(child);
            }

            ViewGroup parent = ((ViewGroup) $this.getParent());
            parent.addView(child, parent.indexOfChild($this));

        }
        return this;
    }


    /**
     * Inserts each of the views in the supplied set to the parent of the first view in the set, before it.
     * If the first view is not attached to a parent, the children will be added before the first view in the set that does have a parent.
     * If any of these views are already attached to a parent, they are first detached from it.
     * @param children The ViewProxy set containing the views to be added.
     * @return This ViewProxy instance.
     */
    public $ before($ children) {
        for (ViewWrapper wrapper : views) {
            View $this = wrapper.view;
            if ($this.getParent() == null) continue;

            ViewGroup parent = ((ViewGroup) $this.getParent());
            int index = parent.indexOfChild($this);
            for (ViewWrapper childWrapper : children.views) {
                if (childWrapper.view.getParent() != null) {
                    ((ViewGroup) childWrapper.view.getParent()).removeView(childWrapper.view);
                }

                parent.addView(childWrapper.view, index);
                // this' index increases by 1 each iteration
                index++;
            }

        }
        return this;
    }


    /**
     * Inserts the specified view to the parent of the first view in the set, after it.
     * If the first view is not attached to a parent, the child will be added after the first view in the set that does have a parent.
     * If this view is already attached to a parent, it is first detached from it.
     * @param child The view that will be added.
     * @return This ViewProxy instance.
     */
    public $ after(View child) {
        for (ViewWrapper wrapper : views) {
            View $this = wrapper.view;
            if ($this.getParent() == null) continue;

            if (child.getParent() != null) {
                ((ViewGroup) child.getParent()).removeView(child);
            }

            ViewGroup parent = ((ViewGroup) $this.getParent());
            parent.addView(child, parent.indexOfChild($this) + 1);

        }
        return this;
    }


    /**
     * Inserts each of the views in the supplied set to the parent of the first view in the set, after it.
     * If the first view is not attached to a parent, the children will be added after the first view in the set that does have a parent.
     * If any of these views are already attached to a parent, they are first detached from it.
     * @param children The ViewProxy set containing the views to be added.
     * @return This ViewProxy instance.
     */
    public $ after($ children) {
        for (ViewWrapper wrapper : views) {
            View $this = wrapper.view;
            if ($this.getParent() == null) continue;

            ViewGroup parent = ((ViewGroup) $this.getParent());
            int index = parent.indexOfChild($this) + 1;
            for (ViewWrapper childWrapper : children.views) {
                if (childWrapper.view.getParent() != null) {
                    ((ViewGroup) childWrapper.view.getParent()).removeView(childWrapper.view);
                }

                if (DEBUG_LAYOUT) Log.d(TAG, "Added CHILD to parent at index " + index);
                parent.addView(childWrapper.view, index);
                // this' index increases by 1 each iteration
                index++;
            }

            parent.requestLayout();

        }
        return this;
    }

    /**
     * For each view in this set that is a {@link android.view.ViewGroup}, this method will remove all of its children views.
     * @return This ViewProxy instance.
     */
    public $ empty() {
        for (ViewWrapper wrapper : views) {
            View view = wrapper.view;

            if (view instanceof ViewGroup) {
                ((ViewGroup) view).removeAllViews();
            }
        }

        return this;
    }

    //endregion

    //region animation controllers
    // *************************** ANIMATION CONTROLS *********************************

    public $Animator animate() {
        return new $Animator();
    }

    public $ delayForLayout(String ... queue) {
        final String Queue = queue != null && queue.length > 0 ? queue[0] : SerialQueue;

        if (views.size() == 0) return this;

        return animate()
                .duration(1000) // TODO infinite repeat
                .started(new AnimationCallback() {
                    @Override
                    public void run($ collection) {

                        views.get(0).view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                views.get(0).view.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                                finish(Queue, false);
                            }
                        });
                    }
                })
                .start(Queue);
    }

    public $ delay(long delay, String ... queue) {
        final String Queue = queue != null && queue.length > 0 ? queue[0] : SerialQueue;

        return animate()
                .duration(delay)
                .start(Queue);
    }



    /**
     * Check whether the specified view is currently playing an animation.
     * @return True if the view is animated, false otherwise.
     */
    public static boolean animated(View view) {
        return $.animated(view, null);
    }


    /**
     * Check whether the specified view is currently playing an animation in the specified queue.
     * @param queue The queue for which to check animations. If queue is null, this will check all queues.
     * @return True if the view is animated, false otherwise.
     */
    public static boolean animated(View view, String queue) {
        if (queue == null) {
            AnimationQueue queues[] = $.getAllQueues(view);
            if (queues != null) for (AnimationQueue animationQueue : queues) {
                // One active queue is sufficient to determine that this view is animated.
                if (!animationQueue.isEmpty()) return true;
            }
        }
        else {
            AnimationQueue animationQueue = $.getQueue(view, queue, false);
            return animationQueue != null && !animationQueue.isEmpty();
        }

        return false;
    }

    /**
     * Check whether the first view in the set is currently animated.
     * @return True if the view is animated, false if not or if the set is empty.
     */
    public boolean animated() {
        return animated((String) null);
    }


    /**
     * Check whether the first view in the set is currently playing an animation in the specified queue.
     * @param queue The queue for which to check animations. If queue is null, this will check all queues.
     * @return True if the view is animated, false if not or if the set is empty.
     */
    public boolean animated(String queue) {
        return views.size() > 0 && $.animated(views.get(0).view, queue);
    }

    /**
     * Stop the currently playing animation on the default queue for each view.
     * @param clearQueue A single optional boolean value. If true, this will also empty the queue, preventing other pending animations from starting.
     * @return This ViewProxy instance.
     */
    public $ stop(boolean ... clearQueue) {
        if (clearQueue == null) {
            return stop((String) null);
        }

        return stop(SerialQueue, clearQueue);
    }


    /**
     * Stop the currently playing animation on the specified queue for each view.
     * @param queue The queue to be stopped. If null, the default queue will be stopped.
     * @param clearQueue A single optional boolean value. If true, this will also empty the queue, preventing other pending animations from starting.
     * @return This ViewProxy instance.
     */
    public $ stop(String queue, boolean ... clearQueue) {
        for (ViewWrapper wrapper : views) {
            $.stopQueue(wrapper.view, queue, clearQueue.length > 0 && clearQueue[0]);
        }

        return this;
    }


    /**
     * Immediately ends the currently playing animation on the default queue for each view. Before ending, this will apply the final values of the animations.
     * Animations stopped in this way still have their "ended" callback invoked.
     * @param clearQueue A single optional boolean value. If true, this will also empty the queue, applying the end values for each pending animation.
     *                   The values are applied in the order in which those animations were supposed to run, so after calling this, the same end values are applied as if the animations had run normally.
     * @return This ViewProxy instance.
     */
    public $ finish(boolean ... clearQueue) {
        if (clearQueue == null) {
            return finish((String) null);
        }

        return finish(SerialQueue, clearQueue);
    }

    /**
     * Immediately ends the currently playing animation on the default queue for each view. Before ending, this will apply the final values of the animations.
     * Animations stopped in this way still have their "ended" callback invoked.
     * @param queue The queue to be finished. If null, the default queue will be finished.
     * @param clearQueue A single optional boolean value. If true, this will also empty the queue, applying the end values for each pending animation.
     *                   The values are applied in the order in which those animations were supposed to run, so after calling this, the same end values are applied as if the animations had run normally.
     * @return This ViewProxy instance.
     */
    public $ finish(String queue, boolean ... clearQueue) {
        for (ViewWrapper wrapper : views) {
            $.finishQueue(wrapper.view, queue, clearQueue.length > 0 && clearQueue[0]);
        }

        return this;
    }


    /**
     * For each view, this pauses the animation currently running on the default queue. Call {@link $#resume() resume} to resume it.
     * @return This ViewProxy instance.
     */
    public $ pause() {
        return pause(null);
    }

    /**
     * For each view, this pauses the animation currently running on the specified queue. Call {@link $#resume() resume} to resume it.
     * @param queue The queue to be paused. If null, the default queue will be paused.
     * @return This ViewProxy instance.
     */
    public $ pause(String queue) {
        for (ViewWrapper wrapper : views) {
            $.pauseQueue(wrapper.view, queue);
        }

        return this;
    }


    /**
     * For each view, this resumes the animation on the default queue if it was paused.
     * @return This ViewProxy instance.
     */
    public $ resume() {
        return resume(null);
    }

    /**
     * For each view, this resumes the animation on the specified queue if it was paused.
     * @param queue The queue to be resumed. If null, the default queue will be resumed.
     * @return This ViewProxy instance.
     */
    public $ resume(String queue) {
        for (ViewWrapper wrapper : views) {
            $.resumeQueue(wrapper.view, queue);
        }

        return this;
    }

    //endregion

    // region animators
    // ******************************* ANIMATIONS *******************************

    public interface AnimationCallback {
        public void run($ collection);
    }

    public interface AnimationUpdateCallback {
        public void update(View view, float fraction);
    }

    /**
     * The <strong>AnimationQueue</strong> is a list of animations that will play on a certain view.
     * <br/>
     * <br/>Backed by an array, the AnimationQueue has some control over how these animations will play and also exposes some methods to control them.
     * The AnimationQueue has 2 concrete implementations: <br/>
     * <ul>
     *     <li>The {@link com.BogdanMihaiciuc.util.$.SerialAnimationQueue} which plays animations back to back.</li>
     *     <li>The {@link com.BogdanMihaiciuc.util.$.ParallelAnimationQueue} which plays animations at the same time, as soon as they're added to the queue.</li>
     * </ul>
     * <br/>
     * The actual queues aren't visible outside of the ViewProxy namespace. Instead, each queue has an identifier assigned to it (or no identifier, in which case a default identifier is used).
     * To interact with the queues, one must use either the static methods which affect all active queues, or the set methods which only affect the queues assigned to views in the set.
     */
    private static abstract class AnimationQueue {
        protected Runnable depleted;

        protected String identifier;

        /**
         * Pushes a view animator onto this queue.
         * @param animator
         */
        abstract public void push(PausableValueAnimator animator);

        /**
         * Plays the next animation in this queue.
         */
        abstract protected void dequeue();

        /**
         * Stops the current animation, then {@link com.BogdanMihaiciuc.util.$.AnimationQueue#dequeue dequeues} the next one.
         */
        abstract public void continueQueue();

        /**
         * Stops the current animation, then {@link com.BogdanMihaiciuc.util.$.AnimationQueue#dequeue dequeues} the next one.
         * @param finish If true, the current animation will apply its final values and then finish normally.
         */
        abstract public void continueQueue(boolean finish);

        /**
         * Pauses the currently playing animation.
         */
        abstract public void pause();

        /**
         * Resumes the current animation if it was paused.
         */
        abstract public void resume();

        /**
         * Stops the currently playing animation and then empties the queue. Pending animations do not start at all.
         */
        abstract public void clear();

        /**
         * Stops the currently playing animation, applying its final values.
         * The queue will then go through the pending animations in order and apply their final values, finishing then normally and removing them one by one.
         */
        abstract public void finish();

        /**
         * Checks whether this queue is empty.
         * @return True if the queue is empty, false otherwise.
         */
        abstract public boolean isEmpty();
    }

    private static class PausableValueAnimator extends ValueAnimator {

        private boolean paused;
        private long pausedTime;
        private AnimatorUpdateListener pauseListener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (paused && getCurrentPlayTime() != pausedTime) setCurrentPlayTime(pausedTime);
            }
        };

        public void pause() {
            if (Build.VERSION.SDK_INT >= 19) {
                super.pause();
                return;
            }

            if (!paused) {
                paused = true;
                pausedTime = getCurrentPlayTime();

                addUpdateListener(pauseListener);
            }
        }

        public void resume() {
            if (Build.VERSION.SDK_INT >= 19) {
                super.resume();
                return;
            }

            if (paused) {
                paused = false;
                setCurrentPlayTime(pausedTime);

                removeUpdateListener(pauseListener);
            }
        }

    }

    private static class ParallelAnimationQueue extends AnimationQueue {
        ArrayList<PausableValueAnimator> animators = new ArrayList<PausableValueAnimator>();
        @Override
        public void push(final PausableValueAnimator animator) {
            animators.add(animator);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animators.remove(animator);

                    dequeue();
                }
            });

            animator.start();
        }

        public boolean isEmpty() {
            return animators.size() == 0;
        }

        /**
         * Has no effect on the <strong>ParrallelAnimationQueue</strong>, as animations are instantly dequeued as soon as they are pushed.
         */
        @Override
        protected void dequeue() {
            // there is no need to dequeue, as animations start as soon as they are pushed

            if (animators.size() == 0 && depleted != null) depleted.run();
        }

        public void pause() {
            for (PausableValueAnimator animator : animators) {
                animator.pause();
            }
        }

        public void resume() {
            for (PausableValueAnimator animator : animators) {
                animator.resume();
            }
        }

        /**
         * Has no effect on the <strong>ParrallelAnimationQueue</strong>, as animations are instantly dequeued as soon as they are pushed, and there are never pending animations.
         * Instead, this stops all current animations.
         */
        @Override
        public void continueQueue() {
            // continuing per se is not possible, as no animation is ever waiting in the queue
            clear();
        }


        /**
         * Has no effect on the <strong>ParrallelAnimationQueue</strong>, as animations are instantly dequeued as soon as they are pushed, and there are never pending animations.
         * Instead, this stops all current animations.
         * @param finish If true, the animations will be finished instead, causing their final values to be applied, in the correct order.
         */
        @Override
        public void continueQueue(boolean finish) {
            // continuing per se is not possible, as no animation is ever waiting in the queue
            if (finish) finish(); else clear();
        }

        @Override
        public void clear() {
            while (animators.size() > 0) {
                animators.get(0).cancel();
            }

            if (depleted != null) depleted.run();
        }

        @Override
        public void finish() {
            while (animators.size() > 0) {
                int sizePre = animators.size();

                animators.get(0).end();

                if (sizePre == animators.size()) throw new IllegalStateException("Animator " + animators.get(0) + " did not clean itself up!");
            }

//            Log.d(TAG, "All animations finished, running depleter!");

            if (depleted != null) depleted.run();
        }
    }

    private static class SerialAnimationQueue extends AnimationQueue {
        ArrayList<PausableValueAnimator> animators = new ArrayList<PausableValueAnimator>();
        @Override
        public void push(final PausableValueAnimator animator) {
            animators.add(animator);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animators.remove(animator);

                    dequeue();
                }
            });

            if (animators.size() == 1) dequeue();
        }

        public boolean isEmpty() {
            return animators.size() == 0;
        }

        @Override
        protected void dequeue() {

            if (animators.size() > 0) animators.get(0).start();

            if (animators.size() == 0 && depleted != null) depleted.run();

        }

        public void pause() {
            if (animators.size() > 0) animators.get(0).pause();
        }

        public void resume() {
            if (animators.size() > 0) animators.get(0).resume();
        }

        @Override
        public void continueQueue() {
            continueQueue(false);
        }

        @Override
        public void continueQueue(boolean finish) {
            if (animators.size() > 0) {
                if (finish) animators.get(0).end();
                else animators.get(0).cancel();
            }

            if (animators.size() == 0 && depleted != null) depleted.run();
        }

        @Override
        public void clear() {
            while (animators.size() > 1) {
                animators.remove(1);
            }

            if (animators.size() > 0) animators.get(0).cancel();

            if (animators.size() == 0 && depleted != null) depleted.run();
        }

        @Override
        public void finish() {
            while (animators.size() > 0) {
                int sizePre = animators.size();

                animators.get(0).end();

                if (sizePre == animators.size()) throw new IllegalStateException("Animator did not clean itself up. This application must now crash!");
            }

            if (animators.size() == 0 && depleted != null) depleted.run();
        }
    }

    final static TimeInterpolator StandardInterpolator = new Utils.FrictionInterpolator(1.5f);

    public static interface Getter<T> {
        public T get(View view);
    }

    public static Getter<Float> propertyMetadataGetter(final String metadata) {
        return new Getter<Float>() {
            @Override
            public Float get(View view) {
                return (Float) $.metadata(view, metadata);
            }
        };
    }

    public static Getter<Integer> layoutMetadataGetter(final String metadata) {
        return new Getter<Integer>() {
            @Override
            public Integer get(View view) {
                return (Integer) $.metadata(view, metadata);
            }
        };
    }

    private static class PropertyAnimation<T> {
        Property<T> property;
        TimeInterpolator interpolator;
        T[] values;

        // The final per-view start and end values and bundled together
        // to minimize the time spent searching within the map.
        // Array dereferencing afterwards is negligible in comparison.
//        @Deprecated
//        Map<View, Object[]> viewValues = new IdentityHashMap<View, Object[]>();

        // The new array-based start+end values bundle
        // This array is indexed by the current position of each view in this set
        // The index is then stored by each animator and used to retrieve the values
        Object[] targetValues[];

        String key;
        Getter<T> startGetter;
        Getter<T> targetGetter;

        Op op = Op.Set;
    }

    public final static String OriginCenter = "$Center";
    public final static String OriginTopLeft = "$TopLeft";

    public class $Animator {

        private Map<String, PropertyAnimation> properties = new HashMap<String, PropertyAnimation>();
        private Map<String, PropertyAnimation> layoutProperties = new HashMap<String, PropertyAnimation>();
        private Map<String, PropertyAnimation> colorProperties = new HashMap<String, PropertyAnimation>();

        private PropertyAnimation<Point> cubicAnimation;

        private ArrayList<PropertyAnimation> customProperties = new ArrayList<PropertyAnimation>();
        private AnimationQueue queue;

        //        private ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        private TimeInterpolator globalInterpolator = StandardInterpolator;
        private long stride;
        private long delay;
        private long duration = 300;

        private boolean layer;
        private Integer visibility;
        private boolean remove;

        private boolean screenshot;
        private ViewGroup screenshotContainer;

        private AnimationCallback complete;
        private AnimationCallback started;
        private AnimationCallback ended;
        private AnimationCallback cancelled;
        private AnimationUpdateCallback update;


        /**
         * Sets this animator to animate the position of each view in the set, using a cubic bezier path to the specified target location.
         * The target location is relative to the center of each view.
         * @param startControl The control point for the start point. This is relative the current origin of each view.
         * @param target The target point.
         * @param targetControl The control point for the target point. This is relative to the target point.
         * @param origin An optional string specifying how the points are relative to the view. If omitted, the points are considered relative to the center of the view.
         * @return This animator instance.
         */
        public $Animator cubic(Point startControl, Point target, Point targetControl, String ... origin) {
            return cubic(null, startControl, target, targetControl, (TimeInterpolator) null, origin);
        }


        /**
         * Sets this animator to animate the position of each view in the set, using a cubic bezier path from the start point to the specified target location.
         * The points are relative to the centers of each view.
         * @param start The start point.
         * @param startControl The control point for the start point. This is relative the start point.
         * @param target The target point.
         * @param targetControl The control point for the target point. This is relative to the target point.
         * @param origin An optional string specifying how the points are relative to the view. If omitted, the points are considered relative to the center of the view.
         * @return This animator instance.
         */
        public $Animator cubic(Point start, Point startControl, Point target, Point targetControl, String ... origin) {
            return cubic(start, startControl, target, targetControl, (TimeInterpolator) null, origin);
        }

        /**
         * Sets this animator to animate the position of each view in the set, using a cubic bezier path to the specified target location, using a custom interpolator.
         * This property's animation will ignore this animator's interpolator, using the provided one instead.
         * The target location is relative to the center of each view.
         * @param startControl The control point for the start point. This is relative the current origin of each view.
         * @param target The target point.
         * @param targetControl The control point for the target point. This is relative to the target point.
         * @param interpolator The interpolator that will be used to tween each view's position. If left null, the position will use the animator's global interpolator.
         * @param origin An optional string specifying how the points are relative to the view. If omitted, the points are considered relative to the center of the view.
         * @return This animator instance.
         */
        public $Animator cubic(Point startControl, Point target, Point targetControl, TimeInterpolator interpolator, String ... origin) {
            return cubic(null, startControl, target, targetControl, interpolator, origin);
        }
        /**
         * Sets this animator to animate the position of each view in the set, using a cubic bezier path from the start point to the specified target location, using a custom interpolator.
         * This property's animation will ignore this animator's interpolator, using the provided one instead.
         * The points are relative to the centers of each view.
         * @param start The start point.
         * @param startControl The control point for the start point. This is relative the start point.
         * @param target The target point.
         * @param targetControl The control point for the target point. This is relative to the target point.
         * @param interpolator The interpolator that will be used to tween each view's position. If left null, the position will use the animator's global interpolator.
         * @param origin An optional string specifying how the points are relative to the view. If omitted, the points are considered relative to the center of the view.
         * @return This animator instance.
         */
        public $Animator cubic(Point start, Point startControl, Point target, Point targetControl, TimeInterpolator interpolator, String ... origin) {

            cubicAnimation = new PropertyAnimation<Point>();

            cubicAnimation.interpolator = interpolator;
            cubicAnimation.values = new Point[]{start, startControl, target, targetControl};
            cubicAnimation.key = "$Cubic";

            if (origin != null && origin.length > 0) {
                cubicAnimation.key = origin[0];
            }

            // The target values are interpreted differently for the cubic animation:
            // It is a list of the start AND end points, relative to the containing window
            cubicAnimation.targetValues = new Point[$.this.length()][];

            return this;
        }

        /**
         * Sets the animation to update the specified property. The {@link com.BogdanMihaiciuc.util.$.Getter getters} are used to dinamically asign the start and and end values for each view.
         * @param property The property which will be animated.
         * @param startValue The getter that will provide the start value.
         * @param targetValue The getter that will provide the target value.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator property(String property, Getter<Float> startValue, Getter<Float> targetValue, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Float> animation = new PropertyAnimation<Float>();

            animation.property = Properties.get(property);
            animation.interpolator = null;
            animation.startGetter = startValue;
            animation.targetGetter = targetValue;

            animation.key = property;

            properties.put(property, animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }

        /**
         * Sets the animation to update the specified property.
         * @param property The property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator property(String property, float value, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Float> animation = new PropertyAnimation<Float>();

            animation.property = Properties.get(property);
            animation.interpolator = null;
            animation.values = new Float[] {value};

            animation.key = property;

            properties.put(property, animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }


        /**
         * Sets the animation to update the specified property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator property(String property, float value, TimeInterpolator interpolator, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Float> animation = new PropertyAnimation<Float>();

            animation.property = Properties.get(property);
            animation.interpolator = interpolator;
            animation.values = new Float[] {value};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            properties.put(property, animation);

            return this;
        }

        /**
         * Sets the animation to update the specified property.
         * @param property The property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator property(String property, float startValue, float endValue, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Float> animation = new PropertyAnimation<Float>();

            animation.property = Properties.get(property);
            animation.interpolator = null;
            animation.values = new Float[] {startValue, endValue};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            properties.put(property, animation);

            return this;
        }


        /**
         * Sets the animation to update the specified property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator property(String property, float startValue, float endValue, TimeInterpolator interpolator,Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Float> animation = new PropertyAnimation<Float>();

            animation.property = Properties.get(property);
            animation.interpolator = interpolator;
            animation.values = new Float[] {startValue, endValue};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            properties.put(property, animation);

            return this;
        }

        /**
         * Sets the animation to update the specified layout property. The {@link com.BogdanMihaiciuc.util.$.Getter getters} are used to dinamically asign the start and and end values for each view.
         * @param property The layout property which will be animated.
         * @param startValue The getter that will provide the start value.
         * @param targetValue The getter that will provide the target value.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator layout(String property, Getter<Integer> startValue, Getter<Integer> targetValue, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = LayoutProperties.get(property);
            animation.interpolator = null;
            animation.startGetter = startValue;
            animation.targetGetter = targetValue;

            animation.key = property;

            layoutProperties.put(property, animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }



        /**
         * Sets the animation to update the specified layout property.
         * @param property The layout property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator layout(String property, int value, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = LayoutProperties.get(property);
            animation.interpolator = null;
            animation.values = new Integer[] {value};

            animation.key = property;

            layoutProperties.put(property, animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }

        /**
         * Sets the animation to update the specified layout property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The layout property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator layout(String property, int value, TimeInterpolator interpolator, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = LayoutProperties.get(property);
            animation.interpolator = interpolator;
            animation.values = new Integer[] {value};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            layoutProperties.put(property, animation);

            return this;
        }

        /**
         * Sets the animation to update the specified layout property.
         * @param property The layout property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator layout(String property, int startValue, int endValue, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = LayoutProperties.get(property);
            animation.interpolator = null;
            animation.values = new Integer[] {startValue, endValue};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            layoutProperties.put(property, animation);

            return this;
        }

        /**
         * Sets the animation to update the specified layout property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The layout property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator layout(String property, int startValue, int endValue, TimeInterpolator interpolator,Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = LayoutProperties.get(property);
            animation.interpolator = interpolator;
            animation.values = new Integer[] {startValue, endValue};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            layoutProperties.put(property, animation);

            return this;
        }

        /**
         * Sets the animation to update the specified color property. The {@link com.BogdanMihaiciuc.util.$.Getter getters} are used to dinamically asign the start and and end values for each view.
         * @param property The color property which will be animated.
         * @param startValue The getter that will provide the start value.
         * @param targetValue The getter that will provide the target value.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator color(String property, Getter<Integer> startValue, Getter<Integer> targetValue, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = ColorProperties.get(property);
            animation.interpolator = null;
            animation.startGetter = startValue;
            animation.targetGetter = targetValue;

            animation.key = property;

            colorProperties.put(property, animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }

        /**
         * Sets the animation to update the specified color property.
         * @param property The color property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator color(String property, int value, Op ... op) {
            if (property == null) return this;

            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = ColorProperties.get(property);
            animation.interpolator = null;
            animation.values = new Integer[] {value};

            animation.key = property;

            colorProperties.put(property, animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }

        /**
         * Sets the animation to update the specified color property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The color property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator color(String property, int value, TimeInterpolator interpolator, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = ColorProperties.get(property);
            animation.interpolator = interpolator;
            animation.values = new Integer[] {value};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            colorProperties.put(property, animation);

            return this;
        }


        /**
         * Sets the animation to update the specified color property.
         * @param property The color property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator color(String property, int startValue, int endValue, Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = ColorProperties.get(property);
            animation.interpolator = null;
            animation.values = new Integer[] {startValue, endValue};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            colorProperties.put(property, animation);

            return this;
        }

        /**
         * Sets the animation to update the specified color property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The color property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         */
        public $Animator color(String property, int startValue, int endValue, TimeInterpolator interpolator,Op ... op) {
            if (property == null) return this;
            PropertyAnimation<Integer> animation = new PropertyAnimation<Integer>();

            animation.property = ColorProperties.get(property);
            animation.interpolator = interpolator;
            animation.values = new Integer[] {startValue, endValue};

            animation.key = property;

            if (op.length > 0) {
                animation.op = op[0];
            }

            colorProperties.put(property, animation);

            return this;
        }



        /**
         * Sets the animation to update the specified custom property.
         * @param property The property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         * @deprecated Deprecated. Instead, register your own properties with {@link $#extendProperty(String, Property)}, {@link $#extendColor(String, Property)} or {@link $#extendLayout(String, Property)}
         * and use those with the generic animation setup methods.
         */
        @Deprecated
        public $Animator property(Property property, Object value, Op ... op) {
            PropertyAnimation animation = new PropertyAnimation();

            animation.property = property;
            animation.interpolator = null;
            animation.values = new Object[] {value};

            customProperties.add(animation);

            if (op.length > 0) {
                animation.op = op[0];
            }

            return this;
        }


        /**
         * Sets the animation to update the specified custom property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The color property which will be animated.
         * @param value The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         * @deprecated Deprecated. Instead, register your own properties with {@link $#extendProperty(String, Property)}, {@link $#extendColor(String, Property)} or {@link $#extendLayout(String, Property)}
         * and use those with the generic animation setup methods.
         */
        @Deprecated
        public $Animator property(Property property, Object value, TimeInterpolator interpolator, Op ... op) {
            PropertyAnimation animation = new PropertyAnimation();

            animation.property = property;
            animation.interpolator = interpolator;
            animation.values = new Object[] {value};

            if (op.length > 0) {
                animation.op = op[0];
            }

            customProperties.add(animation);

            return this;
        }

        /**
         * Sets the animation to update the specified custom property.
         * @param property The custom property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         * @deprecated Deprecated. Instead, register your own properties with {@link $#extendProperty(String, Property)}, {@link $#extendColor(String, Property)} or {@link $#extendLayout(String, Property)}
         * and use those with the generic animation setup methods.
         */
        @Deprecated
        public $Animator property(Property property, Object startValue, Object endValue, Op ... op) {
            PropertyAnimation animation = new PropertyAnimation();

            animation.property = property;
            animation.interpolator = null;
            animation.values = new Object[] {startValue, endValue};

            if (op.length > 0) {
                animation.op = op[0];
            }

            customProperties.add(animation);

            return this;
        }

        /**
         * Sets the animation to update the specified color property, using a custom interpolator. This property's animation will ignore this animator's interpolator, using the provided one instead.
         * @param property The color property which will be animated.
         * @param startValue The starting value that will be applied when the animation starts.
         * @param endValue The target value to which the property will be animated.
         * @param interpolator The interpolator that will be used to tween this property's values.
         * @param op An optional operator that can be used to combine the new property values with the current property value. See {@link com.BogdanMihaiciuc.util.$.Op}
         * @return This animator instance.
         * @deprecated Deprecated. Instead, register your own properties with {@link $#extendProperty(String, Property)}, {@link $#extendColor(String, Property)} or {@link $#extendLayout(String, Property)}
         * and use those with the generic animation setup methods.
         */
        @Deprecated
        public $Animator property(Property property, Object startValue, Object endValue, TimeInterpolator interpolator, Op ... op) {
            PropertyAnimation animation = new PropertyAnimation();

            animation.property = property;
            animation.interpolator = interpolator;
            animation.values = new Object[] {startValue, endValue};

            if (op.length > 0) {
                animation.op = op[0];
            }

            customProperties.add(animation);

            return this;
        }

        /**
         * Controls whether this animation will require a hardware layer to be enabled for the views in the set.
         * If a hardware layer is required, the animator will automatically {@link android.view.View#setLayerType(int, Paint) setLayerType(int, Paint)} with {@link android.view.View#LAYER_TYPE_HARDWARE}
         * at the start of the animation, and then again at the end, using whichever layer type each view had before the animation started.
         * @param layer Whether a hardware layer should be set for this animation.
         * @return This animator instance.
         */
        public $Animator layer(boolean layer) {
            this.layer = layer;

            return this;
        }

        /**
         * Controls whether this animation will require animating static screenshot views instead of the regular views.<br/><br/>
         * If enabled, before the animation starts, the animator will call {@link $#screenshot(View) $.screenshot(View)} to obtain a screenshot view for each view in the set.
         * Each screenshot view will be placed directly inside the window's {@link Window#getDecorView() decor view}, on top of all other views. During the animation, the original views
         * will be hidden using {@link View#setVisibility(int)}, and the animated properties will be applied to the screenshot views instead. When the animation completes on each view,
         * the corresponding screenshot view will be removed, and the final values of each animated properties will be applied to the original view. <br/><br/>
         * When using a custom update listener, it will be called twice for the final frame: once to apply the properties to the screenshot view and then again to apply them to the original view.<br/><br/>
         * You may use the {@link com.BogdanMihaiciuc.util.$.$Animator#screenshotContainer(ViewGroup)} to change where the screenshot views will be placed.
         * @param screenshot Whether a screenshot layer should be set for this animation.
         * @return This animator instance.
         * @throws IllegalStateException If this called after the animation has been started.
         */
        public $Animator screenshot(boolean screenshot) {
            this.screenshot = screenshot;
            // TODO
            // TODO
            // TODO
            // TODO
            // TODO
            // TODO

            return this;
        }

        /**
         * Controls where the screenshot views will be placed during this animation.<br/><br/>
         * Must be used with {@link $.$Animator#screenshot(boolean) screenshot(true)}
         * @param container The {@link android.view.ViewGroup} in which the screenshots will be placed.
         * @return This animator instance.
         * @throws IllegalStateException If this called after the animation has been started.
         */
        public $Animator screenshotContainer(ViewGroup container) {
            this.screenshotContainer = container;
            // TODO
            // TODO
            // TODO
            // TODO
            // TODO
            // TODO

            return this;
        }

        /**
         * Controls whether views in this set will be removed when the animation ends.
         * @param remove True if the views will be removed, false otherwise.
         * @return This animator instance.
         */
        public $Animator remove(boolean remove) {
            this.remove = remove;

            return this;
        }

        public boolean layer() {
            return layer;
        }

        public $Animator duration(long duration) {
            this.duration = duration;

            return this;
        }

        long duration() {
            return duration;
        }

        /**
         * Sets the visibility of the view before or after the animation runs.
         * @param visibility The visibility of the view:
         *                   View.VISIBLE: will be set at the beginning of the animation
         *                   View.INVISIBLE or View.GONE: will be set at the end of the animation
         *                   null: does not change the view's visibility setting
         * @return This animator instance.
         */
        public $Animator visibility(Integer visibility) {
            this.visibility = visibility;

            return this;
        }

        /**
         * Applies the start values for each property that defines them.
         * Additionally, this will evaluate the end-values for all properties that defined them as "current value".
         * @return This animator instance.
         */
        public $Animator forcefeed() {
            // TODO op, correct end-property calculations?

            for (ViewWrapper wrapper : views) {
                View view = wrapper.view;

                if (cubicAnimation != null && cubicAnimation.values[0] != null) {
                    $.center(view, cubicAnimation.values[0].x, cubicAnimation.values[0].y);
                }

                for (PropertyAnimation<Float> property : properties.values()) {
                    if (property.values.length == 2) {
                        if (property.values[1] == $.CurrentPropertyValue) property.values[1] = property.property.get(view);
                        property.property.set(view, property.values[0]);
                    }
                    else if (property.startGetter != null) {
                        // This also implies an end-getter, so no reason to forcefeed the current end-values.
                        property.property.set(view, property.startGetter.get(view));
                    }
                }

                for (PropertyAnimation<Integer> property : layoutProperties.values()) {
                    if (property.values.length == 2) {
                        if (property.values[1] == $.CurrentLayoutValue) property.values[1] = property.property.get(view);
                        property.property.set(view, property.values[0]);
                    }
                    else if (property.startGetter != null) {
                        // This also implies an end-getter, so no reason to forcefeed the current end-values.
                        property.property.set(view, property.startGetter.get(view));
                    }
                }

                for (PropertyAnimation<Integer> property : colorProperties.values()) {
                    if (property.values.length == 2) {
                        if (property.values[1] == $.CurrentColorValue) property.values[1] = property.property.get(view);
                        property.property.set(view, property.values[0]);
                    }
                    else if (property.startGetter != null) {
                        // This also implies an end-getter, so no reason to forcefeed the current end-values.
                        property.property.set(view, property.startGetter.get(view));
                    }
                }

            }

            return this;
        }

        public $Animator interpolator(TimeInterpolator interpolator) {
            globalInterpolator = interpolator;
            return this;
        }

        public $Animator delay(long delay) {
            if (delay < 0) delay = 0;
            this.delay = delay;
            return this;
        }

        public $Animator stride(long stride) {
            this.stride = stride;
            return this;
        }

        /**
         * Register a callback that will be invoked when the animation has been completed.
         * @param callback The callback that will be invoked.
         * @return This animator instance.
         */
        public $Animator complete(AnimationCallback callback) {
            complete = callback;

            return this;
        }


        /**
         * Register a callback that will be invoked when the first animation from this set starts.
         * @param callback The callback that will be invoked.
         * @return This animator instance.
         */
        public $Animator started(AnimationCallback callback) {
            started = callback;

            return this;
        }


        /**
         * Register a callback that will be invoked when the animation has been cancelled.
         * @param callback The callback that will be invoked.
         * @return This animator instance.
         */
        public $Animator cancelled(AnimationCallback callback) {
            cancelled = callback;

            return this;
        }


        /**
         * Register a callback that will be invoked when the animation ends naturally.
         * When ended is called, all the views in this set have had their final animation values applied.
         * Ended is called when the animation is finished via $().finish(), but not if cancelled early with $().stop()
         * @param callback The callback that will be invoked.
         * @return This animator instance.
         */
        public $Animator ended(AnimationCallback callback) {
            ended = callback;

            return this;
        }


        /**
         * Register a callback that will be invoked after every frame of the animation.
         * @param callback The callback that will be invoked.
         * @return This animator instance.
         */
        public $Animator update(AnimationUpdateCallback callback) {
            update = callback;

            return this;
        }

        public $ start() {
            return start(true);
        }

        public $ start(boolean queue) {
            return start(queue ? SerialQueue : ParallelQueue);
        }

        public $ start(AnimationQueue queue) {
            int i = 0;

            for (PropertyAnimation animation : properties.values()) {
                animation.targetValues = new Object[$.this.length()][];
            }

            for (PropertyAnimation animation : layoutProperties.values()) {
                animation.targetValues = new Object[$.this.length()][];
            }

            for (PropertyAnimation animation : colorProperties.values()) {
                animation.targetValues = new Object[$.this.length()][];
            }

            for (ViewWrapper wrapper : views) {
                final View view = wrapper.view;

                long delay = this.delay + stride * i;
                PausableValueAnimator animator = animatorForView(view, delay, i);

                setCallbacksForAnimator(animator, i);
                queue.push(animator);

                i++;
            }

            return $.this;
        }

        public $ start(String queue) {

            if (queue == null) queue = ParallelQueue;

            for (PropertyAnimation animation : properties.values()) {
                animation.targetValues = new Object[$.this.length()][];
            }

            for (PropertyAnimation animation : layoutProperties.values()) {
                animation.targetValues = new Object[$.this.length()][];
            }

            for (PropertyAnimation animation : colorProperties.values()) {
                animation.targetValues = new Object[$.this.length()][];
            }

            int i = 0;
            for (ViewWrapper wrapper : views) {
                final View view = wrapper.view;

                long delay = this.delay + stride * i;
                PausableValueAnimator animator = animatorForView(view, delay, i);

                setCallbacksForAnimator(animator, i);
                $.getQueue(view, queue).push(animator);

                i++;
            }

            return $.this;
        }

        private void setCallbacksForAnimator(PausableValueAnimator animator, int index) {
            if (index == views.size() - 1) {
                animator.addListener(new AnimatorListenerAdapter() {
                    public boolean animationCancelled;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (cancelled != null) cancelled.run($.this);

                        animationCancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!animationCancelled && ended != null) ended.run($.this);
                        if (complete != null) complete.run($.this);
                    }
                });
            }
            if (index == 0) {

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (started != null) started.run($.this);
                    }
                });
            }

        }

        private PausableValueAnimator animatorForView(final View view, long delay, final int Index) {
            PausableValueAnimator animator = new PausableValueAnimator();
            animator.setFloatValues(0f, 1f);

            animator.setInterpolator(new LinearInterpolator());

//            final ArrayList<PropertyAnimation<Float>> StandardProperties = new ArrayList<PropertyAnimation<Float>>(properties.values());
            //noinspection unchecked
            final PropertyAnimation<Float> StandardProperties[] = properties.values().toArray(new PropertyAnimation[properties.size()]);

            animator.setDuration(duration);
            animator.setStartDelay(delay);

            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                private boolean firstStep = true;

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {

                    // region cubic
                    if (cubicAnimation != null) {
                        // TODO support current property value for bezier animation!
                        if (firstStep && cubicAnimation.values[0] == null) {
                            cubicAnimation.targetValues[Index] = new Point[] {
                                    $.center(view),
                                    cubicAnimation.values[1],
                                    cubicAnimation.values[2],
                                    cubicAnimation.values[3]
                            };

                            Point point[] = (Point[]) cubicAnimation.targetValues[Index];
                            cubicAnimation.targetValues[Index][1] = new Point(point[0].x + point[1].x, point[0].y + point[1].y);
                            cubicAnimation.targetValues[Index][3] = new Point(point[2].x + point[3].x, point[2].y + point[3].y);
                        }
                        else if (firstStep) {
                            cubicAnimation.targetValues[Index] = new Point[] {
                                    cubicAnimation.values[0],
                                    cubicAnimation.values[1],
                                    cubicAnimation.values[2],
                                    cubicAnimation.values[3]
                            };

                            if (true) Log.d(TAG, "Points are " + cubicAnimation.values[0] + cubicAnimation.values[1] + cubicAnimation.values[2] + cubicAnimation.values[3]);

                            Point point[] = (Point[]) cubicAnimation.targetValues[Index];
                            cubicAnimation.targetValues[Index][1] = new Point(point[0].x + point[1].x, point[0].y + point[1].y);
                            cubicAnimation.targetValues[Index][3] = new Point(point[2].x + point[3].x, point[2].y + point[3].y);
                        }

                        Point point[] = (Point[]) cubicAnimation.targetValues[Index];
                        float fraction = animation.getAnimatedFraction();

                        if (cubicAnimation.interpolator == null) {
                            fraction = globalInterpolator.getInterpolation(fraction);
                        }
                        else {
                            fraction = cubicAnimation.interpolator.getInterpolation(fraction);
                        }

                        if (cubicAnimation.key != null && cubicAnimation.key == OriginTopLeft) {
                            $.position(view, (int) Utils.bezierX(fraction, point[0], point[1], point[2], point[3]), (int) Utils.bezierY(fraction, point[0], point[1], point[2], point[3]));
                        }
                        else {
                            $.center(view, (int) Utils.bezierX(fraction, point[0], point[1], point[2], point[3]), (int) Utils.bezierY(fraction, point[0], point[1], point[2], point[3]));
                        }

                    }
                    //endregion

                    // region float properties
                    for (PropertyAnimation<Float> property : StandardProperties) {
                        if (firstStep) {
                            // The start value is not affected by ops.
                            float startValue = property.startGetter != null
                                    ? property.startGetter.get(view)
                                    : (property.values.length > 1
                                    ? property.values[0]
                                    : property.property.get(view));
//                float endValue = property.values.length < 2 ? property.values[0] : property.values[1];

                            if (property.targetGetter != null) {
                                property.values = new Float[] { property.targetGetter.get(view) };
                            }

                            int endValueIndex = property.values.length - 1;
                            float endValue;
                            if (property.values[endValueIndex] == $.CurrentPropertyValue) {
                                // Current property value is not affected by ops.
                                endValue = property.property.get(view);
                            }
                            else {

                                endValue = property.values[endValueIndex];
                                switch (property.op) {
                                    case Add:
                                        endValue += startValue;
                                        break;
                                    case Scale:
                                        endValue *= startValue;
                                        break;
                                }
                            }

//                            property.viewValues.put(view, new Float[] {startValue, endValue});
                            // Array access is much faster than IndentityHash lookup in a map
                            property.targetValues[Index] = new Float[] {startValue, endValue};
                        }

                        float fraction = animation.getAnimatedFraction();

//                        Float[] values = (Float[]) property.viewValues.get(view);
                        Float[] values = (Float[]) property.targetValues[Index];
                        float startValue = values[0];
                        float endValue = values[1];

                        if (property.interpolator == null) {
                            property.property.set(view, Utils.interpolateValues(globalInterpolator.getInterpolation(fraction), startValue, endValue));
                        } else {
                            property.property.set(view, Utils.interpolateValues(property.interpolator.getInterpolation(fraction), startValue, endValue));
                        }

                    }
                    // endregion


                    firstStep = false;

                }
            });

            if (layoutProperties.size() > 0) {
                //noinspection unchecked
                final PropertyAnimation<Integer> LayoutProperties[] = layoutProperties.values().toArray(new PropertyAnimation[layoutProperties.size()]);

                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    private boolean firstStep = true;

                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {

                        for (PropertyAnimation<Integer> property : LayoutProperties) {

                            if (firstStep) {
                                int startValue = property.startGetter != null
                                        ? property.startGetter.get(view)
                                        : (property.values.length > 1
                                        ? property.values[0]
                                        : property.property.get(view));

                                if (property.targetGetter != null) {
                                    property.values = new Integer[] { property.targetGetter.get(view) };
                                }

                                int endValueIndex = property.values.length - 1;
                                int endValue;
                                if (property.values[endValueIndex] == $.CurrentPropertyValue) {
                                    // Current property value is not affected by ops.
                                    endValue = property.property.get(view);
                                }
                                else {

                                    endValue = property.values[endValueIndex];
                                    switch (property.op) {
                                        case Add:
                                            endValue += startValue;
                                            break;
                                        case Scale:
                                            endValue *= startValue;
                                            break;
                                    }
                                }

//                                property.viewValues.put(view, new Integer[] {startValue, endValue});
                                // Array access is much faster than IndentityHash lookup in a map
                                property.targetValues[Index] = new Integer[] {startValue, endValue};
                            }

                            float fraction = animation.getAnimatedFraction();

                            Integer[] values = (Integer[]) property.targetValues[Index];
                            int startValue = values[0];
                            int endValue = values[1];

                            // Layout operations are very expensive, and even equal assignments will trigger a full traversal; skip as many as possible.
                            if (startValue == endValue) continue;

                            if (property.interpolator == null) {
                                property.property.set(view, (int) Utils.interpolateValues(globalInterpolator.getInterpolation(fraction), startValue, endValue));
                            } else {
                                property.property.set(view, (int) Utils.interpolateValues(property.interpolator.getInterpolation(fraction), startValue, endValue));
                            }

                        }


                        firstStep = false;

                    }
                });
            }

            if (colorProperties.size() > 0) {
                //noinspection unchecked
                final PropertyAnimation<Integer> ColorProperties[] = colorProperties.values().toArray(new PropertyAnimation[colorProperties.size()]);

                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    private boolean firstStep = true;

                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {

                        for (PropertyAnimation<Integer> property : ColorProperties) {

                            if (firstStep) {
                                int startValue = property.startGetter != null
                                        ? property.startGetter.get(view)
                                        : (property.values.length > 1
                                        ? property.values[0]
                                        : property.property.get(view));

                                if (property.targetGetter != null) {
                                    property.values = new Integer[] { property.targetGetter.get(view) };
                                }

                                int endValueIndex = property.values.length - 1;
                                int endValue;
                                if (property.values[endValueIndex] == $.CurrentPropertyValue) {
                                    // Current property value is not affected by ops.
                                    endValue = property.property.get(view);
                                }
                                else {

                                    endValue = property.values[endValueIndex];
                                    switch (property.op) {
                                        case Add:
                                            endValue += startValue;
                                            break;
                                        case Scale:
                                            endValue *= startValue;
                                            break;
                                    }
                                }

//                                property.viewValues.put(view, new Integer[] {startValue, endValue});
                                // Array access is much faster than IndentityHash lookup in a map
                                property.targetValues[Index] = new Integer[] {startValue, endValue};
                            }

                            float fraction = animation.getAnimatedFraction();

                            Integer[] values = (Integer[]) property.targetValues[Index];
                            int startValue = values[0];
                            int endValue = values[1];

                            // Layout operations are very expensive, and even equal assignments will trigger a full traversal; skip as many as possible.
                            if (startValue == endValue) continue;

                            if (property.interpolator == null) {
                                property.property.set(view, Utils.interpolateColors(globalInterpolator.getInterpolation(fraction), startValue, endValue));
                            } else {
                                property.property.set(view, Utils.interpolateColors(property.interpolator.getInterpolation(fraction), startValue, endValue));
                            }

                        }


                        firstStep = false;

                    }
                });
            }

            if (update != null) {
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        update.update(view, animation.getAnimatedFraction());
                    }
                });
            }

            final int CurrentLayer = view.getLayerType();

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (layer) view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    if (visibility != null && visibility == View.VISIBLE) view.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (layer) view.setLayerType(CurrentLayer, null);
                    if (visibility != null && visibility != View.VISIBLE) view.setVisibility(visibility);
                    if (remove) ((ViewGroup) view.getParent()).removeView(view);
                }
            });

            return animator;
        }

    }

    private final static ArrayList<AnimationQueue> AllQueues = new ArrayList<AnimationQueue>();

    /**
     * The metadata entry under which each view's queues are stored.
     */
    private final static String QueueKey = "$Queues";
    /**
     * The identifier for the default parallel queue.
     */
    public final static String ParallelQueue = "$QueueParallel";
    /**
     * The identifier for the default serial queue.
     */
    public final static String SerialQueue = "$QueueSerial";

    /**
     * Gets the animation queue identified by the QueueIdentifier for the target view. If it doesn't exist, an appropriate queue is created and returned.
     * @param TargetView The view to which the queue belongs.
     * @param QueueIdentifier A string key that uniquely identifies a queue.
     * @return The identified animation queue for the specified view.
     */
    private static AnimationQueue getQueue(final View TargetView, final String QueueIdentifier) {
        return getQueue(TargetView, QueueIdentifier, true);
    }

    /**
     * Returns the queue identified by the supplied identifier for the given view.
     * @param TargetView The view for which to get the animation queue.
     * @param QueueIdentifier The queue's unique identifier.
     * @param CreateIfNecessary If true, the queue will be created if it doesn't exist.
     * @return The requested queue, or <strong>null</strong> if <strong>CreateIfNecessary</strong> is false and this view doesn't have an active queue with the given identifier.
     */
    private static AnimationQueue getQueue(final View TargetView, final String QueueIdentifier, final boolean CreateIfNecessary) {
        if (!$.hasMetadata(TargetView, QueueKey)) {
            if (!CreateIfNecessary) return null;

            Map<String, AnimationQueue> queues = new HashMap<String, AnimationQueue>();

            $.metadata(TargetView, QueueKey, queues);
        }

        final Map<String, AnimationQueue> Queues = (Map<String, AnimationQueue>) $.metadata(TargetView, QueueKey);

        if (Queues.containsKey(QueueIdentifier)) {
            return Queues.get(QueueIdentifier);
        }
        else if (!CreateIfNecessary) {
            return null;
        }

        final AnimationQueue NewQueue = ParallelQueue.equals(QueueIdentifier) ? new ParallelAnimationQueue() : new SerialAnimationQueue();
        NewQueue.identifier = QueueIdentifier;

        NewQueue.depleted = new Runnable() {
            @Override
            public void run() {
                Queues.remove(QueueIdentifier);
                AllQueues.remove(NewQueue);
            }
        };

        AllQueues.add(NewQueue);
        Queues.put(QueueIdentifier, NewQueue);

        return NewQueue;
    }

    /**
     * Retrieves all queues associated with the specified view.
     * @param TargetView The view for which to get queues.
     * @return An array of all this view's queues. If the view has no queues, the array may be null.
     */
    private static AnimationQueue[] getAllQueues(final View TargetView) {
        if (!$.hasMetadata(TargetView, QueueKey)) {
            return null;
        }

        final Map<String, AnimationQueue> Queues = (Map<String, AnimationQueue>) $.metadata(TargetView, QueueKey);

        return Queues.values().toArray(new AnimationQueue[Queues.size()]);
    }

    /**
     * Stops the current animation and prevents all pending animations from running on the target queue
     * @param TargetView The view on which to stop animations.
     * @param QueueIdentifier The queue which will be stopped.
     */
    private static void stopQueue(final View TargetView, final String QueueIdentifier, boolean clearQueue) {
        if (QueueIdentifier == null) {

            if (!$.hasMetadata(TargetView, QueueKey)) return;

            // Clear all queues
            // noinspection unchecked
            Map<String, AnimationQueue> queues = (Map<String, AnimationQueue>) $.metadata(TargetView, QueueKey);

            if (queues != null) {
                AnimationQueue animationQueues[] = queues.values().toArray(new AnimationQueue[queues.size()]);

                for (AnimationQueue queue : animationQueues) {
                    if (clearQueue) queue.clear();
                    else queue.continueQueue(false);
                }
            }
        }
        else {
            AnimationQueue queue = $.getQueue(TargetView, QueueIdentifier, false);

            if (queue != null) {
                if (clearQueue) queue.clear();
                else queue.continueQueue(false);
            }
        }
    }

    /**
     * Stops the currently running animation within the supplied queue for all views, applying the end values instantly.
     * @param queue The queue which will be stopped, or <strong>null</strong> to stop all animations started through the ViewProxy animators.
     */
    public static void finishQueue(String queue) {
        int i = 0;
        int queueSize = AllQueues.size();
        for (i = 0; i < queueSize; i++) {
            AnimationQueue q = AllQueues.get(i);

            if (queue == null || queue.equals(q.identifier)) {
                q.continueQueue(true);
            }

            if (queueSize > AllQueues.size()) {
                i = i - (queueSize - AllQueues.size());
                queueSize = AllQueues.size();
            }
        }
    }


    /**
     * Stops the currently running animation within the supplied queue for all views, applying the end values instantly.
     * @param queue The queue which will be stopped, or <strong>null</strong> to stop all animations started through the ViewProxy animators.
     * @param clear If true, all other pending animations will also be instantly finished and the queue will be depleted.
     */
    public static void finishQueue(String queue, boolean clear) {
        int i = 0;
        int queueSize = AllQueues.size();
        for (i = 0; i < queueSize; i++) {
            AnimationQueue q = AllQueues.get(i);

            if (queue == null || queue.equals(q.identifier)) {
                if (clear) {
                    q.finish();
                }
                else {
                    q.continueQueue(true);
                }
            }

            if (queueSize > AllQueues.size()) {
                i = i - (queueSize - AllQueues.size());
                queueSize = AllQueues.size();
            }
        }
    }


    /**
     * Stops the current animation and prevents all pending animations from running on the target queue, but applies the end values for each animation in the queue.
     * @param TargetView The view on which to stop animations.
     * @param QueueIdentifier The queue which will be stopped.
     * @param clearQueue If true, pending animations are finished as well, otherwise this method will only cancel the currently playing animation.
     */
    private static void finishQueue(final View TargetView, final String QueueIdentifier, boolean clearQueue) {
        if (QueueIdentifier == null) {
            if (!$.hasMetadata(TargetView, QueueKey)) return;
            // Clear all queues
            // noinspection unchecked
            Map<String, AnimationQueue> queues = (Map<String, AnimationQueue>) $.metadata(TargetView, QueueKey);

            if (queues != null) {
                AnimationQueue animationQueues[] = queues.values().toArray(new AnimationQueue[queues.size()]);

                for (AnimationQueue queue : animationQueues) {
                    if (clearQueue) queue.finish();
                    else queue.continueQueue(true);
                }
            }
        }
        else {
            AnimationQueue queue = $.getQueue(TargetView, QueueIdentifier, false);

            if (queue != null) {
                if (clearQueue) queue.finish();
                else queue.continueQueue(true);
            }
        }
    }



    /**
     * Stops the current animation and prevents all pending animations from running on the target queue
     * @param TargetView The view on which to stop animations.
     * @param QueueIdentifier The queue which will be stopped.
     */
    private static void pauseQueue(final View TargetView, final String QueueIdentifier) {
        if (QueueIdentifier == null) {
            if (!$.hasMetadata(TargetView, QueueKey)) return;
            // Clear all queues
            // noinspection unchecked
            Map<String, AnimationQueue> queues = (Map<String, AnimationQueue>) $.metadata(TargetView, QueueKey);

            if (queues != null) {
                AnimationQueue animationQueues[] = queues.values().toArray(new AnimationQueue[queues.size()]);

                for (AnimationQueue queue : animationQueues) {
                    queue.pause();
                }
            }
        }
        else {
            AnimationQueue queue = $.getQueue(TargetView, QueueIdentifier, false);

            if (queue != null) {
                queue.pause();
            }
        }
    }


    /**
     * Resumes the current animation for the target queue.
     * @param TargetView The view on which to resume animations.
     * @param QueueIdentifier The queue which will be resumed. If it is null, all the view's queues will be resumed.
     */
    private static void resumeQueue(final View TargetView, final String QueueIdentifier) {
        if (QueueIdentifier == null) {
            if (!$.hasMetadata(TargetView, QueueKey)) return;
            // Clear all queues
            // noinspection unchecked
            Map<String, AnimationQueue> queues = (Map<String, AnimationQueue>) $.metadata(TargetView, QueueKey);

            if (queues != null) {
                AnimationQueue animationQueues[] = queues.values().toArray(new AnimationQueue[queues.size()]);

                for (AnimationQueue queue : animationQueues) {
                    queue.resume();
                }
            }
        }
        else {
            AnimationQueue queue = $.getQueue(TargetView, QueueIdentifier, false);

            if (queue != null) {
                queue.resume();
            }
        }
    }

    private static AnimationQueue globalQueue;

    /**
     * Creates a global queue. This action will instantly finish all other non-ambient queues.
     * @return A new global queue.
     */
    public static AnimationQueue createGlobalQueue() {
        if (globalQueue != null) {
            globalQueue.finish();
            AllQueues.remove(globalQueue);

            globalQueue = null;
        }

        while (AllQueues.size() > 0) {
            AllQueues.get(0).finish();
        }

        final AnimationQueue GlobalQueue = new ParallelAnimationQueue();
        AllQueues.add(GlobalQueue);

        globalQueue = GlobalQueue;

        GlobalQueue.depleted = new Runnable() {
            @Override
            public void run() {
                AllQueues.remove(GlobalQueue);
            }
        };

        return GlobalQueue;
    }


    //endregion


}
