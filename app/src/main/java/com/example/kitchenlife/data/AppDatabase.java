package com.example.kitchenlife.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                // 기존 엔티티들 …
                MealPlanEntry.class,
                MyMealSet.class,
                PantryItem.class,
                ShoppingItem.class,
                RecipeCache.class,
                Recipe.class,
                RecipeIngredient.class,

                // ✅ IngredientMap 포함
                IngredientMap.class
        },
        version = 10,              // ← 기존보다 +1 (중요)
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    // DB 전용 스레드풀 (쿼리, 트랜잭션 모두 여기서 실행)
    public static final ExecutorService DB_EXECUTOR =
            Executors.newFixedThreadPool(2);

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "kitchenlife.db")
                            // ❌ 절대 쓰지 마세요: .allowMainThreadQueries()
                            .setQueryExecutor(DB_EXECUTOR)
                            .setTransactionExecutor(DB_EXECUTOR)
                            .enableMultiInstanceInvalidation()
                            .fallbackToDestructiveMigration() // 개발 중
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // 기존
    public abstract MealPlanDao mealPlanDao();
    public abstract MyMealSetDao myMealSetDao();

    // 새
    public abstract PantryDao pantryDao();
    public abstract ShoppingDao shoppingDao();
    public abstract IngredientMapDao ingredientMapDao();
    public abstract RecipeCacheDao recipeCacheDao();
    public abstract RecipeDao recipeDao();
}
