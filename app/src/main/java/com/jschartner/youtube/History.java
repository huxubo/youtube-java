package com.jschartner.youtube;

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class History {
    private SearchFunction searchFunction;
    private List<Object> results;
    private List<String> words;

    interface SearchFunction {
        void search(final String keyword, @NonNull final Consumer<Object> onThen);
    }

    public Object back() {
        int n = words.size();
        if(n==0) return null;

        results.remove(n-1);
        words.remove(n-1);

        return n==1 ? null : results.get(n - 2);
    }

    public void search(String argument, @NonNull final Consumer<Object> onThen) {
        searchFunction.search(argument, (result) -> {
            if(result != null) {
                results.add(result);
                words.add(argument);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onThen.accept(result);
            }
        });
    }

    public void refresh(@NonNull final Consumer<Object> onThen) {
        int n = words.size();
        if(n==0) return;

        searchFunction.search(words.get(n - 1), (result) -> {
            if(result!=null) {
                results.set(n - 1, result);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onThen.accept(result);
            }
        });
    }

    public History(SearchFunction searchFunction) {
        this.searchFunction = searchFunction;
        results = new ArrayList<>();
        words = new ArrayList<>();
    }
}
