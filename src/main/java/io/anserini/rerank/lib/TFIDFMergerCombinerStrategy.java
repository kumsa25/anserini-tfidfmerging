package io.anserini.rerank.lib;

import io.anserini.rerank.BM25QueryContext;
import io.anserini.rerank.RerankerContext;

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
                if(!context.getSearchArgs().stemmer.equals("none")) {
                    assignedweight = context.calculateWeight(original.getTerm(), synonymsTF);
                }
                if(assignedweight==0)
                {
                    if(shdLog)
                    {
                        System.out.println( "Why the assigned weight is zero >>>" + original.getTerm() + ":::" + synonymsTF.getTerm() );
                    }
                }
            }
            if(shdLog){
                System.out.println("Weight is >>>"+original.getTerm()+"::"+synonymsTF.getTerm()+"::::"+assignedweight);
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

    public float discountTF(TFStats synonymsTF, BM25QueryContext context,float weight) {
        float tfTotal=0;
        float freqTotal=0;
       // for(TFStats synonymsTF: synonymsTFStats){

            float assignedweight = synonymsTF.getAssignedweight();
            System.out.println("AssignedWeight is >>>"+assignedweight);
            if(assignedweight==0){
                assignedweight=weight;
                System.out.println("AssignedWeight  after is >>>"+assignedweight);
            }


            //System.out.println("Assigned weight >>"+assignedweight+":::"+original.getTerm());
            freqTotal+= synonymsTF.getFreq()* assignedweight;

            // System.out.println("Freq  >>"+freqTotal+":::"+original.getTerm()+"::::"+synonymsTF.getFreq());
            //freq / (freq + k1 * (1 - b + b * dl / avgdl))

        //}
        float avgdl=synonymsTF.getAvgdl_average_length_of_field();
        float dl=synonymsTF.getDl_length_of_field();
        float b=synonymsTF.getB_length_normalization_parameter();
        float k1=synonymsTF.getK1_term_saturation_parameter();
        if(freqTotal==0.0){
            return synonymsTF.getTfValue();
        }
        float v1 = b * dl / avgdl;
        float v = freqTotal + (k1 * (1 - b + v1));
        tfTotal=freqTotal / v;

        return tfTotal;
    }


    public float aggregateTermsFre(TFStats original, List<TermScoreDetails> synonymsTFStats,BM25QueryContext context) {
        boolean shouldDebug=context.shouldDebug();
        float tfTotal=original.getTfValue();
        float freqTotal=original.getFreq();
        if(synonymsTFStats.size() > 0){
            // System.out.println("Should debug >>>"+context.getQueryId());
        }
        // System.out.println("synonymsTFStats.size >>>>>"+synonymsTFStats.size());
        for(TermScoreDetails synonymsTF: synonymsTFStats){

            float assignedweight = synonymsTF.getWeight();

            if(shouldDebug) {
                // System.out.println("Inside aggregateTermsFre actual query term >>>" + original + ":::" + assignedweight+"::qid:"+context.getQueryId());
                //  System.out.println("Inside aggregateTermsFre expansion term >>>" + original.getTerm() + "::"+synonymsTF.getTerm() + ":::synfre:" +synonymsTF.getTfSStats().getFreq() +"::freqTotal"+freqTotal+"::qid:"+context.getQueryId());
                // System.out.println(original.getTerm()+":::"+"fre::"+original.getFreq()+":::"+freqTotal+"::"+"tf::"+original.getTfValue()+"::qid:"+context.getQueryId());


            }


            //System.out.println("Assigned weight >>"+assignedweight+":::"+original.getTerm());
            freqTotal+= synonymsTF.getTfSStats().getFreq()* assignedweight;

            // System.out.println("Freq  >>"+freqTotal+":::"+original.getTerm()+"::::"+synonymsTF.getFreq());
            //freq / (freq + k1 * (1 - b + b * dl / avgdl))
            if(shouldDebug) {
                // System.out.println("Inside aggregateTermsFre actual query term >>>" + original + ":::" + assignedweight+"::qid:"+context.getQueryId());
                // System.out.println("Inside aggregateTermsFre expansion term >>>" + original.getTerm() + "::"+synonymsTF.getTerm() + ":::synfre:" +synonymsTF.getTfSStats().getFreq() +"::freqTotal"+freqTotal+"::qid:"+context.getQueryId());
                System.out.println(original.getTerm()+":::"+"fre::"+original.getFreq()+":::"+"::"+synonymsTF.getTerm() + ":::synfre:" +synonymsTF.getTfSStats().getFreq()+"::"+freqTotal+"::"+"tf::"+original.getTfValue()+"::qid:"+context.getQueryId());


            }


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

        if(shouldDebug){
            System.out.println("aggregated freq is >>"+original.getTerm()+":::"+freqTotal+":::orig fre::"+original.getFreq()+"::orig tf:"+original.getTfValue()+"::finalTF:"+tfTotal);
        }

        if(tfTotal !=original.getTfValue()){
            //    System.out.println("TF DIFFERENCE >>>>>>>>"+original.getTfValue()+":::"+tfTotal+"::"+freqTotal+"::"+original.getFreq());
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
            System.out.println("Document count did not match Expected and actual are ::"+count+"::"+allDocs.size()+"::"+context.getQueryId());

        }


        if(context.getSearchArgs().pickSmallerIDF==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return getSmallestIDF(original,synonymsIDFStats);
        }
        if(context.getSearchArgs().originalidf==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return original.getIdfValue();
        }

        if(context.getSearchArgs().pickAvgIDF==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return getAvgIDF(original,synonymsIDFStats);
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

    public float aggregateIDF1(IDFStats original, List<TermScoreDetails> synonymsIDFStats, BM25QueryContext context) {




        if(context.getSearchArgs().originalidf==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return original.getIdfValue();
        }
        if(context.getSearchArgs().pickSmallerIDF==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return getSmallestIDF1(original,synonymsIDFStats);
        }
        if(context.getSearchArgs().pickAvgIDF==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return getAvgIDF1(original,synonymsIDFStats);
        }
        if(context.getSearchArgs().pickLargerIDF==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return getLargestIDF1(original,synonymsIDFStats);
        }
        if(context.getSearchArgs().idfUnion==true){
            //  System.out.println("originalidf is true. So, returning original idf");

            return UnionIDF(original,synonymsIDFStats,context);
        }

        return original.getIdfValue();


    }

    private float UnionIDF(IDFStats original, List<TermScoreDetails> synonymsIDFStats, BM25QueryContext context) {
        Set docId = context.getDocId(original);
        Set<String> mergedSets= new HashSet<>();
        mergedSets.addAll(docId);
        for(TermScoreDetails termScoreDetails : synonymsIDFStats){
            Set expansionDocs = context.getDocId(termScoreDetails.getIdfStats());
            mergedSets.addAll(expansionDocs);
        }

        float corpusSize=original.getTotal_number_of_documents_with_field();
        float docIdsSize=mergedSets.size();
        //log(1 + (N - n + 0.5) / (n + 0.5))
        double value=1+(corpusSize-docIdsSize+0.5)/(docIdsSize+0.5);
        double logValue=Math.log(value);
        float v = Double.valueOf( logValue ).floatValue();
        return v;
    }


    private float getSmallestIDF(IDFStats original, List<IDFStats> synonymsIDFStats) {
        IDFStats idfStats=original;
        for(IDFStats idf : synonymsIDFStats){
            if(idf.getIdfValue() < idfStats.getIdfValue()){
                idfStats=idf;
            }
        }
        return idfStats.getIdfValue();
    }
    private float getSmallestIDF1(IDFStats original, List<TermScoreDetails> synonymsIDFStats) {
        IDFStats idfStats=original;
        for(TermScoreDetails idf : synonymsIDFStats){
            if(idf.getIdfStats().getIdfValue() < idfStats.getIdfValue()){
                idfStats=idf.getIdfStats();
            }
        }
        return idfStats.getIdfValue();
    }
    private float getAvgIDF1(IDFStats original, List<TermScoreDetails> synonymsIDFStats) {
        IDFStats idfStats=original;
        float sum=idfStats.getIdfValue();
        for(TermScoreDetails idf : synonymsIDFStats){
            sum+=idf.getIdfStats().getIdfValue();
        }
        return sum/(synonymsIDFStats.size()+1);
    }

    private float getLargestIDF1(IDFStats original, List<TermScoreDetails> synonymsIDFStats) {
        IDFStats idfStats=original;
        for(TermScoreDetails idf : synonymsIDFStats){
            if(idf.getIdfStats().getIdfValue() > idfStats.getIdfValue()){
                idfStats=idf.getIdfStats();
            }
        }
        return idfStats.getIdfValue();
    }

    private float getSumIDF(IDFStats original, List<IDFStats> synonymsIDFStats) {
        IDFStats idfStats=original;
        float sum=original.getIdfValue();
        for(IDFStats idf : synonymsIDFStats){
            sum+=idf.getIdfValue();
        }
        return sum;
    }
    private float getAvgIDF(IDFStats original, List<IDFStats> synonymsIDFStats) {
        IDFStats idfStats=original;
        float sum=original.getIdfValue();
        for(IDFStats idf : synonymsIDFStats){
            sum+=idf.getIdfValue();
        }
        return sum/(synonymsIDFStats.size()+1);
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
