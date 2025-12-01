package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MyMealsDao {

    @Query("SELECT * FROM my_meal_sets ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<MyMealSet>> observeAll();

    @Query("SELECT * FROM my_meal_sets WHERE name LIKE :q ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<MyMealSet>> search(String q);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(MyMealSet set);

    @Delete
    void delete(MyMealSet set);
}
