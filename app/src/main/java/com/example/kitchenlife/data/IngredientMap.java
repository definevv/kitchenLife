package com.example.kitchenlife.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Entity(tableName = "ingredient_map")
public class IngredientMap {
    @PrimaryKey @NonNull public String ingredientKey;
    @NonNull public String name;
    @Nullable public String defaultUnit;
}
