package io.anserini.rerank.lib;

import io.anserini.rerank.RerankerContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TFIDFMergerCombinerStrategy implements TFIDFCombinerStrategy {
    @Override
    public float aggregateTF(TFStats original, List<TFStats> synonymsTFStats,boolean shdLog,RerankerContext context) {
        float tfTotal=original.getTfValue();
        float freqTotal=original.getFreq();
        for(TFStats synonymsTF: synonymsTFStats){

            float assignedweight = synonymsTF.getAssignedweight();
            if(assignedweight==0){
                assignedweight=context.calculateWeight(original.getTerm(),synonymsTF);
                if(assignedweight==0)
                {
                    if(shdLog)
                    {
                        System.out.println( "Why the assigned weight is zero >>>" + original.getTerm() + ":::" + synonymsTF.getTerm() );
                    }
                }
            }
            if(assignedweight !=1.0){
                System.out.println("Weight is >>>"+original.getTerm()+"::"+synonymsTF.getTerm()+"::::"+assignedweight+":::queryid::"+context.getQueryId());
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
        if(shdLog){
            System.out.println(original.getTerm()+":::"+"fre::"+original.getFreq()+":::"+freqTotal+"::"+"tf::"+original.getTfValue()+"::"+tfTotal);
        }
        return tfTotal;
    }

    @Override
    public float aggregateIDF(IDFStats original, List<IDFStats> synonymsIDFStats,boolean shdLog,RerankerContext context) {

        float count=original.getNumOfDocsContainingTerm();
        Set<String> allDocs=new HashSet<>();

        List<String> allActualDocIds = original.getAllActualDocIds();
        //System.out.println("inside tfidfmerger ::"+original+":::"+allActualDocIds);
        allDocs.addAll( allActualDocIds );

        if(allDocs.size() !=count){
            System.out.println("Document count did not match Expected and actual are ::"+count+"::"+allDocs.size());
        }
        if(context.getSearchArgs().originalidf==true){
          //  System.out.println("originalidf is true. So, returning original idf");
            return original.getIdfValue();
        }

        for(IDFStats idfStats: synonymsIDFStats){
            List<String> synonymsDocIds = idfStats.getAllActualDocIds();
            allDocs.addAll(synonymsDocIds);
        }
        //TDO revisit this optimization

        if(shdLog){
            System.out.println("original Matching docs before and after >>"+original.getTerm()+"::"+original.getAllActualDocIds().size()+":::"+allDocs.size());
        }
        float corpusSize=original.getTotal_number_of_documents_with_field();
        float docIdsSize=allDocs.size();
        //log(1 + (N - n + 0.5) / (n + 0.5))
        double value=1+(corpusSize-docIdsSize+0.5)/(docIdsSize+0.5);
        double logValue=Math.log(value);
        float v = Double.valueOf( logValue ).floatValue();
        if(shdLog){
          //  System.out.println(original.getTerm()+":::"+"IDF::"+original.getIdfValue()+":::"+"final ::"+v+"::orig size"+originalDocIds.size()+"::total size:"+allDocs.size());
           // System.out.println("Corpus >>>"+corpusSize);
        }


        return v;

    }

    @Override
    public float aggregateTF( TFStats original, List<TFStats> synonymsTFStats,RerankerContext context)
    {
        return aggregateTF( original,synonymsTFStats,false,context );
    }

    @Override
    public float aggregateIDF( IDFStats original, List<IDFStats> synonymsIDFStats,RerankerContext context)
    {
        return aggregateIDF( original,synonymsIDFStats,false,context );
    }
}
