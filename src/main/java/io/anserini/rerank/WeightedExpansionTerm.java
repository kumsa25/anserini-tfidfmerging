package io.anserini.rerank;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeightedExpansionTerm that = (WeightedExpansionTerm) o;

        return Objects.equals(expansionTerm, that.expansionTerm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expansionTerm);
    }


    @Override
    public String toString() {
        return "WeightedExpansionTerm{" +
                "expansionTerm='" + expansionTerm + '\'' +
                '}';
    }
}
