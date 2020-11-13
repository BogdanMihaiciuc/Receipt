package com.BogdanMihaiciuc.receipt;

import android.app.IntentService;
import android.content.Intent;

public class BackendSaveService extends IntentService {

	public BackendSaveService() {
		super("BackendSaveService");
		
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		synchronized (BackendStorage.DiskLock) {
			// TODO
		}
	}

}
