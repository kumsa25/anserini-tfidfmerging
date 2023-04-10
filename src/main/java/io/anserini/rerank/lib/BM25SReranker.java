/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *:q
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.rerank.lib;

import io.anserini.rerank.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BM25SReranker implements Reranker {
  private static final Logger LOG = LogManager.getLogger( BM25SReranker.class);
  public static final String DOC_ID_DEBUG = "WSJ870810-0010";
  public static final String QUERY_DEBUG = "Leveraged Buyouts";

  private final Analyzer analyzer;
  private final String field;
  private final boolean outputQuery;
  private Map<String,Document> docIdVsDocument= new ConcurrentHashMap<>();
  private Map<String,Float> synonymsWeigh= new ConcurrentHashMap<>();
  private Map<String,List<String>> docIdsSet= new ConcurrentHashMap<>();

  public BM25SReranker(Analyzer analyzer, String field, boolean outputQuery) {
    this.analyzer = analyzer;
    this.outputQuery = outputQuery;
    this.field = field;

  }

  @Override
  public ScoredDocuments rerank(ScoredDocuments docs, RerankerContext _context) {
    BM25QueryContext context=(BM25QueryContext)_context;

    String outputPath=context.getSearchArgs().output;
    Path path = Paths.get(outputPath);
    String debugDoc=context.getSearchArgs().debugDocID;
    String outputDir= path.getParent().toFile().getAbsolutePath();

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


        String queryId = context.getQueryId().toString();
        boolean shouldLLog=false;
        PrintWriter out =null;
        boolean logExplanation=debugDoc !=null && actualDocId.equals(debugDoc) && context.shouldDebug();
        //boolean logExplanation=true;
        //int debugDoc=Integer.parseInt(context.getSearchArgs().debugDocID);
        //System.out.println("Debu DOC IS >>>>>"+debugDoc+":::"+id+":::"+actualDocId);
        if(logExplanation){
          // System.out.println("GOING TO LOG >>>>>");

          String debugPath=outputDir+ File.separator+ queryId.toString()+".debug";
          Path path1 = Paths.get(debugPath);
          path1.toFile().delete();
          out = new PrintWriter(Files.newBufferedWriter(path1, StandardCharsets.UTF_8));
          //shouldLLog=true;

        }
        docIdVsDocument.putIfAbsent( queryText+":"+actualDocId+":"+queryId, doc);
        //  System.out.println("Query is >>>"+queryText);
        Explanation explain = searcher.explain(query, id);
        if(shouldLLog){
          out.write("Query is "+query);
          out.write("Actual doc id"+actualDocId +"::id::"+id);
          out.write(explain.toString());
          out.write("#############"+id+"::"+actualDocId);
          out.flush();
        }


        Map<String, List<TermScoreDetails>> allStats = extractStatsFromExplanation(explain, query,context,actualDocId,queryText);
        allDocsSStats.putAll(allStats);



      } catch (IOException e) {
        e.printStackTrace();
      }

    }

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
    if(context.shouldDebug()){
      //System.out.println("FINAL MAP ::::"+stringFloatMap);
    }

    LinkedHashMap<String, Float> reverseSortedMap = new LinkedHashMap<>();

    //Use Comparator.reverseOrder() for reverse ordering
    stringFloatMap.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .limit( 1000 )
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
      boolean logExplanation=debugDoc !=null && docid.equals(debugDoc) && context.shouldDebug();

      Document doc = docIdVsDocument.get(docid );
      if(logExplanation){
        System.out.println("Score should be >>> "+scoredDocs.scores[index]);
      }
      scoredDocs.documents[index++]=doc;
    }

   // System.out.println("FINAL sorted cle MAP ::::"+stringFloatMap);


    //   docIdVsDocument.clear();
    return scoredDocs;
  }


  private Map<String, Float> expandAndSearchSynonyms(String queryText, Map<String, List<TermScoreDetails>> originalScoredDocsStats, IndexSearcher searcher, BM25QueryContext context, ScoredDocuments originalDocs) throws IOException {
    //Query query = toSynQuery(queryText,1);
    //TODO change it later
    return rerankDocs(originalScoredDocsStats,context);

  }

  private List<String> findSynonyms(String queryText) {
    List<String> synonyms= new ArrayList<>();
    synonyms.add(queryText);
    return synonyms;
  }


  private Map<String,Float> rerankDocs(Map<String, List<TermScoreDetails>> originalScoredDocsStats, BM25QueryContext context_) {
    Map<String,Float> finalComputedScores=new HashMap<>();
    for(String docId: originalScoredDocsStats.keySet()) {
      List<TermScoreDetails> termScoreDetails = originalScoredDocsStats.get(docId);


      List<TermScoreDetails> queryTerms = context_.preprocess(termScoreDetails,context_);
      validateForQueryTerms(queryTerms,context_);
     // System.out.println("FOUND QUERY EXPANSION :  "+queryTerms.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet())+"::"+context_.getQueryId()+":::"+context_.getQueryText()+":::"+docId);


      for(TermScoreDetails term:queryTerms){
        if(term.getIdfStats().getBoost() !=1){
          System.out.println("ERROR WHY THE BOST IS NOT 1");
        }
      }
      /*List<TermScoreDetails> expansionWords = context_.filterOnlyExpansionTermsMatches(termScoreDetails, queryTerms);
     // System.out.println("REMAINING EXPANSION TERMS "+expansionWords.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet())+"::"+context_.getQueryId()+":::"+context_.getQueryText()+":::"+docId);

      if(context_.getQueryId().equals("71")){
        System.out.println("for 71 expansionWords >>>>"+expansionWords);
      }
      for(TermScoreDetails term:expansionWords){
        if(term.getIdfStats().getBoost() ==1){
          //System.out.println("ERROR WHY THE BOST IS  1 for expansion"+term.getTerm()+"::"+term.getWeight()+"::"+context_.getQuery()+"::"+context_.getQueryId());
        }
      }

      if(context_.shouldDebug()){
        // System.out.println("docid:"+docId);
        //System.out.println("Query terms are "+queryTerms);
        //System.out.println("Expansion terms are "+expansionWords);
      }
      if(!expansionWords .isEmpty()){
        //  System.out.println("Only Expansion terms found "+docId +"::"+"expansion::"+extractTerms(expansionWords)+"::Query ::"+extractTerms(queryTerms));
      }
      if(!expansionWords .isEmpty() && !queryTerms.isEmpty()){
        // System.out.println("Both  Expansion terms and Query terms  found "+docId+"::"+"expansion::"+extractTerms(expansionWords)+"::Query ::"+extractTerms(queryTerms));
      }
      */


      Iterator<TermScoreDetails> iterator = queryTerms.iterator();
      if(context_.getQueryId().toString().equalsIgnoreCase("6")){
        //System.out.println("for Query id 6 is >>>"+queryTerms);
      }

      List<TermScoreDetails> processedTerms= new ArrayList<>();
      float totalScore = 0;
      while (iterator.hasNext()) {
        TermScoreDetails scoreDetails = iterator.next();
        TFStats tfSStats = scoreDetails.getTfSStats();
        if(context_.shouldDebug()){
          // System.out.println("tfStats are >>>"+System.identityHashCode(tfSStats));
        }

        IDFStats idfStats = scoreDetails.getIdfStats();

        if(idfStats.getBoost() !=1){
          // System.out.println("Boost for query term is >>"+idfStats.getBoost());
        }
        processedTerms.add(scoreDetails);
        //System.out.println("MATCHED EXPANSION TERM TOO IN  ::"+docId);
        if(scoreDetails.getSynonymsTerms() !=null && !scoreDetails.getSynonymsTerms().isEmpty()){
          //System.out.println("MATCHED EXPANSION TERM TOO IN  ::"+docId);
        }
        processedTerms.addAll(scoreDetails.getSynonymsTerms());
        //System.out.println("Processed for 6::>>> "+processedTerms);
      //  System.out.println("TERMS PROCESSED AS QUERY AND SYNONYMS  : "+scoreDetails.getTerm()+":::"+processedTerms.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet())+"::"+context_.getQueryId()+":::"+termScoreDetails.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet())+"::"+context_.getQueryText());

        boolean covid19 = scoreDetails.getScore() !=null && scoreDetails.getScore().floatValue() != -1;
        if(covid19){
          System.out.println("for covid 19 score is "+scoreDetails.getTerm()+":::"+scoreDetails.getScore().floatValue());
        }else{
          //System.out.println("NON covid score is "+scoreDetails.getTerm()+":::"+scoreDetails.getScore().floatValue());
        }
        float termWeight = covid19 ? scoreDetails.getScore().floatValue() : createTermWeight(idfStats.getBoost(),tfSStats, idfStats, scoreDetails.getSynonymsTerms(), context_, docId);

        totalScore += termWeight;
      }
      if(context_.getQueryId().equals("71")){
        System.out.println("for 71 termScoreDetails >>>>"+termScoreDetails);
      }
      Set<String> onlySynonymsMatched=new HashSet<>();
      for(TermScoreDetails remaining : termScoreDetails){
       // System.out.println("Remaining is >>"+remaining+":::"+processedTerms);
        if(processedTerms.contains(remaining)){
          //System.out.println("CONTINUE");
          continue;
        }

        //System.out.println("REMAINING ");
        if(remaining.getIdfStats().getBoost()  > 1){
          System.out.println("Boost for expansion term is >>"+remaining.getTerm()+"::"+remaining.getIdfStats().getBoost()+"::"+context_.getQueryId()+"::"+remaining.getWeight()+"::"+docId+"::"+context_.getQueryText());
        }
        float weight=0;
        if(!context_.getSearchArgs().bm25w) {
          // System.out.println("NO BM25W $$$$$$$$$$");
          //System.out.println("ONLY MATCHED EXPANSION TERM IN  ::"+docId);

      //    System.out.println("Remaining expansion term :::"+remaining.getTerm()+":::"+context_.getQueryId());
          onlySynonymsMatched.add(remaining.getTerm());
          weight = createTermWeight(remaining.getIdfStats().getBoost(), remaining.getTfSStats(), remaining.getIdfStats(),context_,termScoreDetails);
        }else{
          System.out.println("YES BM25W $$$$$$$$$$");
          weight = createTermWeight(remaining.getIdfStats().getBoost(),remaining.getTfSStats(), remaining.getIdfStats(), context_, docId);
        }
        totalScore+=weight;
      }
      Set<String> allMatchedTerms=termScoreDetails.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet());
      Set<String> allConsidered= processedTerms.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet());
      allConsidered.addAll(onlySynonymsMatched);
      if(allMatchedTerms.size() !=allConsidered.size()){
        throw new RuntimeException("All terms were not considered for weight >>>"+allMatchedTerms+":::"+allConsidered+"::"+context_.getQueryId()+"::"+docId);
      }


      /*for(TermScoreDetails expansion : expansionWords){
        if(context_.notIncluded(queryTerms,expansion)){
          if(expansion.getIdfStats().getBoost() ==1){
           // System.out.println("Boost for expansion term is >>"+expansion.getIdfStats().getBoost()+"::"+context_.getQueryId()+"::"+expansion.getWeight()+"::"+docId+"::"+context_.getQueryText());
          }
          float weight=createTermWeight(expansion.getIdfStats().getBoost(), expansion.getTfSStats(),expansion.getIdfStats());
          totalScore+=weight;
        }
      }*/
      String key=context_.getQueryText()+":"+ docId+":"+context_.getQueryId();
      finalComputedScores.put( key, totalScore );

    }






    return finalComputedScores;
  }

  private void validateForQueryTerms(List<TermScoreDetails> queryTerms, BM25QueryContext context_) {
    List queryTokens = context_.getQueryTokens();
    for(TermScoreDetails termScoreDetails: queryTerms){
      if(!queryTokens.contains(termScoreDetails.getTerm().toLowerCase())){
        throw new RuntimeException("NOT A QUERY TERM >>>"+queryTerms.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet())+":::"+context_.getQueryId()+":::"+context_.getQueryText()+"::"+context_.getQueryTokens());
      }
    }
  }


  public String extractTerms(List<TermScoreDetails> termScoreDetails){
    return termScoreDetails.stream().map(TermScoreDetails::getTerm).collect(Collectors.joining(" "));
  }





  public float createWeight(float boost, String docId,List<TermScoreDetails> termScoreDetailsList,BM25QueryContext context_){
    boolean shouldLog=false;
    if(context_.getQueryText().equalsIgnoreCase(QUERY_DEBUG) && docId.equalsIgnoreCase( DOC_ID_DEBUG )){
      shouldLog=true;
    }

    Iterator<TermScoreDetails> iterator = termScoreDetailsList.iterator();
    float totalScore=0;
    while(iterator.hasNext()){
      TermScoreDetails termScoreDetails = iterator.next();
      TFStats tfSStats = termScoreDetails.getTfSStats();


      IDFStats idfStats = termScoreDetails.getIdfStats();


      float termWeight=createTermWeight(boost,tfSStats,idfStats,termScoreDetailsList,context_,docId);

      totalScore+=termWeight;
    }
    if(context_.getQueryText().equalsIgnoreCase(QUERY_DEBUG)){
      System.out.println("Final Weight of query >>>"+docId+"::"+totalScore);
    }
    return totalScore;
  }


  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats, List<TermScoreDetails> synonymsTerms,BM25QueryContext context_,String docId) {

    TFIDFMergerCombinerStrategy tfidfCombinerStrategy= new TFIDFMergerCombinerStrategy();
    //System.out.println("Going to invoke tfMerging "+tfSStats.getTerm()+":::"+synonymsTerms.stream().map(TermScoreDetails::getTerm).collect(Collectors.toSet())+":::"+docId+":::"+context_.getQueryId());
    if(context_.shouldDebug() && context_.getSearchArgs().debugDocID.trim().equalsIgnoreCase(docId)){
      //System.out.println("Going to invoke tfMerging "+tfSStats.getTerm()+":::"+synonymsTerms.size()+":::"+docId+":::"+Thread.currentThread());
    }
    float finalTFValue = tfidfCombinerStrategy.aggregateTermsFre(tfSStats, synonymsTerms,context_);


    float finalIDFValue=tfidfCombinerStrategy.aggregateIDF1(idfStats,synonymsTerms,context_);
    if(context_.shouldDebug() && context_.getSearchArgs().debugDocID.trim().equalsIgnoreCase(docId)){
      System.out.println("for doc ID "+docId+"::"+tfSStats.getTerm()+"::orig tf::"+tfSStats.getTfValue()+":orig idf::"+idfStats.getIdfValue()+":::"+Thread.currentThread());
      System.out.println("for doc ID "+docId+"::"+tfSStats.getTerm()+"::final merged tf::"+finalTFValue+":final idf::"+finalIDFValue+":::"+Thread.currentThread());
    }
    float v = idfStats.getBoost() * finalTFValue * finalIDFValue;


    return v;
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats,BM25QueryContext context_,String docId) {

    TFIDFMergerCombinerStrategy tfidfCombinerStrategy= new TFIDFMergerCombinerStrategy();

    float finalTFValue = tfidfCombinerStrategy.discountTF(tfSStats,context_,boost);
    if(tfSStats.getTfValue() !=finalTFValue){
      System.out.println("called  discountTF>>>>"+tfSStats.getTfValue()+"::"+finalTFValue);
    }



    float finalIDFValue= idfStats.getIdfValue();
    if(context_.shouldDebug() && context_.getSearchArgs().debugDocID.trim().equalsIgnoreCase(docId)){
      System.out.println("for doc ID "+docId+"::"+tfSStats.getTerm()+"::orig tf::"+tfSStats.getTfValue()+":orig idf::"+idfStats.getIdfValue()+":::"+Thread.currentThread());
      System.out.println("for doc ID "+docId+"::"+tfSStats.getTerm()+"::final merged tf::"+finalTFValue+":final idf::"+finalIDFValue+":::"+Thread.currentThread());
    }
    float v = idfStats.getBoost() * finalTFValue * finalIDFValue;


    return v;
  }

  private boolean isSynonym( String orig, String expanded, RerankerContext context ) {
    return context.isSynonyms(orig,expanded);
  }

  private float createTermWeight(float boost, TFStats tfSStats, IDFStats idfStats, BM25QueryContext context_, List<TermScoreDetails> termScoreDetails) {
    //System.out.println("EXPANSION WITHOUT ACTUAL "+idfStats.getTerm()+"::::"+context_.getQueryId());
    float expansionIDF=idfStats.getIdfValue();
    float originalIDF = IDFStats.getOriginalIDF(idfStats.getTerm().toLowerCase(),context_,termScoreDetails);
    if(context_.getSearchArgs().alwaysUseOriginalIdf){
      expansionIDF=originalIDF;
    }
    //System.out.println("originalIDF >>>"+originalIDF+"::"+expansionIDF);

    return boost * tfSStats .getTfValue()* expansionIDF;
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

  private IDFStats extractIDFDetails(String term, Explanation idfExplanation, Explanation[] termSpecificExplanation_, boolean isCovid19) {
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
    if(!isCovid19) {
      IDFStats.setCorpusSize(corpusSize);
    }
    double logValue=Math.log(value);
    float v = Double.valueOf( logValue ).floatValue();



    /*if(term.equals( "prove" )){
      System.out.println("Explanation >>"+idfExplanation);
      //System.out.println("Debugging for term :::"+term+"::"+numbOfDocsWithThatTerm+":::"+totalnumbOfDocsWithField+":::boost::"+boost);
    }*/
    return new IDFStats(term,idfValue,numbOfDocsWithThatTerm,totalnumbOfDocsWithField,boost);

  }

  private Map<String, List<TermScoreDetails>> extractStatsFromExplanation(Explanation explanation, Query query,BM25QueryContext context_,String actaulDocId,String queryText) {
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
    List<String> termsMatched= new ArrayList<>();
    for(Explanation eachTermExpInThatDoc: details){
      Number eachTermScore = eachTermExpInThatDoc.getValue();
      String description = eachTermExpInThatDoc.getDescription();
      int colonIndex=description.indexOf(":");
      String textAfterColon=description.substring(colonIndex+1);
      String term=textAfterColon.split(" ")[0];
      if(term.equals("contentsXXX")){
        // System.out.println("CONTENTS FOUND"+query.toString()+"::"+context_.getQueryId());
        String outputPath=context_.getSearchArgs().output;
        Path path = Paths.get(outputPath);
        String outputDir= path.getParent().toFile().getAbsolutePath();


        String debugPath=outputDir+ File.separator+ context_.getQueryId()+".explain";
        Path path1 = Paths.get(debugPath);
        path1.toFile().delete();
        try{
          PrintWriter out = new PrintWriter(Files.newBufferedWriter(path1, StandardCharsets.UTF_8));
          out.write(query.toString());
          out.write(explanation.toString());
          out.flush();
        }catch(Exception e){
          e.printStackTrace();
        }


      }
      termsMatched.add(term);
      if(context_.shouldDebug()){
        //System.out.println("MATCHED TERM >>>>>>"+term);
      }
      String stemmedTerm=term;
      if(("covid 19".equalsIgnoreCase(stemmedTerm) || ("2019 ncov".equalsIgnoreCase(stemmedTerm) || ("sar cov 2".equalsIgnoreCase(stemmedTerm))))){
       // System.out.println("COVID RELATED DATA >>>>>>>");
      }
      //term=getActualTerm(queryText,term);

      Explanation[] eachTermScoreExplanation = eachTermExpInThatDoc.getDetails();
      for(Explanation termScoreExplanation: eachTermScoreExplanation){
        Number eachTerrmscore = termScoreExplanation.getValue();
        Explanation[] termSpecificExplanation = termScoreExplanation.getDetails();
        boolean isCovid19=false;
        Number value=-1;
        if(termSpecificExplanation.length !=2){
         // System.out.println("length is not equal to  2::"+termSpecificExplanation.length +":::"+termScoreExplanation);
          if((termScoreExplanation.getDescription().indexOf("covid 19") !=-1 || termScoreExplanation.getDescription().indexOf("2019 ncov") !=-1) || (termScoreExplanation.getDescription().indexOf("sar cov 2") !=-1)) {
            //System.out.println("termScoreExplanation.getDescription()::::"+termScoreExplanation.getDescription());
            colonIndex=termScoreExplanation.getDescription().indexOf(":");
            int in_index=termScoreExplanation.getDescription().indexOf("in");
            //System.out.println("in_index >>>"+in_index);
            textAfterColon=termScoreExplanation.getDescription().substring(colonIndex+1,in_index);
           // System.out.println("textAfterColon >>>"+textAfterColon);
            term=textAfterColon;
            stemmedTerm=term;
            isCovid19=true;
            //System.out.println("FOUND COVID !( DATA "+stemmedTerm);
            Explanation[] details1 = termScoreExplanation.getDetails();

            //System.out.println("details1  size is >>>>"+details1.length+":::"+stemmedTerm);
            if(details1.length !=1){
              throw new RuntimeException("Size should be 1");
            }
            value = details1[0].getValue();
            termSpecificExplanation=details1[0].getDetails();
          }
          //continue;
        }
        Explanation idfExplanation=termSpecificExplanation[0];
        IDFStats idfStats = extractIDFDetails(term, idfExplanation,termSpecificExplanation,isCovid19);

        idfStats.setStemmedTerm(stemmedTerm);
        context_.setDocid(term.toLowerCase(),actaulDocId);
        Explanation tfExplnation=termSpecificExplanation[1];
        TFStats tfStats = extractTFDetails(term, tfExplnation,termSpecificExplanation);
        if(isCovid19){
         // System.out.println("for covid19 >>>"+idfStats.getTerm()+":::"+idfStats.getIdfValue());
          //System.out.println("for covid19 >>>"+tfStats.getTerm()+":::"+tfStats.getTfValue());

        }
        TermScoreDetails termScoreDetails= new TermScoreDetails(term,actaulDocId,idfStats,tfStats);
        if(isCovid19){
          System.out.println("Setting score for term >>>"+stemmedTerm+"::"+value);
          termScoreDetails.setScore(value);
        }

        termScoreDetailsList.add(termScoreDetails);
      }

    }
    docIdvsAlltermsScoreDetails.put(actaulDocId,termScoreDetailsList);
    if(context_.shouldDebug()){
      //System.out.println("TERMS MATCHED >>"+actaulDocId+"::"+termsMatched);
    }
    return docIdvsAlltermsScoreDetails;
  }

  private String getActualTerm(String queryText, String term) {
    List<String> queryTokens = Arrays.stream(queryText.split(" ")).collect(Collectors.toList());
    for(String str : queryTokens){
      if(RerankerContext.isEQULUsingStem(str,term)){
        return str;
      }
    }
    return term;
  }


  @Override
  public String tag() {
    return "BM25SynonymReranker";
  }

}
