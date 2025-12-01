package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
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
        indices = {
                @Index(value = {"recipe_id"}),
                @Index(value = {"ingredient_name"}),
                @Index(value = {"ingredient_key"})
        }
)
public class RecipeIngredient {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 상위 레시피 FK */
    @ColumnInfo(name = "recipe_id")
    public long recipe_id;

    /** 팬트리 매칭용(선택): PantryItem.ingredientKey 와 매칭 */
    @Nullable
    @ColumnInfo(name = "ingredient_key")
    public String ingredient_key;

    /** 재료명 (예: "양파") */
    @NonNull
    @ColumnInfo(name = "ingredient_name", defaultValue = "")
    public String ingredient_name = "";

    /** 원문 표기 (예: "1개", "200 g") */
    @Nullable
    @ColumnInfo(name = "quantity_text")
    public String quantity_text;

    /** 단위 (예: "g", "개"; 없으면 null) */
    @Nullable
    @ColumnInfo(name = "unit")
    public String unit;

    /** 수량 수치 (예: 200.0; 없으면 null) */
    @Nullable
    @ColumnInfo(name = "amount_numeric")
    public Double amount_numeric;

    /** 선택 재료 여부 */
    @ColumnInfo(name = "is_optional", defaultValue = "0")
    public boolean is_optional;

    public RecipeIngredient() { }

    public RecipeIngredient(long recipeId,
                            @Nullable String ingredientKey,
                            @NonNull String ingredientName,
                            @Nullable String quantityText,
                            @Nullable String unit,
                            @Nullable Double amountNumeric,
                            boolean isOptional) {
        this.recipe_id = recipeId;
        this.ingredient_key = ingredientKey;
        this.ingredient_name = (ingredientName == null) ? "" : ingredientName;
        this.quantity_text = quantityText;
        this.unit = unit;
        this.amount_numeric = amountNumeric;
        this.is_optional = isOptional;
    }
}
