package com.example.kitchenlife.data;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 쇼핑리스트 저장/업서트 처리 (DI 없이 사용) */
public class ShoppingRepository {

    private final AppDatabase db;
    private final ShoppingDao shoppingDao;

    public ShoppingRepository(AppDatabase db) {
        this.db = db;
        this.shoppingDao = db.shoppingDao();
    }

    /**
     * 픽커 결과 "id|name|amount|unit" 와 (옵션) 원문 텍스트 파싱 후 저장
     *  - amount가 비었거나 0/음수면 기본값 1로 가정
     *  - 동일 (name + unit) 항목이 있으면 neededQty 가산
     * @return 반영(삽입/가산)된 항목 수
     */
    public int addFromPickerLines(
            ArrayList<String> lines,
            @Nullable ArrayList<String> textsOpt
    ) {
        List<IngredientLine> parsed = parseLines(lines, textsOpt);
        return addAllAsNeeded(parsed);   // ★ int 반환
    }

    /**
     * (팬트리 비교 없이) 필요한 수량 그대로 needed_qty 로 가산 업서트
     * @return 반영된 항목 수
     */
    public int addAllAsNeeded(List<IngredientLine> inputs) {
        final int[] affected = {0};

        db.runInTransaction(() -> {
            long now = System.currentTimeMillis();

            for (IngredientLine in : inputs) {
                if (in == null) continue;

                String name = (in.name == null) ? "" : in.name.trim();
                if (name.isEmpty()) continue;

                // 단위 정규화: null → ""
                String unit = (in.unit == null) ? "" : in.unit.trim();

                // 수량: null/0/음수 → 기본 1
                double need = 1d;
                if (in.amountNumeric != null && in.amountNumeric > 0d) {
                    need = in.amountNumeric;
                }

                // 동일 (name, unit) 찾아서 가산
                ShoppingItem exist = shoppingDao.byNameUnitSync(name, unit);
                if (exist == null) {
                    ShoppingItem si = new ShoppingItem();
                    si.ingredientKey = buildKey(in);
                    si.name = name;
                    si.unit = unit.isEmpty() ? null : unit;
                    si.neededQty = need;
                    si.boughtQty = 0d;
                    si.checked = false;
                    si.updatedAt = now;
                    shoppingDao.insert(si);
                    affected[0]++; // 새로 추가됨
                } else {
                    exist.neededQty = (exist.neededQty <= 0d ? 0d : exist.neededQty) + need;
                    // 이름/단위 최신화(단위가 비어 있지 않을 때만 덮어쓰기)
                    exist.name = name;
                    if (!unit.isEmpty()) exist.unit = unit;
                    exist.updatedAt = now;
                    shoppingDao.update(exist);
                    affected[0]++; // 기존 항목에 가산됨
                }
            }
        });

        return affected[0];
    }

    /** 키 생성 규칙: id가 있으면 id:<id>, 없으면 name:<lowercase> */
    private static String buildKey(IngredientLine in) {
        if (in.id != null && !in.id.isEmpty()) return "id:" + in.id;
        String n = in.name == null ? "" : in.name.trim().toLowerCase(Locale.ROOT);
        return "name:" + n;
    }

    /** "id|name|amount|unit" + (옵션) 원문 텍스트 배열을 파싱 */
    public static List<IngredientLine> parseLines(List<String> lines, @Nullable List<String> textsOpt) {
        List<IngredientLine> out = new ArrayList<>();
        if (lines == null) return out;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null) continue;

            String[] p = raw.split("\\|", -1);
            String id = p.length > 0 ? nullToEmpty(p[0]) : "";
            String name = p.length > 1 ? nullToEmpty(p[1]) : "";
            String amountStr = p.length > 2 ? nullToEmpty(p[2]) : "";
            String unit = p.length > 3 ? nullToEmpty(p[3]) : "";

            Double amount = null;
            try {
                if (!amountStr.isEmpty()) amount = Double.valueOf(amountStr);
            } catch (Exception ignore) {}

            IngredientLine il = new IngredientLine();
            il.id = id;
            il.name = name;
            il.unit = unit;
            il.amountNumeric = amount; // null 가능(위에서 기본값 1로 보정)
            il.quantityText = (textsOpt != null && i < textsOpt.size()) ? textsOpt.get(i) : null;

            out.add(il);
        }
        return out;
    }

    private static String nullToEmpty(String s){ return s == null ? "" : s; }

    /** 입력 라인 DTO */
    public static class IngredientLine {
        @Nullable public String id;
        @Nullable public String name;
        @Nullable public String unit;
        @Nullable public Double amountNumeric;
        @Nullable public String quantityText;
    }
}
