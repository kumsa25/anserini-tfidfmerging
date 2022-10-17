package io.anserini.rerank.lib;

import io.anserini.rerank.RerankerContext;

import java.util.List;

public interface TFIDFCombinerStrategy {
    public float aggregateTF(TFStats original, List<TFStats>synonymsTFStats, RerankerContext context );

    public float aggregateIDF(IDFStats original, List<IDFStats>synonymsIDFStats,RerankerContext context);

    public float aggregateTF(TFStats original, List<TFStats>synonymsTFStats,boolean shouldLog,RerankerContext context);

    public float aggregateIDF(IDFStats original, List<IDFStats>synonymsIDFStats,boolean shouldLog,RerankerContext context);
}
