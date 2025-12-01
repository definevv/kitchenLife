package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ShoppingDao {

    /* ---------- 조회 ---------- */

    /** 최신 수정순 전체 목록 (동기) */
    @Query("SELECT * FROM shopping_items ORDER BY updatedAt DESC")
    List<ShoppingItem> loadAll();

    /** 최신 수정순 전체 관찰 (LiveData) */
    @Query("SELECT * FROM shopping_items ORDER BY updatedAt DESC")
    LiveData<List<ShoppingItem>> observeAll();

    /** 이름+단위로 1건 조회 (단위가 NULL일 때도 매칭되도록) */
    @Query("SELECT * FROM shopping_items " +
            "WHERE name = :name AND ((unit IS NULL AND :unit IS NULL) OR unit = :unit) " +
            "LIMIT 1")
    ShoppingItem byNameUnitSync(String name, String unit);

    /** 키(ingredientKey)로 1건 조회 */
    @Query("SELECT * FROM shopping_items WHERE ingredientKey = :key LIMIT 1")
    ShoppingItem byKeySync(String key);


    /* ---------- 쓰기 기본 ---------- */

    /** 충돌 시 무시(IGNORE)로 insert */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(ShoppingItem item);

    /** 전체 필드 업데이트 */
    @Update
    int update(ShoppingItem item);


    /* ---------- 체크/삭제 유틸 ---------- */

    /** 체크 상태와 구매 수량, 갱신시각을 함께 변경 */
    @Query("UPDATE shopping_items " +
            "SET checked = :checked, bought_qty = :bought, updatedAt = :updatedAt " +
            "WHERE id = :id")
    int setChecked(long id, boolean checked, double bought, long updatedAt);

    /** 단건 체크 처리(구매수량은 건드리지 않음) */
    @Query("UPDATE shopping_items SET checked = 1, updatedAt = :updatedAt WHERE id = :id")
    int checkedSync(long id, long updatedAt);

    /** 체크된 항목 모두 삭제 */
    @Query("DELETE FROM shopping_items WHERE checked = 1")
    int deleteAllChecked();

    /** 체크된 항목 전부 조회(팬트리 이동용) */
    @Query("SELECT * FROM shopping_items WHERE checked = 1 ORDER BY updatedAt DESC")
    List<ShoppingItem> checkedSync();

    /** 단위 업데이트 */
    @Query("UPDATE shopping_items SET unit = :unit, updatedAt = :updatedAt WHERE id = :id")
    void updateBuyUnit(long id, String unit, long updatedAt);

    /** 쇼핑 리스트 삭제 */
    @Query("DELETE FROM shopping_items WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Query("UPDATE shopping_items SET bought_qty=:qty, unit=:unit, updatedAt=:updatedAt WHERE id=:id")
    void updateBoughtAndUnit(long id, double qty, String unit, long updatedAt);

    /* ---------- 이름+단위로 가산 업서트 ---------- */

    /**
     * 주어진 리스트를 (name, unit) 기준으로 업서트하면서
     * needed_qty 를 가산한다. 단위가 비어있지 않으면 최신 단위로 덮어씀.
     */
    @Transaction
    default void upsertAddAll(List<ShoppingItem> list) {
        if (list == null) return;
        long now = System.currentTimeMillis();

        for (ShoppingItem in : list) {
            if (in == null) continue;

            String name = (in.name == null) ? "" : in.name.trim();
            if (name.isEmpty()) continue;

            String unit = (in.unit == null) ? null : in.unit.trim();
            ShoppingItem exist = byNameUnitSync(name, unit);

            if (exist == null) {
                in.name = name;
                in.unit = (unit == null || unit.isEmpty()) ? null : unit;
                in.updatedAt = now;
                // neededQty / boughtQty / checked 값은 호출부에서 준비되어 들어온다고 가정
                insert(in);
            } else {
                double cur = Math.max(0d, exist.neededQty);
                double add = Math.max(0d, in.neededQty);
                exist.neededQty = cur + add;

                // 단위가 주어졌다면 최신 단위로 유지
                if (unit != null && !unit.isEmpty()) exist.unit = unit;
                exist.name = name;
                exist.updatedAt = now;

                update(exist);
            }
        }
    }
}
