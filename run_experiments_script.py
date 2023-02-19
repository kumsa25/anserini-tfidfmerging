import os

#stem = "with_stemming"
stem = "without_stemming"

#idf = "-originalidf"
#idf = "-pickLargerIDF"
#idf = "-pickSmallerIDF"
#idf = "-idfUnion"
idf = "-pickAvgIDF"

"""
bm25 (optimal, non-optimal) (with, without stemming), always originalidf

[GOOD] optimal without stemming
[GOOD] non-optimal without stemming

bm25s (optimal, non-optimal) (with, without stemming), (original, larger, smaller, union)

[DONE] optimal without stemming idfunion
[DONE] non-optimal without stemming idfunion

[DONE] optimal without stemming pickLargerIDF
[DONE] non-optimal without stemming pickLargerIDF

[DONE] optimal without stemming originalidf
[DONE] non-optimal without stemming originalidf

[DONE] optimal without stemming pickSmallerIDF
[DONE] non-optimal without stemming pickSmallerIDF

[DONE] optimal without stemming pickAvgIDF
[DONE] non-optimal without stemming pickAvgIDF


"""

out_dir = "runs_final/" + stem + "/" + idf + "/"
query_dir = "expanded_queries_final/"
for filename in os.listdir(query_dir):
    if filename[-4:] == ".txt":
        split_name = filename.split(".")
        disk = split_name[0] # one of disk12, covid
        method = split_name[-2] # one of bm25, bm25s


        # bm25, optimal
        #if not("optimal." in filename and "bm25." in filename):
        #    continue

        # bm25, not optimal
        #if not("wordnet." in filename and "bm25." in filename):
        #    continue

        # bm25s, optimal
        if not("optimal." in filename and "bm25s." in filename):
            continue

        # bm25s, not optimal
        #if not("wordnet." in filename and "bm25s." in filename):
        #    continue
        
        stem_flag = ""

        if stem == "without_stemming":
            stem_flag = " -stemmer none"
            if disk == "disk12":
                disk = "disk12_nostem"
            elif disk == "cran":
                disk = "cran_nostem"
            elif disk == "covid":
                disk = "covid_nostem"
            else:
                continue
        
        print(filename)   
        print(stem_flag, disk)

        if disk in ["disk12", "disk12_nostem", "disk1"] and method == "bm25s":
            os.system(
            "target/appassembler/bin/SearchCollection -index indexes/lucene-index." + disk + "/ -topics src/main/resources/topics-and-qrels/topics.adhoc.51-100.txt -topicreader Trec -output " + out_dir + "/run." + filename + " -bm25 -bm25s -expwords " + query_dir + filename + " -rerankCutoff 1000 -ignoreBoost " + idf + stem_flag)

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -m recall.1000 -m map -m P.30 src/main/resources/topics-and-qrels/qrels.adhoc.51-100.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")

        elif disk in ["covid", "covid_nostem"] and method == "bm25s":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index-" + disk + "/ -topics src/main/resources/topics-and-qrels/topics.covid-round1.xml  -topicreader Covid -topicfield query -removedups -output " + out_dir + "/run." + filename + " -bm25 -bm25s -expwords " + query_dir + filename + " -rerankCutoff 1000 -ignoreBoost " + idf + stem_flag)

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -c -m recall.1000 -m P.30 -m map  src/main/resources/topics-and-qrels/qrels.covid-round1.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")
        
        elif disk in ["cran", "cran_nostem"] and method == "bm25s":
            os.system(
            "target/appassembler/bin/SearchCollection -index indexes/lucene-index." + disk + "/ -topics cran/cran_queries_trec.txt -topicreader Trec -output " + out_dir + "/run." + filename + " -bm25 -bm25s -expwords " + query_dir + filename + " -rerankCutoff 1000 -ignoreBoost " + idf + stem_flag)

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -m recall.1000 -m map -m P.30 cran/cran_qrel_trec.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")



        elif disk in ["disk12", "disk12_nostem", "disk1"] and method == "bm25":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index." + disk + "/ -topics src/main/resources/topics-and-qrels/topics.adhoc.51-100.txt -threads 1  -parallelism 1 -topicreader Trec -output " + out_dir + "/run." + filename + " -bm25 -bm25Weighted -ignoreBoost -expwords " + query_dir + filename + stem_flag + " -rerankCutoff 1000")

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -m recall.1000 -m P.30 -m map  src/main/resources/topics-and-qrels/qrels.adhoc.51-100.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")

        elif disk in ["covid", "covid_nostem"] and method == "bm25":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index-" + disk + "/ -topics src/main/resources/topics-and-qrels/topics.covid-round1.xml -threads 1  -parallelism 1 -topicreader Covid -topicfield query -removedups -output " + out_dir + "/run." + filename + " -bm25 -bm25Weighted -ignoreBoost -expwords " + query_dir + filename + stem_flag)

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -c -m recall.1000 -m P.30 -m map  src/main/resources/topics-and-qrels/qrels.covid-round1.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")

        elif disk in ["cran", "cran_nostem"] and method == "bm25":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index." + disk + "/ -topics cran/cran_queries_trec.txt -threads 1  -parallelism 1 -topicreader Trec -output " + out_dir + "/run." + filename + " -bm25 -bm25Weighted -ignoreBoost -expwords " + query_dir + filename + stem_flag + " -rerankCutoff 1000")

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -m recall.1000 -m P.30 -m map  cran/cran_qrel_trec.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")