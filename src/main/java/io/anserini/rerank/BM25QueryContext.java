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

import io.anserini.rerank.lib.TermScoreDetails;
import io.anserini.search.SearchArgs;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.tartarus.snowball.ext.PorterStemmer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import io.anserini.rerank.lib.IDFStats;
import java.util.concurrent.CopyOnWriteArraySet;



public class BM25QueryContext<K>  extends  RerankerContext{
    public static final String QUERYID_AND_TERM_SEPERATOR = "-";
    private static volatile boolean done = false;
    private final IndexSearcher searcher;
    private final Query query;
    private final K queryId;
    private final String queryDocId; // this is for News Track Background Linking task
    private final String queryText;
    private final List<String> queryTokens;
    private final Query filter;
    private final SearchArgs searchArgs;
    private static Map<String, Map<String, List<WeightedExpansionTerm>>> expansionWords = new ConcurrentHashMap<>(); // Simple text
    private static Map<String, List<WeightedExpansionTerm>> weightedBM25Terms = new ConcurrentHashMap<>();
    private static Map<String, Map<String, String>> queryIdVsFullTokens = new ConcurrentHashMap<>();
    private static Map<String, Map<String, String>> queryIdVsStemmedTokens = new ConcurrentHashMap<>();

    private static Map<String, CopyOnWriteArrayList<String>> queryIdVsActualTokens = new ConcurrentHashMap<>();

    private static Map<String,Map<String,Float>> expansionTermsWeight= new ConcurrentHashMap<>();
    private static Map<String,CopyOnWriteArrayList<String>> queryTerms= new ConcurrentHashMap<>();
    private  Map<String, Set<String>> termVsDocIds= new ConcurrentHashMap<>();



