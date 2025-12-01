package com.example.kitchenlife.net;

import com.example.kitchenlife.data.Recipe;
import com.example.kitchenlife.data.RecipeIngredient;
import com.example.kitchenlife.data.RecipeStep;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface SupabaseApi {

    /* ------------------------------
     * 레시피 목록 (기본 / 고급)
     * ------------------------------ */

    // 기본 목록
    @GET("/rest/v1/v_recipes")
    Call<List<Recipe>> listRecipes(
            @Query("select") String select,
            @Query("order")  String order,
            @Query("limit")  Integer limit,
            @Query("offset") Integer offset
    );

    // 고급(검색/필터/페이징)
    @GET("/rest/v1/v_recipes")
    Call<List<Recipe>> listRecipesAdvanced(
            @Query("select")    String select,       // "*"
            @Query("order")     String order,        // "created_at.desc"
            @Query("limit")     Integer limit,       // 50
            @Query("offset")    Integer offset,      // 0
            @Query("title")     String titleIlike,   // "ilike.*김치*" (nullable)
            @Query("category")  String categoryEq,   // "eq.한식"     (nullable)
            @Query("difficulty")String difficultyEq, // "eq.쉬움"     (nullable)
            @Query("time_minutes") String timeLte    // "lte.30"      (nullable)
    );

    /* ------------------------------
     * 레시피 상세: 스텝 / 재료
     * ------------------------------ */

    @GET("/rest/v1/v_recipe_steps")
    Call<List<RecipeStep>> getStepsByRecipe(
            @Query("select")    String select,     // e.g. "*"
            @Query("recipe_id") String recipeIdEq, // "eq.<id>"
            @Query("order")     String order       // "step_no.asc"
    );

    // 재료 조회 (recipe_id로)
    @GET("/rest/v1/v_recipe_ingredients")
    Call<List<RecipeIngredient>> getIngredientsByRecipe(
            @Query("select")    String select,     // "*"
            @Query("recipe_id") String recipeIdEq, // "eq.<id>"
            @Query("order")     String order       // "id.asc"
    );

    // 같은 엔드포인트 별칭(기존 코드 호환용)
    @GET("/rest/v1/v_recipe_ingredients")
    Call<List<RecipeIngredient>> listRecipeIngredients(
            @Query("recipe_id") String recipeIdEq, // "eq.<id>"
            @Query("select")    String select,     // "*"
            @Query("order")     String order       // "id.asc"
    );

    /* ------------------------------
     * 단건 / 간단 목록
     * ------------------------------ */

    @GET("/rest/v1/recipes")
    Call<List<Recipe>> getRecipeById(
            @Query("select") String select,  // "id,title,time_minutes,thumbnail_url,category,difficulty"
            @Query("id")     String eqId     // "eq.<id>"
    );

    @GET("/rest/v1/recipes")
    Call<List<Recipe>> listRecipesSimple(
            @Query("select") String select,  // "id,title,time_minutes,thumbnail_url"
            @Query("order")  String order,   // "created_at.desc"
            @Query("limit")  Integer limit,
            @Query("offset") Integer offset
    );

    /* ------------------------------
     * 🔎 제목으로 정확히 레시피 찾기 (식단→쇼핑 동기화에서 사용)
     * ------------------------------ */

    @GET("/rest/v1/recipes")
    Call<List<Recipe>> findRecipeByExactTitle(
            @Query("select") String select,   // "id"
            @Query("title")  String titleEq   // "eq.김치찌개"
    );
}
