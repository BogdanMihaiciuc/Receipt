package com.BogdanMihaiciuc.receipt;

import android.R.interpolator;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public final class HelpOverlayBuilder {
	
	private Activity context;
	private int sizeX = 0, sizeY = 0;
	private int x=0, y=0; // positions
	private int maxExplanationLines = 4;
	private float scale;
	private String title=null, explanation=null;
	private boolean nextButton=false;
	private View highlightedView;
	private Rect rct = new Rect();
	private Drawable helperGraphic = null;
	private int titleRequiredLineWidth = 0;
	private int explanationMaxLineWidth = 0;
	private boolean canShareButtonSpace;
	private int image;
	
	private boolean isStoryPage = false;
	private int storyPageIndex = 0;
	
	private View root;
	
	private HelpOverlayBuilder nextPage = null;
	private OnClickListener nextAction = null;
	private OnClickListener doneAction = null;
	
	private OnClickListener transitionToNext = new OnClickListener() {
		@Override
		public void onClick(View view) {
			final View nextView = nextPage.build();
			final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
			int slideLength = root.getWidth();
			final View currentView = (View)view.getParent().getParent();
	        root.addView(nextView);
			currentView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			currentView.animate()
				.alpha(0).x(-slideLength/2)
	        	.setDuration(AnimationLength)
	        	.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.accelerate_decelerate))
	        	.setListener(new AnimatorListener() {
					
					@Override
					public void onAnimationStart(Animator animation) {}
					
					@Override
					public void onAnimationRepeat(Animator animation) {}
					
					@Override
					public void onAnimationEnd(Animator animation) {
	        			root.removeView(currentView);
					}
					
					@Override
					public void onAnimationCancel(Animator animation) {
	        			root.removeView(currentView);
					}
				});
	    	nextView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
	    	nextView.setX(slideLength/2);
	    	nextView.setAlpha(0);
	        nextView.animate()
	        	.x(0).alpha(1)
	        	.setDuration(AnimationLength)
	        	.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.accelerate_decelerate))
	        	.setListener(new AnimatorListener() {
					
					@Override
					public void onAnimationStart(Animator animation) {}
					
					@Override
					public void onAnimationRepeat(Animator animation) {}
					
					@Override
					public void onAnimationEnd(Animator animation) {
	        			nextView.setLayerType(View.LAYER_TYPE_NONE, null);
					}
					
					@Override
					public void onAnimationCancel(Animator animation) {
	        			nextView.setLayerType(View.LAYER_TYPE_NONE, null);
					}
				});
		}
	};
	
	private OnClickListener done = new OnClickListener() {
		@Override
		public void onClick(View view) {
			final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
			final View currentView = (View)view.getParent().getParent();
			currentView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			currentView.animate()
				.scaleX(2).scaleY(2).alpha(0)
	        	.setDuration(AnimationLength)
	        	.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.accelerate_cubic))
	        	.setListener(new AnimatorListener() {
					
					@Override
					public void onAnimationStart(Animator animation) {}
					
					@Override
					public void onAnimationRepeat(Animator animation) {}
					
					@Override
					public void onAnimationEnd(Animator animation) {
	        			root.removeView(currentView);
					}
					
					@Override
					public void onAnimationCancel(Animator animation) {
	        			root.removeView(currentView);
					}
				});
		}
	};
	
	private static int textMargin;
	private static int titleTopMargin;
	private static int buttonBarHeight;
	private static int initialExtraTopMargin;
	private static int buttonMargin;
	private static int buttonPadding;
	private static int explanationTopMargin;
	
	//These are standard attributes
	private static Paint ErasePaint = new Paint();
	private static boolean PaintInitialized = false;
	private static int TitleColor = 0, ExplanationColor = 0, ShadowColor = 0;
	private static int MinimumLineWidth = 0;
	
	final static boolean DEBUG = false;
	
	// ********* CONSTANTS *********
	final static int ClingRadius = 94;
	final static int ClingDiameter = 192;
	//The outer diameter is the are onto which not other control may be overlaid
	final static int ClingOuterRadius = 141;
	final static int ClingOuterDiameter = 282;
	
	//This is the minimum line width onto which text may be shown
	//This includes any possible padding
	final static int PhoneMinimumLineWidth = 320;
	final static int TabletMinimumLineWidth = 600;
	
	//This is a full width strip containing the buttons
	final static int PhoneButtonBarHeight = 66;
	final static int TabletButtonBarHeight = 80;
	final static int ButtonBarId = 5;
	final static int NextButtonId = 4;
	
	//Text and TextView dimensions
	final static int PhoneTextMargin = 16;
	final static int PhoneTitleTopMargin = 32;
	final static int TabletTextMargin = 64;
	final static int TitleTextSize = 32;
	final static int ExplanationTextSize = 20;
	final static int PhoneExplanationTopMargin = 8;
	final static int TabletExplanationTopMargin = 32;
	
	//Button Dimensions
	final static int PhoneButtonPadding = 32;
	final static int PhoneButtonMargin = 16;
	final static int TabletButtonPadding = 48;
	final static int TabletButtonMargin = 24;
	
	//Colors
	final static int BackgroundColor = 0xDD000000;
	
	//Times
	final static int AnimationLength = 300;
	
	final static int ImageTap = 0;
	final static int ImageZoom = 1;
	final static int ImageFling = 2;
	final static int ImageFlingRight = 2;
	final static int ImageNone = -1;
	
	HelpOverlayBuilder(Activity context, int x, int y) {
		
		this.context = context;
		this.x = x;
		this.y = y;
		highlightedView = null;
		//context.getWindow().getDecorView().getWindowVisibleDisplayFrame(rct);
		sizeX = context.getWindow().getDecorView().getWidth();
		sizeY = context.getWindow().getDecorView().getHeight();
		rct.bottom = sizeY;
		rct.top = 0;
		rct.left = 0;
		rct.right = sizeX;
		scale = 1;
		image = ImageTap;
		
		staticInitialize();
	}
	
	HelpOverlayBuilder(Activity context, View highlightView) {
		this.context = context;
		int viewPosition[] = new int[2];
		int viewDimensions[] = new int[2];
		this.highlightedView = highlightView;
		highlightedView.getLocationOnScreen(viewPosition);
		viewDimensions[0] = highlightedView.getWidth();
		viewDimensions[1] = highlightedView.getHeight();
		this.x = viewPosition[0] + viewDimensions[0]/2;
		this.y = viewPosition[1] + viewDimensions[1]/2;
		
		DisplayMetrics metrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
		int highestDimension = viewDimensions[0] > viewDimensions[1] ? viewDimensions[0] : viewDimensions[1];
		scale = ((float)highestDimension)/(metrics.density * 96f);
		if (DEBUG) Log.d("HelpOverlayBuilder", "Brute scale is " + scale);
		scale = scale > 0.66f ? scale : 0.66f;
		scale = scale > 1.25f ? 1.25f : scale;
		if (DEBUG) Log.d("HelpOverlayBuilder", "Scale is " + scale);
		
		//context.getWindow().getDecorView().getWindowVisibleDisplayFrame(rct);
		sizeX = context.getWindow().getDecorView().getWidth();
		sizeY = context.getWindow().getDecorView().getHeight();
		rct.bottom = sizeY;
		rct.top = 0;
		rct.left = 0;
		rct.right = sizeX;
		image = ImageTap;
		
		staticInitialize();
	}
	
	HelpOverlayBuilder(Activity context, View highlightView, String title, String explanation) {
		this.context = context;
		int viewPosition[] = new int[2];
		int viewDimensions[] = new int[2];
		this.highlightedView = highlightView;
		if (highlightedView == null) throw new RuntimeException("Passed view is null!");
		highlightedView.getLocationInWindow(viewPosition);
		viewDimensions[0] = highlightedView.getWidth();
		viewDimensions[1] = highlightedView.getHeight();
		this.x = viewPosition[0] + viewDimensions[0]/2;
		this.y = viewPosition[1] + viewDimensions[1]/2;
		
		DisplayMetrics metrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
		int highestDimension = viewDimensions[0] > viewDimensions[1] ? viewDimensions[0] : viewDimensions[1];
		scale = ((float)highestDimension)/(metrics.density * 96f);
		if (DEBUG) Log.d("HelpOverlayBuilder", "Decoded view into x " + x + " and y " + y);
		if (DEBUG) Log.d("HelpOverlayBuilder", "Brute scale is " + scale);
		scale = scale > 0.66f ? scale : 0.66f;
		scale = scale > 1.25f ? 1.25f : scale;
		if (DEBUG) Log.d("HelpOverlayBuilder", "Scale is " + scale);
		
		//context.getWindow().getDecorView().getWindowVisibleDisplayFrame(rct);
		sizeX = context.getWindow().getDecorView().getWidth();
		sizeY = context.getWindow().getDecorView().getHeight();
		rct.bottom = sizeY;
		rct.top = 0;
		rct.left = 0;
		rct.right = sizeX;
		
		this.title = title;
		this.explanation = explanation;
		
		image = ImageTap;
		
		staticInitialize();
	}
	
	private void staticInitialize() {

		// Initialize device-specific dimensions
        int swdp = context.getResources().getConfiguration().smallestScreenWidthDp;
        if (swdp < 600) {
        	buttonBarHeight = PhoneButtonBarHeight;
        	titleTopMargin = PhoneTitleTopMargin;
        	textMargin = PhoneTextMargin;
        	MinimumLineWidth = PhoneMinimumLineWidth;
        	buttonMargin = PhoneButtonMargin;
        	buttonPadding = PhoneButtonPadding;
        	explanationTopMargin = PhoneExplanationTopMargin;
        }
        else {
        	buttonBarHeight = TabletButtonBarHeight;
        	titleTopMargin = TabletTextMargin;
        	textMargin = TabletTextMargin;
        	MinimumLineWidth = TabletMinimumLineWidth;
        	buttonMargin = TabletButtonMargin;
        	buttonPadding = TabletButtonPadding;
        	explanationTopMargin = TabletExplanationTopMargin;
        	if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        		initialExtraTopMargin = 128;
        }
        
        // Initialize common graphics and colors
		if (!PaintInitialized) {
	        ErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
	        ErasePaint.setColor(0xFFFFFF);
	        ErasePaint.setAlpha(0);
	        
	        PaintInitialized = true;
		}/*
		if (helperGraphic == null) {
			helperGraphic = context.getResources().getDrawable(R.drawable.cling);
		}*/
		if (TitleColor == 0) {
			TitleColor = context.getResources().getColor(android.R.color.holo_blue_light);
			ExplanationColor = context.getResources().getColor(android.R.color.white);
			ShadowColor = 0x88000000;
		}
		
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
	public HelpOverlayBuilder setHighlightPosition(int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}
	
	public HelpOverlayBuilder setTitle(String title) {
		this.title=title;
		return this;
	}
	
	public HelpOverlayBuilder setExplanation(String explanation) {
		this.explanation = explanation;
		return this;
	}
	
	public HelpOverlayBuilder setCanContinue(boolean canContinue) {
		this.nextButton = canContinue;
		return this;
	}
	
	public HelpOverlayBuilder setScale(float scale) {
		this.scale = scale;
		return this;
	}
	
	public HelpOverlayBuilder setMaxExplanationLines(int maxLines) {
		maxExplanationLines = maxLines;
		return this;
	}
	
	public HelpOverlayBuilder setNextOnClickListener(OnClickListener listener) {
		if (nextPage != null) return this;
		nextAction = listener; 
		if (listener != null && nextButton == false) nextButton = true;
		return this;
	}
	
	public HelpOverlayBuilder setTitleRequiredLineWidth(int lineWidth) {
		titleRequiredLineWidth = lineWidth;
		return this;
	}
	
	public HelpOverlayBuilder setExplanationMaxLineWidth(int lineWidth) {
		explanationMaxLineWidth = lineWidth;
		return this;
	}
	
	public HelpOverlayBuilder setImage(int image) {
		this.image = image;
		return this;
	}
	
	public HelpOverlayBuilder setCanShareButtonSpace(boolean canShare) {
		canShareButtonSpace = canShare;
		return this;
	}
	
	public HelpOverlayBuilder setNextPage(HelpOverlayBuilder nextPage) {
		if (nextPage == null) {
			this.nextPage = null;
			nextAction = null;
			return this;
		}
		nextButton = true;
		this.nextPage = nextPage;
		nextAction = this.transitionToNext;
		return this;
	}
	
	public HelpOverlayBuilder getNextPage() {
		return nextPage;
	}
	
	@SuppressWarnings("deprecation")
	public RelativeLayout build() {
		RelativeLayout layout = new RelativeLayout(context);
		root = layout;
        layout.setPivotX(x);
        layout.setPivotY(y);
		
		DisplayMetrics metrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Initialize the draw buffer (to allow punching through)
        Bitmap b = Bitmap.createBitmap(sizeX, sizeY,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        
        if (image == ImageTap) {
        	helperGraphic = context.getResources().getDrawable(R.drawable.cling);
        }
        else if (image == ImageZoom) {
			helperGraphic = context.getResources().getDrawable(R.drawable.cling_zoom);
        }
        else if (image == ImageFling) {
        	helperGraphic = context.getResources().getDrawable(R.drawable.cling_fling);
        }
        else {
			helperGraphic = context.getResources().getDrawable(R.drawable.cling);
        }
        
        // Draw the background
        c.drawColor(BackgroundColor);
        
        int dw = (int) (scale * helperGraphic.getIntrinsicWidth());
        int dh = (int) (scale * helperGraphic.getIntrinsicHeight());
        
        // Clear the background through the circle
        if (image == ImageTap || image == ImageFling)
        	c.drawCircle(x, y,  metrics.density * 94 * scale + 1, ErasePaint);
        else if (image == ImageZoom)
        	c.drawCircle(x, y,  metrics.density * 94 * 0.75f * scale + 1, ErasePaint);
        // Draw the circles graphics
        Rect clingPosition = new Rect(x - dw/2, y - dh/2, x + dw/2, y + dh/2);
        if (image != -1) {
	        helperGraphic.setBounds(clingPosition);
	        helperGraphic.draw(c);
        }
        
        //This is the rectangle other views must not intersect
        //It caches the expensive float operations and improves readability in later parts of the code
        Rect restrictedLayoutRectangle = new Rect((int)(x - metrics.density * ClingOuterRadius * scale),
        		(int)(y - metrics.density * ClingOuterRadius * scale),
        		(int)(x + metrics.density * ClingOuterRadius * scale),
        		(int)(y + metrics.density * ClingOuterRadius * scale));
        
    	BitmapDrawable background = new BitmapDrawable(context.getResources(), b);
    	layout.setBackgroundDrawable(background);

        c.setBitmap(null);
        b = null;
        
        int buttonStripMargin = 0;
        Rect buttonBarRectangle = new Rect(0, (int)(sizeY - buttonBarHeight * metrics.density), sizeX, sizeY);
        if (Rect.intersects(buttonBarRectangle, restrictedLayoutRectangle)) {
        	//The button strip is on top if the cling intersects its usual bottom location
        	buttonStripMargin = (int)(metrics.density * (buttonBarHeight + 30));
        	buttonBarRectangle.top = 0;
        	buttonBarRectangle.bottom = (int)(buttonBarHeight * metrics.density);
        }
        
        //Construct the button bar
        RelativeLayout buttonBar = new RelativeLayout(context);
        RelativeLayout.LayoutParams buttonBarParams = new RelativeLayout.LayoutParams(sizeX, (int)(buttonBarHeight * metrics.density));
        if (buttonStripMargin != 0) {
        	buttonBarParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        	buttonBarParams.topMargin = (int)(30 * metrics.density);
        }
        else
        	buttonBarParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        buttonBar.setLayoutParams(buttonBarParams);
        
        
        //Construct and add the cancel button
        Button cancelButton = new Button(context);
        cancelButton.setText(context.getResources().getString(R.string.DoneButtonLabel));
        RelativeLayout.LayoutParams cancelParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (!nextButton) {
	    	cancelParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
	    	cancelParams.rightMargin = (int)(buttonMargin * metrics.density);
		}
        else {
	    	cancelParams.addRule(RelativeLayout.LEFT_OF, NextButtonId);
	    	cancelParams.rightMargin = (int)(buttonMargin * metrics.density);
        }
        cancelButton.setLayoutParams(cancelParams);
        cancelButton.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.cling_button_bg));
        //setBackgroundDrawable clears the padding, so padding must be set after setting the backogrund
        cancelButton.setPadding((int)(buttonPadding * metrics.density), (int)(16 * metrics.density), (int)(buttonPadding * metrics.density), (int)(16 * metrics.density));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setTextColor(ExplanationColor);
        cancelButton.setTextSize(ExplanationTextSize);
        if (isStoryPage) 
        	cancelButton.setOnClickListener(doneAction);
        else
        	cancelButton.setOnClickListener(done);
        buttonBar.addView(cancelButton);
        
        //Construct and add the next button, if it exists
        if (nextButton) {
        	Button nextButtonView = new Button(context);
        	if (nextAction != null) nextButtonView.setOnClickListener(nextAction);
            nextButtonView.setText(context.getResources().getString(R.string.NextButtonLabel));
            RelativeLayout.LayoutParams doneParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        	doneParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        	doneParams.rightMargin = (int)(buttonMargin * metrics.density);
        	doneParams.leftMargin = (int)(buttonMargin * metrics.density);
            nextButtonView.setLayoutParams(doneParams);
            nextButtonView.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.cling_button_bg));
            //setBackgroundDrawable clears the padding, so padding must be set after setting the backogrund
            nextButtonView.setPadding((int)(buttonPadding * metrics.density), (int)(16 * metrics.density), (int)(buttonPadding * metrics.density), (int)(16 * metrics.density));
            nextButtonView.setGravity(Gravity.CENTER);
            nextButtonView.setTextColor(ExplanationColor);
            nextButtonView.setTextSize(ExplanationTextSize);
            buttonBar.addView(nextButtonView);
        	nextButtonView.setId(NextButtonId);
        }
        
        layout.addView(buttonBar);
        
        if (canShareButtonSpace)
        	buttonStripMargin = 0;
        
        //This is set to true if the title isn't inline with the cling and shown above it
        boolean titleIsAboveCling = false;
        //This is set to true if the title and text are show inline with the cling
        boolean canInlineText = false;
        
        //Construct the title and explanation
        if (title != null) {
        	
        	int MinimumLineWidth = HelpOverlayBuilder.MinimumLineWidth;
        	if (titleRequiredLineWidth != 0)
        		MinimumLineWidth = titleRequiredLineWidth;
        	//Position parameters
        	RelativeLayout.LayoutParams titleParams= new RelativeLayout.LayoutParams(rct.right, (int) (48 * metrics.scaledDensity));
        	titleParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);

        	//The additional margin is applied depending on the cling position
        	int additionalMarginRight = 0;
        	int additionalMarginLeft = 0;
        	int additionalMarginTop = 0;
        	
        	//see if title can be drawn above the cling
        	if (restrictedLayoutRectangle.top > titleParams.height + titleTopMargin * metrics.density) {
        		additionalMarginTop = 0;
        		titleIsAboveCling = true;
        	}
        	
        	if (!titleIsAboveCling) {
	        	//See if title can be drawn inline with the cling
	        	if ((int)(metrics.density * MinimumLineWidth) < restrictedLayoutRectangle.left) {
	        		canInlineText = true;
	        		additionalMarginRight = sizeX - restrictedLayoutRectangle.left;
	        	} else {if ((int)(metrics.density * MinimumLineWidth) < sizeX - restrictedLayoutRectangle.right) {
	        		canInlineText = true;
	        		additionalMarginLeft = restrictedLayoutRectangle.right;
	        	}}
	        	if (!canInlineText) {
	        		//It can't be shown above or inlined; show it below
            		additionalMarginTop = restrictedLayoutRectangle.bottom;
                	titleParams.topMargin = y + ClingOuterRadius + (int)(10 * metrics.density);
	        	}
        	}
        	
        	if (buttonStripMargin != 0 && titleIsAboveCling) {
        		additionalMarginTop += buttonStripMargin;
        		//The title might need to be inlined if its bottom border intersects the restricted layout rectangle
        		//See if title can be drawn inline with the cling
        		if (DEBUG) Log.d("HelpOverlayBuilder", "Title is above the cling and below the buttonbar.");
	        	if ((int)(metrics.density * MinimumLineWidth) < restrictedLayoutRectangle.left) {
	        		canInlineText = true;
	        		additionalMarginRight = sizeX - restrictedLayoutRectangle.left;
	        		if (DEBUG) Log.d("HelpOverlayBuilder", "Title can be inlined.");
	        	} else {if ((int)(metrics.density * MinimumLineWidth) < sizeX - restrictedLayoutRectangle.right) {
	        		canInlineText = true;
	        		additionalMarginLeft = restrictedLayoutRectangle.right;
	        	}}
        	}
        	
        	//Set the margin, with whatever extras the previous comparisons may have added
        	if (additionalMarginTop == 0 && !titleIsAboveCling) {
        		additionalMarginTop = initialExtraTopMargin;
        	}
        	titleParams.topMargin = (int)(titleTopMargin * metrics.density) + additionalMarginTop;
        	titleParams.leftMargin = (int)(textMargin * metrics.density) + additionalMarginLeft;
        	titleParams.rightMargin = (int)(textMargin * metrics.density) + additionalMarginRight;
        	
        	//Actual title view
        	TextView titleView = new TextView(context);
        	titleView.setText(title);
        	titleView.setTextColor(TitleColor);
        	titleView.setShadowLayer(metrics.density, 0, 2*metrics.density, ShadowColor);
        	titleView.setTextSize(36);
        	titleView.setLayoutParams(titleParams);
        	titleView.setId(1);
        	
        	layout.addView(titleView);
        	
        	MinimumLineWidth = HelpOverlayBuilder.MinimumLineWidth;
        	
    		//For now, the explanation needs a title in order to be shown
        	if (explanation != null) {
        		//Position parameters
            	RelativeLayout.LayoutParams explanationParams= new RelativeLayout.LayoutParams(rct.right, LayoutParams.WRAP_CONTENT);
            	if (explanationMaxLineWidth != 0)
            		explanationParams.width = (int)(explanationMaxLineWidth * metrics.density);
            	if (rct.right > 600 * metrics.density)
            		explanationParams.width = (int)(600 * metrics.density);
            		
            	explanationParams.leftMargin = (int)(textMargin * metrics.density) + additionalMarginLeft;
            	explanationParams.rightMargin = (int)(textMargin * metrics.density) + additionalMarginRight;
            	// If the title isn't above the cling just show the explanation below it, with any additional margins the title may have
            	if (!titleIsAboveCling) {
	            	explanationParams.addRule(RelativeLayout.BELOW, 1);
	            	explanationParams.topMargin = (int)(explanationTopMargin * metrics.density);
            	}
            	//Actual title view
            	TextView explanationView = new TextView(context);
            	explanationView.setText(explanation);
            	explanationView.setTextColor(ExplanationColor);
            	explanationView.setMaxLines(maxExplanationLines);
            	explanationView.setShadowLayer(metrics.density, 0, 2*metrics.density, ShadowColor);
            	explanationView.setTextSize(20);
            	explanationView.setLayoutParams(explanationParams);
            	
            	if (titleIsAboveCling)  {
            		if (canInlineText) {
            			//The title is inlined; it's ok to just inline the explanation as well and skip checking the cling
    	            	explanationParams.addRule(RelativeLayout.BELOW, 1);
    	            	explanationParams.topMargin = (int)(explanationTopMargin * metrics.density);
            		}
            		else {
            			//The title is not inlined but above the cling
            			//The explanation may either be below the title, inlined, or below the cling
	            		additionalMarginRight = 0;
	            		additionalMarginLeft = 0;
	            		//See if the explanation can be moved above the cling as well; this needs the text to be added to the layout in order
	            		//to get its height
	            		int maxExplanationHeight = (int)(maxExplanationLines * explanationView.getLineHeight());
	            		int titleBottom = (int)((48 + titleTopMargin) * metrics.density) + additionalMarginTop;
	        			if (DEBUG) Log.d("HelpOverlayBuilder", "The explanation height is " + maxExplanationHeight + " and the title bottom is " + titleBottom);
	            		if (titleBottom + 32 * metrics.density + maxExplanationHeight > restrictedLayoutRectangle.top) {
	            			//It can't be shown above the cling; it must be moved into a safe place
	            			
	            			//See if it can be inlined with the cling
	            			canInlineText = false;
	            			if ((int)(metrics.density * MinimumLineWidth) < restrictedLayoutRectangle.left) {
	            				//It can be inlined and thus shown below the title, but indented
	        	            	explanationParams.addRule(RelativeLayout.BELOW, 1);
	        	            	explanationParams.topMargin = (int)(explanationTopMargin  * metrics.density);
	                    		canInlineText = true;
	                    		additionalMarginRight = sizeX - restrictedLayoutRectangle.left;
	                    	} else {if ((int)(metrics.density * MinimumLineWidth) < sizeX - restrictedLayoutRectangle.right) {
	        	            	explanationParams.addRule(RelativeLayout.BELOW, 1);
	        	            	explanationParams.topMargin = (int)(explanationTopMargin * metrics.density);
	                    		canInlineText = true;
	                    		additionalMarginLeft = restrictedLayoutRectangle.right;
	                    	}}
	            			if (!canInlineText) {
	            				//It can't be inlined or shown below the title; show it below the cling
	            				if (buttonStripMargin != 0) {
	            					//However, if the button bar is on top, the only way to show this is below the title
	            	            	explanationParams.addRule(RelativeLayout.BELOW, 1);
	            	            	explanationParams.topMargin = (int)(explanationTopMargin * metrics.density);
		                    		additionalMarginLeft = restrictedLayoutRectangle.right;
		                    		additionalMarginRight = sizeX - restrictedLayoutRectangle.left;
		                    		if (additionalMarginLeft > additionalMarginRight) {
		                    			additionalMarginLeft = 0;
		                    		}
		                    		else {
		                    			additionalMarginRight = 0;
		                    		}
	            				}
	            				else {
			    	            	explanationParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 1);
			            			explanationParams.topMargin = restrictedLayoutRectangle.bottom + (int)(textMargin * metrics.density);
	            				}
	            			}
	    	            	explanationParams.leftMargin = (int)(textMargin * metrics.density) + additionalMarginLeft;
	    	            	explanationParams.rightMargin = (int)(textMargin * metrics.density) + additionalMarginRight;
	            		}
	            		else {
	            			//It can be shown along with the title
	    	            	explanationParams.addRule(RelativeLayout.BELOW, 1);
	    	            	explanationParams.topMargin = (int)(explanationTopMargin * metrics.density);
	            		}
            		}
            	}
            	
            	if (buttonStripMargin == 0) {
            		// If the button strip is on the bottom, make sure the explanation doesn't intersect it by setting its above rule
            		explanationParams.addRule(RelativeLayout.ABOVE, ButtonBarId);
            	}

            	explanationView.setLayoutParams(explanationParams);
            	layout.addView(explanationView);
            	// keep the margins in sync
            	titleParams.topMargin = (int)(titleTopMargin * metrics.density) + additionalMarginTop;
            	titleParams.leftMargin = (int)(textMargin * metrics.density) + additionalMarginLeft;
            	titleView.setLayoutParams(titleParams);
        	}
        	
        }
		
		return layout;
	}
	
	public HelpOverlayBuilder show() {
		final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
		final View helpOverlay = build();
        root.addView(helpOverlay);
    	helpOverlay.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	helpOverlay.setScaleX(2);
    	helpOverlay.setScaleY(2);
    	helpOverlay.setAlpha(0);
        helpOverlay.animate()
        	.scaleX(1).scaleY(1).alpha(1)
        	.setDuration(AnimationLength)
        	.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.decelerate_cubic))
        	.setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
        			helpOverlay.setLayerType(View.LAYER_TYPE_NONE, null);
					
				}
			});
        return this;
	}
	
	public HelpOverlayBuilder showInstantly() {
		final ViewGroup root = (ViewGroup)context.getWindow().getDecorView();
		final View helpOverlay = build();
        root.addView(helpOverlay);
        return this;
	}
	
	// The methods following this line relate directly to HelpStory
	
	public void setIsPartOfStory(boolean isPartOfStory) {
		this.isStoryPage = isPartOfStory;
	}
	
	public void setStoryPageIndex(int index) {
		this.storyPageIndex = index;
	}
	
	public int getStoryPageIndex(int index) {
		return storyPageIndex;
	}
	
	public void setCanAdvanceInStory(boolean canAdvance) {
		nextButton = canAdvance;
	}
	
	public void setDoneOnClickListener(OnClickListener onClickListener) {
		doneAction = onClickListener;
	}
	
	public void cleanup() {
		context = null;
		highlightedView = null;
		helperGraphic = null;
		if (root != null)
			root.setBackgroundColor(0);
		root = null;
	}
	
}
