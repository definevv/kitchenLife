package com.mealplanner;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

public class SupabaseClient {
    private static SupabaseClient instance;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private static final String SUPABASE_URL = "https://tryjfibwdoquqgtvwrpr.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRyeWpmaWJ3ZG9xdXFndHZ3cnByIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI3NDM1NDcsImV4cCI6MjA3ODMxOTU0N30.2-CFSFqPmlYlDTRluqSvNkgKYQVF2YT-fYGncib5cDU";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private SupabaseClient() {
        httpClient = new OkHttpClient();
        gson = new Gson();
    }

    public static synchronized SupabaseClient getInstance() {
        if (instance == null) {
            instance = new SupabaseClient();
        }
        return instance;
    }

    public String get(String table) throws IOException {
        String url = SUPABASE_URL + "/rest/v1/" + table;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public String post(String table, JsonObject data) throws IOException {
        String url = SUPABASE_URL + "/rest/v1/" + table;
        RequestBody body = RequestBody.create(gson.toJson(data), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().string();
        }
    }
}
