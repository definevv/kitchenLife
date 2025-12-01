package com.example.kitchenlife.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Supabase 레시피의 요약 캐시 */
@Entity(
        tableName = "recipe_cache",
        indices = { @Index(value = {"title"}) }
)
public class RecipeCache {
    /** Supabase recipes.id 를 그대로 PK로 사용(충돌시 REPLACE 하기 위함) */
    @PrimaryKey
    public long recipeId;

    public String title;
    public Integer timeMinutes;
    public String thumbnailUrl;
    public String category;
    public String difficulty;

    /** 캐시 적재/갱신 시각(ms) */
    public long updatedAt;
}
