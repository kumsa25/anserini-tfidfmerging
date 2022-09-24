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

import io.anserini.search.SearchArgs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class RerankerContext<K> {
  private final IndexSearcher searcher;
  private final Query query;
  private final K queryId;
  private final String queryDocId; // this is for News Track Background Linking task
  private final String queryText;
  private final List<String> queryTokens;
  private final Query filter;
  private final SearchArgs searchArgs;
  private static Map<String,List<WeightedExpansionTerm>> expansionWords= new HashMap<>(); // Simple text


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
      Properties properties = new Properties();
      properties.load(new FileInputStream(new File(expWordsWithWeightsFile)));
      Set<Object> keys = properties.keySet();
      Iterator<Object> iterator = keys.iterator();
      while (iterator.hasNext()) {
        String word = (String) iterator.next();
        String propertyvalue = properties.getProperty(word);
        //String[] split = propertyvalue.split(":");
        String expansions = propertyvalue.trim();
        int current = 0;
        int lastIndex = expansions.lastIndexOf(")");
        List<WeightedExpansionTerm> weightedExpansionTerms = new ArrayList<>();
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
        expansionWords.put(word, weightedExpansionTerms);


      }
    }
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
    List<WeightedExpansionTerm> weightedExpansionTerms = expansionWords.get( word );
    return weightedExpansionTerms !=null ? weightedExpansionTerms : new ArrayList<>();
  }

  public static boolean isSynonyms(String original, String expanded){
    List<WeightedExpansionTerm> weightedExpansionTerms = expansionWords.get(original);
    if(weightedExpansionTerms==null || weightedExpansionTerms.isEmpty()){
      String rootWord=findRootWord(original);
      if(rootWord==null)
      {
        return false;
      }
      weightedExpansionTerms = expansionWords.get(rootWord);
    }
    for(WeightedExpansionTerm weightedExpansionTerm: weightedExpansionTerms){
      if(weightedExpansionTerm.getExpansionTerm().equalsIgnoreCase(expanded)){
        return true;
      }
    }
    return false;
  }

  private static String findRootWord(String original)
  {
    Set<String> strings = expansionWords.keySet();
    for(String str: strings){
      if(str.toLowerCase().startsWith( original.toLowerCase() )){
        return str;
      }
    }
    return null;
  }
}
