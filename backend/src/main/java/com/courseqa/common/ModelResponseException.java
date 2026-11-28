package com.courseqa.common;

import com.courseqa.contracts.internal.ProviderUsage;

/** 外层响应可计费，即使其内容未通过结构校验。 */
public final class ModelResponseException extends ServiceException {
    private final ProviderUsage usage;
    private final String actualModel;

    public ModelResponseException(String message, ProviderUsage usage, String actualModel) {
        super("MODEL_OUTPUT_INVALID", 502, message, true);
        this.usage = usage;
        this.actualModel = actualModel;
    }

    public ProviderUsage usage() { return usage; }
    public String actualModel() { return actualModel; }
}
