package com.courseqa.providers;

import com.courseqa.common.FeatureNotImplementedException;
import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.ChunkDraft;
import com.courseqa.contracts.internal.Deadline;
import com.courseqa.contracts.internal.EmbeddedChunk;
import com.courseqa.contracts.internal.EmbeddingBatchResult;
import com.courseqa.contracts.internal.ProviderUsage;
import com.courseqa.contracts.internal.QueryVector;
import com.courseqa.contracts.port.EmbeddingProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** C 的百炼 OpenAI 兼容向量适配；认证、连接及单次 HTTP 超时由 D 统一处理。 */
@Component
public class HttpEmbeddingProvider implements EmbeddingProvider {
    private static final String MODEL = "text-embedding-v4";
    private static final int DIMENSIONS = 1024;
    private static final long RETRY_WAIT_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final ObjectProvider<ModelHttpClient> clients;
    private final String configuredModel;
    private final int configuredDimensions;
    private final BooleanSupplier cancelled;

    @Autowired
    public HttpEmbeddingProvider(ObjectProvider<ModelHttpClient> clients,
            @Value("${app.model.embedding-model}") String configuredModel,
            @Value("${app.model.embedding-dimensions}") int configuredDimensions) {
        this(clients, configuredModel, configuredDimensions, () -> false);
    }

    private HttpEmbeddingProvider(ObjectProvider<ModelHttpClient> clients, String configuredModel,
            int configuredDimensions, BooleanSupplier cancelled) {
        this.cancelled = cancelled;
        this.clients = clients;
        this.configuredModel = configuredModel;
        this.configuredDimensions = configuredDimensions;
    }

    /** 每次导入单独绑定 B 的取消标记，模型客户端与 JSON 映射器均可复用。 */
    @Bean
    Function<BooleanSupplier, EmbeddingProvider> importEmbeddingProviders() {
        return signal -> new HttpEmbeddingProvider(clients, configuredModel, configuredDimensions, signal);
    }

