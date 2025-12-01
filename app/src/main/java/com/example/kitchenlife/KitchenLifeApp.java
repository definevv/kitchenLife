package com.example.kitchenlife;

import android.app.Application;

import com.example.kitchenlife.data.AppDatabase;

public class KitchenLifeApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // DB 프리워밍: 백그라운드에서 미리 열어두기
        AppDatabase.DB_EXECUTOR.execute(() -> AppDatabase.get(this).shoppingDao().loadAll());
    }
}
