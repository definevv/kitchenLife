package com.example.kitchenlife.data;

import androidx.annotation.Nullable;

public class MapService {
    private final IngredientMapDao mapDao;

    public MapService(IngredientMapDao dao) { this.mapDao = dao; }

    public static class Norm {
        public String key;
        public String name;
        public String unit;
        public double qty;
    }

    /** 재료명/수량텍스트를 표준화 */
    public Norm normalize(String ingredientName, @Nullable String quantityText){
        String raw = ingredientName == null ? "" : ingredientName.trim();
        // 1) 정확히 매핑
        IngredientMap m = mapDao.byNameSync(raw);
        if (m == null) {
            // 2) 대강 매칭(예: “양파(다진 것)” → “양파”)
            String like = "%" + raw.replace(" ", "") + "%";
            m = mapDao.byRawSync(like);
        }

        Norm n = new Norm();
        n.name = m != null ? m.name : raw;
        n.key  = m != null ? m.ingredientKey : slug(raw);
        n.unit = (m != null && m.defaultUnit != null) ? m.defaultUnit : parseUnit(quantityText);
        n.qty  = parseQty(quantityText);
        return n;
    }

    // 간단 파서(필요하면 강화)
    private static double parseQty(@Nullable String t){
        if (t == null) return 0d;
        String s = t.replaceAll("[^0-9./]", "");
        try {
            if (s.contains("/")) { // 분수 "1/2"
                String[] a = s.split("/");
                return Double.parseDouble(a[0]) / Double.parseDouble(a[1]);
            }
            return s.isEmpty() ? 0d : Double.parseDouble(s);
        } catch (Exception e){ return 0d; }
    }

    private static String parseUnit(@Nullable String t){
        if (t == null) return "ea";
        String s = t.toLowerCase();
        if (s.contains("g"))  return "g";
        if (s.contains("kg")) return "kg";
        if (s.contains("ml")) return "ml";
        if (s.contains("l"))  return "l";
        if (s.contains("개"))  return "ea";
        return "ea";
    }

    private static String slug(String s){
        return s.toLowerCase().replaceAll("\\s+", "_");
    }
}
