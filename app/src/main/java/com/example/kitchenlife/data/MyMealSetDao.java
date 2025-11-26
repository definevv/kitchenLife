package com.example.kitchenlife.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface MyMealSetDao {

    @Query("SELECT * FROM my_meal_set ORDER BY name COLLATE NOCASE")
    LiveData<List<MyMealSet>> all();

    @Query("SELECT * FROM my_meal_set WHERE name LIKE '%' || :q || '%' ORDER BY name COLLATE NOCASE")
    LiveData<List<MyMealSet>> search(String q);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MyMealSet set);

    @Update
    int update(MyMealSet set);

    @Delete
    int delete(MyMealSet set);
}
