package io.anserini.rerank.lib;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TFIDFMergerCombinerStrategy implements TFIDFCombinerStrategy {
    @Override
    public float aggregateTF(TFStats original, List<TFStats> synonymsTFStats) {
        float tfTotal=original.getTfValue();
        for(TFStats synonymsTF: synonymsTFStats){
            tfTotal+=synonymsTF.getTfValue();
        }
        return tfTotal;
    }

    @Override
    public float aggregateIDF(IDFStats original, List<IDFStats> synonymsIDFStats) {
        float count=original.getNumOfDocsContainingTerm();
        Set<Integer> allDocs=new HashSet<>();
        Set<Integer> originalDocIds=IDFStats.getDocId(original);
        allDocs.addAll(originalDocIds);
        for(IDFStats idfStats: synonymsIDFStats){
            Set<Integer> synonymsDocIds = IDFStats.getDocId(idfStats);
            allDocs.addAll(synonymsDocIds);
        }
        float corpusSize=original.getTotal_number_of_documents_with_field();
        float docIdsSize=allDocs.size();
        //log(1 + (N - n + 0.5) / (n + 0.5))
        double value=1+(corpusSize-docIdsSize+0.5)/(docIdsSize+0.5);
        double logValue=Math.log(value);
       return  Double.valueOf(logValue).floatValue();
    }
}
