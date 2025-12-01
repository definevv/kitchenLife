package com.example.kitchenlife.ui.mealplan;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.MealPlanEntry;
import com.example.kitchenlife.data.MealPlanRepository;
import com.example.kitchenlife.data.MyMealSet;
import com.example.kitchenlife.data.MyMealSetDao;
import com.example.kitchenlife.data.RecipeCacheRepository;

import java.util.List;

/** Meal plan + My Meals(세트) 관리 ViewModel */
public class MealPlanViewModel extends AndroidViewModel {

    // ── MealPlan 저장소(Room)
    private final MealPlanRepository repo;

    // ── 레시피 캐시(Room) — 현재는 직접 사용 안 하지만, 확장 대비해 보유
    @SuppressWarnings("FieldCanBeLocal")
    private final RecipeCacheRepository cacheRepo;

    // ── My Meals(Room)
    private final MyMealSetDao myMealSetDao;
    private final LiveData<List<MyMealSet>> myMealSets; // 전체 세트 목록 캐시

    public MealPlanViewModel(@NonNull Application app) {
        super(app);

        // 기존 MealPlan repository
        repo = new MealPlanRepository(app);

        // Room 초기화
        AppDatabase db = AppDatabase.get(app);
        myMealSetDao = db.myMealSetDao();
        myMealSets = myMealSetDao.all();

        // 레시피 캐시(옵션)
        cacheRepo = new RecipeCacheRepository(app);
    }

    // ─────────────────────────────
    // MealPlan (하루 끼니별 항목)
    // ─────────────────────────────
    public LiveData<List<MealPlanEntry>> meals(long dateKey, int mealType) {
        return repo.getMeals(dateKey, mealType);
    }

    /** 자유 텍스트로 식단 항목 추가 */
    public void add(long dateKey, int mealType, String title) {
        MealPlanEntry e = new MealPlanEntry();
        e.dateKey = dateKey;           // yyyymmdd
        e.mealType = mealType;         // 0=Breakfast,1=Lunch,2=Dinner
        e.title = title == null ? "" : title.trim();
        e.createdAt = System.currentTimeMillis();
        repo.insert(e);
    }

    /** 레시피 제목을 이용해 식단에 바로 추가 (RecipeDetail → MealPlan 연동에서 사용) */
    public void addRecipeToMeal(long dateKey, int mealType, String recipeTitle) {
        add(dateKey, mealType, recipeTitle);
    }

    public void update(MealPlanEntry e, String newTitle) {
        e.title = newTitle == null ? "" : newTitle.trim();
        repo.update(e);
    }

    public void delete(MealPlanEntry e) {
        repo.delete(e);
    }

    // ─────────────────────────────
    // My Meals (저장된 식단 세트)
    // ─────────────────────────────
    /** 전체 My Meals 목록 */
    public LiveData<List<MyMealSet>> myMealSets() {
        return myMealSets;
    }

    /** 검색(부분일치) */
    public LiveData<List<MyMealSet>> searchMyMealSets(String q) {
        return myMealSetDao.search(q == null ? "" : q);
    }

    /** 생성/수정 */
    public void saveMyMealSet(MyMealSet set) {
        new Thread(() -> {
            if (set.id == 0) {
                myMealSetDao.insert(set);
            } else {
                myMealSetDao.update(set);
            }
        }).start();
    }

    /** 삭제 */
    public void deleteMyMealSet(MyMealSet set) {
        new Thread(() -> myMealSetDao.delete(set)).start();
    }
}
