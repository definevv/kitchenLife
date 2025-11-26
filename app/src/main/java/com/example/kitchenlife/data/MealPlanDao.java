package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface MealPlanDao {

    @Query("SELECT * FROM meal_plan_entries WHERE dateKey = :dateKey AND mealType = :mealType ORDER BY id DESC")
    LiveData<List<MealPlanEntry>> getMeals(long dateKey, int mealType);

    @Insert
    long insert(MealPlanEntry e);

    @Update
    int update(MealPlanEntry e);

    @Delete
    int delete(MealPlanEntry e);
}
