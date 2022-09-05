package io.anserini.rerank;

public class WeightedExpansionTerm {
    private  float weight;
    private String expansionTerm;

    public WeightedExpansionTerm(float weight, String expansionTerm) {
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
}
