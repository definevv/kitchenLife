package com.example.kitchenlife;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.example.kitchenlife.data.MyMealSet;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.data.MealPlanEntry;
import com.example.kitchenlife.ui.mealplan.MealPlanViewModel;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * WEEK / MONTH 캘린더 + 레시피/내식단(MY MEALS) 바텀시트
 */
public class MealPlanActivity extends AppCompatActivity {

    // ───────── 상단 UI ─────────
    private RecyclerView rvBreakfast, rvLunch, rvDinner;
    private CardView slotBreakfast, slotLunch, slotDinner;
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
    private LocalDate weekAnchor   = LocalDate.now();  // 주 뷰의 기준(선택일 포함 주)
    private YearMonth monthAnchor  = YearMonth.now();  // 월 뷰의 기준

    private int editingMealType = 0; // 0=B,1=L,2=D
    @Nullable private MealPlanEntry editingEntry = null;

    // 리스트 어댑터
    private MealEntryAdapter breakfastAdapter, lunchAdapter, dinnerAdapter;
    private WeekDaysAdapter   weekDaysAdapter;
    private MonthGridAdapter  monthGridAdapter;
    private RecipeAdapter     recipeAdapter;
    private MyMealsAdapter    myMealsAdapter;

    // 데이터(로컬)
    private final List<Recipe> masterRecipes = new ArrayList<>();
    @Nullable private Recipe selectedRecipe = null;

    // MY MEALS 선택/관찰
    @Nullable private MyMealSet selectedSet = null;
    @Nullable private LiveData<List<MyMealSet>> myMealsLive = null;

