package com.BogdanMihaiciuc.util;

import android.graphics.Point;
import android.view.ViewGroup;

public class HelpPage {

    public final static int ActionTap = 0;
    public static final int ActionSwipeDown = 1;
    public final static int ActionSwipeLeft = 2;
    public final static int ActionSwipeRight = 3;
    public final static int ActionSwipeUp = 4;

    public final static int GravityCenteredOnAchor = 0;
    public final static int GravityFromAnchor = 1;
    public final static int GravityToAnchor = 2;

    final static int AnchorTypePoint = 0;
    final static int AnchorTypeView = 1;
    final static int AnchorTypeID = 2;
    final static int AnchorTypeListener = 3;

    static interface PositionGetter {
        Point getPosition();
    }

    static abstract class HelpElement {
        abstract void create(ViewGroup container);
    }

    static class ActionHelpElement extends HelpElement {
        int anchorType;
        int actionType;
        Object anchor;

        public void create(ViewGroup container) {

        }
    }

    static class GestureHelpElement extends ActionHelpElement {
        int distance;
        int gravity;

        public void create(ViewGroup container) {

        }
    }

    static class ImageHelpElement extends ActionHelpElement {

    }

    private String title;

}
