package com.example.kitchenlife;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.kitchenlife.ui.mealplan.HandsFreeCookingActivity;
import com.example.kitchenlife.ui.pantry.PantryActivity;

import com.example.kitchenlife.ui.RecipesActivity; // ✅ 새 버전 명시
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.header_gray));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.header_gray));

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setCheckedItem(R.id.nav_home);

        CardView pantryCard = findViewById(R.id.pantry_quick_action);
        CardView shoppingCard = findViewById(R.id.shopping_quick_action);

        pantryCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PantryActivity.class)));

        shoppingCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ShoppingActivity.class)));
    }

    // Overflow 메뉴 비활성화
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;

        } else if (id == R.id.nav_meal_plan) {
            startActivity(new Intent(this, MealPlanActivity.class));

        } else if (id == R.id.nav_shopping) {
            startActivity(new Intent(this, ShoppingActivity.class));

        } else if (id == R.id.nav_recipes) {
            // ✅ 새 버전으로 강제 지정
            Intent i = new Intent(this, RecipesActivity.class);
            startActivity(i);

        } else if (id == R.id.nav_pantry) {
            startActivity(new Intent(this, PantryActivity.class));

        } else if (id == R.id.nav_stats) {
            startActivity(new Intent(this, StatsActivity.class));
        } else if (id == R.id.nav_handsfree) {
            Intent intent = new Intent(MainActivity.this, HandsFreeCookingActivity.class);
            startActivity(intent);
        }


        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}