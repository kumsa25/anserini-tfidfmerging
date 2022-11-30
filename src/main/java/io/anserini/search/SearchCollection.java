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

package io.anserini.search;

import io.anserini.analysis.AnalyzerUtils;
import io.anserini.analysis.DefaultEnglishAnalyzer;
import io.anserini.analysis.TweetAnalyzer;
import io.anserini.index.IndexArgs;
import io.anserini.index.generator.TweetGenerator;
import io.anserini.index.generator.WashingtonPostGenerator;
import io.anserini.rerank.RerankerCascade;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.rerank.lib.*;
import io.anserini.search.query.QueryGenerator;
import io.anserini.search.query.SdmQueryGenerator;
import io.anserini.search.similarity.AccurateBM25Similarity;
import io.anserini.search.similarity.ImpactSimilarity;
import io.anserini.search.similarity.TaggedSimilarity;
import io.anserini.search.topicreader.BackgroundLinkingTopicReader;
import io.anserini.search.topicreader.TopicReader;
import io.anserini.search.topicreader.Topics;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bn.BengaliAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;
import org.apache.lucene.analysis.uk.UkrainianMorfologikAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.similarities.AfterEffectL;
import org.apache.lucene.search.similarities.AxiomaticF2EXP;
import org.apache.lucene.search.similarities.AxiomaticF2LOG;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelIn;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.DistributionSPL;
import org.apache.lucene.search.similarities.IBSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.LambdaDF;
import org.apache.lucene.search.similarities.NormalizationH2;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.ParserProperties;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry point for search.
 */
public final class SearchCollection implements Closeable {
  // These are the default tie-breaking rules for documents that end up with the same score with respect to a query.
  // For most collections, docids are strings, and we break ties by lexicographic sort order. For tweets, docids are
  // longs, and we break ties by reverse numerical sort order (i.e., most recent tweet first). This means that searching
  // tweets requires a slightly different code path, which is enabled by the -searchtweets option in SearchArgs.
  public static final Sort BREAK_SCORE_TIES_BY_DOCID =
      new Sort(SortField.FIELD_SCORE, new SortField(IndexArgs.ID, SortField.Type.STRING_VAL));
  public static final Sort BREAK_SCORE_TIES_BY_TWEETID =
      new Sort(SortField.FIELD_SCORE,
          new SortField(TweetGenerator.TweetField.ID_LONG.name, SortField.Type.LONG, true));

  private static final Logger LOG = LogManager.getLogger(SearchCollection.class);

  private final SearchArgs args;
  private final IndexReader reader;
  private final Analyzer analyzer;
  private List<TaggedSimilarity> similarities;
  private List<RerankerCascade> cascades;
  private final boolean isRerank;
  private Map<String, ScoredDocuments> qrels;
  private Set<String> queriesWithRel;

  private final class SearcherThread<K> extends Thread {
    final private IndexReader reader;
    final private IndexSearcher searcher;
    final private SortedMap<K, Map<String, String>> topics;
    final private TaggedSimilarity taggedSimilarity;
    final private RerankerCascade cascade;
    final private String outputPath;
    final private String runTag;

    private SearcherThread(IndexReader reader, SortedMap<K, Map<String, String>> topics, TaggedSimilarity taggedSimilarity,
                           RerankerCascade cascade, Map<String, ScoredDocuments> qrels, String outputPath, String runTag) {
      this.reader = reader;
      this.topics = topics;
      this.taggedSimilarity = taggedSimilarity;
      this.cascade = cascade;
      this.runTag = runTag;
      this.outputPath = outputPath;
      this.searcher = new IndexSearcher(this.reader);
      this.searcher.setSimilarity(this.taggedSimilarity.getSimilarity());
      setName(outputPath);
    }

