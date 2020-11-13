package com.BogdanMihaiciuc.receipt;

import android.R.interpolator;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;

public class HelpStory {

	public interface OnSelectPageListener {
		
		void onSelectPage(int page);
		
	}
	
	public interface OnCloseListener {
		
		void onClose(int page);
		
	}
	
	private OnClickListener nextClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			int index = ((View) (view.getParent().getParent())).getId();
			goToPage(index + 1);
		}
	};
	
	private boolean doneButtonsEnabled = true;
	
	private OnClickListener doneClickListener = new OnClickListener (){
		@Override
		public void onClick(View view) {
			if (!doneButtonsEnabled)
				return;
			view.setEnabled(false);
			exitStory();
		}
	};
	
	private PagerAdapter pageAdapter = new PagerAdapter() {
		@Override
		public int getCount() {
			return pages.size();
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}
		
		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			View newItem = pages.get(position).build();
			newItem.setId(position);
			container.addView(newItem);
			return newItem;
		}
		
		@Override
		public void destroyItem (ViewGroup container, int position, Object object) {
			container.removeView((View)object);
		}
	};
	
	private ViewPager pageHolder;
	private Activity context;
	private ArrayList<HelpOverlayBuilder> pages;
	private OnSelectPageListener onPreparePageListener = null;
	private OnCloseListener onCloseListener = null;
	private boolean started = false;
	private int currentPage = 0;
	private int cacheSize = 3;
	
	HelpStory(Activity context) {
		this.context = context;
		pages = new ArrayList<HelpOverlayBuilder>();
	}
	
	public HelpStory addPage(HelpOverlayBuilder page) {
		int totalPages = pages.size();
		if (totalPages > 0) {
			//Adding a new page, so update the previous last page to have a functional next button
			pages.get(totalPages - 1).setCanAdvanceInStory(true);
			pages.get(totalPages - 1).setNextOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					int index = ((View)(((View)view.getParent()).getParent())).getId();
					goToPage(index+1);
				}
			});
		}
		page.setIsPartOfStory(true);
		page.setDoneOnClickListener(doneClickListener);
		page.setStoryPageIndex(this.pages.size());
		page.setCanAdvanceInStory(false);
		pages.add(page);
		if (started) pageAdapter.notifyDataSetChanged();
		return this;
	}
	
	public HelpStory addPageToIndex(HelpOverlayBuilder page, int index) {
		int totalPages = pages.size();
		if (index > pages.size()) index = pages.size();
		if (pages.size() > index) {
			//Ading before an existing page, so make this one have a functional next button
			page.setCanAdvanceInStory(true);
			page.setNextOnClickListener(nextClickListener);
		}
		if (totalPages > 0 && index != 0) {
			//Adding a new page, so update the previous last page to have a functional next button
			pages.get(index - 1).setCanAdvanceInStory(true);
			pages.get(index - 1).setNextOnClickListener(nextClickListener);
		}
		page.setIsPartOfStory(true);
		page.setDoneOnClickListener(doneClickListener);
		page.setStoryPageIndex(this.pages.size());
		page.setCanAdvanceInStory(false);
		pages.add(index, page);
		if (started) pageAdapter.notifyDataSetChanged();
		return this;
	}
	
	public HelpStory addPages(HelpOverlayBuilder ... pages) {
		for (HelpOverlayBuilder page : pages) {
			addPage(page);
		}
		return this;
	}
	
	public HelpStory repalcePageAtIndexWithPage(int index, HelpOverlayBuilder page) {
		pages.remove(index);
		addPageToIndex(page, index);
		if (started) pageAdapter.notifyDataSetChanged();
		return this;
	}
	
	public HelpStory goToPage(int page) {
		if (started) pageHolder.setCurrentItem(page, true);
		currentPage = page;
		return this;
	}
	
	public HelpStory goToPage(int page, boolean animated) {
		if (started) pageHolder.setCurrentItem(page, animated);
		currentPage = page;
		return this;
	}
	
	public HelpStory setCacheSize(int cacheLength) {
		if (started) 
			pageHolder.setOffscreenPageLimit(cacheLength);
		cacheSize = cacheLength;
		return this;
	}
	
	public HelpStory setOnSelectPageListener(OnSelectPageListener listener) {
		onPreparePageListener = listener;
		return this;
	}
	
	public HelpStory setOnCloseListener(OnCloseListener listener) {
		onCloseListener = listener;
		return this;
	}

	public void exitStory() {
		started = false;
		doneButtonsEnabled = false;
		if (this.onCloseListener != null)
			onCloseListener.onClose(currentPage);
		final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
		final View content = root.getChildAt(0);
    	pageHolder.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		//Disable scrolling for the duration of the animation
		//If the user tries to scroll after the last view during the animation
		//and the blue overscroll appears, this makes the animation choppy
        pageHolder.setOverScrollMode(View.OVER_SCROLL_NEVER);
		pageHolder.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View arg0, MotionEvent arg1) {
                    return true;
            }
        }); 
		pageHolder.buildLayer();
        pageHolder.animate()
        	.scaleX(2).scaleY(2).alpha(0)
        	.setStartDelay(0)
        	.setDuration(HelpOverlayBuilder.AnimationLength)
        	.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.decelerate_quad))
        	.setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
        			//in case rotation or something happens during the animation
        	        cleanup();
        	        content.setLayerType(View.LAYER_TYPE_NONE, null);
				}
			});
	}
	
	public HelpOverlayBuilder getPageAt(int index) {
		return pages.get(index);
	}

	public void cleanup() {
		final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
		context = null;
        root.removeView(pageHolder);
        int size = pages.size();
        for (int i = 0; i < size; i++) {
        	pages.get(i).cleanup();
        }
        pageHolder.setAdapter(null);
        pageHolder = null;
	}

	public void startStory() {
		startStoryWithPageDelayed(0, 0);
	}
	
	public void startStoryWithPage(int page) {
		startStoryWithPageDelayed(page, 0);
	}
	
	public void startStoryWithPageDelayed(final int page, long delay) {
		doneButtonsEnabled = true;
		started = true;
		pageHolder = new ViewPager(context);
		pageHolder.setOnPageChangeListener(new OnPageChangeListener() {
			@Override
			public void onPageScrollStateChanged(int arg0) {
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageSelected(int page) {
				currentPage = page;
				if (onPreparePageListener != null)
					onPreparePageListener.onSelectPage(page);
			}
			
		});
		pageHolder.setOffscreenPageLimit(cacheSize);
		
		//To try and limit the delay
		final ArrayList<HelpOverlayBuilder> allPages;
		allPages = pages;
		pages = new ArrayList<HelpOverlayBuilder>();
		pages.add(allPages.get(page));
		pageHolder.setAdapter(pageAdapter);

		final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
        root.addView(pageHolder);
		//Disable scrolling for the duration of the animation
		//If the user tries to scroll after the last view during the animation
		//and the blue overscroll appears, this makes the animation choppy
        pageHolder.setOverScrollMode(View.OVER_SCROLL_NEVER);
		/*pageHolder.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View arg0, MotionEvent arg1) {
                    return true;
            }
        }); */
    	pageHolder.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	pageHolder.setScaleX(2);
    	pageHolder.setScaleY(2);
    	pageHolder.setAlpha(0);
        pageHolder.animate()
        	.scaleX(1).scaleY(1).alpha(1)
        	.setStartDelay(delay)
        	.setDuration(HelpOverlayBuilder.AnimationLength)
        	.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.decelerate_cubic))
        	.setListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {}
				
				@Override
				public void onAnimationRepeat(Animator animation) {}
				
				@Override
				public void onAnimationEnd(Animator animation) {
        			//in case rotation or something happens during the animation
        			if (pageHolder == null) return;
        			pageHolder.setLayerType(View.LAYER_TYPE_NONE, null);
        			pageHolder.setOffscreenPageLimit(cacheSize);
        			
        			//Reenable paging
        			pages = allPages;
        			pageHolder.setAdapter(pageAdapter);
        			pageHolder.setCurrentItem(page, false);
        	        pageHolder.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        			//re-enable the animation
        			pageHolder.setOnTouchListener(null);}
				
				@Override
				public void onAnimationCancel(Animator animation) {
        			//in case rotation or something happens during the animation
        			if (pageHolder == null) return;
        			pageHolder.setLayerType(View.LAYER_TYPE_NONE, null);
        			//re-enable the animation
        			pageHolder.setOnTouchListener(null);}
			});
	}
	
	public void startStoryWithPageInstantly(int page) {
		started = true;
		doneButtonsEnabled = true;
		pageHolder = new ViewPager(context);
		pageHolder.setOnPageChangeListener(new OnPageChangeListener() {
			@Override
			public void onPageScrollStateChanged(int arg0) {
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageSelected(int page) {
				currentPage = page;
				if (onPreparePageListener != null)
					onPreparePageListener.onSelectPage(page);
			}
			
		});
		pageHolder.setAdapter(pageAdapter);
		pageHolder.setOffscreenPageLimit(cacheSize);
		final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
		if (HelpOverlayBuilder.DEBUG) Log.d("ReceiptActivity", "Added story to DecorView.");
        root.addView(pageHolder);
		pageHolder.setCurrentItem(page, false);
	}
	
}
