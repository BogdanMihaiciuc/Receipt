package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;


public abstract class CollectionEventDelegate implements EventTouchListener.EventDelegate {

    private CollectionViewController controller;

    public CollectionEventDelegate(CollectionViewController controller) {
        this.controller = controller;
    }

    @Override
    public boolean viewShouldPerformClick(EventTouchListener listener, View view) {
        return true;
    }

    @Override
    public boolean viewShouldPerformLongClick(EventTouchListener listener, View view) {
        return true;
    }

    @Override
    public boolean viewShouldStartMoving(EventTouchListener listener, View view) {
        if (viewCanStartMoving(listener, view, controller.getObjectForView(view))) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            view.setPressed(false);
            controller.getCollectionView().flushRipplesOnView(view);

            return true;
        }

        return false;
    }

    public abstract boolean viewCanStartMoving(EventTouchListener listener, View view, Object object);

    @Override
    public void viewDidMove(EventTouchListener listener, View view, float distance) {
        Utils.ViewUtils.displaceView(view, distance, 0);
        float alpha = Utils.constrain(Math.abs(view.getTranslationX()) / view.getWidth(), 0, 1);
        alpha = Utils.interpolateValues(alpha, 1f, 0.2f);
        view.setAlpha(alpha);
    }

    @Override
    public void viewDidBeginSwiping(final EventTouchListener listener, View view, float velocity) {
        Object target = controller.getObjectForView(view);

        if (target == null) {
            viewDidCancelSwiping(listener, view);
            return;
        }

        final int Index = controller.getSectionAtIndex(0).indexOfObject(target);

        // The view will continue to move with constant speed
        if (velocity == 0) {
            velocity = EventTouchListener.sgn(view.getTranslationX());
        }

        float totalDistance = controller.getCollectionView().getWidth() - Math.abs(view.getTranslationX());
        long timeRequired = (long) (totalDistance / Math.abs(velocity));
        if (timeRequired > 300) {
            timeRequired = 300;
        }
        if (timeRequired < 100) {
            timeRequired = 100;
        }

        controller.requestBeginTransaction();
        // TODO
        controller.getSectionAtIndex(0).removeObject(target);

        final float StartingAlpha = view.getAlpha();
        final float StartingTranslation = view.getTranslationX();
        final float Velocity = velocity;
        final float TotalDistance = totalDistance;
        final View InnerView = view;

        final CollectionView Collection = controller.getCollectionView();

        Collection.setAnimationsEnabled(true);
        Collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
        Collection.setAnchorCondition(null);
        Collection.setDeleteAnimationStride(0);
        Collection.setDeleteAnimationDuration(timeRequired);
        Collection.setDeleteInterpolator(new LinearInterpolator());
//                            Collection.setMoveWithLayersEnabled(true);
        Collection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                InnerView.setAlpha(1f);
                InnerView.setTranslationX(0f);

                InnerView.setLayerType(View.LAYER_TYPE_NONE, null);
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                view.setAlpha(StartingAlpha);
                view.setTranslationX(view.getTranslationX() + StartingTranslation);
                view.setScaleY(0.99f);
                view.animate().alpha(0f).translationXBy(EventTouchListener.sgn(Velocity) * TotalDistance);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {
                view.setAlpha(1f);
                view.setLayerType(View.LAYER_TYPE_NONE, null);
                Collection.setDeleteInterpolator(CollectionView.StandardDeleteInterpolator);
            }
        });

        controller.requestCompleteTransaction();

        final Object Target = target;

        LegacySnackbar.showSnackbarWithMessage(getDeletedLabelForObject(listener, view, Target, controller.getCollectionView().getViewTypeOfView(view)), new LegacySnackbar.SnackbarListener() {
            @Override
            public void onActionConfirmed(LegacySnackbar snackbar) {
//                storage.removeRepeatDate(Target);
                objectDidConfirmDeletion(listener, Target);
            }

            @Override
            public void onActionUndone(LegacySnackbar snackbar) {
//                actionBar.findItemWithId(R.id.MenuAddList).setEnabled(storage.canAddRepeatDates());
                objectDidUndoDeletion(listener, Target);

                controller.requestBeginTransaction();

                controller.getSectionAtIndex(0).addObjectToIndex(Target, Index);
                CollectionView collectionView = controller.getCollectionView();

                if (collectionView != null) {
                    collectionView.setInsertAnimator(new CollectionView.ReversibleAnimation() {
                        @Override
                        public void playAnimation(View view, Object object, int viewType) {
                            view.setTranslationX(EventTouchListener.sgn(Velocity) * view.getWidth());
                            view.setAlpha(0f);

                            view.animate().alpha(1f);
                        }

                        @Override
                        public void resetState(View view, Object object, int viewType) {
                        }
                    });

                    collectionView.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
                    collectionView.setAnchorCondition(new CollectionView.AnchorInspector() {
                        @Override
                        public boolean isAnchor(Object object, int viewType) {
                            return object == Target;
                        }
                    });

//                    collectionView.setMoveWithLayersEnabled(true);
                    collectionView.setAnimationsEnabled(true);
                }

                controller.requestCompleteTransaction();
            }
        }, (Activity) view.getContext());

//        actionBar.findItemWithId(R.id.MenuAddList).setEnabled(true);
        viewDidStartSwiping(listener, view, Target, controller.getCollectionView().getViewTypeOfView(view));
    }

    public abstract void viewDidStartSwiping(EventTouchListener listener, View view, Object object, int viewType);
    public abstract void objectDidConfirmDeletion(EventTouchListener listener, Object object);
    public abstract CharSequence getDeletedLabelForObject(EventTouchListener listener, View view, Object object, int viewType);
    public abstract void objectDidUndoDeletion(EventTouchListener listener, Object object);

    @Override
    public void viewDidCancelSwiping(EventTouchListener listener, final View view) {
        view.animate()
                .alpha(1f).translationX(0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                view.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
    }

    @Override
    public int getSwipeDistanceThreshold() {
        return 2 * controller.getCollectionView().getWidth() / 3;
    }
}
