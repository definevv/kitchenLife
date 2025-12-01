package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MealPlanDao {

    /** LiveData 관찰용 */
    @Query("SELECT * FROM meal_plan_entries WHERE dateKey=:key AND mealType=:mealType ORDER BY id ASC")
    LiveData<List<MealPlanEntry>> observeByDay(long key, int mealType);

    /** 동기 로딩 (Repository에서 사용) */
    @Query("SELECT * FROM meal_plan_entries WHERE dateKey=:key AND mealType=:mealType ORDER BY id ASC")
    List<MealPlanEntry> getMeals(long key, int mealType);

    @Query("SELECT * FROM meal_plan_entries " +
            "WHERE dateKey BETWEEN :startKey AND :endKey " +
            "AND recipeId != 0 " +
            "ORDER BY dateKey ASC, mealType ASC, id ASC")
    List<MealPlanEntry> listByDateRangeWithRecipe(long startKey, long endKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MealPlanEntry e);

    @Update
    void update(MealPlanEntry e);

    @Delete
    void delete(MealPlanEntry e);

    @Query("UPDATE meal_plan_entries SET title=:title WHERE id=:id")
    void updateTitle(long id, String title);

    /** 체크박스 상태 저장 */
    @Query("UPDATE meal_plan_entries SET done=:done WHERE id=:id")
    void setDone(long id, boolean done);
}
