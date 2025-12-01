package com.example.kitchenlife.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.kitchenlife.R;
import com.example.kitchenlife.data.Recipe;
import com.example.kitchenlife.net.SupabaseApi;
import com.example.kitchenlife.net.SupabaseClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * RecipesActivity
 * - 검색(디바운스) / 필터 칩 / 당겨서 새로고침
 * - 무한 스크롤 페이징(limit/offset)
 * - 빈/에러/로딩 상태
 * - 아이템 클릭 시 RecipeDetailActivity로 이동
 */
public class RecipesActivity extends AppCompatActivity {

    // UI
    private MaterialToolbar toolbar;
    private TextInputEditText etSearch;
    private ChipGroup chipsFilters;
    private Chip chipAll, chipKorean, chipEasy, chip30m;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rv;
    private ProgressBar progressCenter;
    private View stateEmpty, stateError;
    private MaterialButton btnRetry;
    private View fabScrollTop;

    // 데이터/어댑터
    private final RecipesAdapter adapter = new RecipesAdapter();
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private String currentQuery = "";
    private boolean filterKorean = false;
    private boolean filterEasy = false;
    private boolean filter30m = false;

    // 페이징
    private static final int PAGE_SIZE = 30;
    private int offset = 0;
    private boolean isLoading = false;
    private boolean endReached = false;
    private final List<Recipe> allItems = new ArrayList<>();

