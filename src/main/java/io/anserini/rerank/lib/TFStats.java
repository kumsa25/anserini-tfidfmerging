package io.anserini.rerank.lib;

import java.util.List;
import java.util.Objects;

public class TFStats {
    private final String term;
    private final float freq;
    private final float k1_term_saturation_parameter;
    private final float b_length_normalization_parameter;
    private final float dl_length_of_field;
    private final float avgdl_average_length_of_field;
    private float assignedweight;

    public float getAssignedweight()
    {
        return assignedweight;
    }

    public void setAssignedweight( float assignedweight_ )
    {
        assignedweight = assignedweight_;
    }

    public float getTfValue() {
        return tfValue;
    }

    private final float tfValue;


    public String getTerm() {
        return term;
    }

    public float getFreq() {
        return freq;
    }

    public float getK1_term_saturation_parameter() {
        return k1_term_saturation_parameter;
    }

    public float getB_length_normalization_parameter() {
        return b_length_normalization_parameter;
    }

    public float getDl_length_of_field() {
        return dl_length_of_field;
    }

    public float getAvgdl_average_length_of_field() {
        return avgdl_average_length_of_field;
    }

    @Override
    public String toString() {
        return "TFStats{" +
                "term='" + term + '\'' +
                ", freq=" + freq +
                ", k1_term_saturation_parameter=" + k1_term_saturation_parameter +
                ", b_length_normalization_parameter=" + b_length_normalization_parameter +
                ", dl_length_of_field=" + dl_length_of_field +
                ", avgdl_average_length_of_field=" + avgdl_average_length_of_field +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TFStats tfStats = (TFStats) o;
        return Float.compare(tfStats.freq, freq) == 0 &&
                Float.compare(tfStats.k1_term_saturation_parameter, k1_term_saturation_parameter) == 0 &&
                Float.compare(tfStats.b_length_normalization_parameter, b_length_normalization_parameter) == 0 &&
                Float.compare(tfStats.dl_length_of_field, dl_length_of_field) == 0 &&
                Float.compare(tfStats.avgdl_average_length_of_field, avgdl_average_length_of_field) == 0 &&
                Objects.equals(term, tfStats.term);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, freq, k1_term_saturation_parameter, b_length_normalization_parameter, dl_length_of_field, avgdl_average_length_of_field);
    }

    public TFStats(String term, float tfValue,float freq, float k1_term_saturation_parameter, float b_length_normalization_parameter, float dl_length_of_field, float avgdl_average_length_of_field) {
        this.term = term;
        this.tfValue=tfValue;
        this.freq = freq;
        this.k1_term_saturation_parameter = k1_term_saturation_parameter;
        this.b_length_normalization_parameter = b_length_normalization_parameter;
        this.dl_length_of_field = dl_length_of_field;
        this.avgdl_average_length_of_field = avgdl_average_length_of_field;
    }


}
