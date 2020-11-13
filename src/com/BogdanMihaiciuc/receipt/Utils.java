package com.BogdanMihaiciuc.receipt;

import android.animation.TimeInterpolator;

public class Utils {
	
	static class SingleBounceInterpolator implements TimeInterpolator {
		
		float bounceHeight;
		float bounceDifference;
		
		public SingleBounceInterpolator() {
			this(0.1f);
		}
		
		public SingleBounceInterpolator(float bounceHeightPercent) {
			bounceHeight = bounceHeightPercent;
			bounceDifference = 1 - bounceHeight;
		}

		@Override
		public float getInterpolation(float time) {
			if (time < 0.6f) {
				return (float) Math.pow(time * 1.67f, 2);
			}
			if (time >= 0.6f && time < 0.8f) {
				return bounceDifference + (float) (Math.pow((time - 0.8) * 5d, 2)) * bounceHeight;
			}
			else {
				return bounceDifference + (float) (Math.pow((time - 0.8) * 5d, 2)) * bounceHeight;
			}
		}
		
	}

}
