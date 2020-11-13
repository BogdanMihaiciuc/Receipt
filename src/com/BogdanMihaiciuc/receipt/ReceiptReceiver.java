package com.BogdanMihaiciuc.receipt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

// The ReceiptReceiver receives broadcasts while the app is running and visible
public class ReceiptReceiver extends BroadcastReceiver {

    private Runnable refreshBudgetRunnable;
    private BackendFragment.FactoryRunnable factoryRunnable;

    private ReceiptReceiver() {}

    private ReceiptReceiver(Runnable refreshBudgetRunnable) {
        this.refreshBudgetRunnable = refreshBudgetRunnable;
    }

    private ReceiptReceiver(BackendFragment.FactoryRunnable factoryRunnable) {
        this.factoryRunnable = factoryRunnable;
    }

    public static ReceiptReceiver budgetReceiver(Runnable refreshBudgetRunnable) {
        return new ReceiptReceiver(refreshBudgetRunnable);
    }

    public static ReceiptReceiver factoryReceiver(BackendFragment.FactoryRunnable factoryRunnable) {
        return new ReceiptReceiver(factoryRunnable);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //noinspection StringEquality
        if (intent.getAction() == BackendFragment.ActionRefreshBudgetKey) {
            if (refreshBudgetRunnable != null) {
                refreshBudgetRunnable.run();
            }
        }
        //noinspection StringEquality
        if (intent.getAction() == BackendFragment.ActionCreateFromFactoryKey) {
            Bundle extras = intent.getExtras();
            if (extras != null && factoryRunnable != null) {
                factoryRunnable.createFromFactory(extras.getLong(BackendFragment.FactoryUIDKey, BackendFragment.NoFactory));
            }
        }
    }
}
