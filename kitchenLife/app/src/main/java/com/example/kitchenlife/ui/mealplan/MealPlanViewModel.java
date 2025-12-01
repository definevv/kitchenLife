package com.example.kitchenlife.ui.mealplan;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.MealPlanEntry;
import com.example.kitchenlife.data.MealPlanRepository;
import com.example.kitchenlife.data.MyMealSet;
import com.example.kitchenlife.data.MyMealsDao;

import java.util.List;

/**
 * MealPlan 화면 ViewModel
 * - MealPlan(일자/끼니별) 데이터는 Repository를 통해 접근
 * - MyMeals(사용자 정의 세트)는 기존 Dao를 그대로 사용
 *
 * 변경점:
 *  - setDone(...)에서 단순 done 플래그만 저장하지 않고,
 *    repo.toggleDoneAndSyncPantry(...)로 위임하여
 *    "체크 ON → 팬트리 차감 / 체크 OFF → 복구"까지 한 번에 처리.
 */
public class MealPlanViewModel extends AndroidViewModel {

    // MealPlan 전용 리포지토리
    private final MealPlanRepository repo;

    // MyMeals(사용자 세트)용 Dao (Activity에서 사용 중)
    private final MyMealsDao myMealsDao;

    public void addWithRecipeAndSendToShopping(long dateKey, int mealType, long recipeId, String title) {
        repo.addWithRecipeAndSendToShopping(dateKey, mealType, recipeId, title);
    }

    public MealPlanViewModel(@NonNull Application app) {
        super(app);
        repo = new MealPlanRepository(app);
        myMealsDao = AppDatabase.get(app).myMealsDao();
    }

    /* ===================== MealPlan (Repository 경유) ===================== */

    /** 선택한 날짜/끼니의 식단 목록 관찰 */
    public LiveData<List<MealPlanEntry>> meals(long dateKey, int mealType) {
        return repo.observeMeals(dateKey, mealType);
    }

    /** 식단 추가 */
    public void add(long dateKey, int mealType, String title) {
        MealPlanEntry e = new MealPlanEntry();
        e.dateKey   = dateKey;
        e.mealType  = mealType;
        e.title     = title == null ? "" : title.trim();
        e.createdAt = System.currentTimeMillis();
        repo.insertAsync(e);
    }

    /** 식단 제목 수정 */
    public void update(MealPlanEntry e, String newTitle) {
        if (e == null) return;
        e.title = (newTitle == null ? "" : newTitle.trim());
        repo.updateAsync(e);
    }

    /** 식단 삭제 */
    public void delete(MealPlanEntry e) {
        if (e == null) return;
        repo.deleteAsync(e);
    }

    /**
     * 섭취여부(완료) 체크 저장 + 팬트리 증감까지 일괄 처리.
     * checked == true  → 레시피 재료만큼 팬트리 차감
     * checked == false → 레시피 재료만큼 팬트리 복구
     *
     * Repository 내부에서 트랜잭션으로:
     *   - 팬트리 증감/삭제/생성
     *   - meal_plan_entries.done 업데이트
     * 를 한 번에 처리해야 함.
     */
    public void setDone(MealPlanEntry e, boolean checked) {
        if (e == null) return;
        // UI 즉시 반응이 필요하면 메모리 상태도 먼저 변경
        e.done = checked;
        // 핵심: 팬트리까지 동기화
        repo.toggleDoneAndSyncPantry(e, checked);
    }

    /* ===================== MyMeals(사용자 세트) ===================== */

    /** 전체 세트 관찰 */
    public LiveData<List<MyMealSet>> myMealSets() {
        return myMealsDao.observeAll();
    }

    /** 제목 부분검색 */
    public LiveData<List<MyMealSet>> searchMyMealSets(String q) {
        String query = (q == null || q.trim().isEmpty()) ? "%" : "%" + q.trim() + "%";
        return myMealsDao.search(query);
    }

    /** 세트 저장/업서트 */
    public void saveMyMealSet(MyMealSet s) {
        AppDatabase.DB_EXECUTOR.execute(() -> myMealsDao.upsert(s));
    }

    /** 세트 삭제 */
    public void deleteMyMealSet(MyMealSet s) {
        AppDatabase.DB_EXECUTOR.execute(() -> myMealsDao.delete(s));
    }
}
