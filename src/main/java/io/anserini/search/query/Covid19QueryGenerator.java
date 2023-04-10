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
import io.anserini.index.IndexArgs;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.WeightedExpansionTerm;
import io.anserini.search.SearchArgs;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;

import java.sql.SQLOutput;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.anserini.search.query.BagOfWordsQueryGenerator.addExpansionTerms;
import static io.anserini.search.query.BagOfWordsQueryGenerator.printChanges;

public class Covid19QueryGenerator extends QueryGenerator {
    private static final String COVID_NAMES = "(COVID-?19|2019-?nCov|SARS-?COV-?2?|corona ?virus)";
    private static final Pattern COVID_PATTERN = Pattern.compile(".*" + COVID_NAMES + ".*", Pattern.CASE_INSENSITIVE);

    private static final List<String> BOILERPLATE_PATTERNS = List.of(
            "tell me about",
            "(is|are) there",
            "what (type|kind|types|kinds) of",
            "what do we know (about)?",
            "what is known (about)?",
            "what do we know about",
            "what [a-z]+ (is|are) there (on|about|for|related to|related)?",
            "what (is|are)",
            "how (does|do|are|have|has|can|might|will)",
            "(I am |I'm )?(looking|searching|seeking|desiring).*?(documents|information|pages|knowledge|data|numbers|figures|studies|results) (on|of|about|for|related to|related)?",
            "(I am |I'm )?(looking|searching|seeking|desiring)( (for|about|on))?",
            "how (far|long|wide|narrow|tall|short) (does|do|can)",
            "(what|when|where|why|how|which) ");
    private static final String BOILERPLATE_REGEXP;

    static {
        BOILERPLATE_REGEXP = "(?i)^(" + BOILERPLATE_PATTERNS.stream().collect(Collectors.joining("|")) + ")";
    }

    private BagOfWordsQueryGenerator bowQueryGenerator = new BagOfWordsQueryGenerator();

    public String removeBoilerplate(String q) {
        return q.replaceAll(BOILERPLATE_REGEXP, "").trim();
    }

    public boolean isCovidQuery(String q) {
        return COVID_PATTERN.matcher(q).matches();
    }

    @Override
    public Query buildQuery(String field, Analyzer analyzer, String queryText) {
        // Remove boilerplate
        queryText = removeBoilerplate(queryText);

        // If query doesn't contain variants of COVID-19, then just pass through with BoW generator.
        if (!isCovidQuery(queryText)) {
            return bowQueryGenerator.buildQuery(field, analyzer, queryText);
        }

        // Remove the variant of covid-19 itself.
        queryText = queryText.replaceAll("(?i)" + COVID_NAMES, " ");

        List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
        System.out.println("Query tokens are >>>"+tokens+":::"+queryText);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String t : tokens) {
            builder.add(new BoostQuery(new TermQuery(new Term(field, t)), 1),
                    BooleanClause.Occur.SHOULD);


            //builder.add(new TermQuery(new Term(field, t)), BooleanClause.Occur.SHOULD);
        }

        QueryParser parser = new QueryParser(IndexArgs.CONTENTS, analyzer);

       /* try {
            List<Query> disjuncts = new ArrayList<>();
            disjuncts.add(parser.parse("\"COVID-19\""));
            disjuncts.add(parser.parse("\"2019-nCov\""));
            disjuncts.add(parser.parse("\"SARS-CoV-2\""));
            builder.add(new DisjunctionMaxQuery(disjuncts, 0.0f), BooleanClause.Occur.SHOULD);

        } catch (Exception ParseException) {
            // Do nothing.
        }*/