    @Override
    public void run() {
      try {
        // A short descriptor of the ranking setup.
        final String desc = String.format("ranker: %s, reranker: %s", taggedSimilarity.getTag(), cascade.getTag());

        // This is the number of threads that we're going to devote to running the queries in parallel.
        int parallelism = args.parallelism;
        // BM25 PRF is not thread safe, so we can't run in parallel.
        if (args.bm25prf) {
          parallelism = 1;
        }

        // ThreadPool for parallelizing the execution of individual queries:
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(parallelism);
        // Data structure for holding the per-query results, with the qid as the key and the results (the lines that
        // will go into the final run file) as the value.
        ConcurrentSkipListMap<K, String> results = new ConcurrentSkipListMap<>();
        AtomicInteger cnt = new AtomicInteger();

        final long start = System.nanoTime();
        for (Map.Entry<K, Map<String, String>> entry : topics.entrySet()) {
          K qid = entry.getKey();

          // This is the per-query execution, in parallel.
          executor.execute(() -> {
            // This is for holding the results.
            StringBuilder out = new StringBuilder();

            String queryString = "";
            if (args.topicfield.contains("+")) {
              for (String field : args.topicfield.split("\\+")) {
                queryString += " " + entry.getValue().get(field);
              }
            } else {
              queryString = entry.getValue().get(args.topicfield);
            }

            ScoredDocuments queryQrels = null;
            boolean hasRelDocs = false;
            String qidString = qid.toString();
            if (qrels != null) {
              queryQrels = qrels.get(qidString);
              if (queriesWithRel.contains(qidString)) {
                hasRelDocs = true;
              }
            }
            ScoredDocuments docs;
            try {
              if (args.searchtweets) {
                docs = searchTweets(this.searcher, qid, queryString,
                    Long.parseLong(entry.getValue().get("time")), cascade, queryQrels, hasRelDocs);
              } else if (args.backgroundlinking) {
                docs = searchBackgroundLinking(this.searcher, qid, queryString, cascade);
              } else {
                docs = search(this.searcher, qid, queryString, cascade, queryQrels, hasRelDocs);
              }
            } catch (IOException e) {
              throw new CompletionException(e);
            }

            // For removing duplicate docids.
            Set<String> docids = new HashSet<>();

            int rank = 1;
            for (int i = 0; i < docs.documents.length; i++) {
              Document document = docs.documents[i];
              if(document==null)
              {
                LOG.info( "id is >>" + i + "::" + document );
                continue;
              }
              String docid = document.get(IndexArgs.ID);

              if (args.selectMaxPassage) {
                docid = docid.split(args.selectMaxPassage_delimiter)[0];
              }

              if (docids.contains(docid))
                continue;

              // Remove docids that are identical to the query id if flag is set.
              if (args.removeQuery && docid.equals(qid))
                continue;

              if ("msmarco".equals(args.format)) {
                // MS MARCO output format:
                out.append(String.format(Locale.US, "%s\t%s\t%d\n", qid, docid, rank));
              } else {
                // Standard TREC format:
                // + the first column is the topic number.
                // + the second column is currently unused and should always be "Q0".
                // + the third column is the official document identifier of the retrieved document.
                // + the fourth column is the rank the document is retrieved.
                // + the fifth column shows the score (integer or floating point) that generated the ranking.
                // + the sixth column is called the "run tag" and should be a unique identifier for your
                out.append(String.format(Locale.US, "%s Q0 %s %d %f %s\n",
                    qid, docid, rank, docs.scores[i], runTag));
              }

              // Note that this option is set to false by default because duplicate documents usually indicate some
              // underlying indexing issues, and we don't want to just eat errors silently.
              //
              // However, we we're performing passage retrieval, i.e., with "selectMaxSegment", we *do* want to remove
              // duplicates.
              if (args.removedups || args.selectMaxPassage) {
                docids.add(docid);
              }

              rank++;

              if (args.selectMaxPassage && rank > args.selectMaxPassage_hits) {
                break;
              }
            }

            results.put(qid, out.toString());
            int n = cnt.incrementAndGet();
            if (n % 100 == 0) {
              LOG.info(String.format("%s: %d queries processed", desc, n));
            }
          });
        }

        executor.shutdown();

        try {
          // Wait for existing tasks to terminate.
          while (!executor.awaitTermination(1, TimeUnit.MINUTES));
        } catch (InterruptedException ie) {
          // (Re-)Cancel if current thread also interrupted.
          executor.shutdownNow();
          // Preserve interrupt status.
          Thread.currentThread().interrupt();
        }
        final long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);

        LOG.info(desc + ": " + topics.size() + " queries processed in " +
            DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss") +
            String.format(" = ~%.2f q/s", topics.size()/(durationMillis/1000.0)));

        // Now we write the results to a run file.
        PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8));

        // Here's a really screwy corner case that we have to manually hack around: for MS MARCO V1, the query file is not
        // sorted by qid, but the topic representation internally is (i.e., K is a comparable). The original query runner
        // SearchMsmarco retained the order of the queries; however, this class does not. Thus, the run files list the
        // results in different orders. Due to the way that the MS MARCO V1 eval scripts are written (they report MRR to
        // an excessive number of significant digits), different orders yield slightly different metric values (due to
        // floating point precision issues). Just to retain exactly the same output as SearchMsmarco (which was used to,
        // for example, generate Anserini leaderboard runs), we add an ugly hack here to dump the results in the order
        // of the qids in the query files.
        boolean isMSMARCOv1_passage = topics.firstKey().equals(2) &&
            topics.get(2).get("title").equals("Androgen receptor define") &&
            topics.keySet().size() == 6980;
        boolean isMAMARCOv1_doc = topics.firstKey().equals(2) &&
            topics.get(2).get("title").equals("androgen receptor define") &&
            topics.keySet().size() == 5193;

