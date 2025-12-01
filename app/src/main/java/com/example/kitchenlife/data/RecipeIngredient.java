package com.example.kitchenlife.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "recipe_ingredients",
        foreignKeys = @ForeignKey(
                entity = Recipe.class,
                parentColumns = "id",
                childColumns = "recipe_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index(value = {"recipe_id"}), @Index(value = {"ingredient_name"})}
)
public class RecipeIngredient {
    @PrimaryKey(autoGenerate = true) public long id;

    public long recipe_id;            // FK
    public String ingredient_name;    // “양파” 등
    public String quantity_text;      // “1개”, “200 g” 등 원문
    public String unit;               // “g”, “개” 등(없으면 null)
    public Double amount_numeric;     // 200.0 등(없으면 null)
    public boolean is_optional;
}
