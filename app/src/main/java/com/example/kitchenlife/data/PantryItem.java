package com.example.kitchenlife.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Ignore;

@Entity(tableName = "pantry_items")
public class PantryItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String ingredientKey = "";  // 레시피-팬트리 매칭용 키

    @NonNull
    public String name = "";

    public double quantity;            // 보유 수량

    public String unit;

    @Nullable
    public Long expireAt;              // 유통기한 (옵션)

    public long updatedAt;

    // ---------------------------
    // ★ UI 전용 체크박스 상태
    // ---------------------------
    @Ignore
    public boolean checked = false;

    // 기본 생성자
    public PantryItem() {}

    // UI 처리용 보조 생성자
    @Ignore
    public PantryItem(long id, @NonNull String ingredientKey, @NonNull String name,
                      double quantity, String unit, @Nullable Long expireAt, long updatedAt) {
        this.id = id;
        this.ingredientKey = ingredientKey;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.expireAt = expireAt;
        this.updatedAt = updatedAt;
        this.checked = false;
    }
}
