package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PantryDao {

    /* ---------- 조회 ---------- */

    /** 팬트리 전체 관찰 */
    @Query("SELECT * FROM pantry_items ORDER BY name")
    LiveData<List<PantryItem>> observeAll();

    /** 이름 like 검색 관찰 */
    @Query("SELECT * FROM pantry_items WHERE name LIKE :like ORDER BY name")
    LiveData<List<PantryItem>> search(String like);

    /** 키로 단건 동기 조회 */
    @Query("SELECT * FROM pantry_items WHERE ingredientKey = :key LIMIT 1")
    PantryItem byKeySync(String key);

    /** 이름+단위로 단건 동기 조회 (키가 비어있을 때 보조 매칭용) */
    @Query("SELECT * FROM pantry_items WHERE name = :name AND " +
            "((:unit IS NULL AND unit IS NULL) OR unit = :unit) " +
            "LIMIT 1")
    PantryItem byNameAndUnitSync(String name, String unit);

    /** id들로 일괄 삭제 */
    @Query("DELETE FROM pantry_items WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);


    /* ---------- 쓰기 기본 ---------- */

    /** 신규 생성(동일 key 충돌 시 교체) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PantryItem item);

    /** 단건 업데이트 */
    @Update
    void update(PantryItem item);

    /** 삭제 */
    @Delete
    void delete(PantryItem item);

    /** 키로 조회(동일 기능 alias) */
    @Query("SELECT * FROM pantry_items WHERE ingredientKey = :key LIMIT 1")
    PantryItem findByKey(String key);

    /** 수량 가산(+/-) 및 갱신시각 갱신 (키 기준) */
    @Query("UPDATE pantry_items SET quantity = quantity + :delta, updatedAt = :updatedAt " +
            "WHERE ingredientKey = :key")
    void addQty(String key, double delta, long updatedAt);


    /* ---------- 업서트/증감 유틸 ---------- */

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
            p.ingredientKey = key.trim();
            p.name = (name == null) ? "" : name.trim();
            p.unit = (unit == null || unit.trim().isEmpty()) ? null : unit.trim();
            p.quantity = Math.max(0d, delta);
            p.updatedAt = now;
            insert(p);
        } else {
            addQty(key, delta, now);
        }
    }

    /** 여러 항목을 한 번에 가산 업서트 (in.quantity 를 가산 값으로 간주) */
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

    /* ---------- 소비/복구(레시피 연동) 유틸 ---------- */

    /** 내부: 키가 우선, 없으면 이름+단위로 매칭 */
    @Transaction
    default PantryItem findForMatch(String key, String name, String unit) {
        PantryItem found = null;
        if (key != null && !key.trim().isEmpty()) {
            found = byKeySync(key.trim());
            if (found != null) return found;
        }
        if (name != null && !name.trim().isEmpty()) {
            found = byNameAndUnitSync(name.trim(), (unit == null || unit.trim().isEmpty()) ? null : unit.trim());
        }
        return found;
    }

    /**
     * 재고 소비(감소). 남은 수량이 0 이하이면 삭제.
     * amount <= 0 이면 아무 것도 하지 않음.
     * 단위 환산은 하지 않으며, 매칭 실패 시 스킵.
     */
    @Transaction
    default void consume(String key, String name, String unit, double amount) {
        if (amount <= 0) return;
        PantryItem item = findForMatch(key, name, unit);
        if (item == null) return;

        double remain = item.quantity - amount;
        if (remain > 1e-6) {
            item.quantity = remain;
            item.updatedAt = System.currentTimeMillis();
            update(item);
        } else {
            delete(item);
        }
    }

    /**
     * 재고 복구(증가). 기존 항목 없으면 새로 생성.
     * amount <= 0 이면 아무 것도 하지 않음.
     */
    @Transaction
    default void restore(String key, String name, String unit, double amount) {
        if (amount <= 0) return;
        PantryItem item = findForMatch(key, name, unit);
        long now = System.currentTimeMillis();

        if (item == null) {
            // 키가 있으면 그대로, 없으면 이름/단위 기반 임시 키 생성도 가능(지금은 name으로만 생성 X, 키 필수 가정)
            // key 우선 설계이므로 key가 없으면 신규 생성 스킵하도록 하려면 아래 분기 조정
            PantryItem p = new PantryItem();
            p.ingredientKey = (key == null) ? "" : key.trim();
            p.name = (name == null) ? "" : name.trim();
            p.unit = (unit == null || unit.trim().isEmpty()) ? null : unit.trim();
            p.quantity = amount;
            p.updatedAt = now;
            insert(p);
        } else {
            item.quantity += amount;
            item.updatedAt = now;
            update(item);
        }
    }
}
