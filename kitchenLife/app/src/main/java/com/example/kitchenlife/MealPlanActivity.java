package com.example.kitchenlife;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.MealPlanEntry;
import com.example.kitchenlife.data.MyMealSet;
import com.example.kitchenlife.data.PantryDao;
import com.example.kitchenlife.data.PantryItem;
import com.example.kitchenlife.data.RecipeDao;
import com.example.kitchenlife.data.RecipeIngredient;
import com.example.kitchenlife.data.ShoppingDao;
import com.example.kitchenlife.data.ShoppingItem;
import com.example.kitchenlife.net.SupabaseClient;
import com.example.kitchenlife.ui.mealplan.MealPlanViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

/** WEEK / MONTH 캘린더 + 레시피/내식단(MY MEALS) 바텀시트 */
public class MealPlanActivity extends AppCompatActivity {

    // ───────── 상단 UI ─────────
    private RecyclerView rvBreakfast, rvLunch, rvDinner;
    private MaterialCardView slotBreakfast, slotLunch, slotDinner;
    private FloatingActionButton fabAdd;
    private TextView tvSelectedDay, tvRangeTitle;
    private MaterialButtonToggleGroup viewModeToggle;
    private MaterialButton btnWeek, btnMonth;
    private RecyclerView rvWeekDays, rvMonthCalendar;
    private ViewFlipperCompat viewFlipper;

    // ───────── BottomSheet ─────────
    private MaterialCardView recipeSheet;
    private BottomSheetBehavior<MaterialCardView> sheetBehavior;
    private MaterialButtonToggleGroup recipeModeToggle;
    private MaterialButton btnPrimarySheet;
    private RecyclerView rvRecipeLibrary;
    private TextView tvEmptyRecipes;
    private TextInputEditText etRecipeSearch;
    private View groupRecipes, groupMyMeals, btnCloseSheet;

    // MY MEALS 뷰
    private RecyclerView rvMyMeals;
    private TextView tvEmptyMyMeals;
    private MaterialButton btnCreateMyMeals;

    // ───────── VM / State ─────────
    private MealPlanViewModel vm;

    private LocalDate selectedDate = LocalDate.now();  // 현재 선택 일자
    private LocalDate weekAnchor   = LocalDate.now();  // 주 뷰 기준
    private YearMonth monthAnchor  = YearMonth.now();  // 월 뷰 기준

    private int editingMealType = 0; // 0=B,1=L,2=D (UI 표기용)
    @Nullable private MealPlanEntry editingEntry = null;

    // 리스트 어댑터
    private MealEntryAdapter breakfastAdapter, lunchAdapter, dinnerAdapter;
    private WeekDaysAdapter   weekDaysAdapter;
    private MonthGridAdapter  monthGridAdapter;
    private RecipeAdapter     recipeAdapter;
    private MyMealsAdapter    myMealsAdapter;

    // 데이터(레시피: Supabase → 메모리 목록)
    private final List<Recipe> masterRecipes = new ArrayList<>();
    @Nullable private Recipe selectedRecipe = null;

    // MY MEALS 선택/관찰
    @Nullable private MyMealSet selectedSet = null;
    @Nullable private LiveData<List<MyMealSet>> myMealsLive = null;

    // DB 핸들
    private RecipeDao  recipeDao;
    private ShoppingDao shoppingDao;
    private PantryDao   pantryDao;

