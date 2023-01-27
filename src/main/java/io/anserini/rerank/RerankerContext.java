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

package io.anserini.rerank;

import io.anserini.rerank.lib.TFStats;
import io.anserini.search.SearchArgs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.tartarus.snowball.ext.PorterStemmer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RerankerContext<K> {
  public static final String QUERYID_AND_TERM_SEPERATOR = "-";
  private static volatile boolean done=false;
  private final IndexSearcher searcher;
  private final Query query;
  private final K queryId;
  private final String queryDocId; // this is for News Track Background Linking task
  private final String queryText;
  private final List<String> queryTokens;
  private final Query filter;
  private final SearchArgs searchArgs;
  private static Map<String,Map<String,List<WeightedExpansionTerm>>> expansionWords= new HashMap<>(); // Simple text
  private static Map<String, List<WeightedExpansionTerm>> weightedBM25Terms= new ConcurrentHashMap<>();


  public RerankerContext(IndexSearcher searcher, K queryId, Query query, String queryDocId, String queryText,
                         List<String> queryTokens, Query filter, SearchArgs searchArgs) throws IOException {
    this.searcher = searcher;
    this.query = query;
    this.queryId = queryId;
    this.queryDocId = queryDocId;
    this.queryText = queryText;
    this.queryTokens = queryTokens;
    this.filter = filter;
    this.searchArgs = searchArgs;
    String expWordsWithWeightsFile = searchArgs.expwords;
    if (expWordsWithWeightsFile != null && expWordsWithWeightsFile.trim().length() > 0) {
      if(searchArgs.bm25syn) {
        buildDictionaryForExpansion(expWordsWithWeightsFile);
      }
    }
  }


  private static void buildWeightedTerm(String expWordsWithWeightsFile) throws IOException {
    Properties properties = new Properties();
    properties.load(new FileInputStream(new File(expWordsWithWeightsFile)));
    Set<Object> keys = properties.keySet();
    Iterator<Object> iterator = keys.iterator();
    while (iterator.hasNext()) {
      String termWithQID = (String) iterator.next();
      String weight = properties.getProperty(termWithQID).trim();
      int endIndex = termWithQID.indexOf(QUERYID_AND_TERM_SEPERATOR);
      String queryId=termWithQID.substring(0, endIndex);
      String term=termWithQID.substring(endIndex+1);
      List<WeightedExpansionTerm> weightedExpansionTerms = weightedBM25Terms.get(queryId);
      if(weightedExpansionTerms==null){
        weightedExpansionTerms= new ArrayList<>();
        weightedBM25Terms.put(queryId,weightedExpansionTerms);
      }
      weightedExpansionTerms.add(new WeightedExpansionTerm(Float.parseFloat(weight),term.toLowerCase()));
      //String stemWord = findStemWord(term);
      //weightedExpansionTerms.add(new WeightedExpansionTerm(Float.parseFloat(weight),stemWord.toLowerCase()));



    }
  }

  private static void buildDictionaryForExpansion(String expWordsWithWeightsFile) throws IOException {
    Properties properties = new Properties();
    properties.load(new FileInputStream(new File(expWordsWithWeightsFile)));
    Set<Object> keys = properties.keySet();
    Iterator<Object> iterator = keys.iterator();
    while (iterator.hasNext()) {
      String word = (String) iterator.next();
      String[] keyWithQueryId=word.split(QUERYID_AND_TERM_SEPERATOR);
      String id=keyWithQueryId[0];

      String propertyvalue = properties.getProperty(word);
      //String[] split = propertyvalue.split(":");
      String expansions = propertyvalue.trim();
      int current = 0;
      int lastIndex = expansions.lastIndexOf(")");
      List<WeightedExpansionTerm> weightedExpansionTerms = new ArrayList<>();
      word=keyWithQueryId[1];

      Map expansionWordsForTerms= new HashMap<>();
      while (current < lastIndex) {
        current = expansions.indexOf("(",current);
        ;
        int closeIndex = expansions.indexOf(")", current);
        String content = expansions.substring(current + 1, closeIndex);
        String[] split1 = content.split(",");
        String expansionWord = split1[0];
        String weight = split1[1];
        weightedExpansionTerms.add(new WeightedExpansionTerm(Float.parseFloat(weight), expansionWord));
        current = closeIndex + 1;
      }
      expansionWordsForTerms.put(word.toLowerCase(), weightedExpansionTerms);
      Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( id );
      if(stringListMap==null){
        expansionWords.put( id,expansionWordsForTerms );
      }else
      {
        stringListMap.putAll( expansionWordsForTerms );
      }

    }
  }

  public static List<WeightedExpansionTerm> getWeight(String queryid,SearchArgs args) {
    String expWordsWithWeightsFile = args.expwords;
    if (!done && expWordsWithWeightsFile != null && expWordsWithWeightsFile.trim().length() > 0) {
      try {
        buildWeightedTerm(expWordsWithWeightsFile);
        done=true;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    List<WeightedExpansionTerm> weightedExpansionTerms = weightedBM25Terms.get(queryid);
    return weightedExpansionTerms !=null ? weightedExpansionTerms : new ArrayList<>();

  }

  public  float calculateWeight( String original, TFStats synonymsTF_ )
  {
    String root=findRootWord( original );

    if(root !=null){
      Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( queryId.toString() );
      List<WeightedExpansionTerm> weightedExpansionTerms = stringListMap.get( root );
      String rootSynonym=findRootWord( synonymsTF_.getTerm() );
      for(WeightedExpansionTerm weightedExpansionTerm : weightedExpansionTerms){
        String expansionTerm = weightedExpansionTerm.getExpansionTerm();
        if( expansionTerm.equalsIgnoreCase( rootSynonym ) || expansionTerm.equalsIgnoreCase( synonymsTF_.getTerm() ))
        {
          return weightedExpansionTerm.getWeight();
        }


      }

    }
    return findSynRootWord( original,synonymsTF_.getTerm() );
  }

  public  float overrideWeight( String original)
  {
    Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( queryId.toString() );
    Collection<List<WeightedExpansionTerm>> values = stringListMap.values();
    for(List<WeightedExpansionTerm> list : values){
      for(WeightedExpansionTerm term: list){
        if(term.getExpansionTerm().equalsIgnoreCase(original)){
          return term.getWeight();
        }
      }
    }
    return overrideWeight2(original,values);
  }

  public  float overrideWeight2( String original,Collection<List<WeightedExpansionTerm>> values)
  {
    for(List<WeightedExpansionTerm> list : values){
      for(WeightedExpansionTerm term: list){
        if(isEQULUsingStem(term.getExpansionTerm(),original)){
          return term.getWeight();
        }
      }
    }

    //System.out.println("NO MATCH found "+queryId+":::"+original)
    return overrideWeight3(original,values);
  }

  public  float overrideWeight3( String original,Collection<List<WeightedExpansionTerm>> values)
  {
    for(List<WeightedExpansionTerm> list : values){
      for(WeightedExpansionTerm term: list){
        if(isEQULUsingContains(term.getExpansionTerm(),original)){
          return term.getWeight();
        }
      }
    }

    System.out.println("NO MATCH found "+queryId+":::"+original);
    return 1;
  }

  public static boolean isEQULUsingContains(String word, String stemWord){

    return word.indexOf(stemWord) !=-1;
  }



  private  float findSynRootWord( String original_, String synonym )
  {
    Set<String> strings = expansionWords.keySet();
    Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( queryId.toString() );

    for(String  str:strings){
      String stem=findStemWord(str);
      if(stem.equalsIgnoreCase( original_ )){
        List<WeightedExpansionTerm> weightedExpansionTerms = stringListMap.get( str );
        for(WeightedExpansionTerm weightedExpansionTerm : weightedExpansionTerms){
          String expansionTerm = weightedExpansionTerm.getExpansionTerm();
          String stem1=findStemWord( expansionTerm );
          //System.out.println(expansionTerm+":::"+stem1);
          String stem2=findStemWord( synonym );
          //System.out.println(synonym+"::"+stem2);
          if(stem1.equalsIgnoreCase( stem2 )){
            return weightedExpansionTerm.getWeight();
          }
        }
      }
    }
    return 0;

  }

  public static String findStemWord(String word){
    /*PorterStemmer stem = new PorterStemmer();
    stem.setCurrent(word);
    stem.stem();
    return stem.getCurrent();*/
    return word;
  }
  public static boolean isEQULUsingStem(String word, String stemWord){
    PorterStemmer stem = new PorterStemmer();
    stem.setCurrent(word);
    stem.stem();
    return stem.getCurrent().equalsIgnoreCase(stemWord);
  }

  public IndexSearcher getIndexSearcher() {
    return searcher;
  }

  public Query getFilter() {
    return filter;
  }

  public Query getQuery() {
    return query;
  }

  public K getQueryId() {
    return queryId;
  }

  public String getQueryDocId() {
    return queryDocId;
  }

  public String getQueryText() {
    return queryText;
  }

  public List<String> getQueryTokens() {
    return queryTokens;
  }

  public SearchArgs getSearchArgs() {
    return searchArgs;
  }

  public List<WeightedExpansionTerm> getExpansionTerms(String word){
    Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( queryId.toString() );
    if(stringListMap==null){
      System.out.println("Did not find any expansion terms for the queryId>>"+queryId);
      return Collections.EMPTY_LIST;
    }

    List<WeightedExpansionTerm> weightedExpansionTerms = stringListMap.get( word.toLowerCase() );
    if(weightedExpansionTerms==null){
      if(!searchArgs.stemmer.equals("none")) {
        String root = findRootWord(word);
        weightedExpansionTerms = stringListMap.get(root);
      }
    }
    return weightedExpansionTerms !=null ? weightedExpansionTerms : new ArrayList<>();
  }

  public String  getExpansionTerms2(){
    StringBuffer buffer= new StringBuffer();
    Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( queryId.toString() );
    if(stringListMap==null){
      System.out.println("Did not find any expansion terms for the queryId>>"+queryId);
      return buffer.toString();
    }

    Collection<List<WeightedExpansionTerm>> weightedExpansionTerms = stringListMap.values();
    for(List<WeightedExpansionTerm> list : weightedExpansionTerms){
      append(list,buffer);
    }
    return buffer.toString();



  }

  public void append(List<WeightedExpansionTerm> expansionTerms, StringBuffer buffer){
    for(WeightedExpansionTerm weightedExpansionTerm: expansionTerms){

      String expansionTerm = weightedExpansionTerm.getExpansionTerm();
      String uniqueTerms =expansionTerm;

      buffer.append( uniqueTerms );
      buffer.append(" ");
    }

  }

  public  boolean isSynonyms(String original, String expanded){
    //TODO revisit this
    if(original.equalsIgnoreCase( expanded )){
      return false;
    }
    boolean shouldLog=false;
    if(original.equals( "airbu" )){
      //  shouldLog=true;
    }
    Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get( queryId.toString() );

    List<WeightedExpansionTerm> weightedExpansionTerms = stringListMap.get(original);
    if(shouldLog){
      System.out.println("weightedExpansionTerms >>>"+weightedExpansionTerms);
    }
    if(weightedExpansionTerms==null || weightedExpansionTerms.isEmpty()){
      String rootWord=findRootWord(original);
      if(shouldLog){
        System.out.println("Root word >>>"+rootWord+"::::"+expanded);
      }
      if(rootWord==null && !searchArgs.stemmer.equals("none"))
      {
        return false;
      }
      if(!searchArgs.stemmer.equals("none")) {
        weightedExpansionTerms = stringListMap.get(rootWord);
      }
    }
    if(weightedExpansionTerms==null){
      // System.out.println("Inside isSynonyms >>>"+original+":::"+expanded);
      //System.out.println("Stemmer >>"+searchArgs.stemmer);
      return false;
    }
    for(WeightedExpansionTerm weightedExpansionTerm: weightedExpansionTerms){
      if(weightedExpansionTerm.getExpansionTerm().equalsIgnoreCase(expanded)){
        return true;
      }
      if(!searchArgs.stemmer.equals("none")) {
        String rootWord = findRootWord(expanded);
        if (rootWord == null) {
          return false;
        }
        if (weightedExpansionTerm.getExpansionTerm().equalsIgnoreCase(rootWord)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String findRootWord(String original)
  {
    return original;
    /*Set<String> strings = expansionWords.keySet();
    for(String str: strings){
      if(str.toLowerCase().startsWith( original.toLowerCase() )){
        return str;
      }
    }
    return null;*/
  }


}