    private final DateTimeFormatter weekTitleFmt = DateTimeFormatter.ofPattern("'Week of' yyyy-MM-dd");
    private final DateTimeFormatter dayLabelFmt  = DateTimeFormatter.ofPattern("EEEE, d");

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_plan);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Meal Plan");
        }

        vm = new ViewModelProvider(this).get(MealPlanViewModel.class);

        bindViews();
        setupCalendarLists();
        setupBottomSheet();
        setupMealLists();
        seedRecipes();
        observeMealsForDay();
        bindClicks();

        // 초기 렌더: WEEK 활성화 + 타이틀/리스트
        viewModeToggle.check(R.id.btn_week);
        viewFlipper.showWeek();
        updateWeekTitle();
        renderSelectedDayLabel();
        renderWeekStrip();
        renderMonthGrid();
        recipeAdapter.submit(new ArrayList<>(masterRecipes));
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
        // Week strip (가로 7칩)
        weekDaysAdapter = new WeekDaysAdapter(d -> {
            selectedDate = d;
            renderSelectedDayLabel();
            observeMealsForDay(); // 선택 바뀌면 끼니 목록도 바뀜
            weekDaysAdapter.setSelected(d);
        });
        rvWeekDays.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvWeekDays.setAdapter(weekDaysAdapter);

        // Month grid (7x6)
        monthGridAdapter = new MonthGridAdapter(day -> {
            selectedDate = day;
            weekAnchor   = day; // 주기준도 같이 맞춰줌
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
                renderWeekStrip();
                updateWeekTitle();
            } else {
                monthAnchor = monthAnchor.minusMonths(1);
                renderMonthGrid();
                updateMonthTitle();
            }
        });
        findViewById(R.id.btn_next_range).setOnClickListener(v -> {
            if (btnWeek.isChecked()) {
                weekAnchor = weekAnchor.plusWeeks(1);
                renderWeekStrip();
                updateWeekTitle();
            } else {
                monthAnchor = monthAnchor.plusMonths(1);
                renderMonthGrid();
                updateMonthTitle();
            }
        });
    }

    private void updateWeekTitle() {
        tvRangeTitle.setText(selectedDate.format(weekTitleFmt));
    }
    private void updateMonthTitle() {
        tvRangeTitle.setText(monthAnchor.getMonth().name() + " " + monthAnchor.getYear());
    }
    private void renderSelectedDayLabel() {
        tvSelectedDay.setText(selectedDate.format(dayLabelFmt));
    }
    private void renderWeekStrip() {
        LocalDate start = weekStart(weekAnchor, DayOfWeek.SUNDAY);
        List<LocalDate> seven = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) seven.add(start.plusDays(i));
        weekDaysAdapter.submit(seven, selectedDate);
        rvWeekDays.scrollToPosition(selectedDate.getDayOfWeek().getValue() % 7);
    }
    private void renderMonthGrid() {
        monthGridAdapter.submit(monthAnchor, selectedDate);
    }
    private static LocalDate weekStart(LocalDate any, DayOfWeek first) {
        return any.with(TemporalAdjusters.previousOrSame(first));
    }

    // ───────── BottomSheet ─────────
    private void setupBottomSheet() {
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        btnCloseSheet.setOnClickListener(v ->
                sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));

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
                    // 레시피 필터
                    List<Recipe> filtered = new ArrayList<>();
                    for (Recipe r : masterRecipes)
                        if (r.title.toLowerCase().contains(q.toLowerCase())) filtered.add(r);
                    recipeAdapter.submit(filtered);
                    tvEmptyRecipes.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    // MY MEALS는 Room 쿼리로 검색
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
        btnPrimarySheet.setText("ADD TO MEAL");
        btnPrimarySheet.setOnClickListener(v -> onClickPrimaryInRecipesMode());
    }

    // ───────── 바텀시트 모드 전환 ─────────
    private void showRecipesMode() {
        groupRecipes.setVisibility(View.VISIBLE);
        groupMyMeals.setVisibility(View.GONE);

        btnPrimarySheet.setText(editingEntry == null ? "ADD TO MEAL" : "UPDATE MEAL");
        btnPrimarySheet.setVisibility(View.VISIBLE);
        btnPrimarySheet.setOnClickListener(v -> onClickPrimaryInRecipesMode());
    }

    private void showMyMealsMode() {
        groupRecipes.setVisibility(View.GONE);
        groupMyMeals.setVisibility(View.VISIBLE);

        btnPrimarySheet.setVisibility(View.VISIBLE);
        btnPrimarySheet.setText("ADD TO MEAL");
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

    private static class RecipePayload {
        String ingredients; // 줄바꿈으로 구분된 재료
        String steps;       // 줄바꿈으로 구분된 단계
        String tags;        // 콤마 구분 태그
        String note;        // 메모
    }

    private String toRecipeJson(String ingredients, String steps, String tags, String note) {
        // 매우 가벼운 JSON 직렬화 (의존성 없이)
        // 줄바꿈은 \n 그대로 저장
        String esc = "\"";
        return "{"
                + "\"ingredients\":" + esc + ingredients.replace("\"","\\\"") + esc + ","
                + "\"steps\":"       + esc + steps.replace("\"","\\\"")       + esc + ","
                + "\"tags\":"        + esc + tags.replace("\"","\\\"")        + esc + ","
                + "\"note\":"        + esc + note.replace("\"","\\\"")        + esc
                + "}";
    }

    @Nullable
    private RecipePayload parseRecipeJson(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) return null;
        RecipePayload p = new RecipePayload();
        try {
            // 아주 단순 파서(프로덕션이면 Gson/Moshi 권장)
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
        } catch (Exception e) {
            return null;
        }
    }

    /** "prefix ... suffix" 사이 값 꺼내기 */
    @Nullable
    private String between(String s, String prefix, String suffix, boolean unescapeQuotes) {
        int a = s.indexOf(prefix);
        if (a < 0) return null;
        a += prefix.length();
        int b = s.indexOf(suffix, a);
        if (b < 0) return null;
        String v = s.substring(a, b);
        return unescapeQuotes ? v.replace("\\\"", "\"") : v;
    }


    // ───────── 시트 내부 동작 ─────────
    private void onClickPrimaryInRecipesMode() {
        if (selectedRecipe == null) return;
        long key = dateKey(selectedDate);
        if (editingEntry == null) {
            vm.add(key, editingMealType, selectedRecipe.title);
        } else {
            vm.update(editingEntry, selectedRecipe.title);
        }
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void onClickPrimaryInMyMealsMode() {
        if (selectedSet == null) {
            Toast.makeText(this, "Select a meal set.", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = selectedSet.name == null ? "" : selectedSet.name.trim();
        if (title.isEmpty()) {
            android.widget.Toast.makeText(this, "Selected recipe has no title.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        long key = dateKey(selectedDate);
        if (editingEntry == null) vm.add(key, editingMealType, title);
        else vm.update(editingEntry, title);

        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
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

        recipeAdapter = new RecipeAdapter(r -> {
            selectedRecipe = r;
            recipeAdapter.setSelectedId(r.id);
        });
        rvRecipeLibrary.setLayoutManager(new LinearLayoutManager(this));
        rvRecipeLibrary.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvRecipeLibrary.setAdapter(recipeAdapter);

        // MY MEALS 리스트
        myMealsAdapter = new MyMealsAdapter(new MyMealsAdapter.OnPick() {
            @Override public void onPick(MyMealSet s) {
                selectedSet = s;
                myMealsAdapter.setSelectedId(s.id);
            }
            @Override public void onEdit(MyMealSet s) {
                openMyMealEditorPrefilled(s);
            }
            @Override public void onDelete(MyMealSet s) {
                vm.deleteMyMealSet(s);
            }
        });
        rvMyMeals.setLayoutManager(new LinearLayoutManager(this));
        rvMyMeals.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvMyMeals.setAdapter(myMealsAdapter);

        btnCreateMyMeals.setOnClickListener(v -> openMyMealEditor());
    }

    private void observeMealsForDay() {
        long key = dateKey(selectedDate);
        vm.meals(key, 0).observe(this, breakfastAdapter::submit);
        vm.meals(key, 1).observe(this, lunchAdapter::submit);
        vm.meals(key, 2).observe(this, dinnerAdapter::submit);
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
        if (checked == R.id.btn_mode_recipes) showRecipesMode();
        else showMyMealsMode();

        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    // ───────── My Meals 에디터 ─────────
    private void openMyMealEditorPrefilled(com.example.kitchenlife.data.MyMealSet s) {
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

        // notes(JSON) → 폼에 되살리기
        RecipePayload p = parseRecipeJson(s.notes);
        if (p != null) {
            etIngr.setText(p.ingredients);
            etSteps.setText(p.steps);
            etTags.setText(p.tags);
            etNotes.setText(p.note);
        }

        btnSave.setText("UPDATE RECIPE");
        btnSave.setOnClickListener(v -> {
            s.name  = safe(etTitle.getText());
            s.notes = toRecipeJson(
                    safe(etIngr.getText()),
                    safe(etSteps.getText()),
                    safe(etTags.getText()),
                    safe(etNotes.getText())
            );
            vm.saveMyMealSet(s);
            android.widget.Toast.makeText(this, "Updated recipe", android.widget.Toast.LENGTH_SHORT).show();
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

        btnSave.setOnClickListener(v -> {
            String title = safe(etTitle.getText());
            if (title.isEmpty()) { etTitle.setError("Please enter recipe title"); return; }

            // MyMealSet 을 "내 레시피" 용도로 재사용:
            //  - name  : 레시피 제목
            //  - notes : JSON(ingredients/steps/tags/notes)로 직렬화
            com.example.kitchenlife.data.MyMealSet set = new com.example.kitchenlife.data.MyMealSet();
            set.name  = title;
            set.notes = toRecipeJson(
                    safe(etIngr.getText()),
                    safe(etSteps.getText()),
                    safe(etTags.getText()),
                    safe(etNotes.getText())
            );

            vm.saveMyMealSet(set);
            android.widget.Toast.makeText(this, "Saved recipe", android.widget.Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });

        dlg.show();
    }

    private static String safe(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    // ───────── 어댑터들 ─────────

    /** WEEK: 7칩 가로 리스트 */
    private static class WeekDaysAdapter extends RecyclerView.Adapter<WeekDaysAdapter.VH> {
        interface OnPick { void onPick(LocalDate d); }
        private final OnPick onPick;
        private final List<LocalDate> days = new ArrayList<>();
        private @Nullable LocalDate selected;

        WeekDaysAdapter(OnPick onPick) { this.onPick = onPick; }
        void submit(List<LocalDate> list, @Nullable LocalDate sel) {
            days.clear(); days.addAll(list); selected = sel; notifyDataSetChanged();
        }
        void setSelected(LocalDate d) { selected = d; notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_week_day, p, false);
            return new VH(view);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            LocalDate d = days.get(pos);
            h.bind(d, selected, onPick);
        }
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
                tvDow.setText(d.getDayOfWeek().name().substring(0,3));
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
            anchor = ym;
            selected = sel;
            cells.clear();
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
            TextView tv;
            MaterialCardView card;
            VH(@NonNull View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView;
                tv   = itemView.findViewById(R.id.tv_month_day);
            }
            void bind(LocalDate d, boolean inMonth, boolean selected, OnPick onPick) {
                tv.setText(String.valueOf(d.getDayOfMonth()));
                tv.setAlpha(inMonth ? 1f : 0.35f);
                card.setChecked(selected);
                card.setStrokeWidth(selected ? 0 : 1);
                card.setOnClickListener(v -> onPick.onPick(d));
            }
        }
    }

    /** 끼니 항목 리스트 */
    private class MealEntryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_EMPTY = 0, TYPE_ITEM = 1;
        private final IMealSlotListener listener;
        private final List<MealPlanEntry> items = new ArrayList<>();
        MealEntryAdapter(IMealSlotListener l){ listener=l; }

        void submit(@Nullable List<MealPlanEntry> list) {
            items.clear(); if (list != null) items.addAll(list); notifyDataSetChanged();
        }
        @Override public int getItemViewType(int pos){ return items.isEmpty()?TYPE_EMPTY:TYPE_ITEM; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p,int vt){
            if(vt==TYPE_EMPTY){
                TextView tv=new TextView(p.getContext());
                tv.setText("No meal planned (tap to add)");
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
            TextView tvTitle; ImageButton btnEdit, btnDelete;
            MealVH(@NonNull View itemView){ super(itemView);
                tvTitle=itemView.findViewById(R.id.tv_title);
                btnEdit=itemView.findViewById(R.id.btn_edit);
                btnDelete=itemView.findViewById(R.id.btn_delete);
            }
            void bind(MealPlanEntry e, IMealSlotListener l){
                tvTitle.setText(e.title);
                btnEdit.setOnClickListener(v->l.onEdit(e));
                btnDelete.setOnClickListener(v->l.onDelete(e)); // 즉시 삭제
            }
        }
    }

    /** 레시피 목록 */
    private static class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.VH> {
        interface OnPick { void onPick(Recipe r); }
        private final OnPick onPick;
        private final List<Recipe> items = new ArrayList<>();
        @Nullable private Long selectedId=null;

        RecipeAdapter(OnPick p){ onPick=p; }
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
            TextView tvTitle; VH(@NonNull View itemView){ super(itemView); tvTitle=itemView.findViewById(R.id.tv_title);}
        }
    }

    /** MY MEALS 리스트 어댑터 */
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
            if (s.breakfastTitle!=null) meta += "B: "+s.breakfastTitle+"  ";
            if (s.lunchTitle!=null)     meta += "L: "+s.lunchTitle+"  ";
            if (s.dinnerTitle!=null)    meta += "D: "+s.dinnerTitle;

            if (meta.trim().isEmpty()) {
                h.tvMeta.setVisibility(View.GONE);      // ★ 빈 경우 감추기
            } else {
                h.tvMeta.setVisibility(View.VISIBLE);   // ★ 재활용 대비 다시 보이게
                h.tvMeta.setText(meta);
            }

            h.itemView.setOnClickListener(v -> onPick.onPick(s));
            h.btnEdit.setOnClickListener(v -> onPick.onEdit(s));
            h.btnDelete.setOnClickListener(v -> onPick.onDelete(s));
        }
        @Override public int getItemCount(){ return items.size(); }
    }

    // 리스너들
    private class SlotListener implements IMealSlotListener {
        private final int mealType; SlotListener(int m){ mealType=m; }
        @Override public void onAdd(){ openSheetForAddOrEdit(mealType,null); }
        @Override public void onEdit(MealPlanEntry e){ openSheetForAddOrEdit(mealType,e); }
        @Override public void onDelete(MealPlanEntry e){ vm.delete(e); }
    }
    private interface IMealSlotListener {
        void onAdd(); void onEdit(MealPlanEntry e); void onDelete(MealPlanEntry e);
    }

    // ───────── 유틸/데이터 ─────────
    public static class Recipe { public long id; public String title; public Recipe(long id,String t){this.id=id;this.title=t;} }

    private long dateKey(LocalDate d){ return d.getYear()*10000L + d.getMonthValue()*100L + d.getDayOfMonth(); }
    private void seedRecipes(){
        masterRecipes.add(new Recipe(1,"Fried Rice"));
        masterRecipes.add(new Recipe(2,"Chicken Salad"));
        masterRecipes.add(new Recipe(3,"Tomato Pasta"));
        masterRecipes.add(new Recipe(4,"Kimchi Stew"));
        masterRecipes.add(new Recipe(5,"Grilled Salmon"));
    }

    /** 내 식단 세트 모델 (Room 연동 전 임시/또는 Room 엔티티와 동일 필드) */

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
}
