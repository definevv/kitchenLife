package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "shopping_items")
public class ShoppingItem {
    @PrimaryKey(autoGenerate = true) public long id;

    @NonNull public String ingredientKey = "";
    @NonNull public String name = "";

    @Nullable public String unit;                 // g, 컵 등 (없으면 null)

    @ColumnInfo(name = "needed_qty")
    public double neededQty;                      // 필요한 수량

    @ColumnInfo(name = "bought_qty")
    public double boughtQty;                      // 구매(진행) 수량

    public boolean checked;                       // 체크 여부
    public long updatedAt;                        // 갱신 시각(ms)
}
