package com.example.kitchenlife;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.kitchenlife.data.AppDatabase;
import com.example.kitchenlife.data.PantryItem;
import com.example.kitchenlife.data.RecipeDao;
import com.example.kitchenlife.data.RecipeIngredient;
import com.example.kitchenlife.ui.pantry.PantryViewModel;
import com.example.kitchenlife.data.MapService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class RecipeConfirmBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ID = "id";
    private static final String ARG_TITLE = "title";

    public static RecipeConfirmBottomSheet newInstance(long id, String title){
        Bundle b = new Bundle();
        b.putLong(ARG_ID, id); b.putString(ARG_TITLE, title);
        RecipeConfirmBottomSheet f = new RecipeConfirmBottomSheet();
        f.setArguments(b);
        return f;
    }

    private long recipeId;
    private String title;
    private PantryViewModel vm;
    private RecipeDao recipeDao;
    private Adapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_recipe_confirm, c, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        recipeId = getArguments().getLong(ARG_ID);
        title = getArguments().getString(ARG_TITLE, "Recipe");
        ((TextView)v.findViewById(R.id.tv_title)).setText("From: " + title);

        vm = new androidx.lifecycle.ViewModelProvider(requireActivity()).get(PantryViewModel.class);
        recipeDao = AppDatabase.get(requireContext()).recipeDao();

        RecyclerView rv = v.findViewById(R.id.rv_rows);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new Adapter();
        rv.setAdapter(adapter);

        // 불러와서 표준화 + 보유량 비교
        Executors.newSingleThreadExecutor().execute(() -> {
            List<RecipeIngredient> ings =
                    AppDatabase.get(requireContext()).recipeDao().ingredientsOf(recipeId);
            new ViewModelProvider(requireActivity())
                    .get(com.example.kitchenlife.ui.pantry.PantryViewModel.class)
                    .generateShoppingFromRecipe(ings);
            List<Adapter.Row> rows = new ArrayList<>();
            MapService map = new MapService(AppDatabase.get(requireContext()).ingredientMapDao());
            for (RecipeIngredient ri : ings) {
                MapService.Norm n = map.normalize(ri.ingredient_name, ri.quantity_text);
                PantryItem p = AppDatabase.get(requireContext()).pantryDao().byKeySync(n.key);
                double have = (p != null && p.unit != null && p.unit.equalsIgnoreCase(n.unit)) ? p.quantity : 0;
                double shortage = Math.max(0, n.qty - have);
                Adapter.Row r = new Adapter.Row();
                r.key = n.key; r.name = n.name; r.unit = n.unit; r.need = n.qty; r.have = have; r.shortage = shortage; r.checked = shortage > 0;
                rows.add(r);
            }
            requireActivity().runOnUiThread(() -> adapter.submit(rows));
        });

        MaterialButton btn = v.findViewById(R.id.btn_add_shortage);
        btn.setOnClickListener(but -> {
            // 체크된 부족분을 Shopping으로
            List<RecipeIngredient> picked = new ArrayList<>();
            for (Adapter.Row r : adapter.data) if (r.checked && r.shortage > 0) {
                RecipeIngredient ri = new RecipeIngredient();
                ri.ingredient_name = r.name;
                ri.quantity_text = r.shortage + " " + r.unit;
                picked.add(ri);
            }
            vm.generateShoppingFromRecipe(picked);
            dismiss();
        });
    }

    // ====== Adapter for shortage rows ======
    static class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        static class Row {
            String key, name, unit;
            double need, have, shortage;
            boolean checked = true;
        }
        final List<Row> data = new ArrayList<>();
        void submit(List<Row> list){ data.clear(); if(list!=null) data.addAll(list); notifyDataSetChanged(); }

        static class VH extends RecyclerView.ViewHolder{
            CheckBox cb; TextView name, meta;
            VH(View v){ super(v);
                cb = v.findViewById(R.id.cb);
                name = v.findViewById(R.id.tv_name);
                meta = v.findViewById(R.id.tv_meta);
            }
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_shortage_row, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int i){
            Row r = data.get(i);
            h.name.setText(r.name);
            h.meta.setText("need " + r.need + " " + r.unit + " · have " + r.have + " → shortage " + r.shortage);
            h.cb.setChecked(r.checked);
            h.cb.setOnCheckedChangeListener((b, c) -> r.checked = c);
        }
        @Override public int getItemCount(){ return data.size(); }
    }
}