    // API
    private SupabaseApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipes);

        bindViews();
        setupToolbar();
        setupRecycler();
        setupFilters();
        setupSearch();
        setupSwipeRefresh();
        setupScrollTopFab();

        api = SupabaseClient.get().create(SupabaseApi.class);

        // 첫 로드
        loadRecipes(true);
        Log.d("WhichRecipesActivity", "UI RecipesActivity launched");
    }

    private void bindViews() {
        toolbar        = findViewById(R.id.toolbar);
        etSearch       = findViewById(R.id.search_input);
        chipsFilters   = findViewById(R.id.chips_filters);
        chipAll        = findViewById(R.id.chip_all);
        chipKorean     = findViewById(R.id.chip_korean);
        chipEasy       = findViewById(R.id.chip_easy);
        chip30m        = findViewById(R.id.chip_30min);

        swipeRefresh   = findViewById(R.id.swipe_refresh);
        rv             = findViewById(R.id.recycler_recipes);
        progressCenter = findViewById(R.id.progress_center);
        stateEmpty     = findViewById(R.id.state_empty);
        stateError     = findViewById(R.id.state_error);
        btnRetry       = findViewById(R.id.btn_retry);
        fabScrollTop   = findViewById(R.id.fab_scroll_top);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if(getSupportActionBar()!=null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("레시피");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        // 🔗 클릭 → 상세
        adapter.setOnRecipeClickListener(recipe -> {
            Intent i = new Intent(RecipesActivity.this, RecipeDetailActivity.class);
            i.putExtra("recipe_id", recipe.id);
            startActivity(i);
        });

        // 무한 스크롤
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 20 && fabScrollTop.getVisibility() != View.VISIBLE) fabScrollTop.setVisibility(View.VISIBLE);
                if (dy < -10 && !recyclerView.canScrollVertically(-1)) fabScrollTop.setVisibility(View.GONE);

                int total = lm.getItemCount();
                int last  = lm.findLastVisibleItemPosition();
                if (!isLoading && !endReached && last >= total - 5) {
                    loadRecipes(false);
                }
            }
        });
    }

    private void setupFilters() {
        chipAll.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                chipKorean.setChecked(false);
                chipEasy.setChecked(false);
                chip30m.setChecked(false);
                filterKorean = filterEasy = filter30m = false;
                loadRecipes(true);
            }
        });

        chipKorean.setOnCheckedChangeListener((btn, checked) -> {
            filterKorean = checked;
            if (checked) chipAll.setChecked(false);
            loadRecipes(true);
        });

        chipEasy.setOnCheckedChangeListener((btn, checked) -> {
            filterEasy = checked;
            if (checked) chipAll.setChecked(false);
            loadRecipes(true);
        });

        chip30m.setOnCheckedChangeListener((btn, checked) -> {
            filter30m = checked;
            if (checked) chipAll.setChecked(false);
            loadRecipes(true);
        });

        btnRetry.setOnClickListener(v -> loadRecipes(true));
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                currentQuery = s == null ? "" : s.toString().trim();
                debounce.removeCallbacksAndMessages(null);
                debounce.postDelayed(() -> loadRecipes(true), 300);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> loadRecipes(true));
    }

    private void setupScrollTopFab() {
        fabScrollTop.setOnClickListener(v -> rv.smoothScrollToPosition(0));
    }

    private void showLoading(boolean show) {
        progressCenter.setVisibility(show ? View.VISIBLE : View.GONE);
        stateEmpty.setVisibility(View.GONE);
        stateError.setVisibility(View.GONE);
    }

    private void showEmpty() {
        stateEmpty.setVisibility(View.VISIBLE);
        stateError.setVisibility(View.GONE);
        progressCenter.setVisibility(View.GONE);
    }

    private void showError() {
        stateError.setVisibility(View.VISIBLE);
        stateEmpty.setVisibility(View.GONE);
        progressCenter.setVisibility(View.GONE);
    }

    /** 서버에서 목록 로드. reset=true면 페이징 초기화 */
    private void loadRecipes(boolean reset) {
        if (isLoading) return;

        if (reset) {
            offset = 0;
            endReached = false;
            allItems.clear();
            adapter.submit(new ArrayList<>(allItems));
            if (!swipeRefresh.isRefreshing()) showLoading(true);
        }

        if (endReached) {
            swipeRefresh.setRefreshing(false);
            showLoading(false);
            return;
        }

        isLoading = true;

        final String select = "*";
        final String order  = "created_at.desc";
        final Integer limit = PAGE_SIZE;
        final Integer thisOffset = offset;

        String titleParam      = (currentQuery == null || currentQuery.isEmpty()) ? null : "ilike.*" + currentQuery + "*";
        String categoryParam   = filterKorean ? "eq.한식" : null;
        String difficultyParam = filterEasy   ? "eq.쉬움" : null;
        String timeParam       = filter30m    ? "lte.30"  : null;

        Call<List<Recipe>> call = SupabaseClient.get()
                .create(SupabaseApi.class)
                .listRecipesAdvanced(
                        select, order, limit, thisOffset,
                        titleParam, categoryParam, difficultyParam, timeParam
                );

        call.enqueue(new Callback<List<Recipe>>() {
            @Override
            public void onResponse(@NonNull Call<List<Recipe>> call, @NonNull Response<List<Recipe>> response) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                showLoading(false);

                if (!response.isSuccessful() || response.body() == null) {
                    Log.e("Recipes", "HTTP " + response.code());
                    showError();
                    return;
                }

                List<Recipe> page = response.body();
                if (page.isEmpty()) {
                    if (offset == 0) showEmpty();
                    endReached = true;
                    return;
                }

                offset = thisOffset + page.size();
                if (page.size() < PAGE_SIZE) endReached = true;

                allItems.addAll(page);
                stateEmpty.setVisibility(View.GONE);
                stateError.setVisibility(View.GONE);
                adapter.submit(new ArrayList<>(allItems));
            }

            @Override
            public void onFailure(@NonNull Call<List<Recipe>> call, @NonNull Throwable t) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                showLoading(false);
                Log.e("Recipes", "Failure: " + t.getMessage());
                showError();
            }
        });
    }

    /* ------------------------------- */
    /* RecyclerView Adapter (간단)     */
    /* ------------------------------- */
    private static class RecipesAdapter extends RecyclerView.Adapter<RecipesAdapter.VH> {
        private final List<Recipe> items = new ArrayList<>();
        interface OnRecipeClickListener { void onClick(Recipe recipe); }
        private OnRecipeClickListener listener;
        void setOnRecipeClickListener(OnRecipeClickListener l){ this.listener = l; }

        static class VH extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView thumb; // 썸네일은 나중에 Glide 연결
            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Recipe r = items.get(pos);
            h.title.setText(r.title != null ? r.title : "(no title)");
            String meta = "";
            if (r.category != null)     meta += r.category + "  ";
            if (r.difficulty != null)   meta += "• " + r.difficulty + "  ";
            if (r.time_minutes != null) meta += "• " + r.time_minutes + "분";
            h.subtitle.setText(meta.trim());

            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(r);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        public void submit(List<Recipe> newList) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return items.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                    return items.get(oldPos).id == newList.get(newPos).id;
                }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                    Recipe a = items.get(oldPos);
                    Recipe b = newList.get(newPos);
                    return eq(a.title,b.title) && eq(a.category,b.category)
                            && eq(a.difficulty,b.difficulty) && eqInt(a.time_minutes,b.time_minutes);
                }
                private boolean eq(String a, String b){ return (a==null && b==null) || (a!=null && a.equals(b)); }
                private boolean eqInt(Integer a, Integer b){ return (a==null && b==null) || (a!=null && a.equals(b)); }
            });
            items.clear();
            items.addAll(newList);
            diff.dispatchUpdatesTo(this);
        }
    }
}
