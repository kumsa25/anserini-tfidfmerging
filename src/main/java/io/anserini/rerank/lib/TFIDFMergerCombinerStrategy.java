package io.anserini.rerank.lib;

import io.anserini.rerank.RerankerContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TFIDFMergerCombinerStrategy implements TFIDFCombinerStrategy {
    @Override
    public float aggregateTF(TFStats original, List<TFStats> synonymsTFStats) {
        float tfTotal=original.getTfValue();
        float freqTotal=original.getFreq();
        for(TFStats synonymsTF: synonymsTFStats){

            float assignedweight = synonymsTF.getAssignedweight();
            if(assignedweight==0){
                assignedweight=RerankerContext.calculateWeight(original.getTerm(),synonymsTF);
                if(assignedweight==0)
                {
                    System.out.println( "Why the assigned weight is zero >>>" + original.getTerm() + ":::" + synonymsTF.getTerm() );
                }
            }
            //System.out.println("Assigned weight >>"+assignedweight+":::"+original.getTerm());
            freqTotal+= synonymsTF.getFreq()* assignedweight;

           // System.out.println("Freq  >>"+freqTotal+":::"+original.getTerm()+"::::"+synonymsTF.getFreq());
            //freq / (freq + k1 * (1 - b + b * dl / avgdl))

        }
        float avgdl=original.getAvgdl_average_length_of_field();
        float dl=original.getDl_length_of_field();
        float b=original.getB_length_normalization_parameter();
        float k1=original.getK1_term_saturation_parameter();
        if(freqTotal==0.0){
            return original.getTfValue();
        }
        float v1 = b * dl / avgdl;
        float v = freqTotal + (k1 * (1 - b + v1));
        tfTotal=freqTotal / v;

        return tfTotal;
    }

    @Override
    public float aggregateIDF(IDFStats original, List<IDFStats> synonymsIDFStats) {
        float count=original.getNumOfDocsContainingTerm();
        Set<String> allDocs=new HashSet<>();
        Set<String> originalDocIds=IDFStats.getDocId(original);
        if(originalDocIds !=null && !originalDocIds.isEmpty())
        {
            allDocs.addAll( originalDocIds );
        }
        for(IDFStats idfStats: synonymsIDFStats){
            Set<String> synonymsDocIds = IDFStats.getDocId(idfStats);
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
