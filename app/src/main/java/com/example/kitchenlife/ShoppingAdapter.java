package com.example.kitchenlife;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.data.ShoppingItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shopping list adapter (for ShoppingItem)
 *
 * row 레이아웃: R.layout.item_shopping
 *  - CheckBox : @id/cb
 *  - TextView : @id/tv_name
 *  - TextView : @id/tv_meta (예: "need 2.0 컵" 또는 "bought 1.0 / need 2.0 컵")
 */
public class ShoppingAdapter extends RecyclerView.Adapter<ShoppingAdapter.VH> {

    /** 체크 상태 변경 콜백 (액티비티에서 선택 수 갱신/버튼 enable 등에 사용) */
    public interface OnToggle {
        void onToggle(@NonNull ShoppingItem item, boolean checked);
    }

    private final List<ShoppingItem> items = new ArrayList<>();
    private final OnToggle onToggle;

    public ShoppingAdapter(@NonNull OnToggle onToggle) {
        setHasStableIds(true);
        this.onToggle = onToggle;
    }

    /* ===== 데이터 제어 ===== */

    /** 목록 교체 */
    public void submit(List<ShoppingItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /** 전체 아이템 접근 (필요 시) */
    @NonNull
    public List<ShoppingItem> getItems() {
        return items;
    }

    /** 체크된 아이템 개수 */
    public int getCheckedCount() {
        int c = 0;
        for (ShoppingItem it : items) if (it.checked) c++;
        return c;
    }

    /** 체크된 아이템 반환 */
    @NonNull
    public List<ShoppingItem> getCheckedItems() {
        List<ShoppingItem> r = new ArrayList<>();
        for (ShoppingItem it : items) if (it.checked) r.add(it);
        return r;
    }

    /** 체크된 아이템들의 id만 반환(편의) */
    @NonNull
    public List<Long> getCheckedIds() {
        List<Long> ids = new ArrayList<>();
        for (ShoppingItem it : items) if (it.checked) ids.add(it.id);
        return ids;
    }

    /** id 목록을 로컬 리스트에서 제거하고 UI 갱신 (HashSet으로 최적화) */
    public void removeByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Set<Long> set = new HashSet<>(ids);
        Iterator<ShoppingItem> it = items.iterator();
        while (it.hasNext()) {
            if (set.contains(it.next().id)) it.remove();
        }
        notifyDataSetChanged();
    }

    /** 전체 선택/해제 */
    public void toggleAll(boolean checked) {
        for (ShoppingItem it : items) it.checked = checked;
        notifyDataSetChanged();
    }

    /* ===== RecyclerView.Adapter ===== */

    @Override
    public long getItemId(int position) {
        ShoppingItem it = items.get(position);
        return it.id != 0 ? it.id : position;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shopping, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), onToggle);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /* ===== ViewHolder ===== */

    static class VH extends RecyclerView.ViewHolder {
        CheckBox cb;
        TextView tvName;
        TextView tvMeta;

        VH(@NonNull View v) {
            super(v);
            cb     = v.findViewById(R.id.cb);
            tvName = v.findViewById(R.id.tv_name);
            tvMeta = v.findViewById(R.id.tv_meta);
        }

        void bind(@NonNull ShoppingItem item, OnToggle onToggle) {
            // 이름
            tvName.setText(item.name != null ? item.name : "");

            // 단위/수량 메타
            String unit = (item.unit == null || item.unit.trim().isEmpty()) ? "" : item.unit.trim();
            String meta;
            if (item.boughtQty > 0) {
                // 구매 진행 상황이 있으면: "bought 1.0 / need 2.0 컵"
                if (!unit.isEmpty()) {
                    meta = String.format(Locale.getDefault(),
                            "bought %.1f / need %.1f %s", item.boughtQty, item.neededQty, unit);
                } else {
                    meta = String.format(Locale.getDefault(),
                            "bought %.1f / need %.1f", item.boughtQty, item.neededQty);
                }
            } else {
                // 기본: "need 2.0 컵"
                if (!unit.isEmpty()) {
                    meta = String.format(Locale.getDefault(),
                            "need %.1f %s", item.neededQty, unit);
                } else {
                    meta = String.format(Locale.getDefault(),
                            "need %.1f", item.neededQty);
                }
            }
            tvMeta.setText(meta);
            tvMeta.setVisibility(View.VISIBLE);

            // 체크박스 동기화
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(item.checked);
            cb.setOnCheckedChangeListener((button, isChecked) -> {
                item.checked = isChecked;
                if (onToggle != null) onToggle.onToggle(item, isChecked);
            });

            // 행 터치로도 체크 토글
            itemView.setOnClickListener(v -> cb.performClick());
        }
    }
}
