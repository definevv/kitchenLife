package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface PantryDao {

    /* ---------- 조회 ---------- */

    /** 팬트리 전체 관찰 */
    @Query("SELECT * FROM pantry_items ORDER BY name")
    LiveData<List<PantryItem>> observeAll();


    @Query("DELETE FROM pantry_items WHERE id IN (:ids)")   // ← 테이블명/PK 컬럼명 확인!
    void deleteByIds(List<Long> ids);

    /** 이름 like 검색 관찰 */
    @Query("SELECT * FROM pantry_items WHERE name LIKE :like ORDER BY name")
    LiveData<List<PantryItem>> search(String like);

    /** 키로 단건 동기 조회 */
    @Query("SELECT * FROM pantry_items WHERE ingredientKey = :key LIMIT 1")
    PantryItem byKeySync(String key);


    /* ---------- 쓰기 기본 ---------- */

    /** 신규 생성(동일 key 충돌 시 교체) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PantryItem item);

    /** 수량 증감(+/-) 및 갱신시각 갱신 */
    @Query("UPDATE pantry_items SET quantity = quantity + :delta, updatedAt = :updatedAt " +
            "WHERE ingredientKey = :key")
    void addQty(String key, double delta, long updatedAt);

    /** 삭제 */
    @Delete
    void delete(PantryItem item);


    /* ---------- 업서트 유틸(체크된 쇼핑 항목 → 팬트리) ---------- */

    /**
     * (key, name, unit) 기준으로 팬트리에 수량을 가산 업서트합니다.
     * - 기존 항목이 없으면 새로 만들고 quantity=delta 로 저장
     * - 기존 항목이 있으면 quantity += delta
     * - unit 이 비어있지 않다면 새로 생성 시 저장, 기존 항목은 그대로 둠(필요시 확장)
     */
    @Transaction
    default void upsertIncrease(String key, String name, String unit, double delta) {
        if (key == null || key.trim().isEmpty()) return;
        long now = System.currentTimeMillis();

        PantryItem exist = byKeySync(key);
        if (exist == null) {
            PantryItem p = new PantryItem();
            p.ingredientKey = key;
            p.name = (name == null) ? "" : name.trim();
            p.unit = (unit == null || unit.trim().isEmpty()) ? null : unit.trim();
            p.quantity = Math.max(0d, delta);
            p.updatedAt = now;
            insert(p);
        } else {
            addQty(key, delta, now);
        }
    }

    /**
     * 여러 항목을 한 번에 가산 업서트.
     * (호출부에서 PantryItem에 quantity=가산할 값, name/unit/key 를 채워 넣어 전달)
     */
    @Transaction
    default void upsertIncreaseAll(List<PantryItem> list) {
        if (list == null || list.isEmpty()) return;
        for (PantryItem in : list) {
            if (in == null) continue;
            String key = in.ingredientKey;
            String name = in.name;
            String unit = in.unit;
            double delta = in.quantity; // 전달된 quantity 를 가산 값으로 사용
            upsertIncrease(key, name, unit, delta);
        }
    }
}