    public BM25QueryContext(IndexSearcher searcher, K queryId, Query query, String queryDocId, String queryText,
                            List<String> queryTokens, Query filter, SearchArgs searchArgs) throws IOException {
        super();
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
            if (searchArgs.bm25s) {
                buildDictionaryForExpansion(expWordsWithWeightsFile);
                // System.out.println("DICTIONARY IS >>>"+expansionWords);
            }
        }
        populateNonStemmedVsStemmedTokens(queryId, queryText, queryTokens);
        populateStemWordsVsNonStemmedTerms(queryId, queryText, queryTokens);
    }



    public static void addActualQueryTokens(String queryid, String token) {
        queryIdVsActualTokens.putIfAbsent(queryid, new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<String> strings = queryIdVsActualTokens.get(queryid);
        strings.add(token);
    }

    public static void setQueryTerms(String queryid, String term) {
        CopyOnWriteArrayList<String> terms= new CopyOnWriteArrayList();
        CopyOnWriteArrayList<String> strings = queryTerms.putIfAbsent(queryid, terms);
        if(strings !=null){
            strings.add(term);
        }else{
            terms.add(term);
        }
    }

    /**
     *
     * @param termScoreDetails
     * @return actual query terms with synonyms
     */
    public List<TermScoreDetails> preprocess(List<TermScoreDetails> termScoreDetails,BM25QueryContext context_) {
        CopyOnWriteArrayList actuals= new CopyOnWriteArrayList();
        for (TermScoreDetails term : termScoreDetails) {
            boolean aQueryTerm = isAQueryTerm(queryId.toString(), term.getTerm());
            String actualToken = getActualToken(term.getTerm());
            if(aQueryTerm) {
                if(context_.shouldDebug()){
                    //System.out.println("it is a query term ::"+context_.getQueryId()+"::"+term);
                }
                term.setWeight(1);
                setSynonyms(actualToken, term, termScoreDetails);
                actuals.add(term);
            }

        }
        if(actuals.size() !=termScoreDetails.size()){
            // System.out.println("SAME SIZE QUERY AND >>>>>>");
        }
        return actuals;
    }

    public List<TermScoreDetails> filterOnlyExpansionTermsMatches(List<TermScoreDetails> termScoreDetails, List<TermScoreDetails> queryTerms) {
        CopyOnWriteArrayList expansion= new CopyOnWriteArrayList();
        for (TermScoreDetails term : termScoreDetails) {
            if(queryTerms.contains(term)){
                continue;
            }
            boolean isIncludedAsSynonym=isIncludedAsSynonym(term, queryTerms);
            if(!isIncludedAsSynonym) {
                expansion.add(term);
            }
            /*String actualToken = getActualToken(term.getTerm());
            if(actualToken==null){
                // Expansion term
                expansion.add(term);
            }*/


        }
        return expansion;
    }

    private boolean isIncludedAsSynonym(TermScoreDetails synonymTerm, List<TermScoreDetails> queryTerms) {
        for(TermScoreDetails queryTerm : queryTerms){
            List<TermScoreDetails> synonymsTerms = queryTerm.getSynonymsTerms();
            if(synonymsTerms==null || synonymsTerms.isEmpty()){
                continue;
            }
            if(synonymsTerms.contains(synonymTerm)){
                return true;
            }
        }
        return false;
    }

    public boolean isNotAnExpansionTerm(TermScoreDetails termScoreDetails){
        String actualToken = getActualToken(termScoreDetails.getTerm());
        return actualToken !=null;
    }

    public void setSynonyms(String actualTerm, TermScoreDetails actualTermDetails,List<TermScoreDetails> termScoreDetails) {
        for (TermScoreDetails term : termScoreDetails) {
            //String actualToken = getActualToken(term.getTerm());
            if (term==actualTermDetails) {
                continue;
            }
            Map<String, List<WeightedExpansionTerm>> stringListMap = BM25QueryContext.getQueryExpansionTerms(queryId.toString());

            //Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get(queryId);
            if(shouldDebug()){
                //System.out.println("EXPANSION WORDS setSynonyms >>>"+expansionWords);
                // System.out.println("EXPANSION WORDS stringListMap >>>"+stringListMap);

            }
            if(stringListMap==null){
                if(shouldDebug()){
                    System.out.println("SOMETHING WEIRD >>>>>"+queryId+":::"+expansionWords);
                }
                return;
            }
            List<WeightedExpansionTerm> weightedExpansionTerms = stringListMap.get(actualTerm.toLowerCase());
            if(weightedExpansionTerms==null){
                if(shouldDebug()){
                    System.out.println("weightedExpansionTerms is NULL for "+queryId+"::"+actualTerm);
                }
                return;
            }
            for (WeightedExpansionTerm expansionTerm : weightedExpansionTerms) {
                String expansion = expansionTerm.getExpansionTerm(); // This is in dictionary
                // if (actualToken.equalsIgnoreCase(expansion)) {
                boolean expansionTermFoundInSearch=isExpansionTermFoundInSearchResults(expansion,termScoreDetails);
                if(expansionTermFoundInSearch) {
                    if(shouldDebug()){
                        // System.out.println("Setting expansion term weight" + actualTerm + "::" + expansion + "::" + expansionTerm.getWeight());
                    }
                    term.setWeight(expansionTerm.getWeight());
                    if(!actualTermDetails.getSynonymsTerms().contains(term)){

                        actualTermDetails.addSynonymsTFStats(term);
                    }else{
                        if(shouldDebug()){
                            // System.out.println("DUPLICATES ##############");
                        }
                    }
                }
                //   }

            }
        }

    }

    private boolean isExpansionTermFoundInSearchResults(String expansion, List<TermScoreDetails> termScoreDetails) {
        for(TermScoreDetails term: termScoreDetails){
            if(term.getTerm().equalsIgnoreCase(expansion)){
                if(shouldDebug()){
                    //  System.out.println("Comparing >>>"+term.getTerm()+":::"+expansion);
                }
                return true;
            }
        }
        return false;
    }

    private void populateNonStemmedVsStemmedTokens(K queryId, String queryText, List<String> queryTokens) {
        Map<String, String> stemsMap = new ConcurrentHashMap();
        Map<String, String> actualTokenVsStemWords = queryIdVsFullTokens.putIfAbsent(queryId.toString(), stemsMap);

        for (String queryToken : queryTokens) {
            String stemWord = findStemWord(queryToken);

            if (actualTokenVsStemWords != null) {
                actualTokenVsStemWords.put(queryToken.toLowerCase(), stemWord.toLowerCase());
            } else {
                stemsMap.put(queryToken.toLowerCase(), stemWord.toLowerCase());
            }
        }
    }

    public String getActualToken(String stemmedToken) {
        Map<String, String> stringStringMap = queryIdVsStemmedTokens.get(queryId);
        if(stringStringMap==null){
            return stemmedToken;
        }
        String actualToken = stringStringMap.get(stemmedToken.toLowerCase());
        if (actualToken != null) {
            return actualToken;
        }
        return getActualTokensFrom(stemmedToken, queryIdVsFullTokens.get(queryId));
    }

    public boolean isAQueryTerm(String queryId, String term){
        CopyOnWriteArrayList<String> strings = queryTerms.get(queryId);
        if(strings==null){
            return false;
        }
        String stemWord = findStemWord(term);
        boolean stemCheck=strings.contains(stemWord) || strings.contains(stemWord.toLowerCase());
        return strings.contains(term) || strings.contains(term.toLowerCase()) ;
    }

    public String getActualTokensFrom(String token, Map<String, String> tokensMap) {
        return tokensMap.get(queryId);
    }

    private void populateStemWordsVsNonStemmedTerms(K queryId, String queryText, List<String> queryTokens) {
        Map<String, String> stemsMap = new ConcurrentHashMap();
        Map<String, String> actualTokenVsStemWords = queryIdVsStemmedTokens.putIfAbsent(queryId.toString(), stemsMap);

        for (String queryToken : queryTokens) {
            String stemWord = findStemWord(queryToken);

            if (actualTokenVsStemWords != null) {
                actualTokenVsStemWords.put(stemWord.toLowerCase(), queryToken.toLowerCase());
            } else {
                stemsMap.put(stemWord.toLowerCase(), queryToken.toLowerCase());
            }
        }
    }

    public static Map<String, List<WeightedExpansionTerm>> getQueryExpansionTerms(String queryid){
        if(queryid.equalsIgnoreCase("52")){
            //  System.out.println("Inside getQueryExpansionTerms "+queryid+"::"+expansionWords);
        }
        return expansionWords.get(queryid);
    }


    public static void buildDictionaryForExpansion(String expWordsWithWeightsFile) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(new File(expWordsWithWeightsFile)));
        Set<Object> keys = properties.keySet();
        Iterator<Object> iterator = keys.iterator();
        while (iterator.hasNext()) {
            String word = (String) iterator.next();
            String[] keyWithQueryId = word.split(QUERYID_AND_TERM_SEPERATOR);
            String id = keyWithQueryId[0];

            String propertyvalue = properties.getProperty(word);
            //String[] split = propertyvalue.split(":");
            String expansions = propertyvalue.trim();
            int current = 0;
            int lastIndex = expansions.lastIndexOf(")");
            List<WeightedExpansionTerm> weightedExpansionTerms = new CopyOnWriteArrayList<>();
            word = keyWithQueryId[1];

            Map expansionWordsForTerms = new ConcurrentHashMap<>();
            Map<String,Float> expansionTermsWeight= new ConcurrentHashMap<>();


            while (current < lastIndex) {
                current = expansions.indexOf("(", current);
                ;
                int closeIndex = expansions.indexOf(")", current);
                String content = expansions.substring(current + 1, closeIndex);
                String[] split1 = content.split(",");
                String expansionWord = split1[0];
                String weight = split1[1];
                weightedExpansionTerms.add(new WeightedExpansionTerm(Float.parseFloat(weight), expansionWord));
                current = closeIndex + 1;
                if(expansionTermsWeight.containsKey(expansionWord.toString())){
                    throw new RuntimeException("Duplicate expansion words found for query "+id+"::"+expansionWord);
                }
                expansionTermsWeight.put(expansionWord.toLowerCase(),Float.parseFloat(weight));
            }
            expansionWordsForTerms.put(word.toLowerCase(), weightedExpansionTerms);
            Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get(id);
            if (stringListMap == null) {
                expansionWords.put(id, expansionWordsForTerms);
            } else {
                stringListMap.putAll(expansionWordsForTerms);
            }
            Map<String, List<WeightedExpansionTerm>> stringListMap1 = expansionWords.get(id);
            if(id.equals("52")){
                //   System.out.println("Adding in stringListMap1 >>"+expansionWordsForTerms);
            }
            stringListMap1.putAll(expansionWordsForTerms);
            expansionWords.put(id,stringListMap1);

        }
        // System.out.println("BUILT DICTIONARY >>>>>"+expansionWords);
    }

    public float getWeight(String token) {
        Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get(queryId);
        Collection<List<WeightedExpansionTerm>> values = stringListMap.values();
        for (List<WeightedExpansionTerm> list : values) {
            for (WeightedExpansionTerm expansionTerm : list) {
                if (expansionTerm.getExpansionTerm().equalsIgnoreCase(token)) {
                    return expansionTerm.getWeight();
                }

            }

        }
        if (stringListMap.containsKey(token.toLowerCase())) {
            return 1;
        }
        return -1;


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



    public static String findStemWord(String word) {
        PorterStemmer stem = new PorterStemmer();
        stem.setCurrent(word);
        stem.stem();
        return stem.getCurrent();
    }


    public String findCorrepsondingQueryTerm(TermScoreDetails term) {
        Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get(queryId);
        Set<String> strings = stringListMap.keySet();
        Iterator<String> iterator = strings.iterator();
        while(iterator.hasNext()){
            String next = iterator.next();
            String root=findToken(term.getTerm(),stringListMap.get(next));
            if(root !=null){
                return next;
            }
        }
        return null;

    }

    public String findToken(String token, List<WeightedExpansionTerm> expansionTerms){
        for(WeightedExpansionTerm expansionTerm : expansionTerms){
            String s = expansionTerm.getExpansionTerm().toLowerCase();
            if(s.equalsIgnoreCase(token)){
                return token;
            }
            String actual=getActualToken(expansionTerm.getExpansionTerm());
            if(actual.equalsIgnoreCase(token) || getActualToken(token).equalsIgnoreCase(s) || actual.equalsIgnoreCase(getActualToken(token))){
                return token;
            }
        }
        return null;
    }

    public boolean isQueryTermFound(String qeryTerm, List<TermScoreDetails> terms) {
        String actualq=getActualToken(qeryTerm);
        for(TermScoreDetails termScoreDetails : terms){
            if(isNotAnExpansionTerm(termScoreDetails)){
                String term = termScoreDetails.getTerm();
                if(term.equalsIgnoreCase(qeryTerm)){
                    return true;
                }
                String actual=getActualToken(term);

                if(actual.equalsIgnoreCase(qeryTerm) || actual.equalsIgnoreCase(actualq) || term.equalsIgnoreCase(actual)){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean notIncluded(List<TermScoreDetails> queryTerms, TermScoreDetails expansion) {
        for(TermScoreDetails queryterm: queryTerms){
            if(queryterm.getSynonymsTerms().contains(expansion)){
                return false;
            }
        }
        return true;
    }

    public  boolean shouldDebug() {
        return getSearchArgs().debugQueryID.trim().equals(getQueryId().toString().trim());
    }

    public String findQueryTermForExpansion(String expnsionTerm) {
        Map<String, List<WeightedExpansionTerm>> stringListMap = expansionWords.get(queryId.toString());
        Set<String> strings = stringListMap.keySet();
        Iterator<String> iterator = strings.iterator();
        while(iterator.hasNext()){
            String original = iterator.next();
            List<WeightedExpansionTerm> weightedExpansionTerms = stringListMap.get(original);
            List<String> collect = weightedExpansionTerms.stream().map(WeightedExpansionTerm::getExpansionTerm).collect(Collectors.toList());
            for(String s: collect){
                if(s.equalsIgnoreCase(expnsionTerm)){
                    return original;
                }
            }
        }
        return null;
    }

    public void setDocid(String term,String docId){
        Set<String> docIds = termVsDocIds.get(term);
        if(docIds==null){
            docIds= new CopyOnWriteArraySet<>();
        }
        docIds.add(docId);
        termVsDocIds.put(term,docIds);
    }

    public Set<String> getDocId(IDFStats original) {
        return termVsDocIds.get(original.getTerm().toLowerCase());
    }

}
