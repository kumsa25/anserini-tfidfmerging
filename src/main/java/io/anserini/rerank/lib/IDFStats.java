package io.anserini.rerank.lib;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import io.anserini.rerank.BM25QueryContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class IDFStats {
    private static Map<String, Set<String>> termVsDocIds= new HashMap<>();
    private static Map<String,Float> termVsIDF= new ConcurrentHashMap<>();
    private  float boost=1;
    private static volatile float corpusSize;

    public List<String> getAllActualDocIds()
    {
        return allActualDocIds;
    }

    private List<String>allActualDocIds;

    public String getTerm()
    {
        return term;
    }

    private final String term;

    public String getStemmedTerm() {
        return stemmedTerm;
    }

    public void setStemmedTerm(String stemmedTerm) {
        this.stemmedTerm = stemmedTerm;
    }

    private String stemmedTerm;



    public static Set<String> getDocId(IDFStats original) {
        return termVsDocIds.get(original.term);
    }

    public float getNumOfDocsContainingTerm() {
        return numOfDocsContainingTerm;
    }

    private final float numOfDocsContainingTerm;

    public float getTotal_number_of_documents_with_field() {
        return total_number_of_documents_with_field;
    }

    private final float total_number_of_documents_with_field;

    public int[] getDocids() {
        return docids;
    }

    private int[] docids;

    public float getIdfValue() {
        return idfValue;
    }

    private final float idfValue;

    private float assignedweight;

    public float getAssignedweight()
    {
        return assignedweight;
    }

    public void setAssignedweight( float assignedweight_ )
    {
        assignedweight = assignedweight_;
    }


    public IDFStats(String term, float idfValue, float numOfDocsContainingTerm, float total_number_of_documents_with_field) {
        this.term = term;
        this.idfValue=idfValue;
        this.numOfDocsContainingTerm = numOfDocsContainingTerm;
        this.total_number_of_documents_with_field = total_number_of_documents_with_field;
        if(termVsIDF.containsKey(term.toLowerCase())){
            if(termVsIDF.get(term.toLowerCase()) !=idfValue){
                System.out.println("SAME TERM ALREADY EXISTS but DIFFERENT IDF");
            }
        }
        termVsIDF.put(term.toLowerCase(),idfValue);
    }
    public IDFStats(String term, float idfValue, float numOfDocsContainingTerm, float total_number_of_documents_with_field,float boost) {
        this.term = term;
        this.idfValue=idfValue;
        this.numOfDocsContainingTerm = numOfDocsContainingTerm;
        this.total_number_of_documents_with_field = total_number_of_documents_with_field;
        this.boost=boost;
        if(termVsIDF.containsKey(term.toLowerCase())){
            if(termVsIDF.get(term.toLowerCase()) !=idfValue){
                System.out.println("SAME TERM ALREADY EXISTS but DIFFERENT IDF");
            }
        }
        termVsIDF.put(term.toLowerCase(),idfValue);
        /*if(boost !=1){
           // System.out.println("Resetting boost to 1");
            boost=1;
        }*/
    }

    public static void setDocid(String term,String docId){
        Set<String> docIds = termVsDocIds.get(term);
        if(docIds==null){
            docIds= new CopyOnWriteArraySet<>();
        }
        docIds.add(docId);
        termVsDocIds.put(term,docIds);
    }

    public float getBoost(){
        return boost;
    }

    public void setActualDocIds( List<String> docIds)
    {
        this.allActualDocIds=docIds;
    }

    public static float getOriginalIDF(String term, BM25QueryContext context, List<TermScoreDetails> termScoreDetails){
        String queryTerm = context.findQueryTermForExpansion(term,termScoreDetails);
        if(termVsIDF.containsKey(queryTerm)) {
            return termVsIDF.get(queryTerm);
        }else{
            //System.out.println("Original Query term not found "+term+"::"+queryTerm+":::"+context.getQueryId());
            return smoothingIDF(term,termScoreDetails,context);
        }
    }

    public static void setCorpusSize(float corSize) {
        if(corpusSize ==0) {
            corpusSize = corSize;
        }else{
            if(corpusSize !=corSize){
                System.out.println("Invalid corpus size;");
            }
        }
    }

    public static float getCorpusSize(){
        return corpusSize;
    }


    private static float smoothingIDF(String term, List<TermScoreDetails> termScoreDetails, BM25QueryContext context) {
       // System.out.println("Finding smoothing IDF"+term+"::::"+findMatchedTerms(termScoreDetails)+"::::"+context.getQueryId());
        double value=1+(IDFStats.corpusSize+0.5)/(0.5);
        double logValue=Math.log(value);
        float v = Double.valueOf( logValue ).floatValue();
        termVsIDF.putIfAbsent(term,v);
        return v;
    }

    private static Set findMatchedTerms(List<TermScoreDetails> termScoreDetails) {
        return termScoreDetails.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet());
    }
}
