/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.search.query;

import io.anserini.analysis.AnalyzerUtils;
import io.anserini.rerank.BM25QueryContext;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.WeightedExpansionTerm;
import io.anserini.search.SearchArgs;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Bag of Terms query builder
 */
public class BagOfWordsQueryGenerator extends QueryGenerator {
  @Override
  public Query buildQuery(String field, Analyzer analyzer, String queryText) {
    List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
    Map<String, Long> collect = tokens.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String t : collect.keySet()) {
      builder.add(new BoostQuery(new TermQuery(new Term(field, t)), (float) collect.get(t)),
              BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  @Override
  public Query buildQuery(String field, Analyzer analyzer, String queryText,SearchArgs args) {
    List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
    Map<String, Long> collect = tokens.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String t : collect.keySet()) {
      float boost = collect.get(t);
      if(args.bm25IgnoreBoost){
        boost=1;
      }
      builder.add(new BoostQuery(new TermQuery(new Term(field, t)), boost),
              BooleanClause.Occur.SHOULD);


    }
    return builder.build();
  }


  @Override
  public Query buildQuery(String field, Analyzer analyzer, String queryText, String queryid, SearchArgs args) {
    List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
    if(args.debugQueryID.trim().equals(queryid.trim())){
    System.out.println("Query tokens >>"+tokens+":::"+queryid);
  }
    Map<String, Long> collect = tokens.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    boolean debug = args.debugQueryID.equals("52");
    List<WeightedTerm> weightedTerms= new ArrayList<>();
    
    for (String t : collect.keySet()) {
      float boost = collect.get(t);
      float weight=1;
      if(!args.bm25syn && args.bm25considerWeightAndBoost) {
        boost = boost * weight;
      }
      if(args.bm25IgnoreBoost) {
        boost = weight;
      }
      weightedTerms.add(new WeightedTerm(t.toLowerCase(),boost));

      if(!args.bm25syn && args.bm25s) {
        addExpansionTerms(queryid,t,builder,field,args,weightedTerms,analyzer);
      }



      /*builder.add(new BoostQuery(new TermQuery(new Term(field, t)), boost),
              BooleanClause.Occur.SHOULD);*/



    }
    if(!args.bm25syn && args.bm25Weighted) {
      List<WeightedExpansionTerm> expansionTermsForBM25 = RerankerContext.getWeight(queryid,args,analyzer);
      if(queryid.equalsIgnoreCase("89")){
        System.out.println("expansionTermsForBM25>>>>>"+expansionTermsForBM25);
      }
      //Set<WeightedExpansionTerm> uniqueTerms= new HashSet<>(expansionTermsForBM25);
      for(WeightedExpansionTerm weightedExpansionTerm : expansionTermsForBM25){

        WeightedTerm weightedTerm= new WeightedTerm(weightedExpansionTerm.getExpansionTerm(),weightedExpansionTerm.getWeight());
        weightedTerms.add(weightedTerm);

      }

    }
    List<WeightedTerm> weightedTerms2=new ArrayList<>();
    if(args.removeDuplicateTerms){
      int sizeBefore=weightedTerms.size();
      Set<WeightedTerm> finalTerms= new HashSet<>(weightedTerms);

      int sizeAfter=finalTerms.size();
      if(sizeAfter !=sizeBefore){
        printChanges(weightedTerms,finalTerms,queryid);
       // System.out.println("Size changed "+weightedTerms+"::::"+finalTerms+":::"+queryid);
      }
      weightedTerms2.addAll(finalTerms);
    }else{
      weightedTerms2=weightedTerms;
    }
    for(WeightedTerm weightedTerm : weightedTerms2){
      builder.add(new BoostQuery(new TermQuery(new Term(field, weightedTerm.getName())), weightedTerm.getWeight()),
              BooleanClause.Occur.SHOULD);

    }

    return builder.build();
  }

  private void printChanges(List<WeightedTerm> weightedTerms, Set<WeightedTerm> finalTerms,String queryId) {
    for(WeightedTerm orig: weightedTerms){
      if(!finalTerms.contains(orig)){
        System.out.println("Error !Why the original term got removed"+queryId+":::"+orig);
      }
    }
    for(WeightedTerm orig: finalTerms){
      if(!weightedTerms.contains(orig)){
        System.out.println("The new expansion term added "+queryId+":::"+orig);
      }
    }
  }


  public void addExpansionTerms(String queryid, String term, BooleanQuery.Builder builder,String field,SearchArgs args,List<WeightedTerm> weightedTerms,Analyzer analyzer){
    BM25QueryContext.setQueryTerms(queryid,term);
    Map<String, List<WeightedExpansionTerm>> queryExpansionTerms = BM25QueryContext.getQueryExpansionTerms(queryid);
    if(queryExpansionTerms==null){
      System.out.println("NO EXPANSION for query ::"+queryid+"::"+term);
      return ;
    }
    List<WeightedExpansionTerm> weightedExpansionTerms = queryExpansionTerms.get(term.toLowerCase());
    System.out.println("@@@@"+queryid+":::"+term+"::::"+weightedExpansionTerms);
    boolean debug = queryid.equals("52");
    if(debug){
     // System.out.println("Adding expansion for term ::"+term);
     // System.out.println("for expansion Mp >>"+queryExpansionTerms);
     // System.out.println("weightedExpansionTerms for term is >>"+weightedExpansionTerms+"::"+term+":::"+queryid+"::"+queryExpansionTerms);
    }
    if(weightedExpansionTerms==null){
      return ;
    }
    for(WeightedExpansionTerm weightedExpansionTerm : weightedExpansionTerms){
      if(debug){
        System.out.println("Going to add expansion term "+weightedExpansionTerm.getExpansionTerm()+"::"+weightedExpansionTerm.getWeight()+"::"+term);
      }
      String expansionTerm = weightedExpansionTerm.getExpansionTerm();
      List<String> analyze = AnalyzerUtils.analyze(analyzer, expansionTerm);
      for(String analyzedTerms : analyze) {
        weightedTerms.add(new WeightedTerm(analyzedTerms, weightedExpansionTerm.getWeight()));
      }


      /*builder.add(new BoostQuery(new TermQuery(new Term(field, weightedExpansionTerm.getExpansionTerm())), weightedExpansionTerm.getWeight()),
              BooleanClause.Occur.SHOULD);*/
    }
  }




  @Override
  public Query buildQuery(Map<String, Float> fields, Analyzer analyzer, String queryText) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();
      float boost = entry.getValue();

      Query clause = buildQuery(field, analyzer, queryText);

      builder.add(new BoostQuery(clause, boost), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  @Override
  public Query buildQuery(Map<String, Float> fields, Analyzer analyzer, String queryText,SearchArgs args) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();
      float boost = entry.getValue();
      if(args.bm25IgnoreBoost){
        boost=1;
      }

      Query clause = buildQuery(field, analyzer, queryText);

      builder.add(new BoostQuery(clause, boost), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  @Override
  public Query buildQuery(Map<String, Float> fields, Analyzer analyzer, String queryText,String queryid,SearchArgs args) {
    boolean debug = args.debugQueryID.equals("52");
    if(debug){
      System.out.println("Inside buildQuery1 >>>");
    }
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();

      float boost = entry.getValue();
      if(debug){
        System.out.println("going to find weight");
      }

      List<WeightedExpansionTerm> weightedExpansionTerms=RerankerContext.getWeight(queryid,args,analyzer);
      float weight=1;
      for(WeightedExpansionTerm weightedExpansionTerm : weightedExpansionTerms){
        if(weightedExpansionTerm.getExpansionTerm().equalsIgnoreCase(field)){
          weight=weightedExpansionTerm.getWeight();
          break;
        }
      }
      if(args.bm25considerWeightAndBoost) {
        boost = boost * weight;
      }
      if(args.bm25IgnoreBoost) {
        boost = weight;
      }

      Query clause = buildQuery(field, analyzer, queryText);

      builder.add(new BoostQuery(clause, boost), BooleanClause.Occur.SHOULD);


    }
    return builder.build();
  }

  public Query buildQuery(String field, Map<String, Float> queryTokenWeights) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String t : queryTokenWeights.keySet()) {
      builder.add(new BoostQuery(new TermQuery(new Term(field, t)), queryTokenWeights.get(t)),
              BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  public Query buildQuery(Map<String, Float> fields, Map<String, Float> queryTokenWeights) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();
      float boost = entry.getValue();

      Query clause = buildQuery(field, queryTokenWeights);
      builder.add(new BoostQuery(clause, boost), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }


  private static class WeightedTerm{
    private String name;
    private float weight;

    public WeightedTerm(String name, float weight){
      this.name=name.toLowerCase();
      this.weight=weight;
    }

    public String getName() {
      return name.toLowerCase();
    }

    public float getWeight() {
      return weight;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      WeightedTerm that = (WeightedTerm) o;
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }

    @Override
    public String toString() {
      return name+":"+weight;
    }
  }


}