        return builder.build();
    }

    @Override
    public Query buildQuery(String field, Analyzer analyzer, String queryText, String queryid, SearchArgs args) {
        // Remove boilerplate
        System.out.println("Inside buildQuery of covid >>>>"+queryText+"::"+queryid);
        queryText = removeBoilerplate(queryText);


        // If query doesn't contain variants of COVID-19, then just pass through with BoW generator.
        if (!isCovidQuery(queryText)) {
            return bowQueryGenerator.buildQuery(field, analyzer, queryText);
        }

        List<BagOfWordsQueryGenerator.WeightedTerm> weightedTerms = new ArrayList<>();


        // Remove the variant of covid-19 itself.
        queryText = queryText.replaceAll("(?i)" + COVID_NAMES, " ");

        List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String t : tokens) {
            weightedTerms.add(new BagOfWordsQueryGenerator.WeightedTerm(t.toLowerCase(), 1));

            if (args.bm25s) {
                addExpansionTerms(queryid, t, builder, field, args, weightedTerms, analyzer, tokens);
            }
        }

        if (args.bm25Weighted) {
            List<WeightedExpansionTerm> expansionTermsForBM25 = RerankerContext.getWeight(queryid, args, analyzer);
            expansionTermsForBM25 = expansionTermsForBM25.stream().sorted(Comparator.comparing(WeightedExpansionTerm::getExpansionTerm)).collect(Collectors.toList());
            if (queryid.equalsIgnoreCase("89")) {
                System.out.println("expansionTermsForBM25>>>>>" + expansionTermsForBM25);
            }
            //Set<WeightedExpansionTerm> uniqueTerms= new HashSet<>(expansionTermsForBM25);
            for (WeightedExpansionTerm weightedExpansionTerm : expansionTermsForBM25) {

                BagOfWordsQueryGenerator.WeightedTerm weightedTerm = new BagOfWordsQueryGenerator.WeightedTerm(weightedExpansionTerm.getExpansionTerm(), weightedExpansionTerm.getWeight());
                weightedTerms.add(weightedTerm);

            }

        }

        List<BagOfWordsQueryGenerator.WeightedTerm> weightedTerms2 = new ArrayList<>();
        if (args.removeDuplicateTerms) {
            int sizeBefore = weightedTerms.size();
            Set<BagOfWordsQueryGenerator.WeightedTerm> finalTerms = new HashSet<>(weightedTerms);

            int sizeAfter = finalTerms.size();
            if (sizeAfter != sizeBefore) {
                printChanges(weightedTerms, finalTerms, queryid);
                // System.out.println("Size changed "+weightedTerms+"::::"+finalTerms+":::"+queryid);
            }
            weightedTerms2.addAll(finalTerms);
        } else {
            weightedTerms2 = weightedTerms;
        }
        //builder.add(new TermQuery(new Term(field, t)), BooleanClause.Occur.SHOULD);
        weightedTerms2 = weightedTerms2.stream().sorted(Comparator.comparing(BagOfWordsQueryGenerator.WeightedTerm::getName)).collect(Collectors.toList());
        for (BagOfWordsQueryGenerator.WeightedTerm weightedTerm : weightedTerms2) {
            builder.add(new BoostQuery(new TermQuery(new Term(field, weightedTerm.getName())), weightedTerm.getWeight()),
                    BooleanClause.Occur.SHOULD);

        }

        QueryParser parser = new QueryParser(IndexArgs.CONTENTS, analyzer);

        try {
            List<Query> disjuncts = new ArrayList<>();
            Query parse1 = parser.parse("\"COVID-19\"");
            System.out.println(":::PARSE 1 is >>"+parse1+":::"+parse1.getClass());
            disjuncts.add(parse1);
            Query parse2 = parser.parse("\"2019-nCov\"");
            System.out.println(":::PARSE 2 is >>"+parse2+":::"+parse2.getClass());

            disjuncts.add(parse2);
            Query parse3 = parser.parse("\"SARS-CoV-2\"");
            System.out.println(":::PARSE 3 is >>"+parse3+":::"+parse3.getClass());

            disjuncts.add(parse3);
            builder.add(new BoostQuery(new DisjunctionMaxQuery(disjuncts, 0.0f),1), BooleanClause.Occur.SHOULD);

        } catch (Exception ParseException) {
            // Do nothing.
        }

        return builder.build();
    }
}
