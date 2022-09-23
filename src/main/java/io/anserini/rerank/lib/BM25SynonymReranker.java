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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BM25SynonymReranker implements Reranker {
  private static final Logger LOG = LogManager.getLogger( BM25SynonymReranker.class);

  private final Analyzer analyzer;
  private final String field;
  private final boolean outputQuery;
  private Map<String,Document> docIdVsDocument= new ConcurrentHashMap<>();

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
    Map<String, List<TermScoreDetails>> allDocsSStats= new HashMap<>();
    Map<Integer,Float> computedScores=new HashMap<>();
    int[] ids = docs.ids;
    for (int id : ids) {
      try {
        Document doc = searcher.doc( id );
        String actualDocId = doc.get( "id" );
        docIdVsDocument.putIfAbsent( actualDocId, doc);
      //  System.out.println("Query is >>>"+queryText);
        Explanation explain = searcher.explain(query, id);
        Map<String, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, query,context,actualDocId);
     //   System.out.println("All stats >>>"+allStats);
     //   System.out.println("explain >>>" + id + "::actualDOc::"+actualDocId+"::" + explain);
        allDocsSStats.putAll(allStats);
        float weight=createWeight(1,actualDocId,allDocsSStats);
        computedScores.put(id,weight);

      } catch (IOException e) {
        e.printStackTrace();
      }

    }
    /*for (int i=0; i<docs.documents.length; i++) {
      System.out.println("doc>>"+i+"::"+docs.scores[i]);
    }
    System.out.println("Computed Scores >>"+computedScores);
    System.out.println("ALL DOCS stats >>"+allDocsSStats);*/

    if(context.getSearchArgs().no_rerank==true){
   //   System.out.println("reranking is false. So, returning without reranking using queryExpansion");
      return docs;
    }
    Map<String, Float> stringFloatMap =null;

    try {
      stringFloatMap = expandAndSearchSynonyms(queryText, allDocsSStats, searcher, context,docs);
    } catch (IOException e) {
      e.printStackTrace();
    }

    LinkedHashMap<String, Float> reverseSortedMap = new LinkedHashMap<>();

    //Use Comparator.reverseOrder() for reverse ordering
    stringFloatMap.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

  //  System.out.println("reverseSortedMap>>>>"+reverseSortedMap);

    ScoredDocuments scoredDocs= new ScoredDocuments();
    scoredDocs.ids = new int[reverseSortedMap.size()];
    scoredDocs.scores = new float[reverseSortedMap.size()];
    scoredDocs.documents= new Document[reverseSortedMap.size()];
    for(int i=1;i<= reverseSortedMap.size();i++){
      scoredDocs.ids[i-1]=i;
    }
    //scoredDocs.ids = ArrayUtils.toPrimitive(reverseSortedMap.keySet().toArray(new Integer[reverseSortedMap.size()]));
    scoredDocs.scores = ArrayUtils.toPrimitive(reverseSortedMap.values().toArray(new Float[reverseSortedMap.size()]), Float.NaN);
    Iterator<String> iterator = reverseSortedMap.keySet().iterator();
    int index=0;
    while(iterator.hasNext()){
      String docid = iterator.next();
      Document doc = docIdVsDocument.get( docid );
      scoredDocs.documents[index++]=doc;
    }
    docIdVsDocument.clear();
    return scoredDocs;
  }


  private Map<String, Float> expandAndSearchSynonyms(String queryText, Map<String, List<TermScoreDetails>> originalScoredDocsStats, IndexSearcher searcher, RerankerContext context, ScoredDocuments originalDocs) throws IOException {
    //Query query = toSynQuery(queryText,1);
    //TODO change it later
    String expandedQueryTerms= getExpandedQueryTerms(queryText,context);
    Query query = new BagOfWordsQueryGenerator().buildQuery(IndexArgs.CONTENTS, IndexCollection.DEFAULT_ANALYZER, expandedQueryTerms);

    try {
      TopDocs topDocs = searcher.search(query, context.getSearchArgs().hits);
      ScoredDocuments scoredDocuments=ScoredDocuments.fromTopDocs( topDocs,context.getIndexSearcher() );
      ScoreDoc[] docs = topDocs.scoreDocs;


      Map<String, List<TermScoreDetails>> synonymsScoredDocsStats= new HashMap<>();
      Map<Integer,Float> computedScores=new HashMap<>();
      for(ScoreDoc doc: docs){
        int docid=doc.doc;
        Document doc1 = searcher.doc( docid );
        String actualDocId= doc1.get( "id" );
        docIdVsDocument.putIfAbsent( actualDocId, doc1);
      //  System.out.println("actualDoc >>"+actualDocId);
        try {
          Explanation explain = searcher.explain(query, docid);
          Map<String, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, query,context,actualDocId);
        //  System.out.println("All stats >>>"+allStats);
       //   System.out.println("explain >>>" + docid + "::actualDOc::"+actualDocId+"::" + explain);
          synonymsScoredDocsStats.putAll(allStats);

          float weight=createWeight(1,actualDocId,synonymsScoredDocsStats);
          computedScores.put(docid,weight);
        //  System.out.println("After expansion >>>"+computedScores);


        } catch (IOException e) {
          e.printStackTrace();
        }

      }
      return rerankDocs(originalScoredDocsStats,synonymsScoredDocsStats,context);
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
  private Map<String,Float> rerankDocs(Map<String, List<TermScoreDetails>> originalScoredDocsStats, Map<String, List<TermScoreDetails>> synonymsScoredDocsStats,RerankerContext context_) {
    Set<String> originalDocs = originalScoredDocsStats.keySet();
    Set<String> expandedDocs = synonymsScoredDocsStats.keySet();
    Set<String> rankedDocs= new HashSet<>(originalDocs);
    rankedDocs.addAll(expandedDocs);
    Map<String,Float> finalComputedScores=new HashMap<>();
    for(String docid: rankedDocs){
      if(originalDocs.contains( docid ))
      {
        List<TermScoreDetails> synonymsTermScoredDetails = synonymsScoredDocsStats.get( docid );
        float weight = createWeight( 1, docid, originalScoredDocsStats, synonymsTermScoredDetails, context_ );
        finalComputedScores.put( docid, weight );
      }else{
        List<TermScoreDetails> synonymsTermScoredDetails = synonymsScoredDocsStats.get( docid );
        float weight=0;
        for(TermScoreDetails termScoreDetails: synonymsTermScoredDetails){
          float tfValue = termScoreDetails.getTfSStats().getTfValue();
          float idf=termScoreDetails.getIdfStats().getIdfValue();
          weight+=tfValue*idf;
        }
        finalComputedScores.put( docid, weight );

      }

    }
  //  System.out.println("Final final score >>>>"+finalComputedScores);
    return finalComputedScores;
  }



  public float createWeight(float boost, String docId,Map<String, List<TermScoreDetails>> allStats){
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

  public float createWeight(float boost, String docId,Map<String, List<TermScoreDetails>> originalScoredDocsStats,List<TermScoreDetails> expandedScoredDocsStats,RerankerContext context_){
    List<TermScoreDetails> termScoreDetailsList = originalScoredDocsStats.get(docId);
    Iterator<TermScoreDetails> iterator = termScoreDetailsList.iterator();
    float totalScore=0;
    while(iterator.hasNext()){
      TermScoreDetails termScoreDetails = iterator.next();
      TFStats tfSStats = termScoreDetails.getTfSStats();

      IDFStats idfStats = termScoreDetails.getIdfStats();
      float termWeight=createTermWeight(boost,tfSStats,idfStats,expandedScoredDocsStats,context_);
      totalScore+=termWeight;
    }
    return totalScore;
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats, List<TermScoreDetails> expandedScoredDocsStats,RerankerContext context_) {
    if(expandedScoredDocsStats==null){
      expandedScoredDocsStats= new ArrayList<>();
    }
    Iterator<TermScoreDetails> iterator = expandedScoredDocsStats.iterator();
    List<TFStats> expandedTFSStatsList= new ArrayList<>();
    List<IDFStats> expandedIDFStatsList= new ArrayList<>();
    while (iterator.hasNext()){
      TermScoreDetails next = iterator.next();
      if(isSynonym(tfSStats.getTerm(),next.getTerm())) {
        TFStats expandedTFStat = next.getTfSStats();
        IDFStats expandedIDFStat = next.getIdfStats();
        List<WeightedExpansionTerm> expansionTerms = context_.getExpansionTerms( tfSStats.getTerm() );
        Optional<WeightedExpansionTerm> first = expansionTerms.stream()
            .filter( expansionTerm -> expansionTerm.getExpansionTerm().equalsIgnoreCase( next.getTerm() ) ).findFirst();
        if(first.isPresent()){
          expandedTFStat.setAssignedweight( first.get().getWeight() );
          expandedIDFStat.setAssignedweight( first.get().getWeight() );
        }
        expandedTFSStatsList.add( expandedTFStat );
        expandedIDFStatsList.add( expandedIDFStat );
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

  private TFStats extractTFDetails( String term, Explanation tfExplnation, Explanation[] termSpecificExplanation_ ) {
    float tfValue=tfExplnation.getValue().floatValue();
    Explanation[] details = tfExplnation.getDetails();
    if(details ==null || details.length==0){
      LOG.error( "Missing data in Explnation "+ tfExplnation);
      for(Explanation explanation: termSpecificExplanation_){
        LOG.error( "Explanation is >>>"+explanation );
        if(explanation.getDescription().indexOf( "tf" ) !=-1){
          tfValue=explanation.getValue().floatValue();
          tfExplnation=explanation;
          details=tfExplnation.getDetails();
          break;
        }

      }
    }
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

  private IDFStats extractIDFDetails( String term, Explanation idfExplanation, Explanation[] termSpecificExplanation_ ) {
    float idfValue=idfExplanation.getValue().floatValue();
    Explanation[] details = idfExplanation.getDetails();
    if(details ==null || details.length==0){
      LOG.error( "Missing data in Explnation "+ idfExplanation);
      for(Explanation explanation: termSpecificExplanation_){
        LOG.error( "Explanation is >>>"+explanation );
        if(explanation.getDescription().indexOf( "idf" ) !=-1){
          idfValue=explanation.getValue().floatValue();
          idfExplanation=explanation;
          details=idfExplanation.getDetails();
          break;
        }

      }
    }
    Explanation numbOfDocsWithThatTermExp=details[0];
    Explanation totalnumbOfDocsWithFieldExpl=details[1];
    float numbOfDocsWithThatTerm=numbOfDocsWithThatTermExp.getValue().floatValue();
    float totalnumbOfDocsWithField=totalnumbOfDocsWithFieldExpl.getValue().floatValue();
    return new IDFStats(term,idfValue,numbOfDocsWithThatTerm,totalnumbOfDocsWithField);

  }

  private Map<String, List<TermScoreDetails>> extractStatsFromExplanation(Explanation explanation, Query query,RerankerContext context_,String actaulDocId) {
    Number docTotalWeight = explanation.getValue();
    Map<String, List<TermScoreDetails>> docIdvsAlltermsScoreDetails= new HashMap<>();
    Explanation[] details=null;
    if(explanation.getDescription().trim().startsWith( "weight(" )){
      details= new Explanation[]{explanation};
    }else
    {
      details = explanation.getDetails();
    }
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
        IDFStats idfStats = extractIDFDetails(term, idfExplanation,termSpecificExplanation);
        IDFStats.setDocid(term,actaulDocId);
        Explanation tfExplnation=termSpecificExplanation[1];
        TFStats tfStats = extractTFDetails(term, tfExplnation,termSpecificExplanation);
        TermScoreDetails termScoreDetails= new TermScoreDetails(term,actaulDocId,idfStats,tfStats);
        termScoreDetailsList.add(termScoreDetails);
      }
      docIdvsAlltermsScoreDetails.put(actaulDocId,termScoreDetailsList);
    }
    return docIdvsAlltermsScoreDetails;
  }

  @Override
  public String tag() {
    return "BM25SynonymReranker";
  }

}
