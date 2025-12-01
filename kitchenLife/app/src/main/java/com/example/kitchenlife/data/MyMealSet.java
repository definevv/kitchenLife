package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "my_meal_sets",
        indices = {@Index(value = {"name"})}
)
public class MyMealSet {
    @PrimaryKey(autoGenerate = true) public long id;

    @NonNull public String name = "";     // 세트 이름
    public String breakfastTitle;         // 선택
    public String lunchTitle;             // 선택
    public String dinnerTitle;            // 선택

    /** 메모 JSON (ingredients/steps/tags/note) */
    public String notes;

    public long updatedAt;
}
