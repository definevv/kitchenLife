package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecipeDao {

    // 목록
    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<Recipe>> observeAll();

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE ASC")
    List<Recipe> getAllSync();

    // 검색(선택)
    @Query("SELECT * FROM recipes WHERE title LIKE :q ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<Recipe>> search(String q);

    // 단건
    @Query("SELECT * FROM recipes WHERE id=:id LIMIT 1")
    Recipe getById(long id);

    // 재료들
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id=:recipeId ORDER BY id ASC")
    List<RecipeIngredient> ingredientsOf(long recipeId);

    // 적재/업서트
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRecipe(Recipe r);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertIngredients(List<RecipeIngredient> list);

    // 유틸
    @Query("DELETE FROM recipes")
    void clearAll();

    // 총 레시피 개수
    @Query("SELECT COUNT(*) FROM recipes")
    int count();

    // 다중 insert (시드 주입용)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Recipe> list);
}
