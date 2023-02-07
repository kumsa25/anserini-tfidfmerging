import os

out_dir = "runs_automatic_optimalv3_nostem/"
query_dir = "expanded_queries/"
for filename in os.listdir(query_dir):
    if filename[-4:] == ".txt":
        split_name = filename.split(".")
        disk = split_name[0] # one of disk12, covid
        method = split_name[-2] # one of bm25, bm25s


        # bm25, optimal
        #if not("optimal." in filename and "bm25." in filename):
        #    continue

        if "l.1.2." not in filename and "l.1.5." not in filename and "l.1.1." not in filename: continue

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
        if disk == "disk12":
            disk = "disk12_nostem"
            stem_flag = " -stemmer none"
        else:
            continue

        #if "norm_across_query" not in filename:
        #    continue
        
        print(filename)

    

        # -pickLargerIDF
        # -originalidf
        # -pickSmallerIDF

        if disk in ["disk12", "disk12_nostem", "disk1"] and method == "bm25s":
            os.system(
            "target/appassembler/bin/SearchCollection -index indexes/lucene-index." + disk + "/ -topics src/main/resources/topics-and-qrels/topics.adhoc.51-100.txt -topicreader Trec -output " + out_dir + "/run." + filename + " -bm25 -bm25syn -expwords " + query_dir + filename + " -rerankCutoff 1000 -pickSmallerIDF" + stem_flag)

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -m recall.1000 -m map -m P.30 src/main/resources/topics-and-qrels/qrels.adhoc.51-100.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")

        elif disk == "covid" and method == "bm25s":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index-cord19-abstract-2020-07-16/ -topics src/main/resources/topics-and-qrels/topics.covid-round1.xml  -topicreader Covid -topicfield query -removedups -output " + out_dir + "/run." + filename + " -bm25 -bm25syn -expwords " + query_dir + filename + " -rerankCutoff 1000 -originalidf")

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -c -m recall.1000 -m P.30 -m map  src/main/resources/topics-and-qrels/qrels.covid-round1.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")

        elif disk in ["disk12", "disk12_nostem", "disk1"] and method == "bm25":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index." + disk + "/ -topics src/main/resources/topics-and-qrels/topics.adhoc.51-100.txt -threads 1  -parallelism 1 -topicreader Trec -output " + out_dir + "/run." + filename + " -bm25 -bm25Weighted -ignoreBoost -expwords " + query_dir + filename + stem_flag + " -rerankCutoff 1000")

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -m recall.1000 -m P.30 -m map  src/main/resources/topics-and-qrels/qrels.adhoc.51-100.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")


        elif disk == "covid" and method == "bm25":
            os.system("target/appassembler/bin/SearchCollection -index indexes/lucene-index-cord19-abstract-2020-07-16/ -topics src/main/resources/topics-and-qrels/topics.covid-round1.xml -threads 1  -parallelism 1 -topicreader Covid -topicfield query -removedups -output " + out_dir + "/run." + filename + " -bm25 -bm25Weighted -ignoreBoost -expwords " + query_dir + filename)

            os.system("tools/eval/trec_eval.9.0.4/trec_eval -c -m recall.1000 -m P.30 -m map  src/main/resources/topics-and-qrels/qrels.covid-round1.txt " + out_dir + "/run." + filename + " > " + out_dir + "/run." + filename + ".scores")