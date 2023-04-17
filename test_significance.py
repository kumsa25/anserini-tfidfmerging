from scipy import stats

file1 = "runs_final/without_stemming/-originalidf/run.covid.round1.optimal.0.1.bm25.txt.scores"

file2 = "runs_final/without_stemming/-originalidf/run.covid.round1.optimal.1.bm25.txt.scores"

def load_query_run(path, desired_measure="recall_1000"):
    all_queries = {}
    with open(path, "r") as f:
        for line in f:
            measure, query_num, score = line.strip().split()
            if measure != desired_measure: continue
            if query_num == "all":
                print(score)
                continue
            all_queries[query_num] = float(score)
    return all_queries


f1_scores_by_query = load_query_run(file1)
f2_scores_by_query = load_query_run(file2)

f1_scores = []
f2_scores = []

for key in f1_scores_by_query:
    f1_scores.append(f1_scores_by_query[key])
    f2_scores.append(f2_scores_by_query[key])

print(stats.ttest_rel(f1_scores, f2_scores))
