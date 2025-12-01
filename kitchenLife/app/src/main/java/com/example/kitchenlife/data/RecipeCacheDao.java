package com.example.kitchenlife.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface RecipeCacheDao {

    @Query("SELECT * FROM recipe_cache WHERE recipeId = :id LIMIT 1")
    RecipeCache get(long id);

    /** 같은 recipeId 오면 교체(업서트 효과) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RecipeCache e);

    @Query("DELETE FROM recipe_cache WHERE updatedAt < :before")
    int deleteOlderThan(long before);
}
