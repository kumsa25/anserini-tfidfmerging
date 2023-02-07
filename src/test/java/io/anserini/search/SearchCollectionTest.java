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

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchCollectionTest {
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();
  private PrintStream save;

  private void redirectStderr() {
    save = System.err;
    err.reset();
    System.setErr(new PrintStream(err));
  }

  private void restoreStderr() {
    System.setErr(save);
  }

  @Test
  public void testIncompleteOptions() throws Exception {
    redirectStderr();

    SearchCollection.main(new String[] {});
    assertTrue(err.toString().contains("Option \"-index\" is required"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo"});
    assertTrue(err.toString().contains("Option \"-output\" is required"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar"});
    assertTrue(err.toString().contains("Option \"-topicreader\" is required"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz"});
    assertTrue(err.toString().contains("Option \"-topics\" is required"));

    restoreStderr();
  }

  @Test
  public void testOptionErrors() throws Exception {
    redirectStderr();

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz", "-topics", "topic",});
    assertTrue(err.toString().contains("Index path 'foo' does not exist or is not a directory."));

    restoreStderr();
  }

  @Test
  public void testMutallyExclusive() throws Exception {
    redirectStderr();

    // We can't exhaustively test all combinations, so we just sample a few combinations.

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz", "-topics", "topic",
        "-bm25", "-qld"});
    assertTrue(err.toString().contains("cannot be used with the option"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz", "-topics", "topic",
        "-bm25", "-qljm"});
    assertTrue(err.toString().contains("cannot be used with the option"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz", "-topics", "topic",
        "-qljm", "-spl"});
    assertTrue(err.toString().contains("cannot be used with the option"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz", "-topics", "topic",
        "-inl2", "-f2exp"});
    assertTrue(err.toString().contains("cannot be used with the option"));

    err.reset();
    SearchCollection.main(new String[] {"-index", "foo", "-output", "bar", "-topicreader", "baz", "-topics", "topic",
        "-f2log", "-f2exp"});
    assertTrue(err.toString().contains("cannot be used with the option"));

    restoreStderr();
  }

  @Test
  public void testSearchLucene9() throws Exception {
    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_trec_collection2/",
            "-topics", "src/test/resources/sample_topics/Trec",
            "-topicreader", "Trec", "-output", "run.test", "-bm25"});
    check("run.test", new String[]{
        "1 Q0 DOC222 1 0.343200 Anserini",
        "1 Q0 TREC_DOC_1 2 0.333400 Anserini",
        "1 Q0 WSJ_1 3 0.068700 Anserini"});
    new File("run.test").delete();

    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_json_collection_tokenized/",
            "-topics", "src/test/resources/sample_topics/json_topics1.tsv",
            "-topicreader", "TsvInt", "-output", "run.test", "-pretokenized", "-impact"});
    check("run.test", new String[]{
        "1 Q0 2000001 1 4.000000 Anserini",});
    new File("run.test").delete();
  }
  @Test
  public void testSearchLucene8() throws Exception {
    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene8-index.sample_docs_trec_collection2/",
            "-topics", "src/test/resources/sample_topics/Trec",
            "-topicreader", "Trec", "-output", "run.test", "-bm25"});
    check("run.test", new String[]{
        "1 Q0 DOC222 1 0.343192 Anserini",
        "1 Q0 TREC_DOC_1 2 0.333445 Anserini",
        "1 Q0 WSJ_1 3 0.068654 Anserini"});
    new File("run.test").delete();

    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene8-index.sample_docs_json_collection_tokenized/",
            "-topics", "src/test/resources/sample_topics/json_topics1.tsv",
            "-topicreader", "TsvInt", "-output", "run.test", "-pretokenized", "-impact"});
    check("run.test", new String[]{
        "1 Q0 2000001 1 4.000000 Anserini",});
    new File("run.test").delete();
  }

  @Test
  public void testSearchBM25S() throws Exception {
    SearchCollection.main(
        new String[] {"-index", "/shared/bm25s/simulations/lucene-index.simulated_corpus/",
            "-topics", "/shared/bm25s/simulations/topics.txt",
            "-topicreader", "Trec", "-output", "run.test", "-bm25", "-bm25syn",
            "-expwords", "/shared/bm25s/simulations/expanded_query.txt",
            "-rerankCutoff", "1000", "-stemmer", "none"});
    check("run.test", new String[]{
        "0 Q0 790 1 2.962220881933101 Anserini",
        "0 Q0 2 2 2.8904564767776035 Anserini",
        "0 Q0 983 3 2.8883360395047037 Anserini"});
    new File("run.test").delete();

    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_json_collection_tokenized/",
            "-topics", "src/test/resources/sample_topics/json_topics1.tsv",
            "-topicreader", "TsvInt", "-output", "run.test", "-pretokenized", "-impact"});
    check("run.test", new String[]{
        "1 Q0 2000001 1 4.000000 Anserini",});
    new File("run.test").delete();
  }


  @Test
  public void testSearchLucene91() throws Exception {
    SearchCollection.main(
            new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene8-index.sample_docs_trec_collection2/",
                    "-topics", "src/test/resources/sample_topics/Trec",
                    "-topicreader", "Trec", "-output", "run.test", "-bm25","-bm25Weighted","-ignoreBoost", "-expwords","/Users/sk671103/dictionary.txt"});
    check("run.test", new String[]{
            "1 Q0 DOC222 1 0.343192 Anserini",
            "1 Q0 TREC_DOC_1 2 0.333445 Anserini",
            "1 Q0 WSJ_1 3 0.068654 Anserini"});
    new File("run.test").delete();

    SearchCollection.main(
            new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene8-index.sample_docs_json_collection_tokenized/",
                    "-topics", "src/test/resources/sample_topics/json_topics1.tsv",
                    "-topicreader", "TsvInt", "-output", "run.test", "-pretokenized", "-impact"});
    check("run.test", new String[]{
            "1 Q0 2000001 1 4.000000 Anserini",});
    new File("run.test").delete();
  }

  @Test
  public void testBM25TF(){
    float k= (float) 0.9;
    float b= (float) 0.4;
    float dl=6;
    float avgdl= (float) 5.3333335;
    float[] weights= new float[]{0,1,10,20,30,40,50};
    float[] tfTotal= new float[weights.length];
    float v1 = b * dl / avgdl;

    float[] freq= new float[]{ (float) 1.0, (float) 5.0, (float) 10.0, (float) 20.0 };

    for(int i=0;i<freq.length;i++)
    {
      float freqTotal = freq[i];
      for( int j = 0; j < weights.length; j++ )
      {
        freqTotal = freq[i];
        freqTotal += freqTotal * weights[j];
        float v = freqTotal + (k * (1 - b + v1));
        tfTotal[j] = freqTotal / v;
        System.out.println(freq[i]+":::"+freqTotal+":::"+ weights[j] + "::::" + tfTotal[j] );

      }
    }




  }
  /*@Test
  public void testSearchLucene_sk() throws Exception {
    double logValue=Math.log(8.0/7);
    System.out.println("log >>>"+logValue);
    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_trec_collection2/",
            "-topics", "src/test/resources/sample_topics/Trec_sk_1",
            "-topicreader", "Trec", "-output", "run.test1", "-bm25", "-bm25syn","-no_rerank","-expwords","/Users/kumsa25/dictionary.txt"});
    check("run.test1", new String[]{
        "1 Q0 TREC_DOC_1 1 0.259700 Anserini",
        "1 Q0 DOC222 2 0.241600 Anserini"});
    new File("run.test1").delete();


    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_trec_collection2/",
            "-topics", "src/test/resources/sample_topics/Trec_sk_2",
            "-topicreader", "Trec", "-output", "run.test2", "-bm25", "-bm25syn","-no_rerank","-expwords","/Users/kumsa25/dictionary.txt"});
    check("run.test2", new String[]{
        "1 Q0 DOC222 1 0.101500 Anserini",
        "1 Q0 TREC_DOC_1 2 0.073800 Anserini",
        "1 Q0 WSJ_1 3 0.068700 Anserini"

    });
    new File("run.test2").delete();

    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_trec_collection2/",
            "-topics", "src/test/resources/sample_topics/Trec_sk_3",
            "-topicreader", "Trec", "-output", "run.test3", "-bm25", "-bm25syn","-no_rerank","-expwords","/Users/kumsa25/dictionary.txt"});
    check("run.test3", new String[]{
        "1 Q0 WSJ_1 1 0.504300 Anserini" });
    new File("run.test3").delete();

    SearchCollection.main(
        new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_trec_collection2/",
            "-topics", "src/test/resources/sample_topics/Trec",
            "-topicreader", "Trec", "-output", "run.test", "-bm25", "-bm25syn","-expwords","/Users/kumsa25/dictionary.txt"});
    check("run.test", new String[]{
        "1 Q0 DOC222 1 0.343200 Anserini",
        "1 Q0 TREC_DOC_1 2 0.333400 Anserini",
        "1 Q0 WSJ_1 3 0.068700 Anserini"});
    new File("run.test").delete();



    /*SearchCollection.main(
            new String[] {"-index", "src/test/resources/prebuilt_indexes/lucene9-index.sample_docs_json_collection_tokenized/",
                    "-topics", "src/test/resources/sample_topics/json_topics1.tsv",
                    "-topicreader", "TsvInt", "-output", "run.test", "-pretokenized", "-impact", "-bm25syn","-expwords","/Users/kumsa25/dictionary.txt"});
    check("run.test", new String[]{
            "1 Q0 2000001 1 4.000000 Anserini",});
    new File("run.test").delete();
  }*/


  protected void check(String output, String[] ref) throws IOException {
    BufferedReader br = new BufferedReader(new FileReader(output));

    int cnt = 0;
    String s;
    while ((s = br.readLine()) != null) {
      assertEquals(ref[cnt], s);
      cnt++;
    }

    assertEquals(cnt, ref.length);
  }
}
