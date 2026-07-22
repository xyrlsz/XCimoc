package com.xyrlsz.xcimocob.core;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.InterruptedIOException;

/**
 * Minimal stub for Manga — 仅提供解析器需要的 NetworkErrorException 和 getResponseBody
 */
public class Manga {

    public static class NetworkErrorException extends Exception {
        public NetworkErrorException() {
            super();
        }

        public NetworkErrorException(String message) {
            super(message);
        }

        public NetworkErrorException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 发送 HTTP 请求并获取响应体字符串
     */
    public static String getResponseBody(OkHttpClient client, Request request) throws NetworkErrorException {
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            throw new NetworkErrorException("HTTP " + response.code());
        } catch (InterruptedIOException e) {
            throw new NetworkErrorException(e);
        } catch (NetworkErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new NetworkErrorException(e);
        }
    }
}
