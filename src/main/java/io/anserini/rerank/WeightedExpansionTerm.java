package io.anserini.rerank;

import java.util.Objects;

public class WeightedExpansionTerm {
    private  float weight;
    private String expansionTerm;

    public WeightedExpansionTerm(float weight, String expansionTerm) {
        try
        {
            float number=Float.parseFloat( expansionTerm );
            Thread.dumpStack();
        }catch( NumberFormatException nfe ){

        }
        this.weight = weight;
        this.expansionTerm = expansionTerm;
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public String getExpansionTerm() {
        return expansionTerm;
    }

    public void setExpansionTerm(String expansionTerm) {
        this.expansionTerm = expansionTerm;
    }

    @Override
    public String toString() {
        return "WeightedExpansionTerm{" +
                "weight=" + weight +
                ", expansionTerm='" + expansionTerm + '\'' +
                '}';
    }
}