    @Override
    public EmbeddingBatchResult embedDocuments(List<ChunkDraft> chunks, Deadline deadline) {
        checkActive(deadline);
        if (chunks == null || chunks.isEmpty() || chunks.size() > 10) {
            throw invalidRequest("每次片段向量请求必须包含 1 至 10 个片段");
        }
        List<ChunkDraft> input = new ArrayList<>(chunks);
        var ids = new HashSet<java.util.UUID>();
        List<String> texts = new ArrayList<>();
        for (ChunkDraft chunk : input) {
            if (chunk == null || chunk.chunkId() == null || !ids.add(chunk.chunkId())
                    || chunk.searchText() == null || chunk.searchText().isBlank()) {
                throw invalidRequest("片段编号必须非空且不重复，检索文字不能为空");
            }
            texts.add(chunk.searchText());
        }
        ParsedEmbedding response = embed(texts, deadline);
        List<EmbeddedChunk> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            result.add(new EmbeddedChunk(input.get(i).chunkId(), MODEL, DIMENSIONS, response.vectors().get(i)));
        }
        checkActive(deadline);
        return new EmbeddingBatchResult(result, response.usage(), response.actualModel());
    }

    @Override
    public QueryVector embedQuery(String query, Deadline deadline) {
        checkActive(deadline);
        if (query == null || query.isBlank() || query.codePointCount(0, query.length()) > 2000) {
            throw invalidRequest("问题向量输入必须为 1 至 2000 码点的非空文字");
        }
        ParsedEmbedding response = embed(List.of(query), deadline);
        return new QueryVector(MODEL, DIMENSIONS, response.vectors().getFirst(),
                response.usage(), response.actualModel());
    }

    private ParsedEmbedding embed(List<String> inputs, Deadline deadline) {
        if (!MODEL.equals(configuredModel) || configuredDimensions != DIMENSIONS) {
            throw modelMismatch();
        }
        ModelHttpClient client = clients.getIfAvailable();
        if (client == null) {
            throw new FeatureNotImplementedException("D", "ModelHttpClient 公共模型请求接入");
        }
        String body = JSON.writeValueAsString(Map.of("model", MODEL, "input", inputs,
                "dimensions", DIMENSIONS, "encoding_format", "float"));
        for (int attempt = 0; attempt < 2; attempt++) {
            checkActive(deadline);
            String response;
            try {
                response = client.post("embeddings", body, deadline);
            } catch (ServiceException exception) {
                checkActive(deadline);
                if (retryableHttpFailure(exception)) {
                    if (attempt == 0) {
                        waitForRetry(deadline, exception);
                        continue;
                    }
                    throw embeddingFailed("向量服务在一次重试后仍不可用");
                }
                if ("MODEL_OUTPUT_INVALID".equals(exception.asError().code())) {
                    throw embeddingFailed("向量服务返回了无效响应");
                }
                throw exception;
            }
            checkActive(deadline);
            ParsedEmbedding parsed = parse(response, inputs.size());
            checkActive(deadline);
            // 失败请求无可读用量，不能把成功重试的用量冒充两次调用合计。
            return attempt == 0 ? parsed
                    : new ParsedEmbedding(parsed.vectors(), new ProviderUsage(null, null), parsed.actualModel());
        }
        throw new IllegalStateException("向量重试流程未结束");
    }

    private ParsedEmbedding parse(String response, int expected) {
        try {
            JsonNode root = JSON.readTree(response);
            if (root == null || !root.isObject() || root.has("error")) {
                throw embeddingFailed("向量服务响应格式不正确");
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.size() != expected) {
                throw embeddingFailed("向量服务返回数量与输入不一致");
            }
            String actualModel = null;
            JsonNode model = root.get("model");
            if (model != null && !model.isNull()) {
                if (!model.isString() || model.asString().isBlank()) {
                    throw embeddingFailed("向量服务返回的模型名称不合法");
                }
                actualModel = model.asString();
                if (!MODEL.equals(actualModel)) {
                    throw modelMismatch();
                }
            }
            float[][] ordered = new float[expected][];
            for (JsonNode item : data) {
                JsonNode index = item.get("index");
                if (!item.isObject() || index == null || !index.isIntegralNumber() || !index.canConvertToInt()
                        || index.intValue() < 0 || index.intValue() >= expected || ordered[index.intValue()] != null) {
                    throw embeddingFailed("向量服务返回了缺失、重复或越界的片段序号");
                }
                ordered[index.intValue()] = normalized(item.get("embedding"));
            }
            if (Arrays.stream(ordered).anyMatch(java.util.Objects::isNull)) {
                throw embeddingFailed("向量服务返回的片段序号不完整");
            }
            JsonNode usage = root.get("usage");
            Long inputTokens = null;
            if (usage != null && !usage.isNull()) {
                if (!usage.isObject()) {
                    throw embeddingFailed("向量服务返回的用量格式不合法");
                }
                inputTokens = tokenCount(usage.get("prompt_tokens"));
                if (inputTokens == null) {
                    // 百炼也可能只返回 total_tokens；embedding 没有生成输出。
                    inputTokens = tokenCount(usage.get("total_tokens"));
                }
            }
            return new ParsedEmbedding(List.of(ordered), new ProviderUsage(inputTokens, null), actualModel);
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 不在用户错误中暴露供应商原文、解析器片段或密钥。
            throw embeddingFailed("向量服务返回内容无法解析");
        }
    }

    private static Long tokenCount(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw embeddingFailed("向量服务返回的用量不是非负整数");
        }
        return value.longValue();
    }

    private static float[] normalized(JsonNode values) {
        if (values == null || !values.isArray() || values.size() != DIMENSIONS) {
            throw embeddingFailed("向量服务必须返回 1024 维向量");
        }
        float[] vector = new float[DIMENSIONS];
        double squaredNorm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            JsonNode item = values.get(i);
            if (!item.isNumber()) {
                throw embeddingFailed("向量分量必须为有限数值");
            }
            vector[i] = (float) item.doubleValue();
            if (!Float.isFinite(vector[i])) {
                throw embeddingFailed("向量分量必须为有限数值");
            }
            squaredNorm += (double) vector[i] * vector[i];
        }
        if (!(squaredNorm > 0) || !Double.isFinite(squaredNorm)) {
            throw embeddingFailed("向量长度必须为有限非零值");
        }
        double norm = Math.sqrt(squaredNorm);
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    private static boolean retryableHttpFailure(ServiceException failure) {
        // 网络 I/O 错误和超时不属于可自动重试的 HTTP 429/5xx。
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException || cause instanceof InterruptedException) {
                return false;
            }
        }
        String code = failure.asError().code();
        return ("MODEL_RATE_LIMITED".equals(code) && failure.httpStatus() == 429)
                || ("MODEL_UNAVAILABLE".equals(code) && failure.httpStatus() >= 500 && failure.httpStatus() <= 599);
    }

    private void waitForRetry(Deadline deadline, ServiceException failure) {
        long remaining = deadline.deadlineNanos() - System.nanoTime();
        if (remaining <= 0) {
            throw timeout();
        }
        if (remaining <= RETRY_WAIT_NANOS) {
            throw failure;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(RETRY_WAIT_NANOS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw interrupted();
        }
        checkActive(deadline);
    }

    private void checkActive(Deadline deadline) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("资料导入已取消");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw interrupted();
        }
        if (deadline == null) {
            throw invalidRequest("向量调用必须提供执行期限");
        }
        if (deadline.deadlineNanos() - System.nanoTime() <= 0) {
            throw timeout();
        }
    }

    private static ServiceException invalidRequest(String message) {
        return new ServiceException("INVALID_REQUEST", 422, message, false);
    }

    private static ServiceException modelMismatch() {
        return new ServiceException("EMBEDDING_MODEL_MISMATCH", 409,
                "向量模型必须为 text-embedding-v4、1024 维；更换模型需要整库重建", false);
    }

    private static ServiceException embeddingFailed(String message) {
        return new ServiceException("EMBEDDING_FAILED", 502, message, true);
    }

    private static ServiceException timeout() {
        return new ServiceException("MODEL_TIMEOUT", 504, "向量调用已超过本轮执行期限", true);
    }

    private static ServiceException interrupted() {
        return new ServiceException("TASK_INTERRUPTED", 503, "向量调用已中断", true);
    }

    private record ParsedEmbedding(List<float[]> vectors, ProviderUsage usage, String actualModel) {}
}
