package com.BogdanMihaiciuc.receipt;

import android.R.interpolator;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class IndicatorFragmentNonCompat extends Fragment {

	final static int WorkIndicatorAppearDelay = 200;
	final static int IdIndicatorShown = 7076;
	final static int IdIndicatorHidden = 7077;
	
	static interface IndicatorFragmentListener {
		View contentView();
		View rootView();
	}
	
	static class Task {
		String name;
		Runnable cancelAction;
		
		static Task createTask(String name, Runnable cancelAction) {
			Task task = new Task();
			task.name = name;
			task.cancelAction = cancelAction;
			return task;
		}
	}
	
	private Handler delayHandler = new Handler();
	
	private int cancellableTaskCount = 0;
	private View workingView = null;
	private TextView workingText = null;
	private ArrayList<Task> taskList = new ArrayList<Task>();
	
	private Activity activity;
	private ViewGroup content;
	private FrameLayout root;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
		return null;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = getActivity();
        
        root = (FrameLayout)activity.getWindow().getDecorView();
        try {
        	content = (ViewGroup)root.getChildAt(0);
        }
        catch (ClassCastException e) {
        	content = (ViewGroup)root.getChildAt(1);
        }
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		
		// Context references
		activity = null;
		workingView = null;
		workingText = null;
		content = null;
		root = null;
	}
	
	private class StartWorkingRunnable implements Runnable {
		private Task task;
		private long animationLength;
		StartWorkingRunnable(Task task) {
			this.task = task;
			animationLength = 200;
		}
		StartWorkingRunnable(Task task, long animationLength) {
			this.task = task;
			this.animationLength = animationLength;
		}
		@Override
		public void run() {
			if (activity == null) {
				// The fragment is detached, so there's no UI to update
				return;
			}
			if (!taskList.contains(task))
				// Work has completed within the required timeframe, discard the indicator
				return;
			
			final FrameLayout actionBarRoot = (FrameLayout)content.getChildAt(0);
			if (workingView == null) {
				workingView = activity.getLayoutInflater().inflate(R.layout.layout_working_indicator, null);
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, actionBarRoot.getChildAt(0).getHeight());
				params.gravity = Gravity.RIGHT;
				workingView.setLayoutParams(params);
//				actionBarRoot.addView(workingView);
				workingView.setTranslationX(100);
				workingView.setAlpha(0);
				workingText = (TextView)workingView.findViewById(R.id.indicatorText);
				if (task.cancelAction != null) {
					workingText.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							task.cancelAction.run();
						}
					});
				}
				else {
					// make disabled ONLY if there are no other cancellable tasks
					if (cancellableTaskCount == 0)
						workingText.setEnabled(false);
				}
				workingView.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						// Dummy method to make workingView intercept clicks
					}
				});
			}
			if (taskList.size() > 1)
				workingText.setText(task.name + " " + taskList.size());
			else
				workingText.setText(task.name);
			workingView.setId(IdIndicatorShown);
			workingView.animate()
					.translationX(0)
					.alpha(1)
					.setDuration(animationLength)
					.setInterpolator(AnimationUtils.loadInterpolator(activity, interpolator.decelerate_cubic))
					.setListener(new AnimatorListener() {
						@Override
						public void onAnimationStart(Animator animation) {
							if (activity != null)
								workingView.setVisibility(View.VISIBLE);
						}
						@Override
						public void onAnimationRepeat(Animator animation) {
						}
						@Override
						public void onAnimationEnd(Animator animation) {
						}
						@Override
						public void onAnimationCancel(Animator animation) {
						}
					});
		}
	};
	
	public void startWorking(Task workerTask, int delay, int animationLength) {
		//If the task is already within the loop, remove it and then re-add it, so it's on the top of the list
		if (taskList.contains(workerTask)) {
			taskList.remove(workerTask);
			Log.i("IndicatorFragment", "Tried to add task " + workerTask.name + " but it was already in progress!");
			if (workerTask.cancelAction != null)
				cancellableTaskCount -= 1;
		}
		if (workerTask.cancelAction != null) {
			cancellableTaskCount += 1;
			taskList.add(workerTask);
		}
		else {
			// Continuous tasks get added to the start of the tasklist
			taskList.add(0, workerTask);
		}
		// Give the task some time to make it appear 'instant'
		// If work hasn't completed within WorkIndicatorAppearDelay milliseconds
		// show an indicator confirming that there's something happening in the background
		delayHandler.postDelayed(new StartWorkingRunnable(workerTask, animationLength), delay);
	}
	
	public void startWorking(Task workerTask) {
		startWorking(workerTask, WorkIndicatorAppearDelay, 200);
	}
	
	public void startWorkingInstantly(Task workerTask) {
		startWorking(workerTask, 0, 0);
	}
	
	public void stopWorking(Task finishedTask) {
		//stopWorking is instant; no need to post a runnable to the handler
		//only need to do something if the finished task was indeed in the taskList
		if (taskList.contains(finishedTask)) {
			boolean removingLastTask = (taskList.indexOf(finishedTask) == taskList.size() - 1);
			taskList.remove(finishedTask);
			if (finishedTask.cancelAction != null)
				cancellableTaskCount -= 1;
			// If there are no other pending tasks, clear the indicator
			if (taskList.size() == 0) {
				// UI only needs updating the workingView has been posted
				if (workingView != null) {
					// Disable button clicks during animation to prevent ghost task removals
					workingText.setEnabled(false);
					workingView.setId(IdIndicatorHidden);
					workingView.animate()
							.translationX(100)
							.alpha(0)
							.setInterpolator(AnimationUtils.loadInterpolator(activity, interpolator.accelerate_cubic))
							.setListener(new AnimatorListener() {
								@Override
								public void onAnimationStart(Animator animation) {	}
								
								@Override
								public void onAnimationRepeat(Animator animation) {}
								
								@Override
								public void onAnimationEnd(Animator animation) {
									if (activity != null)
									workingView.setVisibility(View.GONE);
								}
								
								@Override
								public void onAnimationCancel(Animator animation) {
									if (activity != null)
									workingView.setVisibility(View.GONE);
								}
							});
				}
			}
			else {
				// Else update it with whatever runnable is left, if needed
				if (removingLastTask) {
					new StartWorkingRunnable(taskList.get(taskList.size() - 1)).run();
				}
				if (cancellableTaskCount < 2) {
					new StartWorkingRunnable(taskList.get(taskList.size() - 1)).run();
				}
			}
		}
		else {
			Log.i("IndicatorFragment", "Tried to remove task " + finishedTask.name + " but it was already finished!");
		}
	}

}
