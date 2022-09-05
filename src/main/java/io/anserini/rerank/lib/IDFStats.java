package io.anserini.rerank.lib;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IDFStats {
    private static Map<String, Set<Integer>> termVsDocIds= new HashMap<>();
    private final String term;

    public static Set<Integer> getDocId(IDFStats original) {
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


    public IDFStats(String term, float idfValue, float numOfDocsContainingTerm, float total_number_of_documents_with_field) {
        this.term = term;
        this.idfValue=idfValue;
        this.numOfDocsContainingTerm = numOfDocsContainingTerm;
        this.total_number_of_documents_with_field = total_number_of_documents_with_field;
    }

    public static void setDocid(String term,int docId){
        Set<Integer> docIds = termVsDocIds.get(term);
        if(docIds==null){
            docIds= new HashSet<>();
        }
        docIds.add(docId);
        termVsDocIds.put(term,docIds);
    }

}
