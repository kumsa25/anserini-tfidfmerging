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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    Map<String, Long> collect = tokens.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String t : collect.keySet()) {
      float weight=1;
      if(!args.bm25syn && args.bm25Weighted) {
       /* List<WeightedExpansionTerm> expansionTermsForBM25 = RerankerContext.getWeight(queryid, args);
        for(WeightedExpansionTerm weightedExpansionTerm : expansionTermsForBM25){
          String stemWord = RerankerContext.findStemWord(weightedExpansionTerm.getExpansionTerm());
          if(weightedExpansionTerm.getExpansionTerm().equalsIgnoreCase(t) || weightedExpansionTerm.getExpansionTerm().toLowerCase().startsWith(t.toLowerCase()) || weightedExpansionTerm.getExpansionTerm().toLowerCase().startsWith(stemWord.toLowerCase())){
            weight=weightedExpansionTerm.getWeight();
            break;
          }
        }*/
      }
      float boost = collect.get(t);
      if(!args.bm25syn && args.bm25considerWeightAndBoost) {
        boost = boost * weight;
      }
      if(args.bm25IgnoreBoost) {
        boost = weight;
      }
      builder.add(new BoostQuery(new TermQuery(new Term(field, t)), boost),
              BooleanClause.Occur.SHOULD);



    }
    if(!args.bm25syn && args.bm25Weighted) {
      List<WeightedExpansionTerm> expansionTermsForBM25 = RerankerContext.getWeight(queryid,args);
      Set<WeightedExpansionTerm> uniqueTerms= new HashSet<>(expansionTermsForBM25);
      for(WeightedExpansionTerm weightedExpansionTerm : expansionTermsForBM25){
        String stemWord = RerankerContext.findStemWord(weightedExpansionTerm.getExpansionTerm());

        boolean queryTerm = tokens.contains(weightedExpansionTerm.getExpansionTerm()) || tokens.contains(stemWord) || tokens.contains(stemWord.toLowerCase());
        if(!queryTerm) {
          builder.add(new BoostQuery(new TermQuery(new Term(field, weightedExpansionTerm.getExpansionTerm())), weightedExpansionTerm.getWeight()),
                  BooleanClause.Occur.SHOULD);
        }

      }
    }

    return builder.build();
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
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();

      float boost = entry.getValue();

      List<WeightedExpansionTerm> weightedExpansionTerms=RerankerContext.getWeight(queryid,args);
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

}
