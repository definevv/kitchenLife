package com.example.kitchenlife.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                // ---- 엔티티 등록 ----
                MealPlanEntry.class,
                MyMealSet.class,
                PantryItem.class,
                ShoppingItem.class,
                RecipeCache.class,
                Recipe.class,
                RecipeIngredient.class,
                IngredientMap.class
        },
        version = 13,              // ← 스키마 변경 시 +1
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /** DB 전용 스레드풀 (쿼리/트랜잭션 실행용) */
    public static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(2);

    // ---- DAO 노출부 ----
    public abstract MealPlanDao mealPlanDao();
    public abstract MyMealSetDao myMealSetDao();
    public abstract PantryDao pantryDao();
    public abstract ShoppingDao shoppingDao();
    public abstract IngredientMapDao ingredientMapDao();
    public abstract RecipeCacheDao recipeCacheDao();
    public abstract RecipeDao recipeDao();
    public abstract MyMealsDao myMealsDao();

    /** 싱글톤 인스턴스 */
    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "kitchenlife.db")
                            // 메인 스레드 쿼리 금지 (절대 켜지 말 것)
                            // .allowMainThreadQueries()  // ❌ 금지
                            .setQueryExecutor(DB_EXECUTOR)
                            .setTransactionExecutor(DB_EXECUTOR)
                            .enableMultiInstanceInvalidation()
                            // 개발 단계: 스키마 바뀌면 파괴적 마이그레이션 (데이터 리셋)
                            .fallbackToDestructiveMigration()
                            // 운영 전환 시엔 위 줄을 제거하고 아래 MIGRATIONS 적용
                            // .addMigrations(MIGRATION_11_12 /*, MIGRATION_12_13 ... */)
                            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // ========= 마이그레이션 예시(운영 전환 시 사용) =========
    // fallbackToDestructiveMigration()를 제거하고 addMigrations(...)로 대체하세요.

    /** 11 → 12 예시: 스키마 변경 사항이 있을 때 여기에 정의
     *  - 예: 컬럼 추가/삭제, 인덱스 추가 등
     *  - 현재는 샘플로 비워둠(필요 시 작성)
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 예시)
            // db.execSQL("ALTER TABLE pantry_items ADD COLUMN expireAt INTEGER");
            // db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_ingredient_key ON recipe_ingredients(ingredient_key)");
        }
    };
}
