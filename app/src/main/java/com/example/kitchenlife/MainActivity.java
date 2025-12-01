package com.example.kitchenlife;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.MealPlanDao;
import com.example.kitchenlife.data.MealPlanEntry;
import com.example.kitchenlife.data.PantryDao;
import com.example.kitchenlife.data.ShoppingDao;
import com.example.kitchenlife.data.ShoppingItem;
import com.example.kitchenlife.ui.RecipesActivity;
import com.example.kitchenlife.ui.pantry.PantryActivity;
import com.google.android.material.navigation.NavigationView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 홈(단일 파일) + 드로어 네비게이션
 * - 상단 "today's menu" 카드: 오늘 아침/점심/저녁 타이틀 요약(실데이터)
 * - 중간 Pantry 카드: 보유요약(실데이터)
 * - 하단 To-Do 카드: 쇼핑 미체크 1개 요약(실데이터)
 * - Pantry/Shopping 퀵카드 클릭 → 각 액티비티 이동
 *
 * 레이아웃에 ID가 거의 없어, 뷰 계층을 안전하게 타고 들어가서 TextView를 찾아 바인딩한다.
 * (찾지 못하면 해당 섹션은 조용히 스킵)
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;

    // ---- DAOs ----
    private MealPlanDao mealDao;
    private PantryDao pantryDao;
    private ShoppingDao shoppingDao;

    // ---- 동적 바인딩될 텍스트 뷰(없으면 null) ----
    @Nullable private TextView tvMenuTitle;
    @Nullable private TextView tvMenuSubtitle;
    @Nullable private TextView tvPantrySubtitle;
    @Nullable private TextView tvTodoName;
    @Nullable private TextView tvTodoSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 시스템바 색상
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.header_gray));

        // Toolbar + Drawer
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            drawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.header_gray));
        }

        NavigationView nav = findViewById(R.id.nav_view);
        if (nav != null) {
            nav.setNavigationItemSelectedListener(this);
            nav.setCheckedItem(R.id.nav_home);
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );
        if (drawerLayout != null) drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 퀵 카드 이동(이 두개는 레이아웃에 id가 있음)
        CardView pantryCard = findViewById(R.id.pantry_quick_action);
        CardView shoppingCard = findViewById(R.id.shopping_quick_action);
        if (pantryCard != null) pantryCard.setOnClickListener(v -> startActivity(new Intent(this, PantryActivity.class)));
        if (shoppingCard != null) shoppingCard.setOnClickListener(v -> startActivity(new Intent(this, ShoppingActivity.class)));

        // DB/DAO
        AppDatabase db = AppDatabase.get(this);
        mealDao     = db.mealPlanDao();
        pantryDao   = db.pantryDao();
        shoppingDao = db.shoppingDao();

        // 레이아웃 위계에서 텍스트뷰들 찾아오기
        bindViewsFromHierarchy();

        // 라이브데이터 연결
        bindTodayMenuLive();
        bindPantrySummaryLive();
        bindTodoLive();
    }

    // Overflow 메뉴 비활성화
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) { return false; }

    @Override
    public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else if (id == R.id.nav_meal_plan) {
            startActivity(new Intent(this, MealPlanActivity.class));
        } else if (id == R.id.nav_shopping) {
            startActivity(new Intent(this, ShoppingActivity.class));
        } else if (id == R.id.nav_recipes) {
            startActivity(new Intent(this, RecipesActivity.class));
        } else if (id == R.id.nav_pantry) {
            startActivity(new Intent(this, PantryActivity.class));
        } else if (id == R.id.nav_stats) {
            startActivity(new Intent(this, StatsActivity.class));
        }
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /* =========================================================
       1) 뷰 계층 탐색으로 텍스트뷰 바인딩
       ========================================================= */
    private void bindViewsFromHierarchy() {
        try {
            // DrawerLayout(루트) -> LinearLayout(컨텐츠) -> ScrollView -> LinearLayout(content)
            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            if (drawer == null) return;

            ViewGroup contentRoot = null;
            // child(0) = LinearLayout(main), child(1) = NavigationView
            View child0 = drawer.getChildAt(0);
            if (child0 instanceof ViewGroup) {
                ViewGroup main = (ViewGroup) child0;
                // main child(1) = ScrollView
                if (main.getChildCount() >= 2 && main.getChildAt(1) instanceof ScrollView) {
                    ScrollView sv = (ScrollView) main.getChildAt(1);
                    if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof ViewGroup) {
                        contentRoot = (ViewGroup) sv.getChildAt(0);
                    }
                }
            }
            if (contentRoot == null) return;

            // ---- 순서 기반(네가 준 XML과 동일한 순서 가정) ----
            // 0: 오늘의 메뉴 카드, 1: 퀵카드 row, 2: "Pantry" 타이틀, 3: 팬트리 카드, 4: "To-Do", 5: 투두 카드
            // (중간 구조가 달라도 try/catch로 안전하게 넘어감)

            // 0) 오늘의 메뉴 카드 → subtitle(TextView)
            try {
                View v0 = contentRoot.getChildAt(0);
                if (v0 instanceof CardView) {
                    ViewGroup card = (ViewGroup) ((CardView) v0).getChildAt(0); // LinearLayout(h)
                    if (card.getChildCount() >= 2 && card.getChildAt(1) instanceof ViewGroup) {
                        ViewGroup right = (ViewGroup) card.getChildAt(1); // LinearLayout(v)
                        if (right.getChildCount() >= 2) {
                            View tTitle = right.getChildAt(0);
                            View tSub   = right.getChildAt(1);
                            if (tTitle instanceof TextView) tvMenuTitle = (TextView) tTitle;
                            if (tSub instanceof TextView)   tvMenuSubtitle = (TextView) tSub;
                        }
                    }
                }
            } catch (Throwable ignore) {}

            // 3) 팬트리 카드 → 하단 요약(TextView)
            try {
                View v3 = contentRoot.getChildAt(3);
                if (v3 instanceof CardView) {
                    ViewGroup card = (ViewGroup) ((CardView) v3).getChildAt(0); // LinearLayout(v)
                    // child(1) = "No items in pantry" TextView
                    if (card.getChildCount() >= 2 && card.getChildAt(1) instanceof TextView) {
                        tvPantrySubtitle = (TextView) card.getChildAt(1);
                    }
                }
            } catch (Throwable ignore) {}

            // 5) To-Do 카드 → 이름/서브(TextView)
            try {
                View v5 = contentRoot.getChildAt(5);
                if (v5 instanceof CardView) {
                    ViewGroup row = (ViewGroup) ((CardView) v5).getChildAt(0); // LinearLayout(h)
                    if (row.getChildCount() >= 2 && row.getChildAt(1) instanceof ViewGroup) {
                        ViewGroup texts = (ViewGroup) row.getChildAt(1); // LinearLayout(v)
                        if (texts.getChildCount() >= 2) {
                            if (texts.getChildAt(0) instanceof TextView) tvTodoName = (TextView) texts.getChildAt(0);
                            if (texts.getChildAt(1) instanceof TextView) tvTodoSub  = (TextView) texts.getChildAt(1);
                        }
                    }
                }
            } catch (Throwable ignore) {}

        } catch (Throwable ignore) {
            // 레이아웃이 예상과 달라도 앱은 죽지 않도록
        }
    }

    /* =========================================================
       2) LiveData 바인딩
       ========================================================= */

    /** 오늘(아/점/저) 타이틀 join → 상단 카드에 표시 */
    private void bindTodayMenuLive() {
        if (tvMenuSubtitle == null) return;
        if (tvMenuTitle != null) tvMenuTitle.setText("오늘의 메뉴");

        long key = dateKey(LocalDate.now());
        LiveData<List<MealPlanEntry>> b = mealDao.observeByDay(key, 0);
        LiveData<List<MealPlanEntry>> l = mealDao.observeByDay(key, 1);
        LiveData<List<MealPlanEntry>> d = mealDao.observeByDay(key, 2);

        MediatorLiveData<String> joined = new MediatorLiveData<>();
        joined.addSource(b, x -> joined.setValue(joinMealTitles(b.getValue(), l.getValue(), d.getValue())));
        joined.addSource(l, x -> joined.setValue(joinMealTitles(b.getValue(), l.getValue(), d.getValue())));
        joined.addSource(d, x -> joined.setValue(joinMealTitles(b.getValue(), l.getValue(), d.getValue())));

        joined.observe(this, s -> tvMenuSubtitle.setText((s == null || s.isEmpty()) ? "식단 계획 없음" : s));
    }

    /** 팬트리 보유 요약 텍스트 */
    private void bindPantrySummaryLive() {
        if (tvPantrySubtitle == null) return;
        pantryDao.observeAll().observe(this, list -> {
            if (list == null || list.isEmpty()) {
                tvPantrySubtitle.setText("보관함에 재료 없음");
                return;
            }
            int cnt = list.size();
            String first = safe(list.get(0).name);
            tvPantrySubtitle.setText(cnt == 1 ? first : (first + " 외 " + (cnt - 1) + "개"));
        });
    }

    /** 쇼핑 미체크 항목 중 첫 1개를 To-Do 카드에 표시 */
    private void bindTodoLive() {
        if (tvTodoName == null || tvTodoSub == null) return;
        shoppingDao.observeAll().observe(this, list -> {
            ShoppingItem target = null;
            if (list != null) {
                for (ShoppingItem s : list) { if (!s.checked) { target = s; break; } }
            }
            if (target == null) {
                tvTodoSub.setText("장보기 목록에 항목을 추가하세요");
            } else {
                tvTodoName.setText(safe(target.name));
                String sub = "";
                if (target.neededQty > 0) sub = trimZero(target.neededQty);
                if (target.unit != null && !target.unit.trim().isEmpty())
                    sub = (sub.isEmpty() ? "" : sub + " ") + target.unit.trim();
                tvTodoSub.setText(sub.isEmpty() ? "장보기 목록에 항목을 추가하세요" : sub);
            }
        });
    }

    /* =========================================================
       유틸
       ========================================================= */
    private static long dateKey(LocalDate d){
        return d.getYear()*10000L + d.getMonthValue()*100L + d.getDayOfMonth();
    }
    private static String safe(@Nullable String s) { return s == null ? "" : s; }
    private static String trimZero(double d) {
        String s = String.valueOf(d);
        return s.endsWith(".0") ? s.substring(0, s.length()-2) : s;
    }
    private static String joinMealTitles(@Nullable List<MealPlanEntry> b,
                                         @Nullable List<MealPlanEntry> l,
                                         @Nullable List<MealPlanEntry> d) {
        List<String> out = new ArrayList<>();
        if (b != null) for (MealPlanEntry e : b) { if (e.title != null && !e.title.trim().isEmpty()) { out.add(e.title.trim()); break; } }
        if (l != null) for (MealPlanEntry e : l) { if (e.title != null && !e.title.trim().isEmpty()) { out.add(e.title.trim()); break; } }
        if (d != null) for (MealPlanEntry e : d) { if (e.title != null && !e.title.trim().isEmpty()) { out.add(e.title.trim()); break; } }
        return out.isEmpty() ? "" : android.text.TextUtils.join(" · ", out);
    }
}
