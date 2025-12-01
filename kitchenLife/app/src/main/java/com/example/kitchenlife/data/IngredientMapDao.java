package com.example.kitchenlife.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

// IngredientMapDao.java
@Dao
public interface IngredientMapDao {
    @Query("SELECT * FROM ingredient_map WHERE ingredientKey = :key LIMIT 1")
    IngredientMap byKeySync(String key);

    // ★ MapService가 부르는 메서드들 추가
    @Query("SELECT * FROM ingredient_map WHERE name = :name LIMIT 1")
    IngredientMap byNameSync(String name);

    // rawName을 LIKE로도 찾고 싶을 때(간단 버전)
    @Query("SELECT * FROM ingredient_map WHERE name LIKE :raw LIMIT 1")
    IngredientMap byRawSync(String raw);
}

