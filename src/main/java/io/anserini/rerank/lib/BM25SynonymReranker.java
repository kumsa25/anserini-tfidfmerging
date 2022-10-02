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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BM25SynonymReranker implements Reranker {
  private static final Logger LOG = LogManager.getLogger( BM25SynonymReranker.class);
  public static final String DOC_ID_DEBUG = "WSJ861210-0110";
  public static final String QUERY_DEBUG = "South African Sanctions";

  private final Analyzer analyzer;
  private final String field;
  private final boolean outputQuery;
  private Map<String,Document> docIdVsDocument= new ConcurrentHashMap<>();
  private Map<String,Float> synonymsWeigh= new ConcurrentHashMap<>();

  public BM25SynonymReranker(Analyzer analyzer, String field, boolean outputQuery) {
    this.analyzer = analyzer;
    this.outputQuery = outputQuery;
    this.field = field;

  }

  @Override
  public ScoredDocuments rerank(ScoredDocuments docs, RerankerContext context) {


    IndexSearcher searcher = context.getIndexSearcher();
    Query query = context.getQuery();
    List queryTokens = context.getQueryTokens();
    String queryText = context.getQueryText();
    Map<String, List<TermScoreDetails>> allDocsSStats= new HashMap<>();
    Map<Integer,Float> computedScores=new HashMap<>();
    float[] scores = docs.scores;
    int[] ids = docs.ids;
    int _index=0;
    for (int id : ids) {
      try {
        Document doc = searcher.doc( id );

        String actualDocId = doc.get( "id" );
        if(context.getQueryText().equalsIgnoreCase( QUERY_DEBUG )){
          System.out.println("Original BM 25 >>"+actualDocId+":::"+scores[_index++]);
        }
        Object queryId = context.getQueryId();
        docIdVsDocument.putIfAbsent( queryText+":"+actualDocId+":"+queryId, doc);
        //  System.out.println("Query is >>>"+queryText);
        Explanation explain = searcher.explain(query, id);
        boolean shdLog=false;
        if(queryText.toLowerCase().indexOf( QUERY_DEBUG) !=-1 &&  actualDocId.equalsIgnoreCase( DOC_ID_DEBUG )){
          System.out.println("Queery test >>>"+queryText);

          shdLog=true;
          System.out.println("Original Query explanation stats is >>"+allDocsSStats);
        }
        Map<String, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, query,context,actualDocId,shdLog);
        //   System.out.println("All stats >>>"+allStats);
        //   System.out.println("explain >>>" + id + "::actualDOc::"+actualDocId+"::" + explain);
        allDocsSStats.putAll(allStats);



        /*float weight=createWeight(1,actualDocId,allDocsSStats,shdLog);

        computedScores.put(id,weight);*/

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
    if(queryText.equalsIgnoreCase( QUERY_DEBUG )){
      System.out.println("FINAL MAP ::::"+stringFloatMap);
    }

    LinkedHashMap<String, Float> reverseSortedMap = new LinkedHashMap<>();

    //Use Comparator.reverseOrder() for reverse ordering
    stringFloatMap.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .limit( 50 )
        .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));

    //  System.out.println("reverseSortedMap>>>>"+reverseSortedMap);

    ScoredDocuments scoredDocs= new ScoredDocuments();
    scoredDocs.ids = new int[reverseSortedMap.size()];
    scoredDocs.scores = new float[reverseSortedMap.size()];
    scoredDocs.documents= new Document[reverseSortedMap.size()];
    for(int i=0;i< reverseSortedMap.size();i++){
      scoredDocs.ids[i]=i;
    }
    //scoredDocs.ids = ArrayUtils.toPrimitive(reverseSortedMap.keySet().toArray(new Integer[reverseSortedMap.size()]));
    scoredDocs.scores = ArrayUtils.toPrimitive(reverseSortedMap.values().toArray(new Float[reverseSortedMap.size()]), Float.NaN);
    Iterator<String> iterator = reverseSortedMap.keySet().iterator();
    int index=0;
    while(iterator.hasNext()){
      String docid = iterator.next();
      Object queryId = context.getQueryId();
      Document doc = docIdVsDocument.get(docid );
      scoredDocs.documents[index++]=doc;
    }
    //   docIdVsDocument.clear();
    return scoredDocs;
  }


  private Map<String, Float> expandAndSearchSynonyms(String queryText, Map<String, List<TermScoreDetails>> originalScoredDocsStats, IndexSearcher searcher, RerankerContext context, ScoredDocuments originalDocs) throws IOException {
    //Query query = toSynQuery(queryText,1);
    //TODO change it later
    String expandedQueryTerms= getExpandedQueryTerms(queryText,context);
    if(queryText.equalsIgnoreCase( QUERY_DEBUG )){
      System.out.println("Found the matching query >>>"+expandedQueryTerms);
    }

    Query query = new BagOfWordsQueryGenerator().buildQuery(IndexArgs.CONTENTS, IndexCollection.DEFAULT_ANALYZER, expandedQueryTerms);

    try {
      TopDocs topDocs = searcher.search(query, context.getSearchArgs().hits);

      ScoreDoc[] docs = topDocs.scoreDocs;
      if(docs.length==0){
        //  System.out.println("NO MATCH after expansion "+queryText);
      }

      Map<String, List<TermScoreDetails>> synonymsScoredDocsStats= new HashMap<>();
      Map<Integer,Float> computedScores=new HashMap<>();
      for(ScoreDoc doc: docs){
        boolean shdLog=false;

        int docid=doc.doc;
        Document doc1 = searcher.doc( docid );
        String actualDocId= doc1.get( "id" );
        if(queryText.toLowerCase().indexOf( QUERY_DEBUG ) !=-1){
          System.out.println("Expanded Queery terms >>>"+expandedQueryTerms);
          shdLog=true;
        }

        if(shdLog){
          System.out.println("Expanded query term "+queryText+"::::"+expandedQueryTerms);
        }

        Object queryId = context.getQueryId();

        docIdVsDocument.putIfAbsent(context.getQueryText()+":"+ actualDocId+":"+queryId, doc1);
        //  System.out.println("actualDoc >>"+actualDocId);
        try {
          Explanation explain = searcher.explain(query, docid);

          Map<String, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, query,context,actualDocId,shdLog);
          if(shdLog){
            System.out.println("ALL STATS >>"+actualDocId+"::"+allStats);
          }
          // System.out.println("allStats for synonyms match >>>"+allStats);
          //  System.out.println("All stats >>>"+allStats);
          //   System.out.println("explain >>>" + docid + "::actualDOc::"+actualDocId+"::" + explain);
          if(shdLog && synonymsScoredDocsStats.containsKey( actualDocId )){
            System.out.println("Why the key already exists >>>>");
          }
          synonymsScoredDocsStats.putAll(allStats);
          if(shdLog){
            System.out.println("synonymsScoredDocsStats>>>"+actualDocId+"::"+synonymsScoredDocsStats);
          }


          float weight=createWeight(1,actualDocId,synonymsScoredDocsStats,shdLog);
          computedScores.put(docid,weight);
          //  System.out.println("After expansion >>>"+computedScores);

          if(shdLog){
            if(actualDocId.equalsIgnoreCase( DOC_ID_DEBUG)){
              System.out.println("explanation for WSJ871218-0126 is "+explain);
              System.out.println("synonyms map >>>>"+allStats);
            }
          }


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
      boolean log=false;
      if( QUERY_DEBUG.toLowerCase().indexOf( token.toLowerCase() ) !=-1){
        log=true;
      }
      List<WeightedExpansionTerm> expansionTerms = context.getExpansionTerms(token);
      if(log){
        System.out.println("expansionTerms >>>"+token+"::::"+token);
      }
      if(expansionTerms==null || expansionTerms.isEmpty()){
        continue;
      }
      for(WeightedExpansionTerm weightedExpansionTerm: expansionTerms){

        String expansionTerm = weightedExpansionTerm.getExpansionTerm();
        String uniqueTerms= getUniqueTerms(weightedExpansionTerm,buffer,context);

        buffer.append( uniqueTerms );
        buffer.append(" ");
      }
    }

    return buffer.toString();

  }

  private String getUniqueTerms( WeightedExpansionTerm expansionTerm, StringBuffer buffer,RerankerContext context )
  {
    String[] s = expansionTerm.getExpansionTerm().split( " " );
    Set<String> uniqueterms= new HashSet<>();
    for(String str : s){
      uniqueterms.add( str );
      String key=context.getQueryText()+":"+context.getQueryId()+":"+RerankerContext.findStemWord( str );
      synonymsWeigh.put( key, expansionTerm.getWeight());
    }
    return uniqueterms.stream().filter( str->buffer.indexOf( str )==-1 ).map(RerankerContext::findStemWord).
        collect( Collectors.joining(" ") );
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
    Set<Object> queryIdsInSamedDoc= new HashSet<>();
    Set<String> originalDocs = originalScoredDocsStats.keySet();
    Set<String> expandedDocs = synonymsScoredDocsStats.keySet();
    if(originalDocs.equals( expandedDocs )){
      //    System.out.println("original docs and expanded are same");
    }
    Set<String> rankedDocs= new HashSet<>(originalDocs);
    rankedDocs.addAll(expandedDocs);
    Map<String,Float> finalComputedScores=new HashMap<>();
    for(String docid: rankedDocs){
      if(originalDocs.contains( docid ))
      {
        List<TermScoreDetails> synonymsTermScoredDetails = synonymsScoredDocsStats.get( docid );
        if(synonymsTermScoredDetails !=null  && !synonymsTermScoredDetails.isEmpty() ){
          queryIdsInSamedDoc.add( context_.getQueryId() );
          //  System.out.println("synonym word found in the same doc id "+docid+":::"+context_.getQueryId()+":::"+context_.getQueryText());
        }

        float weight = createWeight( 1, docid, originalScoredDocsStats, synonymsTermScoredDetails, context_ );
        String key=context_.getQueryText()+":"+ docid+":"+context_.getQueryId();
        finalComputedScores.put( key, weight );

      }else{
        boolean log=false;
        if(context_.getQueryText().equalsIgnoreCase( QUERY_DEBUG )){
          System.out.println(" NEW DOC ID FOUND >>>>"+docid);
          log=true;
        }
        List<TermScoreDetails> synonymsTermScoredDetails = synonymsScoredDocsStats.get( docid );
        // System.out.println("synonym word found in the different doc id>>>>>"+docid);

        float weight=0;
        for(TermScoreDetails termScoreDetails: synonymsTermScoredDetails){
          TFStats tfSStats = termScoreDetails.getTfSStats();
          float tfValue = tfSStats.getTfValue();

          IDFStats idfStats = termScoreDetails.getIdfStats();
          float idf= idfStats.getIdfValue();
          if(log){
            System.out.println(" NEW DOC  tf stats::"+docid+"::"+tfSStats);
            System.out.println(" NEW DOC  idf stats::"+docid+"::"+idfStats);
            System.out.println("NEW DOC BOOST"+docid+"::"+idfStats.getBoost());
          }
          weight+=idfStats.getBoost()*tfValue*idf*getWeight(tfSStats.getTerm(),context_);
        }
        String key=context_.getQueryText()+":"+ docid+":"+context_.getQueryId();
        finalComputedScores.put( key, weight );


      }

    }
    String collect = queryIdsInSamedDoc.stream().map( id -> id.toString() ).collect( Collectors.joining( ", " ) );
    // LOG.info( "Doc ids that had both original query and expansion termms ::"+collect );
    //  System.out.println("Final final score >>>>"+finalComputedScores);
    return finalComputedScores;
  }

  private float getWeight( String term, RerankerContext context )
  {
    String key=context.getQueryText()+":"+context.getQueryId()+":"+RerankerContext.findStemWord(term);

    Float aFloat = synonymsWeigh.get( key );
    if(aFloat !=null){
      System.out.println("Weight not found in the synonyms map"+term+"::"+synonymsWeigh);
    }
    return aFloat !=null ? aFloat.floatValue() : 0;
  }

  public float createWeight( float boost, String docId, Map<String, List<TermScoreDetails>> allStats, boolean shdLog_ ){
    List<TermScoreDetails> termScoreDetailsList = allStats.get(docId);

    Iterator<TermScoreDetails> iterator = termScoreDetailsList.iterator();
    float totalScore=0;
    if(shdLog_){
      System.out.println("Computing Weight for  docid ::"+docId);
    }
    while(iterator.hasNext()){
      TermScoreDetails termScoreDetails = iterator.next();

      TFStats tfSStats = termScoreDetails.getTfSStats();
      IDFStats idfStats = termScoreDetails.getIdfStats();
      boost=idfStats.getBoost();
      if(shdLog_){
        //System.out.println(""+tfSStats.getTerm()+"::"+tfSStats.getTfValue());
        //System.out.println(""+idfStats.getTerm()+":::"+idfStats.getIdfValue());
      }
      float termWeight=createTermWeight(boost,tfSStats,idfStats);
      totalScore+=termWeight;
    }
    return totalScore;
  }

  public float createWeight(float boost, String docId,Map<String, List<TermScoreDetails>> originalScoredDocsStats,List<TermScoreDetails> expandedScoredDocsStats,RerankerContext context_){
    boolean shouldLog=false;
    if(context_.getQueryText().equalsIgnoreCase(QUERY_DEBUG) && docId.equalsIgnoreCase( DOC_ID_DEBUG )){
      shouldLog=true;
    }
    List<TermScoreDetails> termScoreDetailsList = originalScoredDocsStats.get(docId);
    if(shouldLog){
      System.out.println("Doc id >>>"+docId+"::::"+termScoreDetailsList);
    }
    Iterator<TermScoreDetails> iterator = termScoreDetailsList.iterator();
    float totalScore=0;
    while(iterator.hasNext()){
      TermScoreDetails termScoreDetails = iterator.next();
      TFStats tfSStats = termScoreDetails.getTfSStats();


      IDFStats idfStats = termScoreDetails.getIdfStats();
      if(shouldLog){
        System.out.println("before merge::"+docId+":::"+tfSStats+"::::"+idfStats);
        System.out.println(" before merge docId >>"+docId+"::::"+expandedScoredDocsStats);
      }

      float termWeight=createTermWeight(boost,tfSStats,idfStats,expandedScoredDocsStats,context_,docId);
      if(shouldLog){
        System.out.println("::"+docId+":::"+tfSStats+"::::"+idfStats);
        System.out.println("docId >>"+docId+"::::"+expandedScoredDocsStats);
        System.out.println("Weight afterr expansion>>"+tfSStats.getTerm()+":::"+termScoreDetails);
      }
      totalScore+=termWeight;
    }
    if(context_.getQueryText().equalsIgnoreCase(QUERY_DEBUG)){
      System.out.println("Final Weight of query >>>"+docId+"::"+totalScore);
    }
    return totalScore;
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats, List<TermScoreDetails> expandedScoredDocsStats,RerankerContext context_,String docId) {
    if(expandedScoredDocsStats==null){
      expandedScoredDocsStats= new ArrayList<>();
    }
    boolean shouldLog=false;
    if(context_.getQueryText().equals(QUERY_DEBUG) && docId.equalsIgnoreCase( "WSJ871218-0126" )){
      shouldLog=true;
    }
    Iterator<TermScoreDetails> iterator = expandedScoredDocsStats.iterator();
    if(shouldLog){
      System.out.println("expandedScoredDocsStats >>>"+expandedScoredDocsStats);
    }
    List<TFStats> expandedTFSStatsList= new ArrayList<>();
    List<IDFStats> expandedIDFStatsList= new ArrayList<>();
    while (iterator.hasNext()){
      TermScoreDetails next = iterator.next();
      if(isSynonym(tfSStats.getTerm(),next.getTerm())) {
        if(shouldLog){
          System.out.println("Are synonyms"+tfSStats.getTerm()+"::::"+next.getTerm());
        }
        //   System.out.println("found synonym for "+tfSStats.getTerm());
        TFStats expandedTFStat = next.getTfSStats();
        IDFStats expandedIDFStat = next.getIdfStats();
        List<WeightedExpansionTerm> expansionTerms = context_.getExpansionTerms( tfSStats.getTerm() );

        //  System.out.println("Expansion term for >>>"+tfSStats.getTerm()+"::::"+expansionTerms);
        Optional<WeightedExpansionTerm> first = expansionTerms.stream()
            .filter( expansionTerm -> {
              String expansionTerm1 = expansionTerm.getExpansionTerm();
              String term = next.getTerm();
              return context_.findStemWord(expansionTerm1).equalsIgnoreCase( context_.findStemWord(term) );
            } ).findFirst();
        if(first.isPresent()){
          expandedTFStat.setAssignedweight( first.get().getWeight() );
          expandedIDFStat.setAssignedweight( first.get().getWeight() );
          expandedTFSStatsList.add( expandedTFStat );
          expandedIDFStatsList.add( expandedIDFStat );
        }

      }else{
        if(shouldLog){
          System.out.println("are not  synonyms"+tfSStats.getTerm()+"::::"+next.getTerm());
        }
      }

    }
    TFIDFCombinerStrategy tfidfCombinerStrategy= new TFIDFMergerCombinerStrategy();
    float finalTFValue = tfidfCombinerStrategy.aggregateTF(tfSStats, expandedTFSStatsList,shouldLog);

    float finalIDFValue=tfidfCombinerStrategy.aggregateIDF(idfStats,expandedIDFStatsList,shouldLog);
    float v = idfStats.getBoost() * finalTFValue * finalIDFValue;
    if(shouldLog){
      System.out.println("TF before and after >>>"+tfSStats.getTerm()+"::"+tfSStats.getTfValue()+"::::"+finalTFValue);
      System.out.println("IDF before and after >>>"+idfStats.getTerm()+":::"+idfStats.getIdfValue()+"::::"+finalIDFValue);
      System.out.println("final Weight after expanssion "+tfSStats.getTerm()+":::"+v);
    }

    return v;
  }

  private boolean isSynonym(String orig, String expanded) {
    return RerankerContext.isSynonyms(orig,expanded);
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats) {
    return boost * tfSStats .getTfValue()* idfStats.getIdfValue();
  }

  private TFStats extractTFDetails( String term, Explanation tfExplnation, Explanation[] termSpecificExplanation_ ) {
    float tfValue=0;
    Explanation[] details = null;


    for(Explanation explanation: termSpecificExplanation_){
      //LOG.error( "Explanation is >>>"+explanation );
      if(explanation.getDescription().indexOf( "tf" ) !=-1){
        tfValue=explanation.getValue().floatValue();
        tfExplnation=explanation;
        details=tfExplnation.getDetails();
        break;
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

  private IDFStats extractIDFDetails( String term, Explanation idfExplanation, Explanation[] termSpecificExplanation_, boolean shdlog_ ) {
    float idfValue=0;
    float boost=1;
    Explanation[] details = null;

    for(Explanation explanation: termSpecificExplanation_){
      // LOG.error( "Explanation is >>>"+explanation );
      if(explanation.getDescription().indexOf( "boost" ) !=-1){
        boost=explanation.getValue().floatValue();
      }
      if(explanation.getDescription().indexOf( "idf" ) !=-1){
        idfValue=explanation.getValue().floatValue();
        idfExplanation=explanation;
        details=idfExplanation.getDetails();
        break;
      }


    }
    Explanation numbOfDocsWithThatTermExp=details[0];
    Explanation totalnumbOfDocsWithFieldExpl=details[1];
    float numbOfDocsWithThatTerm=numbOfDocsWithThatTermExp.getValue().floatValue();
    float totalnumbOfDocsWithField=totalnumbOfDocsWithFieldExpl.getValue().floatValue();

    float corpusSize=totalnumbOfDocsWithField;
    float docIdsSize=numbOfDocsWithThatTerm;
    //log(1 + (N - n + 0.5) / (n + 0.5))
    double value=1+(corpusSize-docIdsSize+0.5)/(docIdsSize+0.5);
    double logValue=Math.log(value);
    float v = Double.valueOf( logValue ).floatValue();
    if(shdlog_)
    {
      System.out.println( "COMPUTED IDF >>" + term + "::::" + v + "::" + idfValue + "corpusSize:::" + corpusSize + ":::docIdsSize::" + docIdsSize );
    }




    return new IDFStats(term,idfValue,numbOfDocsWithThatTerm,totalnumbOfDocsWithField,boost);

  }

  private Map<String, List<TermScoreDetails>> extractStatsFromExplanation(Explanation explanation, Query query,RerankerContext context_,String actaulDocId,boolean shdlog) {
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
        IDFStats idfStats = extractIDFDetails(term, idfExplanation,termSpecificExplanation,shdlog);
        IDFStats.setDocid(term,actaulDocId);
        Explanation tfExplnation=termSpecificExplanation[1];
        TFStats tfStats = extractTFDetails(term, tfExplnation,termSpecificExplanation);
        TermScoreDetails termScoreDetails= new TermScoreDetails(term,actaulDocId,idfStats,tfStats);
        termScoreDetailsList.add(termScoreDetails);
      }

    }
    docIdvsAlltermsScoreDetails.put(actaulDocId,termScoreDetailsList);
    return docIdvsAlltermsScoreDetails;
  }

  @Override
  public String tag() {
    return "BM25SynonymReranker";
  }

}