        if (isMSMARCOv1_passage || isMAMARCOv1_doc) {
          String raw = "";
          try {
            InputStream inputStream = null;
            if (isMSMARCOv1_passage) {
              inputStream = TopicReader.class.getClassLoader().getResourceAsStream(Topics.MSMARCO_PASSAGE_DEV_SUBSET.path);
            } else {
              inputStream = TopicReader.class.getClassLoader().getResourceAsStream(Topics.MSMARCO_DOC_DEV.path);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
              line = line.trim();
              String[] arr = line.split("\\t");
              out.print(results.get(Integer.parseInt(arr[0])));
            }

            inputStream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          // This is the default case: just dump out the qids by their natural order.
          for (K qid : results.keySet()) {
            out.print(results.get(qid));
          }
        }
        out.flush();
        out.close();

      } catch (Exception e) {
        LOG.error(Thread.currentThread().getName() + ": Unexpected Exception: ", e);
      }
    }
  }

  public SearchCollection(SearchArgs args) throws IOException {
    this.args = args;
    Path indexPath = Paths.get(args.index);

    if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
      throw new IllegalArgumentException(String.format("Index path '%s' does not exist or is not a directory.", args.index));
    }

    LOG.info("============ Initializing Searcher ============");
    LOG.info("Index: " + indexPath);
    this.reader = args.inmem ? DirectoryReader.open(MMapDirectory.open(indexPath)) :
        DirectoryReader.open(FSDirectory.open(indexPath));

    LOG.info("Fields: " + Arrays.toString(args.fields));
    if (args.fields.length != 0) {
      // The -fields argument should be in the form of "field1=weight1 field2=weight2...".
      // Try to parse, and throw exception if anything goes wrong.
      try {
        for (String part : args.fields) {
          String[] tok = part.split("=");
          args.fieldsMap.put(tok[0], Float.parseFloat(tok[1]));
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Error parsing -fields parameter: " + Arrays.toString(args.fields));
      }
    }

    // Are we searching tweets?
    if (args.searchtweets) {
      LOG.info("Searching tweets? true");
      analyzer = new TweetAnalyzer();
    } else if (args.language.equals("ar")) {
      analyzer = new ArabicAnalyzer();
      LOG.info("Language: ar");
    } else if (args.language.equals("bn")) {
      analyzer = new BengaliAnalyzer();
      LOG.info("Language: bn");
    } else if (args.language.equals("da")) {
      analyzer = new DanishAnalyzer();
      LOG.info("Language: da");
    } else if (args.language.equals("de")) {
      analyzer = new GermanAnalyzer();
      LOG.info("Language: de");
    } else if (args.language.equals("es")) {
      analyzer = new SpanishAnalyzer();
      LOG.info("Language: es");
    } else if (args.language.equals("fa")) {
      analyzer = new PersianAnalyzer();
      LOG.info("Language: fa");
    } else if (args.language.equals("fi")) {
      analyzer = new FinnishAnalyzer();
      LOG.info("Language: fi");
    } else if (args.language.equals("fr")) {
      analyzer = new FrenchAnalyzer();
      LOG.info("Language: fr");
    } else if (args.language.equals("hi")) {
      analyzer = new HindiAnalyzer();
      LOG.info("Language: hi");
    } else if (args.language.equals("hu")) {
      analyzer = new HungarianAnalyzer();
      LOG.info("Language: hu");
    } else if (args.language.equals("id")) {
      analyzer = new IndonesianAnalyzer();
      LOG.info("Language: id");
    } else if (args.language.equals("it")) {
      analyzer = new ItalianAnalyzer();
      LOG.info("Language: it");
    } else if (args.language.equals("ja")) {
      analyzer = new JapaneseAnalyzer();
      LOG.info("Language: ja");
    } else if (args.language.equals("ko")) {
      analyzer = new CJKAnalyzer();
      LOG.info("Language: ko");
    } else if (args.language.equals("nl")) {
      analyzer = new DutchAnalyzer();
      LOG.info("Language: nl");
    } else if (args.language.equals("no")) {
      analyzer = new NorwegianAnalyzer();
      LOG.info("Language: no");
    } else if (args.language.equals("pl")) {
      analyzer = new MorfologikAnalyzer();
      LOG.info("Language: pl");
    } else if (args.language.equals("pt")) {
      analyzer = new PortugueseAnalyzer();
      LOG.info("Language: pt");
    } else if (args.language.equals("ru")) {
      analyzer = new RussianAnalyzer();
      LOG.info("Language: ru");
    } else if (args.language.equals("sv")) {
      analyzer = new SwedishAnalyzer();
      LOG.info("Language: sv");
    } else if (args.language.equals("th")) {
      analyzer = new ThaiAnalyzer();
      LOG.info("Language: th");
    } else if (args.language.equals("tr")) {
      analyzer = new TurkishAnalyzer();
      LOG.info("Language: tr");
    } else if (args.language.equals("uk")) {
      analyzer = new UkrainianMorfologikAnalyzer();
      LOG.info("Language: uk");
    } else if (args.language.equals("zh")) {
      analyzer = new CJKAnalyzer();
      LOG.info("Language: zh");
    } else if (args.pretokenized || args.language.equals("sw") || args.language.equals("te")) {
      analyzer = new WhitespaceAnalyzer();
      LOG.info("Pretokenized");
    } else {
      // Default to English
      analyzer = DefaultEnglishAnalyzer.fromArguments(args.stemmer, args.keepstop, args.stopwords);
      LOG.info("Language: en");
      LOG.info("Stemmer: " + args.stemmer);
      LOG.info("Keep stopwords? " + args.keepstop);
      LOG.info("Stopwords file: " + args.stopwords);
      LOG.info("Number of threads for running different parameter configurations: " + args.threads);
      LOG.info("Number of threads for running each individual parameter configuration: " + args.parallelism);
    }

    isRerank = args.rm3 || args.axiom || args.bm25prf || args.rocchio || args.bm25syn;

    if (this.isRerank && args.rf_qrels != null){
      loadQrels(args.rf_qrels);      
    }

    // Fix for index compatibility issue between Lucene 8 and 9: https://github.com/castorini/anserini/issues/1952
    // If we detect an older index version, we turn off consistent tie-breaking, which avoids accessing docvalues,
    // which is the source of the incompatibility.
    if (!reader.toString().contains("lucene.version=9")) {
      args.arbitraryScoreTieBreak = true;
      args.axiom_deterministic = false;
    }
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  private List<TaggedSimilarity> constructSimilarities() {
    List<TaggedSimilarity> similarities = new ArrayList<>();

    if (args.bm25) {
      for (String k1 : args.bm25_k1) {
        for (String b : args.bm25_b) {
          similarities.add(new TaggedSimilarity(new BM25Similarity(Float.valueOf(k1), Float.valueOf(b)),
              String.format("bm25(k1=%s,b=%s)", k1, b)));
        }
      }
    } else if (args.bm25Accurate) {
      for (String k1 : args.bm25_k1) {
        for (String b : args.bm25_b) {
          similarities.add(new TaggedSimilarity(new AccurateBM25Similarity(Float.valueOf(k1), Float.valueOf(b)),
              String.format("bm25accurate(k1=%s,b=%s)", k1, b)));
        }
      }
    } else if (args.qld) {
      for (String mu : args.qld_mu) {
        similarities.add(new TaggedSimilarity(new LMDirichletSimilarity(Float.valueOf(mu)),
            String.format("qld(mu=%s)", mu)));
      }
    } else if (args.qljm) {
      for (String lambda : args.qljm_lambda) {
        similarities.add(new TaggedSimilarity(new LMJelinekMercerSimilarity(Float.valueOf(lambda)),
            String.format("qljm(lambda=%s)", lambda)));
      }
    } else if (args.inl2) {
      for (String c : args.inl2_c) {
        similarities.add(new TaggedSimilarity(
            new DFRSimilarity(new BasicModelIn(), new AfterEffectL(), new NormalizationH2(Float.valueOf(c))),
            String.format("inl2(c=%s)", c)));
      }
    } else if (args.spl) {
      for (String c : args.spl_c) {
        similarities.add(new TaggedSimilarity(
            new IBSimilarity(new DistributionSPL(), new LambdaDF(), new NormalizationH2(Float.valueOf(c))),
            String.format("spl(c=%s)", c)));
      }
    } else if (args.f2exp) {
      for (String s : args.f2exp_s) {
        similarities.add(new TaggedSimilarity(new AxiomaticF2EXP(Float.valueOf(s)), String.format("f2exp(s=%s)", s)));
      }
    } else if (args.f2log) {
      for (String s : args.f2log_s) {
        similarities.add(new TaggedSimilarity(new AxiomaticF2LOG(Float.valueOf(s)), String.format("f2log(s=%s)", s)));
      }
    } else if (args.impact) {
      similarities.add(new TaggedSimilarity(new ImpactSimilarity(), "impact()"));
    } else {
      throw new IllegalArgumentException("Error: Must specify scoring model!");
    }
    return similarities;
  }

  private List<RerankerCascade> constructRerankers() throws IOException {
    List<RerankerCascade> cascades = new ArrayList<>();

    if (args.rm3) {
      for (String fbTerms : args.rm3_fbTerms) {
        for (String fbDocs : args.rm3_fbDocs) {
          for (String originalQueryWeight : args.rm3_originalQueryWeight) {
            String tag;
            if (this.args.rf_qrels != null){
              tag = String.format("rm3Rf(fbTerms=%s,originalQueryWeight=%s)",
                fbTerms, originalQueryWeight);
            } else{
              tag = String.format("rm3(fbTerms=%s,fbDocs=%s,originalQueryWeight=%s)",
                fbTerms, fbDocs, originalQueryWeight);
            }

            RerankerCascade cascade = new RerankerCascade(tag);
            cascade.add(new Rm3Reranker(analyzer, IndexArgs.CONTENTS, Integer.valueOf(fbTerms),
                Integer.valueOf(fbDocs), Float.valueOf(originalQueryWeight), args.rm3_outputQuery,
                !args.rm3_noTermFilter));
            cascade.add(new ScoreTiesAdjusterReranker());
            cascades.add(cascade);
          }
        }
      }
    } else if (args.axiom) {
      for (String r : args.axiom_r) {
        for (String n : args.axiom_n) {
          for (String beta : args.axiom_beta) {
            for (String top : args.axiom_top) {
              for (String seed : args.axiom_seed) {
                String tag;
                if (this.args.rf_qrels != null){
                  tag = String.format("axRf(seed=%s,n=%s,beta=%s,top=%s)", seed, n, beta, top);
                } else{
                  tag = String.format("ax(seed=%s,r=%s,n=%s,beta=%s,top=%s)", seed, r, n, beta, top);
                }
                RerankerCascade cascade = new RerankerCascade(tag);
                cascade.add(new AxiomReranker(args.index, args.axiom_index, IndexArgs.CONTENTS,
                    args.axiom_deterministic, Integer.valueOf(seed), Integer.valueOf(r),
                    Integer.valueOf(n), Float.valueOf(beta), Integer.valueOf(top),
                    args.axiom_docids, args.axiom_outputQuery, args.searchtweets));
                cascade.add(new ScoreTiesAdjusterReranker());
                cascades.add(cascade);
              }
            }
          }
        }
      }
    } else if (args.bm25prf) {
      for (String fbTerms : args.bm25prf_fbTerms) {
        for (String fbDocs : args.bm25prf_fbDocs) {
          for (String k1 : args.bm25prf_k1) {
            for (String b : args.bm25prf_b) {
              for (String newTermWeight : args.bm25prf_newTermWeight) {
                String tag;
                if (this.args.rf_qrels != null){
                  tag = String.format("bm25Rf(fbTerms=%s,k1=%s,b=%s,newTermWeight=%s)",
                    fbTerms, k1, b, newTermWeight);
                } else{
                  tag = String.format("bm25prf(fbTerms=%s,fbDocs=%s,k1=%s,b=%s,newTermWeight=%s)",
                    fbTerms, fbDocs, k1, b, newTermWeight);
                }
                RerankerCascade cascade = new RerankerCascade(tag);
                cascade.add(new BM25PrfReranker(analyzer, IndexArgs.CONTENTS, Integer.valueOf(fbTerms),
                    Integer.valueOf(fbDocs), Float.valueOf(k1), Float.valueOf(b), Float.valueOf(newTermWeight),
                    args.bm25prf_outputQuery));
                cascade.add(new ScoreTiesAdjusterReranker());
                cascades.add(cascade);
              }
            }
          }
        }
      }
    }else if (args.bm25syn) {
      String tag = String.format("bm25synonyms");
      RerankerCascade cascade = new RerankerCascade(tag);
      cascade.add(new BM25SynonymReranker(analyzer, IndexArgs.CONTENTS,
              args.bm25syn_outputQuery));
      cascade.add(new ScoreTiesAdjusterReranker());
      cascades.add(cascade);

       // }
      //}
    }
    else if (args.rocchio) {
      for (String topFbTerms : args.rocchio_topFbTerms) {
        for (String topFbDocs : args.rocchio_topFbDocs) {
          for (String bottomFbTerms : args.rocchio_bottomFbTerms) {
            for (String bottomFbDocs : args.rocchio_bottomFbDocs) {
              for (String alpha : args.rocchio_alpha) {
                for (String beta : args.rocchio_beta) {
                  for (String gamma : args.rocchio_gamma) {
                    String tag;
                    if (args.rocchio_useNegative == false){
                      gamma = "0";
                    }
                    if (this.args.rf_qrels != null){
                      tag = String.format("rocchioRf(topFbTerms=%s,bottomFbTerms=%s,alpha=%s,beta=%s,gamma=%s)", topFbTerms, bottomFbTerms, alpha, beta, gamma);
                    } else{
                      tag = String.format("rocchio(topFbTerms=%s,topFbDocs=%s,bottomFbTerms=%s,bottomFbDocs=%s,alpha=%s,beta=%s,gamma=%s)", topFbTerms, topFbDocs, bottomFbTerms, bottomFbDocs, alpha, beta, gamma);
                    }
                    RerankerCascade cascade = new RerankerCascade(tag);
                    cascade.add(new RocchioReranker(analyzer, IndexArgs.CONTENTS, Integer.valueOf(topFbTerms),
                        Integer.valueOf(topFbDocs), Integer.valueOf(bottomFbTerms), Integer.valueOf(bottomFbDocs),
                        Float.valueOf(alpha), Float.valueOf(beta), Float.valueOf(gamma), args.rocchio_outputQuery, args.rocchio_useNegative));
                    cascade.add(new ScoreTiesAdjusterReranker());
                    cascades.add(cascade);
                  }
                }
              }
            }
          }
        }
      }
    }else {
      RerankerCascade cascade = new RerankerCascade();
      cascade.add(new ScoreTiesAdjusterReranker());
      cascades.add(cascade);
    }

    return cascades;
  }

  private void loadQrels(String rf_qrels) throws IOException {
    LOG.info("============ Loading qrels ============");
    LOG.info("rf_qrels: " + rf_qrels);
    Path rfQrelsFilePath = Paths.get(rf_qrels);
    if (!Files.exists(rfQrelsFilePath) || !Files.isRegularFile(rfQrelsFilePath) || !Files.isReadable(rfQrelsFilePath)) {
        throw new IllegalArgumentException("Qrels file : " + rfQrelsFilePath + " does not exist or is not a (readable) file.");
    }
    Map<String, Map<String, Integer>> qrelsDocs = new HashMap<>();
    this.queriesWithRel = new HashSet<>();
    InputStream fin = Files.newInputStream(Paths.get(rf_qrels), StandardOpenOption.READ);
    BufferedInputStream in = new BufferedInputStream(fin);
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    for (String line : IOUtils.readLines(reader)) {
      String[] cols = line.split("\\s+"); 
      int rel = Integer.valueOf(cols[3]);
      String qid = cols[0];
      if (rel > 0) {
        this.queriesWithRel.add(qid);
      }
      String fbDocid = cols[2];
      Map<String, Integer> queryQrelsDocs = qrelsDocs.get(qid);
      if (queryQrelsDocs == null){
        queryQrelsDocs = new HashMap<>();
        qrelsDocs.put(qid, queryQrelsDocs);
      }
      queryQrelsDocs.put(fbDocid, Integer.valueOf(rel));
    }

    this.qrels = new HashMap<>();
    for (Map.Entry<String, Map<String, Integer>> q : qrelsDocs.entrySet()) {
      String qid = q.getKey();
      Map<String, Integer> queryQrelsDocs = q.getValue();
      this.qrels.put(qid, ScoredDocuments.fromQrels(queryQrelsDocs, this.reader));
    }

  } 

  @SuppressWarnings("unchecked")
  public <K> void runTopics() throws IOException {
    TopicReader<K> tr;
    SortedMap<K, Map<String, String>> topics = new TreeMap<>();
    for (String singleTopicsFile : args.topics) {
      Path topicsFilePath = Paths.get(singleTopicsFile);
      if (!Files.exists(topicsFilePath) || !Files.isRegularFile(topicsFilePath) || !Files.isReadable(topicsFilePath)) {
        throw new IllegalArgumentException("Topics file : " + topicsFilePath + " does not exist or is not a (readable) file.");
      }
      try {
        tr = (TopicReader<K>) Class.forName("io.anserini.search.topicreader." + args.topicReader + "TopicReader")
            .getConstructor(Path.class).newInstance(topicsFilePath);
        topics.putAll(tr.read());
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalArgumentException("Unable to load topic reader: " + args.topicReader);
      }
    }

    final String runTag = args.runtag == null ? "Anserini" : args.runtag;
    LOG.info("runtag: " + runTag);

    final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(args.threads);
    this.similarities = constructSimilarities();
    this.cascades = constructRerankers();

    LOG.info("============ Launching Search Threads ============");

    for (TaggedSimilarity taggedSimilarity : similarities) {
      for (RerankerCascade cascade : cascades) {
        final String outputPath;

        if (similarities.size() == 1 && cascades.size() == 1) {
          outputPath = args.output;
        } else {
          outputPath = String.format("%s_%s_%s", args.output, taggedSimilarity.getTag(), cascade.getTag());
        }

        if (args.skipexists && new File(outputPath).exists()) {
          LOG.info("Run already exists, skipping: " + outputPath);
          continue;
        }
        executor.execute(new SearcherThread<>(reader, topics, taggedSimilarity, cascade, this.qrels, outputPath, runTag));
      }
    }
    executor.shutdown();

    try {
      // Wait for existing tasks to terminate
      while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      executor.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }

  public <K> ScoredDocuments search(IndexSearcher searcher, K qid, String queryString, RerankerCascade cascade, ScoredDocuments queryQrels,
                                    boolean hasRelDocs) throws IOException {
    Query query;

    if (args.sdm) {
      query = new SdmQueryGenerator(args.sdm_tw, args.sdm_ow, args.sdm_uw).buildQuery(IndexArgs.CONTENTS, analyzer, queryString);
    } else {
      QueryGenerator generator;
      try {
        generator = (QueryGenerator) Class.forName("io.anserini.search.query." + args.queryGenerator)
            .getConstructor().newInstance();
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalArgumentException("Unable to load QueryGenerator: " + args.topicReader);
      }

      // If fieldsMap isn't null, then it means that the -fields option is specified. In this case, we search across
      // multiple fields with the associated boosts.
      if(args.bm25Weighted){
        query = args.fields.length == 0 ? generator.buildQuery(IndexArgs.CONTENTS, analyzer, queryString,qid.toString(),args) :
                generator.buildQuery(args.fieldsMap, analyzer, queryString,qid.toString(),args);
      }else {
        query = args.fields.length == 0 ? generator.buildQuery(IndexArgs.CONTENTS, analyzer, queryString,args) :
                generator.buildQuery(args.fieldsMap, analyzer, queryString,args);
      }
    }

    TopDocs rs = new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[]{});
    if (!isRerank || (args.rerankcutoff > 0 && args.rf_qrels == null) || (args.rf_qrels != null && !hasRelDocs)) {
      if (args.arbitraryScoreTieBreak) {// Figure out how to break the scoring ties.
        rs = searcher.search(query, (isRerank && args.rf_qrels == null) ? args.rerankcutoff : args.hits);
      } else {
        rs = searcher.search(query, (isRerank && args.rf_qrels == null) ? args.rerankcutoff : args.hits, BREAK_SCORE_TIES_BY_DOCID, true);
      }
    }

    List<String> queryTokens = AnalyzerUtils.analyze(analyzer, queryString);

    RerankerContext context = new RerankerContext<>(searcher, qid, query, null, queryString, queryTokens, null, args);
    ScoredDocuments scoredFbDocs; 
    if ( isRerank && args.rf_qrels != null) {
      if (hasRelDocs){
        scoredFbDocs = queryQrels;
      } else{//if no relevant documents, only perform score based tie breaking next
        LOG.info("No relevant documents for " + qid.toString());
        scoredFbDocs = ScoredDocuments.fromTopDocs(rs, searcher);
        cascade = new RerankerCascade();
        cascade.add(new ScoreTiesAdjusterReranker());
      }
    } else {
      scoredFbDocs = ScoredDocuments.fromTopDocs(rs, searcher);
    }

    return cascade.run(scoredFbDocs, context);
  }

  public <K> ScoredDocuments searchBackgroundLinking(IndexSearcher searcher, K qid, String docid,
                                                     RerankerCascade cascade) throws IOException {
    // Extract a list of analyzed terms from the document to compose a query.
    List<String> terms = BackgroundLinkingTopicReader.extractTerms(reader, docid, args.backgroundlinking_k, analyzer);
    // Since the terms are already analyzed, we just join them together and use the StandardQueryParser.
    Query docQuery;
    try {
      docQuery = new StandardQueryParser().parse(StringUtils.join(terms, " "), IndexArgs.CONTENTS);
    } catch (QueryNodeException e) {
      throw new RuntimeException("Unable to create a Lucene query comprised of terms extracted from query document!");
    }

    // Per track guidelines, no opinion or editorials. Filter out articles of these types.
    Query filter = new TermInSetQuery(
        WashingtonPostGenerator.WashingtonPostField.KICKER.name, new BytesRef("Opinions"),
        new BytesRef("Letters to the Editor"), new BytesRef("The Post's View"));

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(filter, BooleanClause.Occur.MUST_NOT);
    builder.add(docQuery, BooleanClause.Occur.MUST);
    Query query = builder.build();

    // Search using constructed query.
    TopDocs rs;
    if (args.arbitraryScoreTieBreak) {
      rs = searcher.search(query, (isRerank && args.rf_qrels == null) ? args.rerankcutoff : args.hits);
    } else {
      rs = searcher.search(query, (isRerank && args.rf_qrels == null) ? args.rerankcutoff :
          args.hits, BREAK_SCORE_TIES_BY_DOCID, true);
    }

    RerankerContext context = new RerankerContext<>(searcher, qid, query, docid,
        StringUtils.join(", ", terms), terms, null, args);

    // Run the existing cascade.
    ScoredDocuments docs = cascade.run(ScoredDocuments.fromTopDocs(rs, searcher), context);

    // Perform post-processing (e.g., date filter, dedupping, etc.) as a final step.
    return new NewsBackgroundLinkingReranker().rerank(docs, context);
  }

  public <K> ScoredDocuments searchTweets(IndexSearcher searcher, K qid, String queryString, long t, RerankerCascade cascade, 
                                          ScoredDocuments queryQrels, boolean hasRelDocs) throws IOException {
    Query keywordQuery;
    if (args.sdm) {
      keywordQuery = new SdmQueryGenerator(args.sdm_tw, args.sdm_ow, args.sdm_uw).buildQuery(IndexArgs.CONTENTS, analyzer, queryString);
    } else {
      try {
        QueryGenerator generator = (QueryGenerator) Class.forName("io.anserini.search.query." + args.queryGenerator)
            .getConstructor().newInstance();
        keywordQuery = generator.buildQuery(IndexArgs.CONTENTS, analyzer, queryString);
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalArgumentException("Unable to load QueryGenerator: " + args.topicReader);
      }
    }
    List<String> queryTokens = AnalyzerUtils.analyze(analyzer, queryString);

    // Do not consider the tweets with tweet ids that are beyond the queryTweetTime
    // <querytweettime> tag contains the timestamp of the query in terms of the
    // chronologically nearest tweet id within the corpus
    Query filter = LongPoint.newRangeQuery(TweetGenerator.TweetField.ID_LONG.name, 0L, t);
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(filter, BooleanClause.Occur.FILTER);
    builder.add(keywordQuery, BooleanClause.Occur.MUST);
    Query compositeQuery = builder.build();


    TopDocs rs = new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[]{});
    if (!isRerank || (args.rerankcutoff > 0 && args.rf_qrels == null) || (args.rf_qrels != null && !hasRelDocs)) {
      if (args.arbitraryScoreTieBreak) {// Figure out how to break the scoring ties.
        rs = searcher.search(compositeQuery, (isRerank && args.rf_qrels == null) ? args.rerankcutoff : args.hits);
      } else {
        rs = searcher.search(compositeQuery, (isRerank && args.rf_qrels == null) ? args.rerankcutoff : args.hits, 
                             BREAK_SCORE_TIES_BY_TWEETID, true);
      }
    }

    RerankerContext context = new RerankerContext<>(searcher, qid, keywordQuery, null, queryString, queryTokens, filter, args);
    ScoredDocuments scoredFbDocs; 
    if ( isRerank && args.rf_qrels != null) {
      if (hasRelDocs) {
        scoredFbDocs = queryQrels;
      } else{//if no relevant documents, only perform score based tie breaking next
        scoredFbDocs = ScoredDocuments.fromTopDocs(rs, searcher);
        cascade = new RerankerCascade();
        cascade.add(new ScoreTiesAdjusterReranker());
      }
    } else {
      scoredFbDocs = ScoredDocuments.fromTopDocs(rs, searcher);
    }

    return cascade.run(scoredFbDocs,  context);
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Sanjeev here");
    SearchArgs searchArgs = new SearchArgs();
    CmdLineParser parser = new CmdLineParser(searchArgs, ParserProperties.defaults().withUsageWidth(100));

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      e.printStackTrace();
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.err.println("Example: SearchCollection" + parser.printExample(OptionHandlerFilter.REQUIRED));
      return;
    }

    final long start = System.nanoTime();
    SearchCollection searcher;

    // We're at top-level already inside a main; makes no sense to propagate exceptions further, so reformat the
    // exception messages and display on console.
    try {
      searcher = new SearchCollection(searchArgs);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return;
    }

    searcher.runTopics();
    searcher.close();
    final long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    LOG.info("Total run time: " + DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss"));
  }
}
