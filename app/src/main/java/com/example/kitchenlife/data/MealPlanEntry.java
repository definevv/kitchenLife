package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(
        tableName = "meal_plan_entries",
        indices = {@Index(value = {"dateKey","mealType"}), @Index("recipeId")}
)
public class MealPlanEntry {
    @PrimaryKey(autoGenerate = true) public long id;

    public long dateKey;   // yyyymmdd
    public int  mealType;  // 0=아침, 1=점심, 2=저녁

    /** Supabase recipes.id (없으면 0) */
    public long recipeId;  // ★ 추가

    @NonNull public String title = "";  // 표시용 타이틀(캐시 실패 대비)
    public String notes;
    public long createdAt;
}
