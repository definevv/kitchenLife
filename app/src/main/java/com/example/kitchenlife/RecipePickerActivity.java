package com.example.kitchenlife;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.Recipe;
import com.example.kitchenlife.data.RecipeIngredient;
import com.example.kitchenlife.net.SupabaseApi;
import com.example.kitchenlife.net.SupabaseClient;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 레시피 선택 → 재료까지 묶어서 결과로 반환
 * RESULT_OK data:
 *  - "recipe_id" (long)
 *  - "recipe_title" (String)
 *  - EXTRA_INGREDIENT_LINES (ArrayList<String>)  // "id|name|amount|unit"
 *  - EXTRA_FROM_RECIPE_TITLE (String)
 */
public class RecipePickerActivity extends AppCompatActivity {

    public static final String EXTRA_INGREDIENT_LINES = "extra_ingredient_lines";
    public static final String EXTRA_FROM_RECIPE_TITLE = "extra_from_recipe_title";

    // UI
    private Toolbar toolbar;
    private RecyclerView rv;
    private View emptyView;
    private ProgressBar progressCenter;
    private View stateEmpty, stateError;
    private TextInputEditText etSearch;

    // Adapter & data
    private final List<Recipe> items = new ArrayList<>();
    private RecipesAdapter adapter;

    // Search (debounce)
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private String currentQuery = "";

    // Paging
    private static final int PAGE_SIZE = 30;
    private int offset = 0;
    private boolean isLoading = false;
    private boolean endReached = false;

