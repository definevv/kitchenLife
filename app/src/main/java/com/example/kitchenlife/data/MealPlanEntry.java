package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "meal_plan_entries",
        indices = {
                @Index(value = {"dateKey", "mealType"}),
                @Index("recipeId")
        }
)
public class MealPlanEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 날짜 키: yyyymmdd */
    public long dateKey;

    /** 0=아침, 1=점심, 2=저녁 */
    public int mealType;

    /** Supabase recipes.id (없으면 0) */
    public long recipeId;

    /** 표시용 타이틀(캐시 실패 대비) */
    @NonNull
    public String title = "";

    /** 메모/노트 */
    public String notes;

    /** 생성 시각(ms) */
    public long createdAt;

    /** 먹음 여부(체크박스). 기본값 false(0) */
    @ColumnInfo(defaultValue = "0")
    public boolean done;
}
