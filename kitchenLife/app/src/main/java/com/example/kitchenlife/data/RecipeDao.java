package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecipeDao {

    /* ---------- 레시피 기본 조회 ---------- */

    @Query("SELECT * FROM recipes WHERE title = :title COLLATE NOCASE LIMIT 1")
    Recipe findByTitleSync(String title);

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<Recipe>> observeAll();

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE ASC")
    List<Recipe> getAllSync();

    @Query("SELECT * FROM recipes WHERE title LIKE :q ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<Recipe>> search(String q);

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    Recipe getById(long id);


    /* ---------- 재료 조회 (핵심) ---------- */

    /** 표준 시그니처: 레시피의 전체 재료(옵션 포함) */
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId ORDER BY id ASC")
    List<RecipeIngredient> getIngredients(long recipeId);

    /** 동일 기능(호환용 별칭 1) */
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId ORDER BY id ASC")
    List<RecipeIngredient> ingredientsOf(long recipeId);

    /** 동일 기능(호환용 별칭 2) */
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId ORDER BY id ASC")
    List<RecipeIngredient> getIngredientsForRecipe(long recipeId);

    /** 제목으로 재료 조회 (fallback 용) */
    @Query("SELECT ri.* FROM recipe_ingredients ri " +
            "INNER JOIN recipes r ON r.id = ri.recipe_id " +
            "WHERE r.title = :title COLLATE NOCASE ORDER BY ri.id ASC")
    List<RecipeIngredient> ingredientsByRecipeTitle(String title);

    /** 선택 재료 제외(필요 시 소비/쇼핑 산출에 사용) */
    @Query("SELECT * FROM recipe_ingredients " +
            "WHERE recipe_id = :recipeId AND is_optional = 0 " +
            "ORDER BY id ASC")
    List<RecipeIngredient> getRequiredIngredients(long recipeId);

    /** 수치가 있는 재료만(단위/환산 로직이 필요한 경우 전처리에 유용) */
    @Query("SELECT * FROM recipe_ingredients " +
            "WHERE recipe_id = :recipeId AND amount_numeric IS NOT NULL " +
            "ORDER BY id ASC")
    List<RecipeIngredient> getNumericIngredients(long recipeId);


    /* ---------- 적재 / 업서트 ---------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRecipe(Recipe r);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertIngredients(List<RecipeIngredient> list);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Recipe> list);


    /* ---------- 유틸 ---------- */

    @Query("DELETE FROM recipes")
    void clearAll();

    @Query("SELECT COUNT(*) FROM recipes")
    int count();

    /** 레시피 교체 시 이전 재료 정리용 */
    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId")
    void deleteIngredientsOf(long recipeId);
}
