package com.jschartner.youtube;

import java.util.ArrayList;
import java.util.List;

public class History {
    private SearchFunction searchFunction;
    private List<Object> results;
    private List<String> words;

    interface SearchFunction {
        Object search(String argument);
    }

    public Object back() {
        int n = words.size();
        if(n==0) return null;

        results.remove(n-1);
        words.remove(n-1);

        return n==1 ? null : results.get(n - 2);
    }

    public Object searchLoop(String argument) throws RuntimeException {
        Object result = null;
        int i = 0;
        for(;i<5;i++) {
            result = search(argument);
            if(result!=null) break;
        }
        if(i==5) throw new RuntimeException("Search with argument: "+argument+" failed in 5 searches");
        return result;
    }

    public Object search(String argument) {
        Object result = searchFunction.search(argument);

        if(result != null) {
            results.add(result);
            words.add(argument);
        }

        return result;
    }

    public Object refreshLoop() throws RuntimeException {
        Object result = null;
        int i = 0;
        for(;i<5;i++) {
            result = refresh();
            if(result!=null) break;
        }
        if(i==5) throw new RuntimeException("Refresh failed in 5 searches");
        return result;
    }

    public Object refresh() {
        int n = words.size();
        if(n==0) return null;

        Object result = searchFunction.search(words.get(n - 1));

        if(result!=null) {
            results.set(n - 1, result);
        }

        return result;
    }

    public History(SearchFunction searchFunction) {
        this.searchFunction = searchFunction;
        results = new ArrayList<>();
        words = new ArrayList<>();
    }
}