    // API
    private SupabaseApi api;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_picker);

        bindViews();
        setupToolbar();
        setupRecycler();
        setupOptionalSearch();

        api = SupabaseClient.get().create(SupabaseApi.class);

        loadRecipes(true);
        Log.d("RecipePicker", "RecipePickerActivity launched");
    }

    private void bindViews() {
        toolbar        = findViewById(R.id.toolbar);
        rv             = findViewById(R.id.rv_recipes);
        emptyView      = findViewById(R.id.empty);
        progressCenter = findViewById(R.id.progress_center);
        stateEmpty     = findViewById(R.id.state_empty);
        stateError     = findViewById(R.id.state_error);
        etSearch       = findViewById(R.id.search_input);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Pick a recipe");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        adapter = new RecipesAdapter(this::fetchIngredientsAndFinish);
        rv.setAdapter(adapter);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int total = lm.getItemCount();
                int last  = lm.findLastVisibleItemPosition();
                if (!isLoading && !endReached && last >= total - 5) {
                    loadRecipes(false);
                }
            }
        });
    }

    private void setupOptionalSearch() {
        if (etSearch == null) return;
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

    private void showLoading(boolean show) {
        if (progressCenter != null) progressCenter.setVisibility(show ? View.VISIBLE : View.GONE);
        if (stateEmpty != null) stateEmpty.setVisibility(View.GONE);
        if (stateError != null) stateError.setVisibility(View.GONE);
        if (emptyView != null && show) emptyView.setVisibility(View.GONE);
    }

    private void showEmpty() {
        if (stateEmpty != null) stateEmpty.setVisibility(View.VISIBLE);
        if (stateError != null) stateError.setVisibility(View.GONE);
        if (progressCenter != null) progressCenter.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
    }

    private void showError() {
        if (stateError != null) stateError.setVisibility(View.VISIBLE);
        if (stateEmpty != null) stateEmpty.setVisibility(View.GONE);
        if (progressCenter != null) progressCenter.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);
    }

    /** 목록 로드 */
    private void loadRecipes(boolean reset) {
        if (isLoading) return;

        if (reset) {
            offset = 0;
            endReached = false;
            items.clear();
            adapter.submit(new ArrayList<>(items));
            showLoading(true);
        }

        if (endReached) { showLoading(false); return; }

        isLoading = true;

        final String select = "*";
        final String order  = "created_at.desc";
        final Integer limit = PAGE_SIZE;
        final Integer thisOffset = offset;

        String titleParam = (currentQuery == null || currentQuery.isEmpty())
                ? null : "ilike.*" + currentQuery + "*";

        Call<List<Recipe>> call = api.listRecipesAdvanced(
                select, order, limit, thisOffset,
                titleParam, null, null, null
        );

        call.enqueue(new Callback<List<Recipe>>() {
            @Override public void onResponse(@NonNull Call<List<Recipe>> c, @NonNull Response<List<Recipe>> res) {
                isLoading = false;
                showLoading(false);

                if (!res.isSuccessful() || res.body() == null) { showError(); return; }

                List<Recipe> page = res.body();
                if (page.isEmpty()) {
                    if (offset == 0) showEmpty();
                    endReached = true; return;
                }

                offset = thisOffset + page.size();
                if (page.size() < PAGE_SIZE) endReached = true;

                items.addAll(page);
                if (stateEmpty != null) stateEmpty.setVisibility(View.GONE);
                if (stateError != null) stateError.setVisibility(View.GONE);
                if (emptyView != null) emptyView.setVisibility(View.GONE);
                adapter.submit(new ArrayList<>(items));
            }

            @Override public void onFailure(@NonNull Call<List<Recipe>> c, @NonNull Throwable t) {
                isLoading = false;
                showLoading(false);
                Log.e("RecipePicker", "Failure: " + t.getMessage());
                showError();
            }
        });
    }

    /** 레시피 탭 → 재료 불러와서 결과로 되돌려주기 */
    private void fetchIngredientsAndFinish(Recipe r) {
        if (r == null || r.id <= 0) return;
        showLoading(true);

        String eqRecipeId = "eq." + r.id;
        SupabaseApi api = SupabaseClient.get().create(SupabaseApi.class);
        Call<List<RecipeIngredient>> call = api.getIngredientsByRecipe("*", eqRecipeId, "id.asc");

        call.enqueue(new Callback<List<RecipeIngredient>>() {
            @Override public void onResponse(@NonNull Call<List<RecipeIngredient>> c,
                                             @NonNull Response<List<RecipeIngredient>> res) {
                showLoading(false);

                ArrayList<String> lines = new ArrayList<>();
                if (res.isSuccessful() && res.body() != null) {
                    for (RecipeIngredient ri : res.body()) {
                        String id   = ""; // 쇼핑키는 name 기반이라 비워도 OK
                        String name = safe(ri.ingredient_name);
                        String unit = safe(ri.unit);

                        // 우선 순위: amount_numeric → quantity_text(앞 숫자만) → 공백
                        String amountStr = "";
                        if (ri.amount_numeric != null && ri.amount_numeric > 0d) {
                            amountStr = String.valueOf(ri.amount_numeric);
                        } else if (ri.quantity_text != null) {
                            double parsed = parseLeadingNumber(ri.quantity_text);
                            if (parsed > 0d) amountStr = String.valueOf(parsed);
                            // 단위 추정이 필요하고 현재 unit 비었으면 보완
                            if (unit.isEmpty()) unit = guessUnit(ri.quantity_text);
                        }

                        // ★ ShoppingRepository.parseLines가 기대하는 형식
                        //    "id|name|amount|unit"
                        lines.add(id + "|" + name + "|" + amountStr + "|" + unit);
                    }
                }

                Intent data = new Intent();
                data.putExtra("recipe_id", r.id);
                data.putExtra("recipe_title", r.title == null ? "" : r.title);
                data.putStringArrayListExtra(EXTRA_INGREDIENT_LINES, lines);
                data.putExtra(EXTRA_FROM_RECIPE_TITLE, r.title == null ? "" : r.title);
                setResult(Activity.RESULT_OK, data);
                finish();
            }

            @Override public void onFailure(@NonNull Call<List<RecipeIngredient>> c, @NonNull Throwable t) {
                showLoading(false);
                Intent data = new Intent();
                data.putExtra("recipe_id", r.id);
                data.putExtra("recipe_title", r.title == null ? "" : r.title);
                data.putStringArrayListExtra(EXTRA_INGREDIENT_LINES, new ArrayList<>());
                data.putExtra(EXTRA_FROM_RECIPE_TITLE, r.title == null ? "" : r.title);
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        });
    }

    private static String safe(String s){ return s == null ? "" : s.trim(); }

    private static double parseLeadingNumber(String txt){
        if (txt == null) return 0d;
        String t = txt.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '.') sb.append(ch);
            else break;
        }
        if (sb.length() == 0) return 0d;
        try { return Double.parseDouble(sb.toString()); } catch (Throwable ignore){ return 0d; }
    }

    private static String guessUnit(String txt){
        String t = txt == null ? "" : txt;
        if (t.contains("kg")) return "kg";
        if (t.contains("g"))  return "g";
        if (t.contains("ml")) return "ml";
        if (t.contains("L") || t.contains("l")) return "L";
        if (t.contains("개")) return "개";
        if (t.contains("큰술")) return "큰술";
        if (t.contains("작은술")) return "작은술";
        return "";
    }

    // ---------------------------
    // RecyclerView Adapter
    // ---------------------------
    static class RecipesAdapter extends RecyclerView.Adapter<RecipesAdapter.VH> {
        interface OnPick { void onPick(Recipe r); }
        private final List<Recipe> data = new ArrayList<>();
        private final OnPick onPick;

        RecipesAdapter(OnPick onPick) { this.onPick = onPick; }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(@NonNull View v) { super(v); tv = v.findViewById(R.id.tv_title); }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recipe, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Recipe r = data.get(pos);
            h.tv.setText(r.title == null || r.title.trim().isEmpty() ? "(untitled)" : r.title);
            h.itemView.setOnClickListener(v -> onPick.onPick(r));
        }

        @Override public int getItemCount() { return data.size(); }

        public void submit(List<Recipe> newList) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return data.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                    return data.get(oldPos).id == newList.get(newPos).id;
                }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                    Recipe a = data.get(oldPos), b = newList.get(newPos);
                    return eq(a.title,b.title);
                }
                private boolean eq(String a, String b){ return (a==null&&b==null) || (a!=null&&a.equals(b)); }
            });
            data.clear();
            data.addAll(newList);
            diff.dispatchUpdatesTo(this);
        }
    }
}