    // 포맷터
    private final DateTimeFormatter weekTitleFmt = DateTimeFormatter.ofPattern("'주간' yyyy-MM-dd");
    private final DateTimeFormatter dayLabelFmt  = DateTimeFormatter.ofPattern("EEEE, d", Locale.KOREAN);
    static final DateTimeFormatter DOW_SHORT_KO  =
            DateTimeFormatter.ofPattern("E", Locale.KOREAN);
    static final DateTimeFormatter MONTH_TITLE_KO =
            DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);

    // 레시피 선택 런처 (원하면 “레시피에서 추가” 버튼에서도 사용 가능)
    private final ActivityResultLauncher<Intent> pickRecipeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    long rid = result.getData().getLongExtra("recipe_id", 0L);
                    String rtitle = result.getData().getStringExtra("recipe_title");
                    if (rid > 0 || (rtitle != null && !rtitle.trim().isEmpty())) {
                        reconcileShoppingForRecipe(rid, rtitle);
                        android.widget.Toast.makeText(this, "장보기에 추가됐어요", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });

    // ───────── Supabase 간단 목록 API (id, title 만) ─────────
    interface LocalApi {
        @GET("/rest/v1/v_recipes")
        Call<List<Recipe>> listRecipes(
                @Query("select") String select,
                @Query("order") String order,
                @Query("limit") Integer limit,
                @Query("offset") Integer offset,
                @Query("title") String titleFilterLike
        );
    }
    private LocalApi api() { return SupabaseClient.get().create(LocalApi.class); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_plan);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("식단 계획");
        }

        vm = new ViewModelProvider(this).get(MealPlanViewModel.class);

        bindViews();
        setupCalendarLists();
        setupBottomSheet();
        setupMealLists();
        observeMealsForDay();
        bindClicks();

        // DB 핸들
        AppDatabase db = AppDatabase.get(this);
        recipeDao   = db.recipeDao();
        shoppingDao = db.shoppingDao();
        pantryDao   = db.pantryDao();

        // 초기 렌더
        viewModeToggle.check(R.id.btn_week);
        viewFlipper.showWeek();
        updateWeekTitle();
        renderSelectedDayLabel();
        renderWeekStrip();
        renderMonthGrid();

        // 레시피 목록 로드 (실패 시 seed)
        fetchRecipes(null);
    }

    private void bindViews() {
        tvRangeTitle = findViewById(R.id.tv_range_title);
        tvSelectedDay = findViewById(R.id.tv_selected_day);

        rvBreakfast = findViewById(R.id.rv_breakfast);
        rvLunch     = findViewById(R.id.rv_lunch);
        rvDinner    = findViewById(R.id.rv_dinner);

        slotBreakfast = findViewById(R.id.slot_breakfast);
        slotLunch     = findViewById(R.id.slot_lunch);
        slotDinner    = findViewById(R.id.slot_dinner);

        fabAdd = findViewById(R.id.fab_add_meal);

        // toggle + flipper
        viewModeToggle = findViewById(R.id.toggle_view_mode);
        btnWeek = findViewById(R.id.btn_week);
        btnMonth = findViewById(R.id.btn_month);
        rvWeekDays = findViewById(R.id.rv_week_days);
        rvMonthCalendar = findViewById(R.id.rv_month_calendar);
        viewFlipper = new ViewFlipperCompat(findViewById(R.id.view_flipper));

        // sheet
        recipeSheet     = findViewById(R.id.recipe_sheet);
        sheetBehavior   = BottomSheetBehavior.from(recipeSheet);
        recipeModeToggle= findViewById(R.id.toggle_recipe_mode);
        btnPrimarySheet = findViewById(R.id.btn_primary_sheet);
        btnCloseSheet   = findViewById(R.id.btn_close_sheet);

        // 레시피 탭
        groupRecipes    = findViewById(R.id.group_recipes);
        rvRecipeLibrary = findViewById(R.id.rv_recipe_library);
        tvEmptyRecipes  = findViewById(R.id.tv_empty_recipes);
        etRecipeSearch  = findViewById(R.id.et_recipe_search);

        // MY MEALS 탭
        groupMyMeals     = findViewById(R.id.group_my_meals);
        rvMyMeals        = findViewById(R.id.rv_my_meals);
        tvEmptyMyMeals   = findViewById(R.id.tv_empty_my_meals);
        btnCreateMyMeals = findViewById(R.id.btn_create_my_meals);
    }

    // ───────── Calendar Lists ─────────
    private void setupCalendarLists() {
        // Week strip
        weekDaysAdapter = new WeekDaysAdapter(d -> {
            selectedDate = d;
            renderSelectedDayLabel();
            observeMealsForDay();
            weekDaysAdapter.setSelected(d);
        });
        rvWeekDays.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvWeekDays.setAdapter(weekDaysAdapter);

        // Month grid
        monthGridAdapter = new MonthGridAdapter(day -> {
            selectedDate = day;
            weekAnchor   = day;
            btnWeek.setChecked(true);
            viewFlipper.showWeek();
            renderWeekStrip();
            renderSelectedDayLabel();
            updateWeekTitle();
            observeMealsForDay();
        });
        rvMonthCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        rvMonthCalendar.setAdapter(monthGridAdapter);

        // Week/Month 토글
        viewModeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_week) {
                viewFlipper.showWeek();
                updateWeekTitle();
            } else {
                viewFlipper.showMonth();
                updateMonthTitle();
            }
        });

        // Prev/Next 버튼
        findViewById(R.id.btn_prev_range).setOnClickListener(v -> {
            if (btnWeek.isChecked()) {
                weekAnchor = weekAnchor.minusWeeks(1);
                renderWeekStrip(); updateWeekTitle();
            } else {
                monthAnchor = monthAnchor.minusMonths(1);
                renderMonthGrid(); updateMonthTitle();
            }
        });
        findViewById(R.id.btn_next_range).setOnClickListener(v -> {
            if (btnWeek.isChecked()) {
                weekAnchor = weekAnchor.plusWeeks(1);
                renderWeekStrip(); updateWeekTitle();
            } else {
                monthAnchor = monthAnchor.plusMonths(1);
                renderMonthGrid(); updateMonthTitle();
            }
        });
    }

    private void updateWeekTitle() {
        LocalDate start = weekStart(weekAnchor, DayOfWeek.SUNDAY);
        tvRangeTitle.setText(start.format(weekTitleFmt));
    }
    private void updateMonthTitle() { tvRangeTitle.setText(monthAnchor.atDay(1).format(MONTH_TITLE_KO)); }
    private void renderSelectedDayLabel() { tvSelectedDay.setText(selectedDate.format(dayLabelFmt)); }
    private void renderWeekStrip() {
        LocalDate start = weekStart(weekAnchor, DayOfWeek.SUNDAY);
        List<LocalDate> seven = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) seven.add(start.plusDays(i));
        weekDaysAdapter.submit(seven, selectedDate);
        rvWeekDays.scrollToPosition(selectedDate.getDayOfWeek().getValue() % 7);
    }
    private void renderMonthGrid() { monthGridAdapter.submit(monthAnchor, selectedDate); }
    private static LocalDate weekStart(LocalDate any, DayOfWeek first) { return any.with(TemporalAdjusters.previousOrSame(first)); }

    // ───────── BottomSheet ─────────
    private void setupBottomSheet() {
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        btnCloseSheet.setOnClickListener(v -> sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else finish();
            }
        });

        recipeModeToggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            if (id == R.id.btn_mode_recipes) showRecipesMode();
            else showMyMealsMode();
        });

        etRecipeSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                String q = s == null ? "" : s.toString().trim();
                int checked = recipeModeToggle.getCheckedButtonId();
                if (checked == R.id.btn_mode_recipes) {
                    List<Recipe> filtered = new ArrayList<>();
                    for (Recipe r : masterRecipes)
                        if (r.title != null && r.title.toLowerCase().contains(q.toLowerCase())) filtered.add(r);
                    recipeAdapter.submit(filtered);
                    tvEmptyRecipes.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    if (myMealsLive != null) myMealsLive.removeObservers(MealPlanActivity.this);
                    myMealsLive = vm.searchMyMealSets(q);
                    myMealsLive.observe(MealPlanActivity.this, list -> {
                        myMealsAdapter.submit(list);
                        boolean empty = list == null || list.isEmpty();
                        tvEmptyMyMeals.setVisibility(empty ? View.VISIBLE : View.GONE);
                        btnCreateMyMeals.setVisibility(View.VISIBLE);
                    });
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        recipeModeToggle.check(R.id.btn_mode_recipes);
        btnPrimarySheet.setText("식단에 추가");
        btnPrimarySheet.setOnClickListener(v -> onClickPrimaryInRecipesMode());
    }

    private void showRecipesMode() {
        groupRecipes.setVisibility(View.VISIBLE);
        groupMyMeals.setVisibility(View.GONE);
        btnPrimarySheet.setVisibility(View.VISIBLE);
        btnPrimarySheet.setText(editingEntry == null ? "식단에 추가" : "식단 수정");
        btnPrimarySheet.setOnClickListener(v -> onClickPrimaryInRecipesMode());
    }

    private void showMyMealsMode() {
        groupRecipes.setVisibility(View.GONE);
        groupMyMeals.setVisibility(View.VISIBLE);
        btnPrimarySheet.setVisibility(View.VISIBLE);
        btnPrimarySheet.setText("식단에 추가");
        btnPrimarySheet.setOnClickListener(v -> onClickPrimaryInMyMealsMode());

        if (myMealsLive != null) myMealsLive.removeObservers(this);
        myMealsLive = vm.myMealSets();
        myMealsLive.observe(this, list -> {
            myMealsAdapter.submit(list);
            boolean empty = list == null || list.isEmpty();
            tvEmptyMyMeals.setVisibility(empty ? View.VISIBLE : View.GONE);
            btnCreateMyMeals.setVisibility(View.VISIBLE);
        });
    }

    // ───────── 시트 내부 동작 ─────────
    private void onClickPrimaryInRecipesMode() {
        if (selectedRecipe == null) return;
        long key = dateKey(selectedDate);
        if (editingEntry == null) vm.add(key, editingMealType, selectedRecipe.title);
        else vm.update(editingEntry, selectedRecipe.title);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // ✅ 식단 확정 → 장보기에 즉시 반영
        reconcileShoppingForRecipe(selectedRecipe.id, selectedRecipe.title);

        // 리스트 즉시 갱신
        observeMealsForDay();
    }

    private void onClickPrimaryInMyMealsMode() {
        if (selectedSet == null) {
            android.widget.Toast.makeText(this, "세트를 선택하세요.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String title = safe(selectedSet.name);
        if (title.isEmpty()) {
            android.widget.Toast.makeText(this, "선택한 세트에 제목이 없습니다.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        long key = dateKey(selectedDate);
        if (editingEntry == null) vm.add(key, editingMealType, title);
        else vm.update(editingEntry, title);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // 간단 보정
        reconcileShoppingForMyMealSet(selectedSet);

        // 리스트 즉시 갱신
        observeMealsForDay();
    }

    // ───────── 끼니 리스트 ─────────
    private void setupMealLists() {
        breakfastAdapter = new MealEntryAdapter(new SlotListener(0));
        lunchAdapter     = new MealEntryAdapter(new SlotListener(1));
        dinnerAdapter    = new MealEntryAdapter(new SlotListener(2));

        rvBreakfast.setLayoutManager(new LinearLayoutManager(this));
        rvLunch.setLayoutManager(new LinearLayoutManager(this));
        rvDinner.setLayoutManager(new LinearLayoutManager(this));

        rvBreakfast.setAdapter(breakfastAdapter);
        rvLunch.setAdapter(lunchAdapter);
        rvDinner.setAdapter(dinnerAdapter);

        // 레시피 리스트
        recipeAdapter = new RecipeAdapter(r -> {
            selectedRecipe = r;
            recipeAdapter.setSelectedId(r.id);
        });
        rvRecipeLibrary.setLayoutManager(new LinearLayoutManager(this));
        rvRecipeLibrary.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvRecipeLibrary.setAdapter(recipeAdapter);

        // MY MEALS
        myMealsAdapter = new MyMealsAdapter(new MyMealsAdapter.OnPick() {
            @Override public void onPick(MyMealSet s) { selectedSet = s; myMealsAdapter.setSelectedId(s.id); }
            @Override public void onEdit(MyMealSet s) { openMyMealEditorPrefilled(s); }
            @Override public void onDelete(MyMealSet s) { vm.deleteMyMealSet(s); }
        });
        rvMyMeals.setLayoutManager(new LinearLayoutManager(this));
        rvMyMeals.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvMyMeals.setAdapter(myMealsAdapter);

        btnCreateMyMeals.setOnClickListener(v -> openMyMealEditor());
    }

    private void observeMealsForDay() {
        long key = dateKey(selectedDate);
        vm.meals(key, 0).observe(this, list -> {
            breakfastAdapter.submit(list);
            rvBreakfast.scrollToPosition(0);
        });
        vm.meals(key, 1).observe(this, list -> {
            lunchAdapter.submit(list);
            rvLunch.scrollToPosition(0);
        });
        vm.meals(key, 2).observe(this, list -> {
            dinnerAdapter.submit(list);
            rvDinner.scrollToPosition(0);
        });
    }

    private void bindClicks() {
        slotBreakfast.setOnClickListener(v -> openSheetForAddOrEdit(0, null));
        slotLunch.setOnClickListener(v -> openSheetForAddOrEdit(1, null));
        slotDinner.setOnClickListener(v -> openSheetForAddOrEdit(2, null));
        fabAdd.setOnClickListener(v -> openSheetForAddOrEdit(0, null));
    }

    private void openSheetForAddOrEdit(int mealType, @Nullable MealPlanEntry entryToEdit) {
        editingMealType = mealType;
        editingEntry = entryToEdit;

        selectedRecipe = null; recipeAdapter.setSelectedId(null);
        selectedSet = null;    myMealsAdapter.setSelectedId(null);

        int checked = recipeModeToggle.getCheckedButtonId();
        if (checked == R.id.btn_mode_recipes) showRecipesMode(); else showMyMealsMode();

        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    // ───────── My Meals 에디터 ─────────
    private void openMyMealEditorPrefilled(MyMealSet s) {
        com.google.android.material.bottomsheet.BottomSheetDialog dlg =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_my_meal_editor, null, false);
        dlg.setContentView(content);

        TextInputEditText etTitle = content.findViewById(R.id.et_recipe_title);
        TextInputEditText etTags  = content.findViewById(R.id.et_recipe_tags);
        TextInputEditText etIngr  = content.findViewById(R.id.et_ingredients);
        TextInputEditText etSteps = content.findViewById(R.id.et_steps);
        TextInputEditText etNotes = content.findViewById(R.id.et_notes);
        MaterialButton btnSave    = content.findViewById(R.id.btn_save_recipe);

        etTitle.setText(s.name);

        RecipePayload p = parseRecipeJson(s.notes);
        if (p != null) {
            etIngr.setText(p.ingredients);
            etSteps.setText(p.steps);
            etTags.setText(p.tags);
            etNotes.setText(p.note);
        }

        btnSave.setText("레시피 수정");
        btnSave.setOnClickListener(v -> {
            s.name  = safe(etTitle.getText());
            s.notes = toRecipeJson(
                    safe(etIngr.getText()),
                    safe(etSteps.getText()),
                    safe(etTags.getText()),
                    safe(etNotes.getText())
            );
            vm.saveMyMealSet(s);
            android.widget.Toast.makeText(this, "수정 완료", android.widget.Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });

        dlg.show();
    }

    private void openMyMealEditor() {
        com.google.android.material.bottomsheet.BottomSheetDialog dlg =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_my_meal_editor, null, false);
        dlg.setContentView(content);

        TextInputEditText etTitle = content.findViewById(R.id.et_recipe_title);
        TextInputEditText etTags  = content.findViewById(R.id.et_recipe_tags);
        TextInputEditText etIngr  = content.findViewById(R.id.et_ingredients);
        TextInputEditText etSteps = content.findViewById(R.id.et_steps);
        TextInputEditText etNotes = content.findViewById(R.id.et_notes);
        MaterialButton btnSave    = content.findViewById(R.id.btn_save_recipe);

        btnSave.setText("레시피 저장");
        btnSave.setOnClickListener(v -> {
            String title = safe(etTitle.getText());
            if (title.isEmpty()) { etTitle.setError("레시피 제목을 입력하세요"); return; }

            MyMealSet set = new MyMealSet();
            set.name  = title;
            set.notes = toRecipeJson(
                    safe(etIngr.getText()),
                    safe(etSteps.getText()),
                    safe(etTags.getText()),
                    safe(etNotes.getText())
            );
            vm.saveMyMealSet(set);
            android.widget.Toast.makeText(this, "저장 완료", android.widget.Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });

        dlg.show();
    }

    private static String safe(@Nullable CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    // ───────── 어댑터들/슬랏 리스너 ─────────
    private class SlotListener implements IMealSlotListener {
        private final int mealType; SlotListener(int m){ mealType=m; }
        @Override public void onAdd(){ openSheetForAddOrEdit(mealType,null); }
        @Override public void onEdit(MealPlanEntry e){ openSheetForAddOrEdit(mealType,e); }
        @Override public void onDelete(MealPlanEntry e){ vm.delete(e); }
    }
    private interface IMealSlotListener { void onAdd(); void onEdit(MealPlanEntry e); void onDelete(MealPlanEntry e); }

    /** WEEK: 7칩 가로 리스트 */
    private static class WeekDaysAdapter extends RecyclerView.Adapter<WeekDaysAdapter.VH> {
        interface OnPick { void onPick(LocalDate d); }
        private final OnPick onPick;
        private final List<LocalDate> days = new ArrayList<>();
        private @Nullable LocalDate selected;

        WeekDaysAdapter(OnPick onPick) { this.onPick = onPick; }
        void submit(List<LocalDate> list, @Nullable LocalDate sel) { days.clear(); days.addAll(list); selected = sel; notifyDataSetChanged(); }
        void setSelected(LocalDate d) { selected = d; notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_week_day, p, false);
            return new VH(view);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(days.get(pos), selected, onPick); }
        @Override public int getItemCount() { return days.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvDow, tvDay;
            MaterialCardView chip;
            VH(@NonNull View itemView) {
                super(itemView);
                chip = (MaterialCardView) itemView;
                tvDow = itemView.findViewById(R.id.tv_dow);
                tvDay = itemView.findViewById(R.id.tv_day);
            }
            void bind(LocalDate d, @Nullable LocalDate sel, OnPick onPick) {
                tvDow.setText(d.format(DOW_SHORT_KO));
                tvDay.setText(String.valueOf(d.getDayOfMonth()));
                boolean selected = sel != null && sel.equals(d);
                chip.setChecked(selected);
                chip.setStrokeWidth(selected ? 0 : 2);
                chip.setOnClickListener(v -> onPick.onPick(d));
            }
        }
    }

    /** MONTH: 7x6 그리드 */
    private static class MonthGridAdapter extends RecyclerView.Adapter<MonthGridAdapter.VH> {
        interface OnPick { void onPick(LocalDate d); }
        private final OnPick onPick;
        private final List<LocalDate> cells = new ArrayList<>();
        private @Nullable LocalDate selected;
        private YearMonth anchor = YearMonth.now();

        MonthGridAdapter(OnPick onPick) { this.onPick = onPick; }

        void submit(YearMonth ym, @Nullable LocalDate sel) {
            anchor = ym; selected = sel; cells.clear();
            LocalDate first = ym.atDay(1);
            LocalDate start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            for (int i=0;i<42;i++) cells.add(start.plusDays(i));
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_month_day, p, false);
            return new VH(view);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            LocalDate d = cells.get(pos);
            boolean inMonth = d.getMonth().equals(anchor.getMonth());
            boolean selectedEq = selected != null && selected.equals(d);
            h.bind(d, inMonth, selectedEq, onPick);
        }
        @Override public int getItemCount() { return cells.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv; MaterialCardView card;
            VH(@NonNull View itemView) { super(itemView); card = (MaterialCardView) itemView; tv = itemView.findViewById(R.id.tv_month_day); }
            void bind(LocalDate d, boolean inMonth, boolean selected, OnPick onPick) {
                tv.setText(String.valueOf(d.getDayOfMonth()));
                tv.setAlpha(inMonth ? 1f : 0.35f);
                card.setChecked(selected);
                card.setStrokeWidth(selected ? 0 : 1);
                card.setOnClickListener(v -> onPick.onPick(d));
            }
        }
    }

    /** 레시피 목록 (Supabase 간단 목록) */
    public static class Recipe { public long id; public String title; }
    private static class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.VH> {
        interface OnPick { void onPick(Recipe r); }
        private final OnPick onPick;
        private final List<Recipe> items = new ArrayList<>();
        @Nullable private Long selectedId=null;

        RecipeAdapter(OnPick p){ onPick=p; }
        void selectById(long id){ selectedId = id; notifyDataSetChanged(); }
        void submit(@Nullable List<Recipe> list){ items.clear(); if(list!=null) items.addAll(list); notifyDataSetChanged();}
        void setSelectedId(@Nullable Long id){ selectedId=id; notifyDataSetChanged(); }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int v){
            View view=LayoutInflater.from(p.getContext()).inflate(R.layout.item_recipe,p,false);
            return new VH(view);
        }
        @Override public void onBindViewHolder(@NonNull VH h,int pos){
            Recipe r=items.get(pos);
            h.tvTitle.setText(r.title);
            h.itemView.setActivated(selectedId!=null && selectedId.equals(r.id));
            h.itemView.setOnClickListener(v->onPick.onPick(r));
        }
        @Override public int getItemCount(){ return items.size(); }
        static class VH extends RecyclerView.ViewHolder{
            TextView tvTitle; VH(@NonNull View itemView){ super(itemView); tvTitle=itemView.findViewById(R.id.tv_title); }
        }
    }

    /** MY MEALS 리스트 */
    private static class MyMealsAdapter extends RecyclerView.Adapter<MyMealsAdapter.VH> {
        interface OnPick { void onPick(MyMealSet s); void onEdit(MyMealSet s); void onDelete(MyMealSet s); }
        private final OnPick onPick;
        private final List<MyMealSet> items = new ArrayList<>();
        @Nullable private Long selectedId = null;

        MyMealsAdapter(OnPick p){ onPick = p; }
        void submit(@Nullable List<MyMealSet> list){ items.clear(); if(list!=null) items.addAll(list); notifyDataSetChanged(); }
        void setSelectedId(@Nullable Long id){ selectedId = id; notifyDataSetChanged(); }

        static class VH extends RecyclerView.ViewHolder{
            TextView tvTitle, tvMeta;
            ImageButton btnEdit, btnDelete;
            VH(View v){
                super(v);
                tvTitle = v.findViewById(R.id.tv_title);
                tvMeta  = v.findViewById(R.id.tv_meta);
                btnEdit = v.findViewById(R.id.btn_edit);
                btnDelete= v.findViewById(R.id.btn_delete);
            }
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int vt){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_my_meal_set, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h,int pos){
            MyMealSet s = items.get(pos);
            h.itemView.setActivated(selectedId!=null && selectedId.equals(s.id));
            h.tvTitle.setText(s.name);
            String meta = "";
            if (s.breakfastTitle!=null) meta += "아침: "+s.breakfastTitle+"  ";
            if (s.lunchTitle!=null)     meta += "점심: "+s.lunchTitle+"  ";
            if (s.dinnerTitle!=null)    meta += "저녁: "+s.dinnerTitle;
            if (meta.trim().isEmpty()) { h.tvMeta.setVisibility(View.GONE);
            } else { h.tvMeta.setVisibility(View.VISIBLE); h.tvMeta.setText(meta); }
            h.itemView.setOnClickListener(v -> onPick.onPick(s));
            h.btnEdit.setOnClickListener(v -> onPick.onEdit(s));
            h.btnDelete.setOnClickListener(v -> onPick.onDelete(s));
        }
        @Override public int getItemCount(){ return items.size(); }
    }

    /** 끼니 항목 리스트 (✔ 체크박스 → 팬트리 자동 증감) */
    private class MealEntryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_EMPTY = 0, TYPE_ITEM = 1;
        private final IMealSlotListener listener;
        private final List<MealPlanEntry> items = new ArrayList<>();
        MealEntryAdapter(IMealSlotListener l){ listener=l; }

        void submit(@Nullable List<MealPlanEntry> list) { items.clear(); if (list != null) items.addAll(list); notifyDataSetChanged(); }
        @Override public int getItemViewType(int pos){ return items.isEmpty()?TYPE_EMPTY:TYPE_ITEM; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int vt){
            if(vt==TYPE_EMPTY){
                TextView tv=new TextView(p.getContext());
                tv.setText("계획된 식사가 없습니다 (추가하려면 탭)");
                tv.setTextSize(16); tv.setPadding(8,16,8,16);
                return new RecyclerView.ViewHolder(tv){};
            }else{
                View v=LayoutInflater.from(p.getContext()).inflate(R.layout.item_meal_entry,p,false);
                return new MealVH(v);
            }
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int pos){
            if(getItemViewType(pos)==TYPE_EMPTY){ h.itemView.setOnClickListener(v->listener.onAdd()); return; }
            MealVH vh=(MealVH)h; MealPlanEntry e=items.get(pos); vh.bind(e,listener);
        }
        @Override public int getItemCount(){ return items.isEmpty()?1:items.size(); }

        class MealVH extends RecyclerView.ViewHolder{
            TextView tvTitle;
            View btnEdit, btnDelete;
            @Nullable android.widget.CheckBox cbDone;

            MealVH(@NonNull View itemView){
                super(itemView);
                tvTitle  = itemView.findViewById(R.id.tv_title);
                btnEdit  = itemView.findViewById(R.id.btn_edit);
                btnDelete= itemView.findViewById(R.id.btn_delete);
                cbDone   = itemView.findViewById(R.id.cb_done);
            }
            void bind(MealPlanEntry e, IMealSlotListener l){
                tvTitle.setText(e.title);
                btnEdit.setOnClickListener(v -> l.onEdit(e));
                btnDelete.setOnClickListener(v -> l.onDelete(e));

                if (cbDone != null) {
                    cbDone.setOnCheckedChangeListener(null);
                    boolean done = false;
                    try { done = e.done; } catch (Throwable ignore) {}
                    cbDone.setChecked(done);

                    cbDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        onToggleMealDone(e, isChecked); // ★ 체크 전환시 팬트리 증감
                    });

                    itemView.setOnClickListener(v -> cbDone.setChecked(!cbDone.isChecked()));
                } else {
                    itemView.setOnClickListener(v -> l.onEdit(e));
                }
            }
        }
    }

    // ────────────────────────
    // 쇼핑리스트 보정 로직
    // ────────────────────────

    /** ShoppingActivity와 동일 포맷으로 ingredientKey 생성 */
    private static String buildKey(String id, String name) {
        if (id != null && !id.isEmpty()) return "id:" + id;
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return "name:" + n;
    }

    // 수량 파서 (amount_numeric 우선, 없으면 quantity_text에서 "숫자 [단위]" 파싱, 실패 시 1)
    private static final Pattern NUM_UNIT = Pattern.compile("\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z가-힣/%]+)?\\s*");
    private static class Parsed { final double amt; final String unit; Parsed(double a, String u){ amt=a; unit=u; } }
    private Parsed parseQuantity(@Nullable Double num, @Nullable String text, @Nullable String unit) {
        if (num != null && num > 0) return new Parsed(num, normUnit(unit));
        if (text != null) {
            Matcher m = NUM_UNIT.matcher(text);
            if (m.find()) {
                try {
                    double a = Double.parseDouble(m.group(1));
                    String u = m.group(2) != null ? m.group(2).trim() : unit;
                    return new Parsed(a, normUnit(u));
                } catch (Exception ignore) {}
            }
        }
        return new Parsed(1.0, normUnit(unit));
    }
    private String normUnit(@Nullable String u) {
        if (u == null) return null;
        String t = u.trim();
        return t.isEmpty() ? null : t;
    }

    /** 레시피 선택 시: 팬트리에 없는/부족한 재료만 쇼핑리스트에 업서트 (ID 실패 시 제목으로 재시도) */
    private void reconcileShoppingForRecipe(long recipeId, @Nullable String recipeTitle) {
        AppDatabase db = AppDatabase.get(getApplicationContext());
        RecipeDao rDao = db.recipeDao();
        PantryDao pDao = db.pantryDao();
        ShoppingDao sDao = db.shoppingDao();

        AppDatabase.DB_EXECUTOR.execute(() -> {
            List<RecipeIngredient> ings = null;

            if (recipeId > 0) ings = rDao.getIngredientsForRecipe(recipeId);
            if ((ings == null || ings.isEmpty()) && recipeTitle != null && !recipeTitle.trim().isEmpty()) {
                ings = rDao.ingredientsByRecipeTitle(recipeTitle.trim());
            }
            if (ings == null || ings.isEmpty()) return;

            long now = System.currentTimeMillis();

            for (RecipeIngredient ri : ings) {
                if (ri == null) continue;
                String name = safe(ri.ingredient_name);
                if (name.isEmpty()) continue;

                Parsed pq = parseQuantity(ri.amount_numeric, ri.quantity_text, ri.unit);
                double need = pq.amt;
                String unit = pq.unit;

                String key = buildKey(null, name);

                // 팬트리 보유량
                PantryItem pi = pDao.findByKey(key);
                double have = (pi == null) ? 0d : Math.max(0d, pi.quantity);

                double missing = need - have;
                if (missing <= 1e-6) continue;

                // 1) key 우선 병합
                ShoppingItem existByKey = sDao.byKeySync(key);
                if (existByKey != null) {
                    existByKey.neededQty = Math.max(0d, existByKey.neededQty) + missing;
                    if (unit != null) existByKey.unit = unit;
                    if (!name.isEmpty()) existByKey.name = name;
                    existByKey.updatedAt = now;
                    sDao.update(existByKey);
                    continue;
                }

                // 2) name+unit 병합
                String nm = !name.isEmpty() ? name : (pi != null && pi.name != null ? pi.name : "");
                String un = unit != null ? unit : (pi != null ? pi.unit : null);
                ShoppingItem existByName = sDao.byNameUnitSync(nm, un);
                if (existByName != null) {
                    existByName.neededQty = Math.max(0d, existByName.neededQty) + missing;
                    existByName.updatedAt = now;
                    sDao.update(existByName);
                    continue;
                }

                // 3) 신규
                ShoppingItem s = new ShoppingItem();
                s.ingredientKey = key;
                s.name = nm;
                s.unit = un;
                s.neededQty = missing;
                s.boughtQty = 0;
                s.checked = false;
                s.updatedAt = now;
                sDao.insert(s);
            }
        });
    }

    /** MyMealSet 메모 JSON의 ingredients를 라인 단위로 쇼핑리스트에 추가 */
    private void reconcileShoppingForMyMealSet(@NonNull MyMealSet set) {
        AppDatabase db = AppDatabase.get(getApplicationContext());
        PantryDao pDao = db.pantryDao();
        ShoppingDao sDao = db.shoppingDao();

        RecipePayload payload = parseRecipeJson(set.notes);
        if (payload == null || payload.ingredients == null) return;

        AppDatabase.DB_EXECUTOR.execute(() -> {
            String[] lines = payload.ingredients.split("\\r?\\n");
            long now = System.currentTimeMillis();
            for (String raw : lines) {
                String name = safe(raw);
                if (name.isEmpty()) continue;

                String key = buildKey(null, name);
                PantryItem exist = pDao.findByKey(key);
                if (exist == null || exist.quantity <= 0) {
                    ShoppingItem byKey = sDao.byKeySync(key);
                    if (byKey != null) {
                        byKey.neededQty = Math.max(0d, byKey.neededQty) + 1d;
                        byKey.updatedAt = now;
                        sDao.update(byKey);
                    } else {
                        ShoppingItem si = new ShoppingItem();
                        si.ingredientKey = key;
                        si.name = name;
                        si.unit = null;
                        si.neededQty = 1d;
                        si.boughtQty = 0d;
                        si.checked = false;
                        si.updatedAt = now;
                        sDao.insert(si);
                    }
                }
            }
        });
    }

    // ────────────────────────
    // 팬트리 소비/복원 로직 (체크 토글)
    // ────────────────────────

    /**
     * meal 'done' 토글 시 레시피 재료만큼 팬트리 수량 증감.
     * done=true  → 소비(감소), done=false → 복원(증가).
     */
    private void onToggleMealDone(@NonNull MealPlanEntry e, boolean done) {
        vm.setDone(e, done); // DB에 완료 상태 저장

        final String title = (e.title == null) ? "" : e.title.trim();
        if (title.isEmpty()) return;

        AppDatabase.DB_EXECUTOR.execute(() ->
                applyPantryDeltaForRecipe(title, done ? -1 : +1));
    }

    /**
     * 주어진 레시피 제목을 로컬 DB에서 찾아 재료 리스트를 얻고,
     * factor(-1/ +1)에 따라 팬트리 수량을 증감.
     */
    private void applyPantryDeltaForRecipe(@NonNull String recipeTitle, int factor) {
        try {
            com.example.kitchenlife.data.Recipe r = recipeDao.findByTitleSync(recipeTitle);
            if (r == null) return;
            List<RecipeIngredient> ings = recipeDao.getIngredientsForRecipe(r.id);
            if (ings == null || ings.isEmpty()) return;

            long now = System.currentTimeMillis();
            for (RecipeIngredient ri : ings) {
                String name = safe(ri.ingredient_name);
                if (name.isEmpty()) continue;

                double base = (ri.amount_numeric == null || ri.amount_numeric <= 0) ? 1d : ri.amount_numeric;
                double delta = base * factor; // - 소비, + 복원
                String key = buildKey(null, name);

                PantryItem item = pantryDao.findByKey(key);
                if (item == null) {
                    // 복원(+1)인 경우에만 새로 만들어줌. 소비(-1)인데 없으면 skip.
                    if (delta > 0) {
                        PantryItem p = new PantryItem();
                        p.ingredientKey = key;
                        p.name = name;
                        p.unit = (ri.unit == null || ri.unit.trim().isEmpty()) ? null : ri.unit.trim();
                        p.quantity = Math.max(0d, delta);
                        p.updatedAt = now;
                        pantryDao.insert(p);
                    }
                } else {
                    double q = item.quantity + delta;
                    if (q < 0) q = 0; // 음수 방지
                    // 레시피에 단위가 명시되어 있으면 최신 단위 유지
                    if (ri.unit != null && !ri.unit.trim().isEmpty()) item.unit = ri.unit.trim();
                    item.quantity = q;
                    item.updatedAt = now;
                    pantryDao.update(item);
                }
            }
        } catch (Throwable ignore) {}
    }

    // ───────── Supabase에서 레시피 목록 로드 ─────────
    private void fetchRecipes(@Nullable String titleLike){
        final String select = "id,title";
        final String order  = "created_at.desc";
        Integer limit = 500, offset = 0;
        String titleFilter = (titleLike==null || titleLike.isEmpty()) ? null : "ilike.*"+titleLike+"*";

        api().listRecipes(select, order, limit, offset, titleFilter).enqueue(new Callback<List<Recipe>>() {
            @Override public void onResponse(@NonNull Call<List<Recipe>> c, @NonNull Response<List<Recipe>> r) {
                if (r.isSuccessful() && r.body()!=null) {
                    masterRecipes.clear();
                    masterRecipes.addAll(r.body());
                    recipeAdapter.submit(new ArrayList<>(masterRecipes));
                    tvEmptyRecipes.setVisibility(masterRecipes.isEmpty()?View.VISIBLE:View.GONE);
                } else {
                    seedRecipes();
                    recipeAdapter.submit(new ArrayList<>(masterRecipes));
                }
            }
            @Override public void onFailure(@NonNull Call<List<Recipe>> c, @NonNull Throwable t) {
                seedRecipes();
                recipeAdapter.submit(new ArrayList<>(masterRecipes));
            }
        });
    }

    private void seedRecipes(){
        if (!masterRecipes.isEmpty()) return;
        Recipe a = new Recipe(); a.id=1; a.title="Fried Rice";
        Recipe b = new Recipe(); b.id=2; b.title="Chicken Salad";
        Recipe c = new Recipe(); c.id=3; c.title="Tomato Pasta";
        Recipe d = new Recipe(); d.id=4; d.title="Kimchi Stew";
        Recipe e = new Recipe(); e.id=5; e.title="Grilled Salmon";
        masterRecipes.add(a); masterRecipes.add(b); masterRecipes.add(c); masterRecipes.add(d); masterRecipes.add(e);
    }

    // ───────── 메모(JSON) 직렬화/파싱 ─────────
    private static class RecipePayload { String ingredients; String steps; String tags; String note; }

    private String toRecipeJson(String ingredients, String steps, String tags, String note) {
        String esc = "\"";
        return "{"
                + "\"ingredients\":" + esc + ingredients.replace("\"","\\\"") + esc + ","
                + "\"steps\":"       + esc + steps.replace("\"","\\\"")       + esc + ","
                + "\"tags\":"        + esc + tags.replace("\"","\\\"")        + esc + ","
                + "\"note\":"        + esc + note.replace("\"","\\\"")        + esc
                + "}";
    }

    @Nullable private RecipePayload parseRecipeJson(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) return null;
        RecipePayload p = new RecipePayload();
        try {
            String s = json;
            p.ingredients = between(s, "\"ingredients\":\"", "\"", true);
            p.steps       = between(s, "\"steps\":\"",       "\"", true);
            p.tags        = between(s, "\"tags\":\"",        "\"", true);
            p.note        = between(s, "\"note\":\"",        "\"", true);
            if (p.ingredients == null) p.ingredients = "";
            if (p.steps == null)       p.steps = "";
            if (p.tags == null)        p.tags = "";
            if (p.note == null)        p.note = "";
            return p;
        } catch (Exception e) { return null; }
    }
    @Nullable private String between(String s, String prefix, String suffix, boolean unescapeQuotes) {
        int a = s.indexOf(prefix); if (a < 0) return null; a += prefix.length();
        int b = s.indexOf(suffix, a); if (b < 0) return null;
        String v = s.substring(a, b);
        return unescapeQuotes ? v.replace("\\\"", "\"") : v;
    }

    // ViewFlipper helper (0=week, 1=month)
    private static class ViewFlipperCompat {
        private final android.widget.ViewFlipper vf;
        ViewFlipperCompat(View v){ vf=(android.widget.ViewFlipper)v; }
        void showWeek(){ vf.setDisplayedChild(0); }
        void showMonth(){ vf.setDisplayedChild(1); }
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // 날짜 → 키
    private long dateKey(LocalDate d){ return d.getYear()*10000L + d.getMonthValue()*100L + d.getDayOfMonth(); }
}
