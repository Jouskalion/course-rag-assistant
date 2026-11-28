package com.courseqa.providers;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.Deadline;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ModelHttpClient implements AutoCloseable {
    private final String baseUrl;
    private final String apiKey;
    private HttpClient client;
    private boolean closed;

    public ModelHttpClient(Environment environment) {
        baseUrl = setting(environment, "app.model.base-url", "MODEL_BASE_URL");
        apiKey = setting(environment, "app.model.api-key", "DASHSCOPE_API_KEY");
    }

    private static String setting(Environment environment, String key, String fallback) {
        String value = environment.getProperty(key, "").strip();
        return value.isEmpty() ? environment.getProperty(fallback, "").strip() : value;
    }

    public boolean configured() { return !baseUrl.isBlank() && !apiKey.isBlank(); }

    public String post(String endpoint, String jsonBody, Deadline deadline) {
        if (Thread.currentThread().isInterrupted()) throw interrupted();
        if (deadline == null || endpoint == null || !Set.of("embeddings", "chat/completions").contains(endpoint)) {
            throw new ServiceException("INVALID_REQUEST", 422, "模型请求的路径或执行期限无效", false);
        }
        long now = System.nanoTime();
        long budget = Math.min(TimeUnit.SECONDS.toNanos(30), deadline.deadlineNanos() - now);
        if (budget <= 0) throw timeout();
        long end = now + budget;
        if (!configured()) throw notConfigured();
        HttpRequest request;
        try {
            URI base = URI.create(baseUrl);
            if (base.getHost() == null || !("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw notConfigured();
            }
            URI uri = URI.create((baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + endpoint);
            request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(budget))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
        } catch (IllegalArgumentException ex) {
            throw notConfigured();
        }

        CompletableFuture<HttpResponse<String>> pending;
        try {
            pending = transport().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (UncheckedIOException ex) {
            throw unavailable(ex.getCause());
        }
        try {
            // get also bounds the time spent receiving a slowly streamed response body.
            var response = pending.get(Math.max(0, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (end - System.nanoTime() <= 0) throw timeout();
            int status = response.statusCode();
            if (status >= 200 && status < 300) return response.body();
            throw responseError(status);
        } catch (TimeoutException ex) {
            pending.cancel(true);
            throw timeout();
        } catch (InterruptedException ex) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw interrupted();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof HttpTimeoutException) throw timeout();
            // C checks the cause chain to distinguish network I/O from an actual HTTP 5xx.
            throw unavailable(cause instanceof IOException io ? io : new IOException("模型传输失败", cause));
        }
    }

    static ServiceException responseError(int status) {
        if (status == 401 || status == 403) {
            return new ServiceException("MODEL_AUTH_FAILED", status, "模型服务鉴权失败，请核对后端配置", false);
        }
        if (status == 429) return new ServiceException("MODEL_RATE_LIMITED", status, "模型服务限流", true);
        if (status >= 500 && status <= 599) {
            return new ServiceException("MODEL_UNAVAILABLE", status, "模型服务暂不可用", true);
        }
        return new ServiceException("MODEL_OUTPUT_INVALID", 502, "模型服务未接受本次请求", true);
    }

    private synchronized HttpClient transport() {
        if (closed) throw interrupted();
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        }
        return client;
    }

    private static ServiceException unavailable(IOException cause) {
        var failure = new ServiceException("MODEL_UNAVAILABLE", 503, "无法连接模型服务", true);
        failure.initCause(cause);
        return failure;
    }

    private static ServiceException notConfigured() {
        return new ServiceException("MODEL_NOT_CONFIGURED", 503, "请配置可用的后端模型地址和密钥", false);
    }
    private static ServiceException timeout() {
        return new ServiceException("MODEL_TIMEOUT", 504, "模型请求已超过执行期限", true);
    }
    private static ServiceException interrupted() {
        return new ServiceException("TASK_INTERRUPTED", 503, "模型请求已中断", true);
    }
    @Override public synchronized void close() {
        closed = true;
        if (client != null) client.shutdownNow();
    }
}
