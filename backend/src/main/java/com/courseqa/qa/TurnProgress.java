package com.courseqa.qa;

import com.courseqa.contracts.dto.ModelSettings;
import com.courseqa.contracts.dto.SearchResult;
import com.courseqa.contracts.dto.Timings;
import com.courseqa.contracts.dto.TokenUsage;
import com.courseqa.contracts.internal.ProviderUsage;
import java.time.Duration;
import java.time.Instant;

/** 单个工作线程维护本轮的计时和供应商用量。未调用为零，已发出但用量未知为 null。 */
final class TurnProgress {
    static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0, 0, 0);
    static final Timings QUEUED = new Timings(null, null, null, null, null, null, null);
    private final long started;
    private final double queueMs;
    private final ModelSettings configured;
    private String actualChat;
    private String actualEmbedding;
    private final Counts rewrite = new Counts();
    private final Counts generation = new Counts();
    private Integer embeddingTokens = 0;
    private Double embeddingMs = 0.0;
    private Double retrievalMs = 0.0;
    private double rewriteMs, generationMs, validationMs;
    private long phaseStarted;
    private String phase;
    private boolean vectorPossible;

    TurnProgress(Instant queuedAt, ModelSettings configured, long started) {
        this.started = started;
        this.configured = configured;
        this.queueMs = Math.max(0, Duration.between(queuedAt, Instant.now()).toMillis());
    }

    void begin(String next, String strategy) {
        end();
        phase = next;
        phaseStarted = System.nanoTime();
        if ("rewriting".equals(next)) rewrite.pending = true;
        if ("generating".equals(next)) generation.pending = true;
        if ("retrieving".equals(next)) {
            vectorPossible = !"bm25".equals(strategy);
            if (vectorPossible) { embeddingTokens = null; embeddingMs = null; }
            retrievalMs = null;
        }
    }

    void end() {
        if (phase == null) return;
        double elapsed = elapsed(phaseStarted);
        switch (phase) {
            case "rewriting" -> rewriteMs += elapsed;
            case "generating" -> generationMs += elapsed;
            case "validating" -> validationMs += elapsed;
            case "retrieving" -> { if (!vectorPossible && retrievalMs == null) retrievalMs = elapsed; }
            default -> throw new IllegalStateException(phase);
        }
        phase = null;
    }

    void chat(boolean rewriting, ProviderUsage usage, String model) {
        var counts = rewriting ? rewrite : generation;
        counts.add(usage);
        actualChat = model;
    }

    void noModelCall(boolean rewriting) { (rewriting ? rewrite : generation).pending = false; }

    void retrieval(SearchResult result) {
        embeddingTokens = result.queryEmbeddingInputTokens();
        embeddingMs = result.queryEmbeddingMs();
        retrievalMs = result.retrievalMs();
        actualEmbedding = result.actualEmbeddingModel();
    }

    void noVectorCall() { vectorPossible = false; embeddingMs = 0.0; embeddingTokens = 0; }

    ModelSettings models() {
        return new ModelSettings(configured.generationModel(), actualChat, configured.embeddingModel(), actualEmbedding,
                configured.embeddingDimensions(), configured.temperature(), configured.maxOutputTokens(),
                configured.enableThinking());
    }

    TokenUsage usage() {
        Integer ri = rewrite.input(), ro = rewrite.output(), gi = generation.input(), go = generation.output();
        Integer total = plus(plus(ri, ro), plus(embeddingTokens, plus(gi, go)));
        return new TokenUsage(ri, ro, embeddingTokens, gi, go, total);
    }

    Timings timings() {
        return new Timings(queueMs, rewriteMs, embeddingMs, retrievalMs, generationMs, validationMs, elapsed(started));
    }

    private static double elapsed(long start) { return Math.max(0, System.nanoTime() - start) / 1_000_000.0; }

    private static Integer plus(Integer a, Integer b) {
        if (a == null || b == null || (long) a + b > Integer.MAX_VALUE) return null;
        return a + b;
    }

    private static Integer integer(Long n) {
        return n == null || n < 0 || n > Integer.MAX_VALUE ? null : n.intValue();
    }

    private static final class Counts {
        boolean pending;
        Integer input = 0, output = 0;
        Integer input() { return pending ? null : input; }
        Integer output() { return pending ? null : output; }
        void add(ProviderUsage usage) {
            pending = false;
            input = plus(input, usage == null ? null : integer(usage.inputTokens()));
            output = plus(output, usage == null ? null : integer(usage.outputTokens()));
        }
    }
}
