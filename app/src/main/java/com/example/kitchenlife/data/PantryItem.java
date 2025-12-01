package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pantry_items",
        indices = {
                @Index(value = {"ingredientKey", "unit"}), // 레시피-팬트리 매칭 우선 키
                @Index("name")                              // 이름 기반 검색/정렬
        }
)
public class PantryItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 레시피-팬트리 매칭용 키(가능하면 사용) */
    @NonNull
    @ColumnInfo(defaultValue = "")
    public String ingredientKey = "";

    /** 표시용/보조 매칭용 이름 */
    @NonNull
    @ColumnInfo(defaultValue = "")
    public String name = "";

    /** 보유 수량 */
    @ColumnInfo(defaultValue = "0")
    public double quantity;

    /** g, ml, 개 등 (nullable 허용) */
    @Nullable
    public String unit;

    /** 유통기한(ms) (옵션) */
    @Nullable
    public Long expireAt;

    /** 갱신 시각(ms) */
    @ColumnInfo(defaultValue = "0")
    public long updatedAt;

    // ---------------------------
    // ★ UI 전용 체크박스 상태
    // ---------------------------
    @Ignore
    public boolean checked = false;

    /** 기본 생성자(필수) */
    public PantryItem() { }

    /** UI/편의용 생성자 */
    @Ignore
    public PantryItem(long id,
                      @NonNull String ingredientKey,
                      @NonNull String name,
                      double quantity,
                      @Nullable String unit,
                      @Nullable Long expireAt,
                      long updatedAt) {
        this.id = id;
        this.ingredientKey = ingredientKey;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.expireAt = expireAt;
        this.updatedAt = updatedAt;
    }
}
