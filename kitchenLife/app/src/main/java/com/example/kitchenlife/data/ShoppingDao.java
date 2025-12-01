package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Dao
public interface ShoppingDao {

    /* ---------- 조회 ---------- */

    /** 동기: updatedAt desc 정렬 전체 */
    @Query("SELECT * FROM shopping_items ORDER BY updatedAt DESC")
    List<ShoppingItem> loadAll();

    /** 관찰: updatedAt desc 정렬 전체 */
    @Query("SELECT * FROM shopping_items ORDER BY updatedAt DESC")
    LiveData<List<ShoppingItem>> observeAll();

    /** 동기: 정렬/보존 로직용 전체(정렬 불문) */
    @Query("SELECT * FROM shopping_items")
    List<ShoppingItem> allSync();

    @Query("SELECT * FROM shopping_items " +
            "WHERE name = :name AND ((unit IS NULL AND :unit IS NULL) OR unit = :unit) " +
            "LIMIT 1")
    ShoppingItem byNameUnitSync(String name, String unit);

    @Query("SELECT * FROM shopping_items WHERE ingredientKey = :key LIMIT 1")
    ShoppingItem byKeySync(String key);


    /* ---------- 쓰기 기본 ---------- */

    @Query("SELECT * FROM shopping_items WHERE ingredientKey = :key AND checked = 0 LIMIT 1")
    ShoppingItem findActiveByKey(String key);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(ShoppingItem item);

    @Update
    int update(ShoppingItem item);

    @Query("DELETE FROM shopping_items")
    void clearAll();


    /* ---------- 체크/삭제 유틸 ---------- */

    // 위치 안 바뀌게 updatedAt/boughtQty 건드리지 않음
    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    int setChecked(long id, boolean checked);

    // 과거 호출부 호환용(무시 파라미터)
    @Deprecated
    default int setChecked(long id, boolean checked, double ignoredBought, long ignoredUpdatedAt) {
        return setChecked(id, checked);
    }

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    int deleteAllChecked();

    @Query("SELECT * FROM shopping_items WHERE checked = 1 ORDER BY updatedAt DESC")
    List<ShoppingItem> checkedSync();

    @Query("UPDATE shopping_items SET unit = :unit, updatedAt = :updatedAt WHERE id = :id")
    void updateBuyUnit(long id, String unit, long updatedAt);

    @Query("DELETE FROM shopping_items WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Query("UPDATE shopping_items SET bought_qty=:qty, unit=:unit, updatedAt=:updatedAt WHERE id=:id")
    void updateBoughtAndUnit(long id, double qty, String unit, long updatedAt);


    /* ---------- 이름+단위 가산 업서트 (수동 추가 등) ---------- */

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
                insert(in);
            } else {
                double cur = Math.max(0d, exist.neededQty);
                double add = Math.max(0d, in.neededQty);
                exist.neededQty = cur + add;

                if (unit != null && !unit.isEmpty()) exist.unit = unit;
                exist.name = name;
                exist.updatedAt = now;

                update(exist);
            }
        }
    }


    /* ---------- 통합 재계산 결과 반영 (entryId 없이 동작) ---------- */

    /**
     * 통합 재계산 결과(newList)로 테이블을 교체하되,
     * 같은 ingredientKey에 대해 기존 진행상황(boughtQty, checked)을 최대한 보존한다.
     */
    @Transaction
    default void replaceAllPreservingProgress(List<ShoppingItem> newList) {
        // 1) 기존 스냅샷을 키 맵으로 만들기
        List<ShoppingItem> old = allSync();
        Map<String, ShoppingItem> oldMap = new HashMap<>();
        for (ShoppingItem o : old) {
            if (o == null) continue;
            oldMap.put(o.ingredientKey, o);
        }

        // 2) 전체 삭제 후 새 목록 삽입
        clearAll();
        long now = System.currentTimeMillis();

        if (newList == null) return;

        for (ShoppingItem in : newList) {
            if (in == null) continue;
            ShoppingItem prior = oldMap.get(in.ingredientKey);

            if (prior != null) {
                // 기존 진행상황 보존 로직
                // - 산 만큼은 유지 (새 필요량보다 큰 경우 클램프)
                in.boughtQty = Math.min(Math.max(0d, prior.boughtQty), Math.max(0d, in.neededQty));
                // - 예전에 이미 충분히 샀던 항목은 체크 유지, 아니면 해제
                in.checked = prior.checked && (prior.boughtQty >= in.neededQty - 1e-6);
                // 단위/이름은 새 계산값을 우선, 필요시 prior.unit/name을 fallback 가능
            } else {
                in.boughtQty = 0d;
                in.checked = false;
            }

            in.updatedAt = now;
            insert(in);
        }
    }
}
