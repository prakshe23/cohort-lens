package com.cohortlens.ml;

import com.cohortlens.core.AnalyticsService;
import com.cohortlens.core.ImportProcessor;
import com.cohortlens.core.ImportResult;
import com.cohortlens.core.Pseudonymizer;
import com.cohortlens.ml.Metrics.CalibrationBin;
import com.cohortlens.ml.Metrics.Confusion;
import com.cohortlens.ml.StudentSplit.Part;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Trains and evaluates a classifier that predicts which students will score below the proficiency cut in
 * math NEXT term, using only what is known at the end of the current term. Writes a Markdown report.
 *
 * <pre>
 * javac --release 17 -d out backend/src/main/java/com/cohortlens/core/*.java backend/src/main/java/com/cohortlens/ml/*.java
 * java -cp out com.cohortlens.ml.EvaluationRunner data/synthetic_education_messy.csv docs/ML_EVALUATION.md
 * </pre>
 *
 * <p>Everything is seeded, so the same input gives the same report.
 */
public final class EvaluationRunner {

    static final long SEED = 20261001L;
    static final double[] LAMBDAS = {0.1, 1, 10, 100, 1000};
    static final int BOOTSTRAP_REPLICATES = 1000;
    static final int MIN_GROUP_STUDENTS = AnalyticsService.DEFAULT_MIN_CELL;

    private EvaluationRunner() {
    }

    private record Fitted(Preprocessor preprocessor, LogisticRegression model, double lambda, double validationLogLoss) {
    }

    private record Scored(String name, double[] validation, double[] test, boolean probabilistic) {
    }

