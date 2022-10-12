package io.anserini.rerank.lib;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class IDFStats {
    private static Map<String, Set<String>> termVsDocIds= new HashMap<>();
    private  float boost=1;

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
    }
    public IDFStats(String term, float idfValue, float numOfDocsContainingTerm, float total_number_of_documents_with_field,float boost) {
        this.term = term;
        this.idfValue=idfValue;
        this.numOfDocsContainingTerm = numOfDocsContainingTerm;
        this.total_number_of_documents_with_field = total_number_of_documents_with_field;
        this.boost=boost;
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
}
