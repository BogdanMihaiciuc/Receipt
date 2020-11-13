package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public class Glyph {
    final static String TAG = Glyph.class.getName();

    public interface GlyphListener {
        public void invalidate();
    }

    public final static int GlyphDone = 1;
    public final static int GlyphClose = 2;
    public final static int GlyphUndo = 3;
    public final static int GlyphPlus = 4;

    final static float[][] PointMapDone = {{4, 13, 9, 17},
                                       {9, 17, 20, 7}};
    final static float[][] PointMapClose = {{6, 6, 18, 18},
                                        {6, 18, 18, 6}};
    final static float[][] PointMapUndo = {{5, 14, 13, 9},
                                           {13, 9, 21, 15}};
    final static float[][] BezierMapDone = {
            {8, 17}, {9, 17}, {9, 17}, {10, 17}
    };
    final static float[][] BezierMapUndo = {
            {5, 14}, {6, 9}, {20, 9}, {21, 15}
    };
    final static float[][] ShapeMapUndo = {{2, 16, 11, 16, 2, 7}};
    final static float[][][] PointMaps = {null, PointMapDone, PointMapClose, PointMapUndo, PointMapClose};
    final static float[][][] ShapeMaps = {null, null, null, ShapeMapUndo, null};

    final static int DefaultSizeDP = 24;
    final static int DefaultThicknessDP = 2;
    final static int DefaultColor = Utils.transparentColor((int) (255 * 0.8), 0);

    final static Paint RenderPaint;
    static {
        RenderPaint = new Paint();
        RenderPaint.setAntiAlias(true);
        RenderPaint.setStyle(Paint.Style.STROKE);
        RenderPaint.setStrokeCap(Paint.Cap.SQUARE);
    }

    private Context context;
    private GlyphListener listener;
    private Utils.DPTranslator pixels;
    private int thickness;
    private int size;
    private float pointMap[][];
    private float shapeMap[][];
    private float bezierMap[][];
    private Path shapePath = new Path();
    private Path linePath = new Path();
    private int glyph = GlyphDone;
    private int color = DefaultColor;
    private float rotation = 0;
    private float multiplier = 1;

    private int centerX, centerY;
    private int sourceX, sourceY;

    private ValueAnimator shapeChanger;

    public Glyph(Context context, GlyphListener listener) {
        this.context = context;
        this.listener = listener;
        pixels = new Utils.DPTranslator(getResources().getDisplayMetrics().density);

        size = pixels.get(DefaultSizeDP);
        thickness = pixels.get(DefaultThicknessDP);

        pointMap = PointMapDone;
    }

    public Resources getResources() {
        return context.getResources();
    }

    public void setCenter(int x, int y) {
        centerX = x;
        centerY = y;

        sourceX = centerX - size / 2;
        sourceY = centerY - size / 2;
    }

    public void setSize(int size) {
        this.size = size;
        multiplier = size / (float) pixels.get(DefaultSizeDP);

        setCenter(centerX, centerY);
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public int getGlyph() {
        return glyph;
    }

    public void setGlyph(final int glyph) {
        setGlyph(glyph, true);
    }

    public void setGlyph(final int glyph, boolean animated) {
        if (this.glyph == glyph) return;

        if (!animated) {
            if (shapeChanger != null) {
                shapeChanger.end();
            }

            pointMap = PointMaps[glyph];
            if (glyph == GlyphUndo) {
                bezierMap = new float[4][2];
                for (int i = 0; i < 4; i++) {
                    System.arraycopy(BezierMapUndo[i], 0, bezierMap[i], 0, 2);
                }
            }
            else {
                bezierMap = null;
            }
            shapeMap = ShapeMaps[glyph];

            if (glyph == GlyphClose) {
                rotation = 90;
            }
            else if (glyph == GlyphPlus) {
                rotation = 45;
            }
            else {
                rotation = 0;
            }

            return;
        }

        final float SourceShapeMap[][];
        final float TargetShapeMap[][];
        final float SourceBezierMap[][];
        final float TargetBezierMap[][];
        if (glyph == GlyphUndo) {
            SourceShapeMap = new float[][] {{4, 13, 4, 13, 4, 13}};
            shapeMap = new float[][] {{4, 13, 4, 13, 4, 13}};
            TargetShapeMap = ShapeMapUndo;
            SourceBezierMap = BezierMapDone;
            TargetBezierMap = BezierMapUndo;
            if (bezierMap == null) {
                bezierMap = new float[4][2];
                for (int i = 0; i < 4; i++) {
                    System.arraycopy(BezierMapDone[i], 0, bezierMap[i], 0, 2);
                }
            }
        }
        else if (this.glyph == GlyphUndo) {
            SourceShapeMap = ShapeMapUndo;
            TargetShapeMap = new float[][] {{4, 13, 4, 13, 4, 13}};
            SourceBezierMap = BezierMapUndo;
            TargetBezierMap = BezierMapDone;
            if (bezierMap == null) {
                bezierMap = new float[4][2];
                for (int i = 0; i < 4; i++) {
                    System.arraycopy(BezierMapUndo[i], 0, bezierMap[i], 0, 2);
                }
            }
        }
        else {
            SourceShapeMap = null;
            TargetShapeMap = null;
            SourceBezierMap = null;
            TargetBezierMap = null;
            bezierMap = null;
        }

        this.glyph = glyph;

        final float SourcePoints[][] = pointMap;
        final float TargetPoints[][] = PointMaps[glyph];
        pointMap = new float[2][4];

        float targetRotation = glyph == GlyphClose ? 90 : 0;
        if (glyph == GlyphPlus) {
            targetRotation = 45;
        }

        if (shapeChanger != null) {
            shapeChanger.end();
        }

        shapeChanger = ValueAnimator.ofFloat(rotation, targetRotation);
        shapeChanger.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                for (int line = 0; line < pointMap.length; line++) {
                    for (int coordinate = 0; coordinate < pointMap[line].length; coordinate++) {
                        pointMap[line][coordinate] = Utils.interpolateValues(fraction, SourcePoints[line][coordinate], TargetPoints[line][coordinate]);
                    }
                }

                if (SourceShapeMap != null) {
                    for (int shape = 0; shape < SourceShapeMap.length; shape++) {
                        for (int point = 0; point < SourceShapeMap[shape].length; point++) {
                            shapeMap[shape][point] = Utils.interpolateValues(fraction, SourceShapeMap[shape][point], TargetShapeMap[shape][point]);
                        }
                    }
                }

                if (SourceBezierMap != null && bezierMap != null) {
                    for (int i = 0; i < bezierMap.length; i++) {
                        for (int j = 0; j < bezierMap[i].length; j++) {
                            bezierMap[i][j] = Utils.interpolateValues(fraction, SourceBezierMap[i][j], TargetBezierMap[i][j]);
                        }
                    }

                    if (glyph != GlyphUndo && fraction == 1) {
                        bezierMap = null;
                    }
                }

                rotation = (Float) valueAnimator.getAnimatedValue();
                listener.invalidate();
            }
        });
        shapeChanger.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                shapeChanger = null;
            }
        });
        shapeChanger.setDuration(300);
        shapeChanger.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        shapeChanger.start();
    }

    public float targetX(float source) {
        return sourceX + multiplier * pixels.get(source);
    }

    public float targetY(float source) {
        return sourceY + multiplier * pixels.get(source);
    }

    public void render(Canvas canvas) {
        canvas.save();
        canvas.rotate(rotation, centerX, centerY);

        RenderPaint.setStrokeWidth(thickness);
        RenderPaint.setColor(color);

        if (bezierMap != null) {
            linePath.rewind();
            boolean lineInit = false;

            int cubicIndex = 0;
            for (float[] line : pointMap) {
                linePath.moveTo(targetX(line[0]), targetY(line[1]));
                linePath.cubicTo(targetX(bezierMap[cubicIndex][0]), targetY(bezierMap[cubicIndex][1]),
                                 targetX(bezierMap[cubicIndex + 1][0]), targetY(bezierMap[cubicIndex + 1][1]),
                                 targetX(line[2]), targetY(line[3]));
                cubicIndex += 2;
            }
            canvas.drawPath(linePath, RenderPaint);
        }
        else {
            for (float[] line : pointMap) {
                canvas.drawLine(targetX(line[0]), targetY(line[1]),
                        targetX(line[2]), targetY(line[3]), RenderPaint);
            }
        }

        if (shapeMap != null) {
            RenderPaint.setStyle(Paint.Style.FILL);
            for (int shape = 0; shape < shapeMap.length; shape++) {
                shapePath.rewind();
                for (int point = 0; point < shapeMap[shape].length / 2; point++) {
                    if (point == 0) {
                        shapePath.moveTo(targetX(shapeMap[shape][point]), targetY(shapeMap[shape][point + 1]));
                    }
                    else {
                        shapePath.lineTo(targetX(shapeMap[shape][point * 2]), targetY(shapeMap[shape][point * 2 + 1]));
                    }
                }

                canvas.drawPath(shapePath, RenderPaint);
            }
            RenderPaint.setStyle(Paint.Style.STROKE);
        }

        canvas.restore();
    }

}