    public static void main(String[] args) throws IOException {
        Path csv = Path.of(args.length > 0 ? args[0] : "data/synthetic_education_messy.csv");
        Path out = Path.of(args.length > 1 ? args[1] : "docs/ML_EVALUATION.md");
        String report = run(Files.readString(csv), csv.getFileName().toString());
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, report);
        System.out.println(report);
    }

    public static String run(String csvText, String sourceName) {
        ImportResult imported = new ImportProcessor(new Pseudonymizer("evaluation-only-key-not-a-real-secret"))
                .process(csvText);
        if (imported.fatal()) {
            throw new IllegalArgumentException(imported.fatalMessage());
        }
        List<Example> all = FeatureBuilder.build(imported.accepted());

        StudentSplit split = new StudentSplit(SEED, 0.6, 0.2);
        List<Example> train = new ArrayList<>();
        List<Example> validation = new ArrayList<>();
        List<Example> test = new ArrayList<>();
        for (Example e : all) {
            Part part = split.assign(e.studentKey());
            (part == Part.TRAIN ? train : part == Part.VALIDATION ? validation : test).add(e);
        }

        List<double[]> trainRaw = raw(train);
        List<double[]> validationRaw = raw(validation);
        List<double[]> testRaw = raw(test);
        boolean[] yTrain = labels(train);
        boolean[] yValidation = labels(validation);
        boolean[] yTest = labels(test);

        Fitted history = fitBest(trainRaw, yTrain, validationRaw, yValidation, FeatureBuilder.BASE_COLUMNS);
        Fitted withDemographics = fitBest(trainRaw, yTrain, validationRaw, yValidation, FeatureBuilder.WITH_DEMOGRAPHICS);

        double trainRate = rate(yTrain);
        double trainMeanMath = meanIgnoringNaN(trainRaw, 0);

        List<Scored> models = new ArrayList<>();
        models.add(new Scored("Logistic regression, history only",
                predictAll(history, validationRaw), predictAll(history, testRaw), true));
        models.add(new Scored("Logistic regression, plus economic and English learner status",
                predictAll(withDemographics, validationRaw), predictAll(withDemographics, testRaw), true));
        models.add(new Scored("Rule based risk score (points)", rule(validation), rule(test), false));
        models.add(new Scored("Current math score only (lower is riskier)",
                mathOnly(validationRaw, trainMeanMath), mathOnly(testRaw, trainMeanMath), false));
        models.add(new Scored("Always predict the training rate", constant(validation.size(), trainRate),
                constant(test.size(), trainRate), true));

        double[][] testScores = new double[models.size()][];
        for (int m = 0; m < models.size(); m++) {
            testScores[m] = models.get(m).test();
        }
        String[] testStudents = test.stream().map(Example::studentKey).toArray(String[]::new);
        int[][] pairs = {{0, 2}, {0, 3}, {1, 0}};
        Bootstrap.Result boot = Bootstrap.run(testStudents, yTest, testScores, pairs, BOOTSTRAP_REPLICATES, SEED + 7);

        StringBuilder sb = new StringBuilder();
        header(sb, sourceName, imported.accepted().size(), all, train, validation, test, history, withDemographics);
        resultsTable(sb, models, yValidation, yTest, boot);
        differences(sb, boot);
        calibration(sb, models.get(0).test(), yTest);
        coefficients(sb, "History only model", history);
        coefficients(sb, "Model with economic and English learner status", withDemographics);
        subgroups(sb, models.get(0), test, yTest, yValidation);
        sanityCheck(sb, history, trainRaw, yTrain, testRaw, yTest);
        limitations(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------ fitting

    private static Fitted fitBest(List<double[]> trainRaw, boolean[] yTrain, List<double[]> validationRaw,
                                  boolean[] yValidation, int[] columns) {
        Preprocessor pre = Preprocessor.fit(trainRaw, columns);
        double[][] xTrain = pre.transformAll(trainRaw);
        double[][] xValidation = pre.transformAll(validationRaw);
        Fitted best = null;
        for (double lambda : LAMBDAS) {
            LogisticRegression model = LogisticRegression.fit(xTrain, yTrain, lambda);
            double ll = Metrics.logLoss(predict(model, xValidation), yValidation);
            if (best == null || ll < best.validationLogLoss()) {
                best = new Fitted(pre, model, lambda, ll);
            }
        }
        return best;
    }

    private static double[] predict(LogisticRegression model, double[][] x) {
        double[] p = new double[x.length];
        for (int i = 0; i < p.length; i++) {
            p[i] = model.predict(x[i]);
        }
        return p;
    }

    private static double[] predictAll(Fitted fitted, List<double[]> raw) {
        return predict(fitted.model(), fitted.preprocessor().transformAll(raw));
    }

    // ------------------------------------------------------------------ small helpers

    private static List<double[]> raw(List<Example> examples) {
        List<double[]> out = new ArrayList<>();
        for (Example e : examples) {
            out.add(e.features());
        }
        return out;
    }

    private static boolean[] labels(List<Example> examples) {
        boolean[] y = new boolean[examples.size()];
        for (int i = 0; i < y.length; i++) {
            y[i] = examples.get(i).label();
        }
        return y;
    }

    private static double[] rule(List<Example> examples) {
        double[] s = new double[examples.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = examples.get(i).ruleScore();
        }
        return s;
    }

    private static double[] mathOnly(List<double[]> raw, double fill) {
        double[] s = new double[raw.size()];
        for (int i = 0; i < s.length; i++) {
            double math = raw.get(i)[0];
            s[i] = 100.0 - (Double.isNaN(math) ? fill : math);
        }
        return s;
    }

    private static double[] constant(int n, double value) {
        double[] s = new double[n];
        java.util.Arrays.fill(s, value);
        return s;
    }

    private static double rate(boolean[] y) {
        int c = 0;
        for (boolean b : y) {
            if (b) {
                c++;
            }
        }
        return y.length == 0 ? Double.NaN : (double) c / y.length;
    }

    private static double meanIgnoringNaN(List<double[]> rows, int column) {
        double sum = 0;
        int n = 0;
        for (double[] r : rows) {
            if (!Double.isNaN(r[column])) {
                sum += r[column];
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private static int distinctStudents(List<Example> examples) {
        Set<String> s = new HashSet<>();
        for (Example e : examples) {
            s.add(e.studentKey());
        }
        return s.size();
    }

    private static String f(double v) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.3f", v);
    }

    private static String pct(double v) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", 100 * v);
    }

    // ------------------------------------------------------------------ report sections

    private static void header(StringBuilder sb, String source, int observations, List<Example> all,
                               List<Example> train, List<Example> validation, List<Example> test,
                               Fitted history, Fitted withDemographics) {
        sb.append("# Machine learning evaluation\n\n");
        sb.append("This report is generated by `EvaluationRunner`. Do not edit it by hand; run the command at the bottom.\n\n");
        sb.append("**The data is synthetic.** It was produced by `scripts/generate_synthetic_data.py`, so the results show that the ")
                .append("method works and is evaluated correctly. They do not show how well it would work on real students.\n\n");
        sb.append("## Question\n\n");
        sb.append("Using what is known about a student at the end of one term, can we predict whether the student's math score ")
                .append("in the **next** term will be below ").append((int) FeatureBuilder.PROFICIENCY_CUT)
                .append("? The result is meant to help a researcher decide where to look first. It is not a basis for decisions ")
                .append("about individual students.\n\n");
        sb.append("## Setup\n\n");
        sb.append("- **Source file:** `").append(source).append("`, cleaned by the import validator first (")
                .append(observations).append(" accepted rows).\n");
        sb.append("- **Examples:** ").append(all.size()).append(" pairs of consecutive terms. Features come from the current term and ")
                .append("the one before it. The label comes only from the next term.\n");
        sb.append("- **Split by student, not by row:** 60 percent train, 20 percent validation, 20 percent test. All rows of a ")
                .append("student stay in one part, so the model is never tested on a student it has seen.\n\n");
        sb.append("| Part | Students | Examples | Share below the cut next term |\n|---|---:|---:|---:|\n");
        splitRow(sb, "Train", train);
        splitRow(sb, "Validation", validation);
        splitRow(sb, "Test", test);
        sb.append("\n");
        sb.append("- **Preprocessing:** missing values are filled with the training mean and flagged with a missing indicator. ")
                .append("Standardization is fitted on the training part only.\n");
        sb.append("- **Model:** L2 regularized logistic regression written from scratch (Newton's method). The penalty was ")
                .append("chosen on the validation part by log loss: lambda = ").append(history.lambda())
                .append(" for the history only model and ").append(withDemographics.lambda()).append(" for the other.\n");
        sb.append("- **Decision threshold:** chosen on the validation part to maximize F1, then applied unchanged to the test part.\n");
        sb.append("- **The test part was used once,** after every choice above was fixed.\n");
        sb.append("- **Uncertainty:** 95 percent intervals come from ").append(BOOTSTRAP_REPLICATES)
                .append(" bootstrap resamples of whole students in the test part.\n\n");
    }

    private static void splitRow(StringBuilder sb, String name, List<Example> part) {
        sb.append("| ").append(name).append(" | ").append(distinctStudents(part)).append(" | ").append(part.size())
                .append(" | ").append(pct(rate(labels(part)))).append(" |\n");
    }

    private static void resultsTable(StringBuilder sb, List<Scored> models, boolean[] yValidation, boolean[] yTest,
                                     Bootstrap.Result boot) {
        sb.append("## Results on the test part\n\n");
        sb.append("AUC is the chance that a student who will fall below the cut gets a higher score than one who will not ")
                .append("(0.5 is guessing, 1.0 is perfect). Precision, recall and F1 use the threshold chosen on the validation part.\n\n");
        sb.append("| Model | AUC (95% interval) | Average precision | Brier | Log loss | Precision | Recall | F1 | Share flagged |\n");
        sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int m = 0; m < models.size(); m++) {
            Scored s = models.get(m);
            double threshold = Metrics.bestF1Threshold(s.validation(), yValidation);
            Confusion c = Metrics.confusionAt(s.test(), yTest, threshold);
            Bootstrap.Interval a = boot.auc()[m];
            sb.append("| ").append(s.name()).append(" | ").append(f(a.estimate())).append(" (").append(f(a.low()))
                    .append(" to ").append(f(a.high())).append(") | ").append(f(Metrics.averagePrecision(s.test(), yTest)))
                    .append(" | ").append(s.probabilistic() ? f(Metrics.brier(s.test(), yTest)) : "n/a")
                    .append(" | ").append(s.probabilistic() ? f(Metrics.logLoss(s.test(), yTest)) : "n/a")
                    .append(" | ").append(f(c.precision())).append(" | ").append(f(c.recall())).append(" | ")
                    .append(f(c.f1())).append(" | ").append(pct(c.flaggedRate())).append(" |\n");
        }
        Scored rule = models.get(2);
        Confusion published = Metrics.confusionAt(rule.test(), yTest, 3.0);
        sb.append("| Rule based risk score at its published MEDIUM cut (3 or more points) | same as above | | n/a | n/a | ")
                .append(f(published.precision())).append(" | ").append(f(published.recall())).append(" | ")
                .append(f(published.f1())).append(" | ").append(pct(published.flaggedRate())).append(" |\n\n");
        sb.append("Brier score and log loss measure how good the predicted probabilities are (lower is better). ")
                .append("They are shown only for models that output probabilities.\n\n");
    }

    private static void differences(StringBuilder sb, Bootstrap.Result boot) {
        sb.append("## Is the difference real?\n\n");
        sb.append("Paired bootstrap on the same resampled students. If the interval for a difference in AUC excludes zero, ")
                .append("the gap is larger than resampling noise.\n\n");
        sb.append("| Comparison | AUC difference (95% interval) |\n|---|---|\n");
        String[] names = {
                "History only model minus rule based risk score",
                "History only model minus current math score only",
                "Model with demographics minus history only model"
        };
        for (int p = 0; p < names.length; p++) {
            Bootstrap.Interval d = boot.difference()[p];
            sb.append("| ").append(names[p]).append(" | ").append(f(d.estimate())).append(" (").append(f(d.low()))
                    .append(" to ").append(f(d.high())).append(") |\n");
        }
        sb.append("\n");
    }

    private static void calibration(StringBuilder sb, double[] probability, boolean[] yTest) {
        List<CalibrationBin> bins = Metrics.calibration(probability, yTest, 10);
        sb.append("## Calibration of the history only model\n\n");
        sb.append("If the model says 30 percent, about 30 percent of those students should fall below the cut. ")
                .append("Expected calibration error: ").append(f(Metrics.expectedCalibrationError(bins))).append(".\n\n");
        sb.append("| Predicted probability | Students | Mean predicted | Observed rate |\n|---|---:|---:|---:|\n");
        for (CalibrationBin b : bins) {
            if (b.count() == 0) {
                continue;
            }
            sb.append("| ").append(String.format(Locale.ROOT, "%.1f to %.1f", b.low(), b.high())).append(" | ")
                    .append(b.count()).append(" | ").append(pct(b.meanPredicted())).append(" | ")
                    .append(pct(b.observedRate())).append(" |\n");
        }
        sb.append("\n");
    }

    private static void coefficients(StringBuilder sb, String title, Fitted fitted) {
        List<String> names = fitted.preprocessor().outputNames(FeatureBuilder.NAMES);
        double[] w = fitted.model().weights();
        sb.append("## Coefficients: ").append(title).append("\n\n");
        sb.append("Features are standardized, so each odds ratio is the change in the odds of falling below the cut for ")
                .append("a one standard deviation increase, holding the others fixed.\n\n");
        sb.append("| Feature | Coefficient | Odds ratio per 1 SD |\n|---|---:|---:|\n");
        for (int j = 0; j < names.size(); j++) {
            sb.append("| ").append(names.get(j)).append(" | ").append(f(w[j + 1])).append(" | ")
                    .append(f(Math.exp(w[j + 1]))).append(" |\n");
        }
        sb.append("\n");
    }

    private static void subgroups(StringBuilder sb, Scored model, List<Example> test, boolean[] yTest,
                                  boolean[] yValidation) {
        double threshold = Metrics.bestF1Threshold(model.validation(), yValidation);
        sb.append("## Subgroup check (history only model, which does not use these attributes)\n\n");
        sb.append("A model can be accurate overall and still behave differently for different groups. Groups with fewer ")
                .append("than ").append(MIN_GROUP_STUDENTS).append(" students are hidden.\n\n");
        sb.append("| Group | Students | Observed rate | Mean predicted | AUC | Recall | Share flagged |\n|---|---:|---:|---:|---:|---:|---:|\n");
        subgroupRow(sb, "Low income", model, test, yTest, threshold, e -> e.lowIncome());
        subgroupRow(sb, "Not low income", model, test, yTest, threshold, e -> !e.lowIncome());
        subgroupRow(sb, "English learner", model, test, yTest, threshold, e -> e.englishLearner());
        subgroupRow(sb, "Not English learner", model, test, yTest, threshold, e -> !e.englishLearner());
        sb.append("\n");
        sb.append("Compare the mean predicted rate with the observed rate inside each group: if they match, the predicted ")
                .append("probabilities are about equally well calibrated for every group. Recall and the share flagged differ ")
                .append("partly because the groups have different base rates, and a single threshold flags more students ")
                .append("in the group where the outcome is more common.\n\n");
    }

    private static void subgroupRow(StringBuilder sb, String name, Scored model, List<Example> test, boolean[] yTest,
                                    double threshold, java.util.function.Predicate<Example> member) {
        List<Integer> rows = new ArrayList<>();
        Set<String> students = new HashSet<>();
        for (int i = 0; i < test.size(); i++) {
            if (member.test(test.get(i))) {
                rows.add(i);
                students.add(test.get(i).studentKey());
            }
        }
        if (students.size() < MIN_GROUP_STUDENTS) {
            sb.append("| ").append(name).append(" | hidden | | | | | |\n");
            return;
        }
        double[] s = new double[rows.size()];
        boolean[] y = new boolean[rows.size()];
        double meanPredicted = 0;
        for (int k = 0; k < s.length; k++) {
            s[k] = model.test()[rows.get(k)];
            y[k] = yTest[rows.get(k)];
            meanPredicted += s[k];
        }
        meanPredicted /= s.length;
        Confusion c = Metrics.confusionAt(s, y, threshold);
        sb.append("| ").append(name).append(" | ").append(students.size()).append(" | ").append(pct(rate(y)))
                .append(" | ").append(pct(meanPredicted)).append(" | ").append(f(Metrics.auc(s, y))).append(" | ")
                .append(f(c.recall())).append(" | ").append(pct(c.flaggedRate())).append(" |\n");
    }

    private static void sanityCheck(StringBuilder sb, Fitted history, List<double[]> trainRaw, boolean[] yTrain,
                                    List<double[]> testRaw, boolean[] yTest) {
        double[][] xTrain = history.preprocessor().transformAll(trainRaw);
        double[][] xTest = history.preprocessor().transformAll(testRaw);
        Random random = new Random(SEED + 1);
        double min = 1;
        double max = 0;
        double sum = 0;
        int runs = 20;
        for (int r = 0; r < runs; r++) {
            List<Boolean> shuffled = new ArrayList<>();
            for (boolean b : yTrain) {
                shuffled.add(b);
            }
            Collections.shuffle(shuffled, random);
            boolean[] y = new boolean[yTrain.length];
            for (int i = 0; i < y.length; i++) {
                y[i] = shuffled.get(i);
            }
            double auc = Metrics.auc(predict(LogisticRegression.fit(xTrain, y, history.lambda()), xTest), yTest);
            min = Math.min(min, auc);
            max = Math.max(max, auc);
            sum += auc;
        }
        sb.append("## Sanity check: shuffled labels\n\n");
        sb.append("The same pipeline was trained on training labels that were shuffled at random, ").append(runs)
                .append(" times, and tested on the real test labels. With nothing to learn, the average AUC should be about 0.5. ")
                .append("Single runs can land well above or below 0.5. AUC ignores the size of the weights, so even tiny random ")
                .append("weights give a direction that can line up with, or against, the real signal, because the test features ")
                .append("are strongly related to the real labels. What matters is that the average is near 0.5 and that no run ")
                .append("comes close to the real model.\n\n");
        sb.append("Test AUC with shuffled training labels: mean ").append(f(sum / runs)).append(", range ").append(f(min))
                .append(" to ").append(f(max)).append(".\n\n");
    }

    private static void limitations(StringBuilder sb) {
        sb.append("## Limitations\n\n");
        sb.append("- **Synthetic data.** Scores in the generator are a student level ability plus noise, so next term math is ")
                .append("largely a continuation of this term. High numbers here reflect that design. Real data is messier and the ")
                .append("same method would need to be re evaluated.\n");
        sb.append("- **Random split by student, not by time.** A forecasting study should also hold out the latest terms to check ")
                .append("for drift. That is not done here.\n");
        sb.append("- **One arbitrary label.** The cut of ").append((int) FeatureBuilder.PROFICIENCY_CUT)
                .append(" is a choice. Other cuts, or predicting the score itself, may answer different questions.\n");
        sb.append("- **A simple model.** Logistic regression is linear. Nonlinear models were not compared.\n");
        sb.append("- **Intervals reflect resampling of test students only.** They do not include the effect of a different random split.\n");
        sb.append("- **Fairness.** Using economic status or English learner status in a risk model can reproduce existing ")
                .append("disparities. The model with these attributes is reported only for comparison.\n");
        sb.append("- **Use.** This is for research exploration. It must not be the only basis for any decision about a student.\n\n");
        sb.append("## Reproduce\n\n```bash\n")
                .append("javac --release 17 -d out backend/src/main/java/com/cohortlens/core/*.java backend/src/main/java/com/cohortlens/ml/*.java\n")
                .append("java -cp out com.cohortlens.ml.EvaluationRunner data/synthetic_education_messy.csv docs/ML_EVALUATION.md\n")
                .append("```\n");
    }
}
