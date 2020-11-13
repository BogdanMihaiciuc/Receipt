package com.BogdanMihaiciuc.receipt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class ReceiptScheduler extends Service {

    void scheduleBudget(Context context, long unixTime) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, ReceiptActivity.class);
        intent.putExtra(BackendFragment.ActionRefreshBudgetKey, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        am.set(AlarmManager.RTC, unixTime, pendingIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
