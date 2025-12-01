package com.example.kitchenlife.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MealPlanRepository {

    private static final String TAG = "MealPlanRepo";

    private final AppDatabase db;
    private final MealPlanDao mealPlanDao;
    private final PantryDao pantryDao;
    private final RecipeDao recipeDao;
    private final ShoppingDao shoppingDao;

    public MealPlanRepository(Context ctx) {
        this.db = AppDatabase.get(ctx);
        this.mealPlanDao = db.mealPlanDao();
        this.pantryDao   = db.pantryDao();
        this.recipeDao   = db.recipeDao();
        this.shoppingDao = db.shoppingDao();
    }

    /* -------- 조회/CRUD -------- */

    public LiveData<List<MealPlanEntry>> observeMeals(long dateKey, int mealType) {
        return mealPlanDao.observeByDay(dateKey, mealType);
    }
    public List<MealPlanEntry> getMealsSync(long dateKey, int mealType) {
        return mealPlanDao.getMeals(dateKey, mealType);
    }
    public void insertAsync(MealPlanEntry e) {
        AppDatabase.DB_EXECUTOR.execute(() -> mealPlanDao.insert(e));
    }
    public void updateAsync(MealPlanEntry e) {
        AppDatabase.DB_EXECUTOR.execute(() -> mealPlanDao.update(e));
    }
    public void deleteAsync(MealPlanEntry e) {
        AppDatabase.DB_EXECUTOR.execute(() -> mealPlanDao.delete(e));
    }
    public void setDone(long id, boolean done) {
        AppDatabase.DB_EXECUTOR.execute(() -> mealPlanDao.setDone(id, done));
    }

    /* -------- 체크 토글 ↔ 팬트리 -------- */

    public void toggleDoneAndSyncPantry(MealPlanEntry entry, boolean checked) {
        if (entry == null) return;
        AppDatabase.DB_EXECUTOR.execute(() -> db.runInTransaction(() -> {
            if (entry.done == checked) return;
            if (entry.recipeId == 0) { mealPlanDao.setDone(entry.id, checked); return; }

            List<RecipeIngredient> ingredients = recipeDao.getIngredients(entry.recipeId);
            if (ingredients == null || ingredients.isEmpty()) { mealPlanDao.setDone(entry.id, checked); return; }

            for (RecipeIngredient ing : ingredients) {
                if (ing == null) continue;
                Parsed pq = parseQuantity(ing.amount_numeric, ing.quantity_text, ing.unit);
                if (pq.amount <= 0) continue;

                if (checked) pantryDao.consume(ing.ingredient_key, ing.ingredient_name, pq.unit, pq.amount);
                else         pantryDao.restore(ing.ingredient_key, ing.ingredient_name, pq.unit, pq.amount);
            }
            mealPlanDao.setDone(entry.id, checked);
        }));
    }

    /* -------- ✨ 식단에 추가 → 장보기 즉시 업서트 -------- */

    public void addWithRecipeAndSendToShopping(long dateKey, int mealType, long recipeId, String title) {
        AppDatabase.DB_EXECUTOR.execute(() -> db.runInTransaction(() -> {
            // 1) 엔트리 저장
            MealPlanEntry e = new MealPlanEntry();
            e.dateKey = dateKey;
            e.mealType = mealType;
            e.recipeId = recipeId;
            e.title = (title == null ? "" : title.trim());
            e.createdAt = System.currentTimeMillis();
            e.id = mealPlanDao.insert(e);

            if (recipeId == 0) return;

            // 2) 레시피 재료 수집(파싱 보강)
            List<RecipeIngredient> ings = recipeDao.getIngredients(recipeId);
            long now = System.currentTimeMillis();
            if (ings == null || ings.isEmpty()) {
                Log.d(TAG, "No ingredients for recipeId=" + recipeId);
                return;
            }

            for (RecipeIngredient ing : ings) {
                if (ing == null) continue;

                Parsed pq = parseQuantity(ing.amount_numeric, ing.quantity_text, ing.unit);
                if (pq.amount <= 0) continue;

                String key  = ing.ingredient_key;
                String name = ing.ingredient_name == null ? "" : ing.ingredient_name.trim();
                String unit = pq.unit; // 파싱된 단위 우선

                // 3) 팬트리 보유량 확인
                PantryItem pi = pantryDao.findForMatch(key, name, unit);
                double have = (pi == null) ? 0d : Math.max(0d, pi.quantity);

                double missing = pq.amount - have;
                if (missing <= 1e-6) continue;

                // 4) 장보기에 업서트(키 우선 → 이름+단위)
                boolean merged = false;
                if (key != null && !key.trim().isEmpty()) {
                    ShoppingItem s = shoppingDao.byKeySync(key);
                    if (s != null) {
                        s.neededQty = Math.max(0d, s.neededQty) + missing;
                        if (unit != null) s.unit = unit;
                        if (!name.isEmpty()) s.name = name;
                        s.updatedAt = now;
                        shoppingDao.update(s);
                        merged = true;
                    }
                }
                if (!merged) {
                    String nm = !name.isEmpty() ? name : (pi != null && pi.name != null ? pi.name : "");
                    String un = unit != null ? unit : (pi != null ? pi.unit : null);

                    ShoppingItem exist = shoppingDao.byNameUnitSync(nm, un);
                    if (exist != null) {
                        exist.neededQty = Math.max(0d, exist.neededQty) + missing;
                        exist.updatedAt = now;
                        shoppingDao.update(exist);
                    } else {
                        ShoppingItem s = new ShoppingItem();
                        s.ingredientKey = (key == null) ? "" : key.trim();
                        s.name = nm;
                        s.unit = un;
                        s.neededQty = missing;
                        s.boughtQty = 0;
                        s.checked = false;
                        s.updatedAt = now;
                        shoppingDao.insert(s);
                    }
                }
            }
        }));
    }

    /* -------- 수량 파싱 유틸 -------- */

    private static final Pattern NUM_UNIT = Pattern.compile(
            "\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z가-힣/%]+)?\\s*"
    );

    /** amount_numeric 우선, 없으면 quantity_text에서 "수치 + 단위"를 파싱. 실패 시 amount=1, 단위는 전달 단위 유지 */
    private Parsed parseQuantity(Double amountNumeric, String quantityText, String unitFromRow) {
        if (amountNumeric != null && amountNumeric > 0) {
            return new Parsed(amountNumeric, normalizeUnit(unitFromRow));
        }
        if (quantityText != null) {
            Matcher m = NUM_UNIT.matcher(quantityText);
            if (m.find()) {
                try {
                    double amt = Double.parseDouble(m.group(1));
                    String u = (m.group(2) != null) ? m.group(2).trim() : unitFromRow;
                    return new Parsed(amt, normalizeUnit(u));
                } catch (Exception ignore) { /* fallthrough */ }
            }
        }
        // 최후의 보루: 최소 1
        return new Parsed(1.0, normalizeUnit(unitFromRow));
    }

    private String normalizeUnit(String u) {
        if (u == null) return null;
        String t = u.trim();
        if (t.isEmpty()) return null;
        // 여기에 "컵→cup", "g→g" 등 통일 규칙을 점진적으로 추가 가능
        return t;
    }

    private static class Parsed {
        final double amount; final String unit;
        Parsed(double a, String u){ amount=a; unit=u; }
    }
}
