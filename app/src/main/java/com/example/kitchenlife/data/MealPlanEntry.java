package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(
        tableName = "meal_plan_entries",
        indices = {@Index(value = {"dateKey","mealType"})}
)
public class MealPlanEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;

    // yyyymmdd (예: 20251124)
    public long dateKey;

    // 0=Breakfast, 1=Lunch, 2=Dinner
    public int mealType;

    @NonNull
    public String title = "";   // 메뉴명(자유 텍스트)

    public String notes;        // 메모(선택)

    public long createdAt;      // System.currentTimeMillis()
}
