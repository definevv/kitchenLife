package com.example.kitchenlife;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.PantryDao;
import com.example.kitchenlife.data.PantryItem;
import com.example.kitchenlife.data.ShoppingDao;
import com.example.kitchenlife.data.ShoppingItem;
import com.example.kitchenlife.data.ShoppingRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shopping 리스트 화면: 수동 추가 + 레시피에서 추가 + 체크/이동/삭제 */
public class ShoppingActivity extends AppCompatActivity {

    // ---- UI ----
    private View progress;
    private TextView tvSelectAll;
    private MaterialButton btnMoveToPantry;
    private MaterialButton btnDeleteSelected;

    // ---- DB/Repo ----
    private ShoppingDao shoppingDao;
    private PantryDao pantryDao;
    private ShoppingRepository repo;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ShoppingAdapter adapter;

    // 결과 재배달/중복 처리 플래그
    private boolean resultConsumed = false;

    private ActivityResultLauncher<Intent> pickRecipeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("장보기");
        }

        progress = findViewById(R.id.progress_overlay);
        if (progress != null) progress.setVisibility(View.GONE);

        tvSelectAll       = findViewById(R.id.tv_select_all);
        btnMoveToPantry   = findViewById(R.id.btn_move_to_pantry);
        btnDeleteSelected = findViewById(R.id.btn_delete_selected);

        // DB / Repo
        AppDatabase db = AppDatabase.get(this);
        shoppingDao = db.shoppingDao();
        pantryDao   = db.pantryDao();
        repo        = new ShoppingRepository(db);

        // RecyclerView
        RecyclerView rv = findViewById(R.id.rv_shopping);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new ShoppingAdapter(
                // ✔ 체크 토글 → DB 저장 + 즉시 UI 반영 + 목록 새로고침
                (s, checked) -> {
                    io.execute(() -> {
                        long now = System.currentTimeMillis();
                        double keepBought = (s.boughtQty <= 0d) ? 0d : s.boughtQty;
                        shoppingDao.setChecked(s.id, checked, keepBought, now);
                        runOnUiThread(() -> {
                            // 로컬 모델 업데이트
                            s.checked = checked;
                            updateActionButtons();
                            updateSelectAllLabel();
                            // 새로고침(재정렬/필터 등 반영 필요시)
                            reloadShoppingList();
                        });
                    });
                },
                // 수량/단위 저장 → DB 저장 + 메타텍스트 갱신
                (s, qty, unit) -> io.execute(() -> {
                    String u = (unit == null || unit.trim().isEmpty()) ? null : unit.trim();
                    shoppingDao.updateBoughtAndUnit(
                            s.id,
                            Math.max(0d, qty),
                            u,
                            System.currentTimeMillis()
                    );
                    // 저장 직후 리스트 재로딩(필요 수량/구매 예정 레이블 재계산)
                    runOnUiThread(this::reloadShoppingList);
                })
        );
        rv.setAdapter(adapter);

        // ActivityResult (레시피 선택)
        pickRecipeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (resultConsumed) return;
                    if (r.getResultCode() != RESULT_OK || r.getData() == null) return;

                    Intent data = r.getData();
                    ArrayList<String> lines =
                            data.getStringArrayListExtra(RecipePickerActivity.EXTRA_INGREDIENT_LINES);
                    if (lines == null || lines.isEmpty()) {
                        Toast.makeText(this, "레시피 재료가 비었습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    resultConsumed = true; // 한 번만 처리
                    addFromRecipeLines(lines);
                }
        );

        // 상단 버튼들
        MaterialButton btnAdd        = findViewById(R.id.btn_add_item);
        MaterialButton btnFromRecipe = findViewById(R.id.btn_from_recipe);

        btnAdd.setOnClickListener(v -> showAddSheet());
        btnFromRecipe.setOnClickListener(v -> {
            resultConsumed = false;
            Intent it = new Intent(this, RecipePickerActivity.class);
            pickRecipeLauncher.launch(it);
        });

        // 모두 선택/해제
        if (tvSelectAll != null) {
            tvSelectAll.setOnClickListener(v -> toggleSelectAll());
        }

        // 팬트리로 이동
        if (btnMoveToPantry != null) {
            btnMoveToPantry.setOnClickListener(v -> moveCheckedToPantry());
        }

        // 리스트 삭제
        if (btnDeleteSelected != null) {
            btnDeleteSelected.setOnClickListener(v -> {
                if (!hasAnyChecked()) {
                    Toast.makeText(this, "선택된 항목이 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle("삭제")
                        .setMessage("선택한 항목을 삭제할까요?")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("삭제", (d, w) -> deleteChecked())
                        .show();
            });
        }

        // 최초 로드
        rv.postDelayed(this::reloadShoppingList, 200);

        // 필요 시 Room 관찰 전환 가능(DAO에 LiveData가 있을 때)
        // shoppingDao.observeAll().observe(this, list -> {
        //     adapter.submit(list);
        //     updateSelectAllLabel();
        //     updateActionButtons();
        // });
    }

    @Override protected void onResume() {
        super.onResume();
        // 돌아왔을 때 반영 안되는 느낌 방지
        reloadShoppingList();
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    /* ---------------- 레시피 추가 ---------------- */

    /** 레시피 픽커에서 받은 라인들을 저장(백그라운드) */
    private void addFromRecipeLines(ArrayList<String> lines) {
        showProgress(true);
        io.execute(() -> {
            int added = 0;
            Throwable err = null;
            try {
                // ShoppingRepository.addFromPickerLines(...) 가 int(추가/가산된 항목 수) 반환
                added = repo.addFromPickerLines(lines, null);
            } catch (Throwable t) {
                err = t;
            }
            final int fAdded = added;
            final Throwable fErr = err;
            runOnUiThread(() -> {
                showProgress(false);
                if (fErr != null) {
                    Toast.makeText(this, "추가 실패: " + fErr.getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, (fAdded > 0) ? "쇼핑리스트에 추가됨" : "추가된 항목 없음", Toast.LENGTH_SHORT).show();
                    reloadShoppingList();
                }
            });
        });
    }

    private void showProgress(boolean show){
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /* ---------------- 수동 추가 ---------------- */

    private void showAddSheet() {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_add_shopping, null, false);
        dlg.setContentView(v);

        final EditText etName = v.findViewById(R.id.et_item_name);
        final EditText etQty  = v.findViewById(R.id.et_item_qty);
        final EditText etUnit = v.findViewById(R.id.et_item_unit);

        v.findViewById(R.id.btn_submit_item).setOnClickListener(b -> {
            String name = safe(etName.getText());
            String unit = safe(etUnit.getText());
            double qty  = parseDoubleSafe(etQty.getText());

            if (name.isEmpty() || qty <= 0) {
                Toast.makeText(this, "이름과 수량을 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }

            final double fQty = qty;
            final String fUnit = unit.isEmpty()? null : unit.trim();

            io.execute(() -> {
                ShoppingItem s = new ShoppingItem();
                s.ingredientKey = buildKey(null, name);
                s.name = name;
                s.unit = fUnit;                  // "" 는 null 로
                s.neededQty = Math.max(0d, fQty);
                s.boughtQty = 0;
                s.checked = false;
                s.updatedAt = System.currentTimeMillis();
                shoppingDao.upsertAddAll(Collections.singletonList(s));
                runOnUiThread(() -> {
                    Toast.makeText(this, "추가됨", Toast.LENGTH_SHORT).show();
                    reloadShoppingList();
                });
            });
            dlg.dismiss();
        });

        dlg.show();
    }

    private static String buildKey(String id, String name) {
        if (id != null && !id.isEmpty()) return "id:" + id;
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return "name:" + n;
    }

    /* ---------------- 목록 로드 ---------------- */

    /** DB에서 목록을 로드해 어댑터에 반영 */
    private void reloadShoppingList() {
        io.execute(() -> {
            List<ShoppingItem> items = shoppingDao.loadAll();
            runOnUiThread(() -> {
                adapter.submit(items);
                updateSelectAllLabel();
                updateActionButtons();
            });
        });
    }

    /* ---------------- 모두 선택/해제 ---------------- */

    private void toggleSelectAll() {
        final List<ShoppingItem> snapshot = adapter.getData();
        if (snapshot.isEmpty()) return;

        // 현재 모두 체크되어 있으면 해제, 아니면 모두 체크
        boolean allChecked = true;
        for (ShoppingItem s : snapshot) {
            if (!s.checked) { allChecked = false; break; }
        }
        final boolean target = !allChecked;

        showProgress(true);
        io.execute(() -> {
            long now = System.currentTimeMillis();
            for (ShoppingItem s : snapshot) {
                try {
                    double keepBought = (s.boughtQty <= 0d) ? 0d : s.boughtQty;
                    shoppingDao.setChecked(s.id, target, keepBought, now);
                } catch (Throwable ignore) {}
            }
            runOnUiThread(() -> {
                showProgress(false);
                reloadShoppingList();
            });
        });
    }

    private void updateSelectAllLabel() {
        if (tvSelectAll == null) return;
        List<ShoppingItem> data = adapter.getData();
        if (data.isEmpty()) { tvSelectAll.setText("전체 선택"); return; }
        boolean allChecked = true;
        for (ShoppingItem s : data) { if (!s.checked) { allChecked = false; break; } }
        tvSelectAll.setText(allChecked ? "전체 취소" : "전체 선택");
    }

    /* ---------------- 버튼 활성화/비활성 갱신 ---------------- */

    private boolean hasAnyChecked() {
        for (ShoppingItem s : adapter.getData()) if (s.checked) return true;
        return false;
    }

    private void updateActionButtons() {
        boolean any = hasAnyChecked();
        if (btnDeleteSelected != null) btnDeleteSelected.setEnabled(any);
        if (btnMoveToPantry   != null) btnMoveToPantry.setEnabled(any);
    }

    /* ---------------- 리스트 삭제 ---------------- */

    private void deleteChecked() {
        showProgress(true);
        io.execute(() -> {
            try {
                shoppingDao.deleteAllChecked();
                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show();
                    reloadShoppingList();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(this, "삭제 실패: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /* ---------------- 팬트리로 이동 ---------------- */

    private void moveCheckedToPantry() {
        showProgress(true);
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) currentFocus.clearFocus();
        io.execute(() -> {
            try {
                // 1) 체크된 쇼핑 항목 조회
                List<ShoppingItem> checked = shoppingDao.checkedSync();
                if (checked == null || checked.isEmpty()) {
                    runOnUiThread(() -> {
                        showProgress(false);
                        Toast.makeText(this, "선택된 항목이 없습니다.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 2) 팬트리 가산 업서트 목록으로 변환 (boughtQty 있으면 우선)
                List<PantryItem> toUpsert = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (ShoppingItem s : checked) {
                    double planned = (s.boughtQty > 0d ? s.boughtQty : s.neededQty);
                    PantryItem p = new PantryItem();
                    p.ingredientKey = s.ingredientKey;
                    p.name = s.name;
                    p.unit = (s.unit == null || s.unit.trim().isEmpty()) ? null : s.unit.trim();
                    p.quantity = Math.max(0d, planned);
                    p.updatedAt = now;
                    toUpsert.add(p);
                }

                // 3) 팬트리에 가산 업서트
                pantryDao.upsertIncreaseAll(toUpsert);

                // 4) 쇼핑리스트에서 체크된 항목 삭제
                shoppingDao.deleteAllChecked();

                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(this, "팬트리에 반영되었습니다.", Toast.LENGTH_SHORT).show();
                    reloadShoppingList();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(this, "이동 실패: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /* ---------------- 어댑터 ---------------- */

    interface OnToggle { void onToggleChecked(ShoppingItem s, boolean checked); }
    interface OnEdit   { void onQtyUnitChanged(ShoppingItem s, double qty, String unit); }

    private static class ShoppingAdapter extends RecyclerView.Adapter<ShoppingAdapter.VH> {
        private final List<ShoppingItem> data = new ArrayList<>();
        private final OnToggle onToggle;
        private final OnEdit onEdit;

        ShoppingAdapter(OnToggle onToggle, OnEdit onEdit) {
            this.onToggle = onToggle;
            this.onEdit   = onEdit;
        }

        List<ShoppingItem> getData(){ return new ArrayList<>(data); }

        void submit(List<ShoppingItem> items){
            data.clear();
            if(items!=null) data.addAll(items);
            notifyDataSetChanged();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvMeta; CheckBox cb;
            EditText etQty, etUnit;
            VH(@NonNull View v){
                super(v);
                tvName = v.findViewById(R.id.tv_name);
                tvMeta = v.findViewById(R.id.tv_meta);
                cb     = v.findViewById(R.id.cb);
                etQty  = v.findViewById(R.id.et_buy_qty);   // 수량 입력 (ID 고정)
                etUnit = v.findViewById(R.id.et_buy_unit);  // 단위 입력 (ID 고정)
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_shopping, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            ShoppingItem s = data.get(i);

            h.tvName.setText(s.name);

            // 메타: 구매예정(boughtQty) 우선, 없으면 need
            String unitSuffix = (s.unit == null || s.unit.isEmpty()) ? "" : (" " + s.unit);
            double planned = (s.boughtQty > 0d ? s.boughtQty : s.neededQty);
            String label   = (s.boughtQty > 0d) ? "구매 예정 " : "필요 ";
            h.tvMeta.setText(label + trimZero(planned) + unitSuffix);

            // 체크박스 (저장 포함)
            h.cb.setOnCheckedChangeListener(null);
            h.cb.setChecked(s.checked);
            h.cb.setOnCheckedChangeListener((btn, checked) -> {
                s.checked = checked;
                if (onToggle != null) onToggle.onToggleChecked(s, checked);
            });
            h.itemView.setOnClickListener(v -> h.cb.setChecked(!h.cb.isChecked()));

            // 수량/단위 표시
            h.etQty.setText(s.boughtQty > 0 ? trimZero(s.boughtQty) : "");
            h.etUnit.setText(s.unit == null ? "" : s.unit);

            // 저장 로직
            Runnable persistAndRefresh = () -> {
                double q = parseDoubleSafe(h.etQty.getText());
                String u = safe(h.etUnit.getText());
                s.boughtQty = Math.max(0d, q);
                s.unit = (u.isEmpty()? null : u);
                if (onEdit != null) onEdit.onQtyUnitChanged(s, s.boughtQty, s.unit);

                String unitNow = (s.unit == null || s.unit.isEmpty()) ? "" : (" " + s.unit);
                double p2 = (s.boughtQty > 0d ? s.boughtQty : s.neededQty);
                String labelNow = (s.boughtQty > 0d) ? "구매 예정 " : "필요 ";
                h.tvMeta.setText(labelNow + trimZero(p2) + unitNow);
            };

            // 포커스 해제 시 저장
            View.OnFocusChangeListener focusSaver = (v, hasFocus) -> { if (!hasFocus) persistAndRefresh.run(); };
            h.etQty.setOnFocusChangeListener(focusSaver);
            h.etUnit.setOnFocusChangeListener(focusSaver);

            // IME 완료 시 저장
            TextView.OnEditorActionListener imeDoneSaver = (tv, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) { persistAndRefresh.run(); return true; }
                return false;
            };
            h.etQty.setOnEditorActionListener(imeDoneSaver);
            h.etUnit.setOnEditorActionListener(imeDoneSaver);
        }

        @Override public int getItemCount() { return data.size(); }

        private static double parseDoubleSafe(CharSequence s){
            try { return Double.parseDouble(s.toString().trim()); } catch (Exception e){ return 0d; }
        }
        private static String trimZero(double v){
            String t = String.valueOf(v);
            return t.endsWith(".0") ? t.substring(0, t.length()-2) : t;
        }
        private static String safe(CharSequence cs){ return cs==null? "" : cs.toString().trim(); }
    }

    /* ---------------- 유틸 ---------------- */

    private static String safe(CharSequence cs){ return cs==null? "" : cs.toString().trim(); }

    private static double parseDoubleSafe(CharSequence cs){
        try { return Double.parseDouble(safe(cs)); } catch (Exception e){ return 0d; }
    }
}
