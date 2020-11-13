package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

// The ReceiptFileReceiver is a placeholder activity
// its only purpose is to receive ACTION_VIEW intents
// and route them to ReceiptActivity, self-destructing afterwards
//
// This ensures that the intent is only handled once
// and not recreated on any rotation
public class ReceiptFileReceiver extends Activity {

    final static String TAG = ReceiptFileReceiver.class.getName();
    final static boolean DEBUG = true;

    static Intent openFileIntent;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DEBUG) Log.d(TAG, "ReceiptFileReceiver has caught a file in onCreate()!");

        Intent launchIntent = getIntent();
        if (launchIntent.getAction().equals(Intent.ACTION_VIEW)) {
            openFileIntent = launchIntent;
        }

        Intent intent = new Intent(this, ReceiptActivity.class);
        startActivity(intent);
        finish();
    }

    public void onNewIntent(Intent launchIntent) {

        if (DEBUG) Log.d(TAG, "ReceiptFileReceiver has caught a file in onNewIntent()");

        if (launchIntent.getAction().equals(Intent.ACTION_VIEW)) {
            openFileIntent = launchIntent;
        }

        Intent intent = new Intent(this, ReceiptActivity.class);
        startActivity(intent);
        finish();
    }

}
