package com.example.kitchenlife.net;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseClient {
    // TODO: 여기에 본인 프로젝트 값 넣기
    private static final String BASE_URL = "https://gtzqouwwerqdehtxcnat.supabase.co";
    private static final String API_KEY  = "sb_publishable_XOa6RNQ7r3EpI_LtlVcmZA_qx72UXsT";

    private static Retrofit retrofit;

    public static Retrofit get() {
        if (retrofit == null) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request req = chain.request().newBuilder()
                                .header("apikey", API_KEY)
                                .header("Authorization", "Bearer " + API_KEY)
                                .build();
                        return chain.proceed(req);
                    })
                    .addInterceptor(log)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit;
    }
}
