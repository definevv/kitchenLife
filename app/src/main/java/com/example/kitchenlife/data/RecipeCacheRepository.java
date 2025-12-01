package com.example.kitchenlife.data;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecipeCacheRepository {
    private final RecipeCacheDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public RecipeCacheRepository(Context ctx) {
        dao = AppDatabase.get(ctx).recipeCacheDao(); // ★ 이제 정상 인식
    }

    public void upsert(RecipeCache e) { io.execute(() -> dao.upsert(e)); }

    public RecipeCache getSync(long id) { return dao.get(id); } // 필요 시 호출

    public void pruneBefore(long ms) { io.execute(() -> dao.deleteOlderThan(ms)); }
}
