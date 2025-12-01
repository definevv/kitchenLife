package com.example.kitchenlife.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.MealPlanActivity;
import com.example.kitchenlife.R;
import com.example.kitchenlife.data.Recipe;
import com.example.kitchenlife.data.RecipeIngredient;
import com.example.kitchenlife.data.RecipeStep;
import com.example.kitchenlife.net.SupabaseClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 상세 화면:
 * - 상단: 썸네일 / 제목 / 요약 / 메타(카테고리·난이도·시간·인분)
 * - 재료 RecyclerView
 * - 스텝 RecyclerView
 * - 로딩/에러 상태
 * - [식단에 추가하기] → MealPlanActivity로 이동 (레시피 id/title 전달)
 */
public class RecipeDetailActivity extends AppCompatActivity {

    private long recipeId;
    @Nullable private String recipeTitle; // MealPlan으로 넘길 때 사용

    // UI
    private MaterialToolbar toolbar;
    private ImageView ivThumb;
    private TextView tvTitle, tvSummary;
    private Chip chipCategory, chipDifficulty, chipTime, chipServings;
    private RecyclerView rvIngredients, rvSteps;
    private ProgressBar progressCenter;
    private View stateError;
    private MaterialButton btnRetry, btnStart; // btnStart = 식단에 추가하기

    // Adapters
    private final IngredientsAdapter ingredientsAdapter = new IngredientsAdapter();
    private final StepsAdapter stepsAdapter = new StepsAdapter();

    // 병렬 로딩 완료 카운트
    private int pending = 0;

    // ----- 로컬 API (이 파일 안에서만 사용: recipe 단건/연관 조회용) -----
    interface LocalApi {
        // v_recipes에서 id=eq.X 로 1건 조회
        @GET("/rest/v1/v_recipes")
        Call<List<Recipe>> getRecipeById(
                @Query("select") String select,
                @Query("id") String idEq // "eq.<id>"
        );

        @GET("/rest/v1/v_recipe_steps")
        Call<List<RecipeStep>> getStepsByRecipe(
                @Query("select") String select,
                @Query("recipe_id") String recipeIdEq, // "eq.<id>"
                @Query("order") String order            // "step_no.asc"
        );

        @GET("/rest/v1/v_recipe_ingredients")
        Call<List<RecipeIngredient>> getIngredientsByRecipe(
                @Query("select") String select,
                @Query("recipe_id") String recipeIdEq, // "eq.<id>"
                @Query("order") String order            // "id.asc"
        );
    }

