package com.example.kitchenlife.ui.pantry;

import android.os.Bundle;
import android.text.Editable;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.PantryAdapter;
import com.example.kitchenlife.R;
import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.PantryDao;
import com.example.kitchenlife.data.PantryItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 팬트리 화면: DB 관찰 → 검색/체크 → 선택 삭제 */
public class PantryActivity extends AppCompatActivity {

    // UI
    private EditText etSearch;
    private TextView tvSelectAll;
    private MaterialButton btnDelete;
    private RecyclerView rv;
    private PantryAdapter adapter;

    // DB
    private PantryDao pantryDao;
    private LiveData<List<PantryItem>> liveAll;

    // 메모리 캐시
    private List<PantryItem> all = new ArrayList<>();         // DB 전체
    private final List<PantryItem> shown = new ArrayList<>();  // 필터 결과(표시용)

    // IO 스레드
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantry);

        // Toolbar
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Pantry");
        }
        tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // DB
        AppDatabase db = AppDatabase.get(this);
        pantryDao = db.pantryDao();

        // Views
        etSearch     = findViewById(R.id.et_search);
        tvSelectAll  = findViewById(R.id.tv_select_all);
        btnDelete    = findViewById(R.id.btn_delete_selected);
        rv           = findViewById(R.id.rv_pantry);

        // Recycler
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PantryAdapter();
        rv.setAdapter(adapter);

        // 체크 상태 바뀌면 버튼/라벨 업데이트
        adapter.setOnToggle((item, checked) -> updateButtons());

        // DB observe → 목록 갱신
        liveAll = pantryDao.observeAll();
        liveAll.observe(this, items -> {
            all = (items == null) ? new ArrayList<>() : items;
            applyFilter();
        });

        // 검색 변경 시 필터
        etSearch.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        // Select All / Unselect All
        tvSelectAll.setOnClickListener(v -> {
            boolean allChecked = !shown.isEmpty();
            for (PantryItem p : shown) { if (!p.checked) { allChecked = false; break; } }
            adapter.toggleAll(!allChecked);
            updateButtons();
        });

        // 선택 삭제
        btnDelete.setOnClickListener(v -> {
            List<Long> ids = adapter.getCheckedIds();
            if (ids.isEmpty()) {
                Toast.makeText(this, "선택된 항목이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            // 백그라운드 삭제
            io.execute(() -> {
                try {
                    pantryDao.deleteByIds(ids); // ← PantryDao에 이 메서드 필요
                    runOnUiThread(() -> {
                        adapter.removeByIds(ids);
                        Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show();
                        // observeAll 로도 반영되지만, 어댑터에서 이미 제거했으므로 즉시 반영됨
                        updateButtons();
                    });
                } catch (Throwable t) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "삭제 실패: " + t.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            });
        });

        // FAB은 현재 숨김
        FloatingActionButton fab = findViewById(R.id.fab_add);
        if (fab != null) fab.setVisibility(View.GONE);

        updateButtons();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 검색어 기준 필터 후 어댑터에 반영 */
    private void applyFilter() {
        String q = (etSearch.getText() == null) ? "" :
                etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        shown.clear();
        for (PantryItem p : all) {
            boolean ok = true;
            if (!q.isEmpty()) {
                ok &= (p.name != null && p.name.toLowerCase(Locale.ROOT).contains(q));
            }
            if (ok) shown.add(p);
        }
        adapter.submit(shown);
        updateButtons();
    }

    /** 삭제 버튼 활성화 & Select All 라벨 갱신 */
    private void updateButtons() {
        int checked = adapter.getCheckedCount();
        if (btnDelete != null) btnDelete.setEnabled(checked > 0);

        boolean allChecked = !shown.isEmpty();
        for (PantryItem p : shown) { if (!p.checked) { allChecked = false; break; } }
        if (tvSelectAll != null) tvSelectAll.setText(allChecked ? "Unselect All" : "Select All");
    }

    /* ---- TextWatcher 축약 헬퍼 ---- */
    abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
