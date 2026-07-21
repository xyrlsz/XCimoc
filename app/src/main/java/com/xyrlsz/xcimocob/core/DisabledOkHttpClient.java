package com.xyrlsz.xcimocob.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import kotlin.jvm.functions.Function0;
import kotlin.reflect.KClass;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

public class DisabledOkHttpClient extends OkHttpClient {
    @NonNull
    @Override
    public Call newCall(@NonNull Request request) {
        return new Call() {
            @Override
            public void addEventListener(@NonNull EventListener eventListener) {

            }

            @NonNull
            @Override
            public <T> T tag(@NonNull Class<T> aClass, @NonNull Function0<? extends T> function0) {
                return function0.invoke();
            }

            @NonNull
            @Override
            public <T> T tag(@NonNull KClass<T> kClass, @NonNull Function0<? extends T> function0) {
                return function0.invoke();
            }

            @Nullable
            @Override
            public <T> T tag(@NonNull Class<? extends T> aClass) {
                return null;
            }

            @Nullable
            @Override
            public <T> T tag(@NonNull KClass<T> kClass) {
                return null;
            }

            @NonNull
            @Override
            public Call clone() {
                return this;
            }

            @NonNull
            @Override
            public Timeout timeout() {
                return Timeout.NONE;
            }

            @NonNull
            @Override
            public Request request() {
                return request;
            }

            @NonNull
            @Override
            public Response execute() throws IOException {
                throw new IOException("OkHttp is disable.");
            }

            @Override
            public void enqueue(@NonNull Callback responseCallback) {
                responseCallback.onFailure(this, new IOException("OkHttp OkHttp is disable"));
            }

            @Override
            public void cancel() {
            }

            @Override
            public boolean isExecuted() {
                return false;
            }

            @Override
            public boolean isCanceled() {
                return false;
            }
        };
    }
}