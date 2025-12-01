package com.example.kitchenlife.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

@Entity(
        tableName = "recipes",
        indices = {
                @Index(value = {"title"}),
                @Index(value = {"external_code"}, unique = false)
        }
)
public class Recipe {
    @PrimaryKey(autoGenerate = true) public long id;

    public String external_code;   // 외부코드(옵션)
    @NonNull public String title = "";
    public String summary;
    public String category;
    public String cuisine;
    public String difficulty;
    public Integer servings;
    public Integer time_minutes;
    public String thumbnail_url;
    public long createdAt;         // System.currentTimeMillis()
}
