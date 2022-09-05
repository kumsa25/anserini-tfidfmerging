package io.anserini.rerank.lib;

import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.WeightedExpansionTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TermScoreDetails {
    private final String term;
    private final int docid;
    private final IDFStats idfStats;
    private final TFStats tfSStats;
    private List<TFStats> synonymsTFStats= new ArrayList<>();
    private List<IDFStats> synonymsIDFStats= new ArrayList<>();



    public TermScoreDetails(String term, int docid, IDFStats idfStats, TFStats tfSStats) {
        this.term = term;
        this.docid = docid;
        this.idfStats = idfStats;
        this.tfSStats = tfSStats;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TermScoreDetails that = (TermScoreDetails) o;
        return docid == that.docid &&
                Objects.equals(term, that.term) &&
                Objects.equals(idfStats, that.idfStats) &&
                Objects.equals(tfSStats, that.tfSStats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, docid, idfStats, tfSStats);
    }

    @Override
    public String toString() {
        return "TermScoreDetails{" +
                "term='" + term + '\'' +
                ", docid=" + docid +
                ", idfStats=" + idfStats +
                ", tfSStats=" + tfSStats +
                '}';
    }

    public String getTerm() {
        return term;
    }

    public int getDocid() {
        return docid;
    }

    public IDFStats getIdfStats() {
        return idfStats;
    }

    public TFStats getTfSStats() {
        return tfSStats;
    }

    public void addSynonymsTFStats(TFStats synonymTFSStat, RerankerContext context_ ){
        synonymsTFStats.add(synonymTFSStat);
        List<WeightedExpansionTerm> expansionTerms = context_.getExpansionTerms( term );
        Optional<WeightedExpansionTerm> first = expansionTerms.stream()
            .filter( expansionTerm -> expansionTerm.getExpansionTerm().equalsIgnoreCase( synonymTFSStat.getTerm() ) ).findFirst();
        if(first.isPresent()){
            synonymTFSStat.setAssignedweight( first.get().getWeight() );
        }
    }
    public void addSynonymsIDFStats(IDFStats synonymIDFStat,RerankerContext context_){
        synonymsIDFStats.add(synonymIDFStat);
        List<WeightedExpansionTerm> expansionTerms = context_.getExpansionTerms( term );
        Optional<WeightedExpansionTerm> first = expansionTerms.stream()
            .filter( expansionTerm -> expansionTerm.getExpansionTerm().equalsIgnoreCase( synonymIDFStat.getTerm() ) ).findFirst();
        if(first.isPresent()){
            synonymIDFStat.setAssignedweight( first.get().getWeight() );
        }
    }
}
