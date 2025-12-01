package com.example.kitchenlife.ui;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.example.kitchenlife.data.ShoppingRepository;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShoppingViewModel extends ViewModel {

    private final ShoppingRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public ShoppingViewModel(ShoppingRepository repo) {
        this.repo = repo;
    }

    public interface Callback {
        void onSuccess();
        void onError(Throwable t);
    }

    /** 픽커에서 받은 라인들을 백그라운드에서 저장 */
    public void addFromPickerAsync(ArrayList<String> lines,
                                   @Nullable ArrayList<String> textsOpt,
                                   Callback cb) {
        io.execute(() -> {
            try {
                repo.addFromPickerLines(lines != null ? lines : new ArrayList<>(), textsOpt);
                if (cb != null) cb.onSuccess();
            } catch (Throwable t) {
                if (cb != null) cb.onError(t);
            }
        });
    }

    @Override
    protected void onCleared() {
        io.shutdown();
    }
}
