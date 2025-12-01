package com.example.kitchenlife;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.data.PantryItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 팬트리 리스트 어댑터
 * - 각 행에 체크박스 포함
 * - Select All / Unselect All / 선택 삭제를 위한 유틸 메서드 제공
 *
 * item_pantry.xml 필요 id:
 *   - CheckBox   : @id/cb
 *   - TextView   : @id/tv_name
 *   - TextView   : @id/tv_meta
 */
public class PantryAdapter extends RecyclerView.Adapter<PantryAdapter.VH> {

    /** 체크 변경을 외부(Activity)로 알리는 콜백 (버튼 enabled 갱신 등에 사용) */
    public interface OnToggle {
        void onToggle(@NonNull PantryItem item, boolean checked);
    }

    private final List<PantryItem> items = new ArrayList<>();
    private OnToggle onToggle;

    public PantryAdapter() {
        // id(Primary Key)가 있으면 안정적인 애니메이션/갱신을 위해 true
        setHasStableIds(true);
    }

    public void setOnToggle(OnToggle t) { this.onToggle = t; }

    /* ================== 데이터 제어 ================== */

    /** 화면에 보여줄 데이터 교체 */
    public void submit(List<PantryItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /** 현재 체크된 항목 수 */
    public int getCheckedCount() {
        int c = 0;
        for (PantryItem it : items) if (it.checked) c++;
        return c;
    }

    /** 현재 체크된 항목들의 id 반환 (삭제 등에 사용) */
    @NonNull
    public List<Long> getCheckedIds() {
        List<Long> ids = new ArrayList<>();
        for (PantryItem it : items) if (it.checked) ids.add(it.id);
        return ids;
    }

    /** 전달된 id 목록을 어댑터 데이터에서 제거하고 UI 갱신 */
    public void removeByIds(@NonNull List<Long> ids) {
        if (ids.isEmpty()) return;
        Iterator<PantryItem> it = items.iterator();
        while (it.hasNext()) {
            if (ids.contains(it.next().id)) it.remove();
        }
        notifyDataSetChanged();
    }

    /** 모든 항목 체크/해제 */
    public void toggleAll(boolean checked) {
        for (PantryItem it : items) it.checked = checked;
        notifyDataSetChanged();
    }

    /** 외부에서 필요하면 현재 리스트 스냅샷 제공 */
    @NonNull
    public List<PantryItem> getItemsSnapshot() {
        return new ArrayList<>(items);
    }

    /* ================== Adapter 구현 ================== */

    @Override
    public long getItemId(int position) {
        PantryItem it = items.get(position);
        // id 필드명이 다르면 수정하세요.
        return it.id != 0 ? it.id : position;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pantry, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PantryItem it = items.get(position);

        // 텍스트
        h.tvName.setText(it.name != null ? it.name : "");
        String unit = (it.unit == null || it.unit.trim().isEmpty()) ? "" : (" " + it.unit.trim());
        // 소수 표현 깔끔하게(필요 시 포맷 조정)
        h.tvMeta.setText(String.format(Locale.getDefault(), "have %.1f%s", it.quantity, unit));

        // 체크박스 바인딩(리스너 중복 방지)
        h.cb.setOnCheckedChangeListener(null);
        h.cb.setChecked(it.checked);
        h.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            it.checked = isChecked;
            if (onToggle != null) onToggle.onToggle(it, isChecked);
        });

        // 행 전체 클릭 → 체크 토글
        h.itemView.setOnClickListener(v -> h.cb.performClick());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /* ================== ViewHolder ================== */
    static class VH extends RecyclerView.ViewHolder {
        CheckBox cb;
        TextView tvName, tvMeta;

        VH(@NonNull View v) {
            super(v);
            cb     = v.findViewById(R.id.cb);
            tvName = v.findViewById(R.id.tv_name);
            tvMeta = v.findViewById(R.id.tv_meta);
        }
    }
}