    private LocalApi api() {
        return SupabaseClient.get().create(LocalApi.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        recipeId = getIntent().getLongExtra("recipe_id", -1);
        if (recipeId <= 0) { finish(); return; }

        bindViews();
        setupToolbar();
        setupLists();

        // 버튼 라벨 한국어로 고정
        btnStart.setText("식단에 추가하기");

        // 클릭 시 MealPlanActivity로 이동 (필요 데이터 전달)
        btnStart.setOnClickListener(v -> {
            Intent i = new Intent(RecipeDetailActivity.this,
                    com.example.kitchenlife.MealPlanActivity.class); // 또는 import
            i.putExtra("from_recipe_id", recipeId);
            i.putExtra("from_recipe_title",
                    tvTitle.getText() == null ? "" : tvTitle.getText().toString());
            startActivity(i);
        });

        btnRetry.setOnClickListener(v -> loadAll());

        loadAll(); // 헤더 + 재료 + 스텝 병렬 로딩
    }

    private void bindViews() {
        toolbar       = findViewById(R.id.toolbar);
        ivThumb       = findViewById(R.id.iv_thumb);
        tvTitle       = findViewById(R.id.tv_title);
        tvSummary     = findViewById(R.id.tv_summary);
        chipCategory  = findViewById(R.id.chip_category);
        chipDifficulty= findViewById(R.id.chip_difficulty);
        chipTime      = findViewById(R.id.chip_time);
        chipServings  = findViewById(R.id.chip_servings);

        rvIngredients = findViewById(R.id.recycler_ingredients);
        rvSteps       = findViewById(R.id.recycler_steps);

        progressCenter= findViewById(R.id.progress_center);
        stateError    = findViewById(R.id.state_error);
        btnRetry      = findViewById(R.id.btn_retry);
        btnStart      = findViewById(R.id.btn_start);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Recipe");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupLists() {
        rvIngredients.setLayoutManager(new LinearLayoutManager(this));
        rvIngredients.setAdapter(ingredientsAdapter);
        rvIngredients.setNestedScrollingEnabled(false);

        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        rvSteps.setAdapter(stepsAdapter);
        rvSteps.setNestedScrollingEnabled(false);
    }

    // --------------------- Loading helpers ---------------------
    private void beginLoading(int tasks) {
        pending = tasks;
        progressCenter.setVisibility(View.VISIBLE);
        stateError.setVisibility(View.GONE);
    }

    private void onTaskDone() {
        pending--;
        if (pending <= 0) {
            progressCenter.setVisibility(View.GONE);
        }
    }

    private void showError() {
        progressCenter.setVisibility(View.GONE);
        stateError.setVisibility(View.VISIBLE);
    }

    // --------------------- Data loading ------------------------
    private void loadAll() {
        beginLoading(3);
        final String idEq = "eq." + recipeId;

        // 1) 헤더(제목/요약/메타/썸네일)
        api().getRecipeById("*", idEq).enqueue(new Callback<List<Recipe>>() {
            @Override public void onResponse(@NonNull Call<List<Recipe>> call, @NonNull Response<List<Recipe>> res) {
                if (res.isSuccessful() && res.body() != null && !res.body().isEmpty()) {
                    Recipe r = res.body().get(0);
                    bindHeader(r);
                } else {
                    showError();
                }
                onTaskDone();
            }
            @Override public void onFailure(@NonNull Call<List<Recipe>> call, @NonNull Throwable t) {
                showError();
                onTaskDone();
            }
        });

        // 2) 재료
        api().getIngredientsByRecipe("*", idEq, "id.asc").enqueue(new Callback<List<RecipeIngredient>>() {
            @Override public void onResponse(@NonNull Call<List<RecipeIngredient>> c, @NonNull Response<List<RecipeIngredient>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    ingredientsAdapter.submit(r.body());
                }
                onTaskDone();
            }
            @Override public void onFailure(@NonNull Call<List<RecipeIngredient>> c, @NonNull Throwable t) {
                onTaskDone();
            }
        });

        // 3) 스텝
        api().getStepsByRecipe("*", idEq, "step_no.asc").enqueue(new Callback<List<RecipeStep>>() {
            @Override public void onResponse(@NonNull Call<List<RecipeStep>> c, @NonNull Response<List<RecipeStep>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    stepsAdapter.submit(r.body());
                }
                onTaskDone();
            }
            @Override public void onFailure(@NonNull Call<List<RecipeStep>> c, @NonNull Throwable t) {
                onTaskDone();
            }
        });
    }

    private void bindHeader(Recipe r) {
        recipeTitle = r.title; // MealPlan 전달용으로 보관

        tvTitle.setText(r.title != null ? r.title : "");
        tvSummary.setText(r.summary != null ? r.summary : "");

        chipCategory.setText(r.category != null ? r.category : "카테고리");
        chipDifficulty.setText(r.difficulty != null ? r.difficulty : "난이도");

        if (r.time_minutes != null) chipTime.setText(formatMinutes(r.time_minutes));
        else chipTime.setText("시간정보");

        if (r.servings != null) chipServings.setText(r.servings + "인분");
        else chipServings.setText("인분");

        // Glide 사용 시(선택):
        // Glide.with(this).load(r.thumbnail_url).placeholder(R.color.placeholder_gray).into(ivThumb);
    }

    private String formatMinutes(Integer minutes) {
        if (minutes == null) return "";
        if (minutes < 60) return minutes + "분";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? (h + "시간") : (h + "시간 " + m + "분");
    }

    // --------------------- Adapters ----------------------------
    private static class IngredientsAdapter extends RecyclerView.Adapter<IngredientsAdapter.VH> {
        private final List<RecipeIngredient> items = new ArrayList<>();

        static class VH extends RecyclerView.ViewHolder {
            TextView name, meta;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_ing_name);
                meta = v.findViewById(R.id.tv_ing_meta);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ingredient, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RecipeIngredient it = items.get(pos);
            h.name.setText(it.ingredient_name != null ? it.ingredient_name : "");
            String meta = "";
            if (it.quantity_text != null && !it.quantity_text.isEmpty()) meta += it.quantity_text;
            if (it.is_optional) {
                if (!meta.isEmpty()) meta += " / ";
                meta += "선택";
            }
            h.meta.setText(meta);
        }

        @Override public int getItemCount() { return items.size(); }

        void submit(List<RecipeIngredient> list) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return items.size(); }
                @Override public int getNewListSize() { return list.size(); }
                @Override public boolean areItemsTheSame(int o, int n) { return items.get(o).id == list.get(n).id; }
                @Override public boolean areContentsTheSame(int o, int n) {
                    RecipeIngredient a = items.get(o), b = list.get(n);
                    return eq(a.ingredient_name,b.ingredient_name) &&
                            eq(a.quantity_text,b.quantity_text) &&
                            (a.is_optional == b.is_optional);
                }
                private boolean eq(String a, String b){ return (a==null&&b==null) || (a!=null&&a.equals(b)); }
            });
            items.clear(); items.addAll(list); diff.dispatchUpdatesTo(this);
        }
    }

    private static class StepsAdapter extends RecyclerView.Adapter<StepsAdapter.VH> {
        private final List<RecipeStep> items = new ArrayList<>();

        static class VH extends RecyclerView.ViewHolder {
            TextView stepNo, desc, tip;
            ImageView img;
            VH(@NonNull View v) {
                super(v);
                stepNo = v.findViewById(R.id.tv_step_no);
                desc   = v.findViewById(R.id.tv_step_desc);
                tip    = v.findViewById(R.id.tv_step_tip);
                img    = v.findViewById(R.id.iv_step_img);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_step, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RecipeStep s = items.get(pos);
            h.stepNo.setText(String.valueOf(s.step_no));
            h.desc.setText(s.description != null ? s.description : "");
            if (s.tip != null && !s.tip.isEmpty()) {
                h.tip.setVisibility(View.VISIBLE);
                h.tip.setText("Tip: " + s.tip);
            } else {
                h.tip.setVisibility(View.GONE);
            }
            h.img.setVisibility(View.GONE); // 이미지 로딩 쓰면 Glide로 교체
        }

        @Override public int getItemCount() { return items.size(); }

        void submit(List<RecipeStep> list) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return items.size(); }
                @Override public int getNewListSize() { return list.size(); }
                @Override public boolean areItemsTheSame(int o, int n) { return items.get(o).id == list.get(n).id; }
                @Override public boolean areContentsTheSame(int o, int n) {
                    RecipeStep a = items.get(o), b = list.get(n);
                    return eq(a.description,b.description) && eq(a.tip,b.tip) && eq(a.image_url,b.image_url);
                }
                private boolean eq(String a, String b){ return (a==null&&b==null) || (a!=null&&a.equals(b)); }
            });
            items.clear(); items.addAll(list); diff.dispatchUpdatesTo(this);
        }
    }
}
