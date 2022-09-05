package io.anserini.rerank.lib;

import java.util.List;

public interface TFIDFCombinerStrategy {
    public float aggregateTF(TFStats original, List<TFStats>synonymsTFStats);

    public float aggregateIDF(IDFStats original, List<IDFStats>synonymsIDFStats);
}
