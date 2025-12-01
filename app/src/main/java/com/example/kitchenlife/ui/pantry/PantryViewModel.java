package com.example.kitchenlife.ui.pantry;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.IngredientMapDao;
import com.example.kitchenlife.data.MapService;
import com.example.kitchenlife.data.PantryDao;
import com.example.kitchenlife.data.PantryItem;
import com.example.kitchenlife.data.RecipeIngredient;
import com.example.kitchenlife.data.ShoppingDao;
import com.example.kitchenlife.data.ShoppingItem;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 팬트리 + 쇼핑 리스트 + 레시피 연동 */
public class PantryViewModel extends AndroidViewModel {

    private final PantryDao pantryDao;
    private final ShoppingDao shoppingDao;
    private final IngredientMapDao mapDao;
    private final MapService mapService;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public PantryViewModel(@NonNull Application app) {
        super(app);
        AppDatabase db = AppDatabase.get(app);
        pantryDao   = db.pantryDao();
        shoppingDao = db.shoppingDao();
        mapDao      = db.ingredientMapDao();
        // MapService는 DAO를 받도록 정의하세요(컨텍스트 아님)
        // public MapService(IngredientMapDao dao){ ... }
        mapService  = new MapService(mapDao);
    }

    // ---------- Pantry ----------
    public LiveData<List<PantryItem>> items() {
        // DAO: @Query("SELECT * FROM pantry_items ORDER BY name")
        return pantryDao.observeAll();
    }

    public LiveData<List<PantryItem>> search(@Nullable String q) {
        // DAO: @Query("SELECT * FROM pantry_items WHERE name LIKE :q ORDER BY name")
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        return pantryDao.search(like);
    }

    /** 키/이름/단위 기준으로 수량 증감(+/-). 없으면 신규 insert. */
    public void addOrUpdate(String key, String name, String unit, double delta, @Nullable Long expireAt){
        io.execute(() -> {
            PantryItem exist = pantryDao.byKeySync(key); // DAO: 단일 동기 조회
            long now = System.currentTimeMillis();
            if (exist == null) {
                PantryItem p = new PantryItem();
                p.ingredientKey = key;
                p.name = name;
                p.unit = unit;
                p.quantity = Math.max(0d, delta); // qty → quantity
                p.expireAt = expireAt;
                p.updatedAt = now;
                pantryDao.insert(p); // @Insert(onConflict = REPLACE)
            } else {
                pantryDao.addQty(key, delta, now); // @Query UPDATE quantity = quantity + :delta
            }
        });
    }

    public void deletePantry(PantryItem p){
        io.execute(() -> pantryDao.delete(p));
    }

    // ---------- Shopping ----------
    public LiveData<List<ShoppingItem>> shopping() {
        // DAO: @Query("SELECT * FROM shopping_items ORDER BY checked ASC, name ASC")
        return shoppingDao.observeAll();
    }

    /** 체크/구매수량 변경 */
    public void setShoppingChecked(long id, boolean checked, double bought){
        io.execute(() ->
                shoppingDao.setChecked(id, checked, bought, System.currentTimeMillis()));
    }

    /**
     * 체크된 쇼핑 항목을 팬트리에 반영하고 삭제
     * - ShoppingDao.checkedSync(): @Query("SELECT * FROM shopping_items WHERE checked=1")
     * - 이후 deleteAllChecked()
     */
    public void clearCheckedToPantry(){
        io.execute(() -> {
            long now = System.currentTimeMillis();
            List<ShoppingItem> checked = shoppingDao.checkedSync();
            if (checked != null) {
                for (ShoppingItem s : checked) {
                    double toAdd = Math.max(0d, s.boughtQty); // 구매한 만큼만 반영
                    if (toAdd > 0d) {
                        PantryItem exist = pantryDao.byKeySync(s.ingredientKey);
                        if (exist == null) {
                            PantryItem p = new PantryItem();
                            p.ingredientKey = s.ingredientKey;
                            p.name = s.name;
                            p.unit = s.unit;
                            p.quantity = toAdd;
                            p.expireAt = null;
                            p.updatedAt = now;
                            pantryDao.insert(p);
                        } else {
                            pantryDao.addQty(s.ingredientKey, toAdd, now);
                        }
                    }
                }
            }
            shoppingDao.deleteAllChecked();
        });
    }

    /** 내부용: 쇼핑 아이템 upsert(필요량 누적) */
    private void upsertShoppingSync(String key, String name, String unit, double need){
        long now = System.currentTimeMillis();
        ShoppingItem s = shoppingDao.byKeySync(key);
        if (s == null) {
            s = new ShoppingItem();
            s.ingredientKey = key;
            s.name = name;
            s.unit = unit;
            s.neededQty = Math.max(0d, need);
            s.boughtQty = 0d;
            s.checked = false;
            s.updatedAt = now;
            shoppingDao.insert(s);
        } else {
            s.neededQty = Math.max(0d, s.neededQty + need);
            s.updatedAt = now;
            shoppingDao.update(s);
        }
    }

    // ---------- Recipe 연동 ----------
    /** 레시피 재료를 읽어 '부족분'만 쇼핑리스트로 생성 */
    public void generateShoppingFromRecipe(List<RecipeIngredient> ings){
        io.execute(() -> {
            if (ings == null) return;
            for (RecipeIngredient ri : ings) {
                // MapService.Norm: key, name, unit, qty 로 표준화해주는 유틸
                MapService.Norm n = mapService.normalize(ri.ingredient_name, ri.quantity_text);

                double have = 0d;
                PantryItem p = pantryDao.byKeySync(n.key);
                if (p != null) {
                    // 단위가 다르면 단위 변환 규칙이 필요(여기선 동일 단위일 때만 반영)
                    if (p.unit != null && p.unit.equalsIgnoreCase(n.unit)) {
                        have = p.quantity; // qty → quantity
                    }
                }
                double shortage = Math.max(0d, n.qty - have);
                if (shortage > 0d) {
                    upsertShoppingSync(n.key, n.name, n.unit, shortage);
                }
            }
        });
    }

    /** 요리 완료 시 팬트리에서 차감(부족하면 음수로 떨어지지 않게 DAO에서 가드해도 됨) */
    public void consumeForRecipe(List<RecipeIngredient> ings){
        io.execute(() -> {
            if (ings == null) return;
            long now = System.currentTimeMillis();
            for (RecipeIngredient ri : ings) {
                MapService.Norm n = mapService.normalize(ri.ingredient_name, ri.quantity_text);
                pantryDao.addQty(n.key, -n.qty, now);
            }
        });
    }
}
