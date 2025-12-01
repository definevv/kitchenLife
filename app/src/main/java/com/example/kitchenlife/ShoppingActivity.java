package com.example.kitchenlife;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.widget.EditText;

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
            getSupportActionBar().setTitle("Shopping");
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
                // 체크 토글 콜백
                (s, checked) -> {
                    io.execute(() -> {
                        long now = System.currentTimeMillis();
                        double keepBought = (s.boughtQty <= 0d) ? 0d : s.boughtQty;
                        shoppingDao.setChecked(s.id, checked, keepBought, now);
                    });
                    updateActionButtons();
                },
                // 수량/단위 입력 저장 콜백
                (s, qty, unit) -> io.execute(() ->
                        shoppingDao.updateBoughtAndUnit(
                                s.id,
                                Math.max(0d, qty),
                                (unit == null ? null : unit.trim()),
                                System.currentTimeMillis()
                        ))
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
            resultConsumed = false; // 새로 실행할 때만 다시 허용
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
                    if (fAdded > 0) {
                        Toast.makeText(this, "쇼핑리스트에 추가됨", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "추가된 항목 없음", Toast.LENGTH_SHORT).show();
                    }
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

        final TextView etName = v.findViewById(R.id.et_item_name);
        final TextView etQty  = v.findViewById(R.id.et_item_qty);
        final TextView etUnit = v.findViewById(R.id.et_item_unit);

        v.findViewById(R.id.btn_submit_item).setOnClickListener(b -> {
            String name = etName.getText().toString().trim();
            String unit = etUnit.getText().toString().trim();
            double qty = 0;
            try { qty = Double.parseDouble(etQty.getText().toString().trim()); } catch (Exception ignored) {}

            if (name.isEmpty() || qty <= 0) {
                Toast.makeText(this, "이름과 수량을 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }

            final double fQty = qty;
            io.execute(() -> {
                ShoppingItem s = new ShoppingItem();
                s.ingredientKey = buildKey(null, name);
                s.name = name;
                s.unit = unit;
                s.neededQty = fQty;
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
                updateActionButtons(); // 목록 갱신 후 버튼 상태도 갱신
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
        if (data.isEmpty()) { tvSelectAll.setText("Select All"); return; }
        boolean allChecked = true;
        for (ShoppingItem s : data) { if (!s.checked) { allChecked = false; break; } }
        tvSelectAll.setText(allChecked ? "Unselect All" : "Select All");
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
                    double planned = (s.boughtQty > 0d ? s.boughtQty : s.neededQty); // 핵심
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
                etQty  = v.findViewById(R.id.et_buy_qty);   // ← 수량 입력
                etUnit = v.findViewById(R.id.et_buy_unit);  // ← 단위 입력
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_shopping, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            ShoppingItem s = data.get(i);
            h.tvName.setText(s.name);
            String unitSuffix = (s.unit == null || s.unit.isEmpty()) ? "" : (" " + s.unit);
            h.tvMeta.setText("need " + s.neededQty + unitSuffix);

            // 체크박스 상태 바인딩
            h.cb.setOnCheckedChangeListener(null);
            h.cb.setChecked(s.checked);
            h.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                s.checked = isChecked; // 로컬 즉시 반영
                if (onToggle != null) onToggle.onToggleChecked(s, isChecked);
            });

            // 행 클릭도 체크 토글
            h.itemView.setOnClickListener(v -> h.cb.setChecked(!h.cb.isChecked()));

            // ----- 수량/단위: 입력값 우선 노출 -----
            h.etQty.setText(s.boughtQty > 0 ? trimZero(s.boughtQty) : "");
            h.etUnit.setText(s.unit == null ? "" : s.unit);

            // 포커스 해제 시 저장(불필요한 다중 저장 방지)
            View.OnFocusChangeListener saver = (v, hasFocus) -> {
                if (hasFocus || onEdit == null) return;
                double q = parseDoubleSafe(h.etQty.getText().toString());
                String u = h.etUnit.getText().toString();
                s.boughtQty = Math.max(0d, q);
                s.unit = (u == null ? null : u.trim());
                onEdit.onQtyUnitChanged(s, s.boughtQty, s.unit);
            };
            h.etQty.setOnFocusChangeListener(saver);
            h.etUnit.setOnFocusChangeListener(saver);
        }

        @Override public int getItemCount() { return data.size(); }

        private static double parseDoubleSafe(String s){
            try { return Double.parseDouble(s.trim()); } catch (Exception e){ return 0d; }
        }
        private static String trimZero(double v){
            String t = String.valueOf(v);
            return t.endsWith(".0") ? t.substring(0, t.length()-2) : t;
        }
    }
}
