package com.example.kitchenlife.data;

import android.content.Context;
import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MealPlanRepository {
    private final MealPlanDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public MealPlanRepository(Context ctx) {
        dao = AppDatabase.get(ctx).mealPlanDao();
    }

    public LiveData<List<MealPlanEntry>> getMeals(long dateKey, int mealType) {
        return dao.getMeals(dateKey, mealType);
    }

    public void insert(MealPlanEntry e) { io.execute(() -> dao.insert(e)); }
    public void update(MealPlanEntry e) { io.execute(() -> dao.update(e)); }
    public void delete(MealPlanEntry e) { io.execute(() -> dao.delete(e)); }
}
