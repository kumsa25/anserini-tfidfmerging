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

package io.anserini.rerank.lib;

import io.anserini.index.IndexArgs;
import io.anserini.index.IndexCollection;
import io.anserini.ltr.QueryContext;
import io.anserini.rerank.Reranker;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.rerank.WeightedExpansionTerm;
import io.anserini.search.query.BagOfWordsQueryGenerator;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BM25SynonymReranker implements Reranker {
  private static final Logger LOG = LogManager.getLogger(BM25SynonymReranker.class);

  private final Analyzer analyzer;
  private final String field;
  private final boolean outputQuery;

  public BM25SynonymReranker(Analyzer analyzer, String field, boolean outputQuery) {
    this.analyzer = analyzer;
    this.outputQuery = outputQuery;
    this.field = field;

  }

  @Override
  public ScoredDocuments rerank(ScoredDocuments docs, RerankerContext context) {

    IndexSearcher searcher = context.getIndexSearcher();
    Query query = context.getQuery();
    String queryText = context.getQueryText();
    Map<Integer, List<TermScoreDetails>> allDocsSStats= new HashMap<>();
    Map<Integer,Float> computedScores=new HashMap<>();
    int[] ids = docs.ids;
    for (int id : ids) {
      try {
        Explanation explain = searcher.explain(query, id);
        Map<Integer, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, id, query);
        System.out.println("All stats >>>"+allStats);
        System.out.println("explain >>>" + id + ":::" + explain);
        allDocsSStats.putAll(allStats);

      } catch (IOException e) {
        e.printStackTrace();
      }
      float weight=createWeight(1,id,allDocsSStats);
      computedScores.put(id,weight);
    }
    for (int i=0; i<docs.documents.length; i++) {
      System.out.println("doc>>"+i+"::"+docs.scores[i]);
    }
    System.out.println("Computed Scores >>"+computedScores);
    System.out.println("ALL DOCS stats >>"+allDocsSStats);
    Map<Integer, Float> integerFloatMap =null;

    try {
      integerFloatMap = expandAndSearchSynonyms(queryText, allDocsSStats, searcher, context,docs);
    } catch (IOException e) {
      e.printStackTrace();
    }

    LinkedHashMap<Integer, Float> reverseSortedMap = new LinkedHashMap<>();

//Use Comparator.reverseOrder() for reverse ordering
    integerFloatMap.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

    ScoredDocuments scoredDocs= new ScoredDocuments();
    scoredDocs.ids = new int[reverseSortedMap.size()];
    scoredDocs.scores = new float[reverseSortedMap.size()];
    scoredDocs.documents= new Document[reverseSortedMap.size()];
    scoredDocs.ids = ArrayUtils.toPrimitive(reverseSortedMap.keySet().toArray(new Integer[reverseSortedMap.size()]));
    scoredDocs.scores = ArrayUtils.toPrimitive(reverseSortedMap.values().toArray(new Float[reverseSortedMap.size()]), Float.NaN);
    Iterator<Integer> iterator = reverseSortedMap.keySet().iterator();
    int index=0;
    while(iterator.hasNext()){
      Integer docid = iterator.next();
      try {
        Document doc = searcher.doc(docid);
        scoredDocs.documents[index++]=doc;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return scoredDocs;
  }
  private Map<Integer, Float> expandAndSearchSynonyms(String queryText, Map<Integer, List<TermScoreDetails>> originalScoredDocsStats, IndexSearcher searcher, RerankerContext context, ScoredDocuments originalDocs) throws IOException {
    //Query query = toSynQuery(queryText,1);
    //TODO change it later
    String expandedQueryTerms= getExpandedQueryTerms(queryText,context);
    Query query = new BagOfWordsQueryGenerator().buildQuery(IndexArgs.CONTENTS, IndexCollection.DEFAULT_ANALYZER, expandedQueryTerms);

    try {
      TopDocs topDocs = searcher.search(query, context.getSearchArgs().hits);
      ScoreDoc[] docs = topDocs.scoreDocs;

      Map<Integer, List<TermScoreDetails>> synonymsScoredDocsStats= new HashMap<>();
      Map<Integer,Float> computedScores=new HashMap<>();
      for(ScoreDoc doc: docs){
        int docid=doc.doc;
        try {
          Explanation explain = searcher.explain(query, docid);
          Map<Integer, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, docid, query);
          System.out.println("All stats >>>"+allStats);
          System.out.println("explain >>>" + docid + ":::" + explain);
          synonymsScoredDocsStats.putAll(allStats);

        } catch (IOException e) {
          e.printStackTrace();
        }
        float weight=createWeight(1,docid,synonymsScoredDocsStats);
        computedScores.put(docid,weight);
        System.out.println("After expansion >>>"+computedScores);

      }
      return rerankDocs(originalScoredDocsStats,synonymsScoredDocsStats);
    }catch (Exception e){
      e.printStackTrace();
    }


    return null;
  }

  private String getExpandedQueryTerms(String queryText, RerankerContext context) {
    List<String> queryTokens = Arrays.stream(queryText.split(" ")).collect(Collectors.toList());
    StringBuffer buffer= new StringBuffer();
    for(String token: queryTokens){
      List<WeightedExpansionTerm> expansionTerms = context.getExpansionTerms(token);
      if(expansionTerms==null || expansionTerms.isEmpty()){
        continue;
      }
      for(WeightedExpansionTerm weightedExpansionTerm: expansionTerms){

        buffer.append(weightedExpansionTerm.getExpansionTerm());
        buffer.append(" ");
      }
    }
    return buffer.toString();

  }

  private List<String> findSynonyms(String queryText) {
    List<String> synonyms= new ArrayList<>();
    synonyms.add(queryText);
    return synonyms;
  }

  public Query toSynQuery(String term,float boost) {
    List<String> synonyms = findSynonyms(term);

    BooleanQuery.Builder feedbackQueryBuilder = new BooleanQuery.Builder();
    for(String syn:synonyms) {
      feedbackQueryBuilder.add(new BoostQuery(new TermQuery(new Term(field, term)), boost), BooleanClause.Occur.SHOULD);
    }

    return feedbackQueryBuilder.build();
  }
  private Map<Integer,Float> rerankDocs(Map<Integer, List<TermScoreDetails>> originalScoredDocsStats, Map<Integer, List<TermScoreDetails>> synonymsScoredDocsStats) {
    Set<Integer> originalDocs = originalScoredDocsStats.keySet();
    Set<Integer> expandedDocs = synonymsScoredDocsStats.keySet();
    Set<Integer> rankedDocs= new HashSet<>(originalDocs);
    rankedDocs.addAll(expandedDocs);
    Map<Integer,Float> finalComputedScores=new HashMap<>();
    for(int docid: rankedDocs){
      List<TermScoreDetails> synonymsTermScoredDetails = synonymsScoredDocsStats.get(docid);
      float weight=createWeight(1,docid,originalScoredDocsStats,synonymsTermScoredDetails);
      finalComputedScores.put(docid,weight);

    }
    System.out.println("Final final score >>>>"+finalComputedScores);
    return finalComputedScores;
  }



  public float createWeight(float boost, int docId,Map<Integer, List<TermScoreDetails>> allStats){
    List<TermScoreDetails> termScoreDetailsList = allStats.get(docId);
    Iterator<TermScoreDetails> iterator = termScoreDetailsList.iterator();
    float totalScore=0;
    while(iterator.hasNext()){
      TermScoreDetails termScoreDetails = iterator.next();
      TFStats tfSStats = termScoreDetails.getTfSStats();
      IDFStats idfStats = termScoreDetails.getIdfStats();
      float termWeight=createTermWeight(boost,tfSStats,idfStats);
      totalScore+=termWeight;
    }
    return totalScore;
  }

  public float createWeight(float boost, int docId,Map<Integer, List<TermScoreDetails>> originalScoredDocsStats,List<TermScoreDetails> expandedScoredDocsStats){
    List<TermScoreDetails> termScoreDetailsList = originalScoredDocsStats.get(docId);
    Iterator<TermScoreDetails> iterator = termScoreDetailsList.iterator();
    float totalScore=0;
    while(iterator.hasNext()){
      TermScoreDetails termScoreDetails = iterator.next();
      TFStats tfSStats = termScoreDetails.getTfSStats();

      IDFStats idfStats = termScoreDetails.getIdfStats();
      float termWeight=createTermWeight(boost,tfSStats,idfStats,expandedScoredDocsStats);
      totalScore+=termWeight;
    }
    return totalScore;
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats, List<TermScoreDetails> expandedScoredDocsStats) {
    if(expandedScoredDocsStats==null){
      expandedScoredDocsStats= new ArrayList<>();
    }
    Iterator<TermScoreDetails> iterator = expandedScoredDocsStats.iterator();
    List<TFStats> expandedTFSStatsList= new ArrayList<>();
    List<IDFStats> expandedIDFStatsList= new ArrayList<>();
    while (iterator.hasNext()){
      TermScoreDetails next = iterator.next();
      if(isSynonym(tfSStats.getTerm(),next.getTerm())) {
        expandedTFSStatsList.add(next.getTfSStats());
        expandedIDFStatsList.add(next.getIdfStats());
      }
    }
    TFIDFCombinerStrategy tfidfCombinerStrategy= new TFIDFMergerCombinerStrategy();
    float finalTFValue = tfidfCombinerStrategy.aggregateTF(tfSStats, expandedTFSStatsList);
    float finalIDFValue=tfidfCombinerStrategy.aggregateIDF(idfStats,expandedIDFStatsList);
    return boost * finalTFValue* finalIDFValue;
  }

  private boolean isSynonym(String orig, String expanded) {
    return RerankerContext.isSynonyms(orig,expanded);
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats) {
    return boost * tfSStats .getTfValue()* idfStats.getIdfValue();
  }

  private TFStats extractTFDetails(String term, Explanation tfExplnation) {
    float tfValue=tfExplnation.getValue().floatValue();
    Explanation[] details = tfExplnation.getDetails();
    Explanation freqExpl=details[0];
    Explanation K1_termSaturationExpl=details[1];
    Explanation b_length_normalizationExp=details[2];
    Explanation dl_lengthOfFieldExp=details[3];
    Explanation avgdl_avgLengthofFieldExp=details[4];
    float K1_termSaturation=K1_termSaturationExpl.getValue().floatValue();
    float b_length_normalization=b_length_normalizationExp.getValue().floatValue();
    float dl_lengthOfField=dl_lengthOfFieldExp.getValue().floatValue();
    float avgdl_avgLengthofField=avgdl_avgLengthofFieldExp.getValue().floatValue();
    return new TFStats(term,tfValue,freqExpl.getValue().floatValue(),K1_termSaturation,b_length_normalization,dl_lengthOfField,avgdl_avgLengthofField);
  }

  private IDFStats extractIDFDetails(String term, Explanation idfExplanation) {
    float idfValue=idfExplanation.getValue().floatValue();
    Explanation[] details = idfExplanation.getDetails();
    Explanation numbOfDocsWithThatTermExp=details[0];
    Explanation totalnumbOfDocsWithFieldExpl=details[1];
    float numbOfDocsWithThatTerm=numbOfDocsWithThatTermExp.getValue().floatValue();
    float totalnumbOfDocsWithField=totalnumbOfDocsWithFieldExpl.getValue().floatValue();
    return new IDFStats(term,idfValue,numbOfDocsWithThatTerm,totalnumbOfDocsWithField);

  }

  private Map<Integer, List<TermScoreDetails>> extractStatsFromExplanation(Explanation explanation, int docId, Query query) {
    Number docTotalWeight = explanation.getValue();
    Map<Integer, List<TermScoreDetails>> docIdvsAlltermsScoreDetails= new HashMap<>();
    Explanation[] details = explanation.getDetails();
    List<TermScoreDetails> termScoreDetailsList= new ArrayList<>();

    for(Explanation eachTermExpInThatDoc: details){
      Number eachTermScore = eachTermExpInThatDoc.getValue();
      String description = eachTermExpInThatDoc.getDescription();
      int colonIndex=description.indexOf(":");
      String textAfterColon=description.substring(colonIndex+1);
      String term=textAfterColon.split(" ")[0];
      Explanation[] eachTermScoreExplanation = eachTermExpInThatDoc.getDetails();
      for(Explanation termScoreExplanation: eachTermScoreExplanation){
        Number eachTerrmscore = termScoreExplanation.getValue();
        Explanation[] termSpecificExplanation = termScoreExplanation.getDetails();
        Explanation idfExplanation=termSpecificExplanation[0];
        IDFStats idfStats = extractIDFDetails(term, idfExplanation);
        IDFStats.setDocid(term,docId);
        Explanation tfExplnation=termSpecificExplanation[1];
        TFStats tfStats = extractTFDetails(term, tfExplnation);
        TermScoreDetails termScoreDetails= new TermScoreDetails(term,docId,idfStats,tfStats);
        termScoreDetailsList.add(termScoreDetails);
      }
      docIdvsAlltermsScoreDetails.put(docId,termScoreDetailsList);
    }
    return docIdvsAlltermsScoreDetails;
  }

  @Override
  public String tag() {
    return "BM25SynonymReranker";
  }

}
