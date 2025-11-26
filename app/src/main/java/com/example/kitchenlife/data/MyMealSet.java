package com.example.kitchenlife.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "my_meal_set")
public class MyMealSet {
    @PrimaryKey(autoGenerate = true) public long id;

    @NonNull public String name = "";
    @Nullable public String breakfastTitle; // 레시피 제목
    @Nullable public String lunchTitle;
    @Nullable public String dinnerTitle;
    @Nullable public String notes;
}
