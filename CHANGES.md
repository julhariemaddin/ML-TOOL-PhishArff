# PhishArff 1.3.0 - changes

## Interface
* Redrawn as a plain, boxed tool in the spirit of the Weka Explorer: grey surfaces, square corners, 1px borders, titled boxes,
  flat tabs, a status bar. No shadows, gradients, rounded cards or animation.
* The application name appears only in the header. The page content and the About page no longer repeat it, and the large
  intro text and logo were removed.
* Import is a single row: Open file... and the file name. Drag and drop still works.

## Weka memory check (Link tab, Export box)
* Choosing character n-grams now shows what Weka needs: RAM and Java heap for a sparse-safe algorithm, and what any other
  algorithm would expand the file to.
* The Weka algorithms that fit the choice are shown beside the n-gram list: functions.SGD (-F 1), functions.SMO and
  bayes.NaiveBayesMultinomial when n-grams are on.
* The n-gram list, the algorithm box and a message turn yellow when: this computer's total or free RAM is below the
  requirement; Weka's Java heap (read from RunWeka.ini, or typed in) is below it, unknown, or almost as large as the RAM;
  this tool's own Java heap is too small for the CSV.
* The default n-gram choice is now Off. The word "recommended" was removed from the 16384 option, because it came from
  scikit-learn tests, not from Weka.
* run.bat now gives the app up to 60% of the RAM (-XX:MaxRAMPercentage=60); the Java default is 25%.

## Code
* New core/Environment.java and core/WekaNeeds.java; 19 new SelfCheck checks (44 in total, all passing).
* DatasetBuilder.VERSION is 1.3.0. ARFF files from 1.2 are unchanged in format.

## What was and was not tested
Compiled with -Xlint:all (no warnings) on JDK 21 with OpenJFX; SelfCheck passes; the window was started headless and the
Link tab driven with a 60,000-row CSV (n-gram list, algorithm box and the Weka-heap warning rendered as described).
NOT tested: Weka itself. The algorithm list and the memory estimates come from how Weka's sparse and dense instances
work and from the README's earlier tests; open one exported file in Weka before relying on them. The free-RAM and
total-RAM warnings are covered by SelfCheck but were not triggered on screen.

# PhishArff 1.2.0 - changes (includes 1.1)

## Accuracy
Measured on a 822,010-row link CSV, Domain only mode (286,370 balanced rows), 80/20 hold-out in which no parent domain
(e.g. duckdns.org) appears on both sides. Model: logistic regression trained with SGD on the exported ARFF.
Your original 20-feature Random Forest scored 71.7% (10-fold cross-validation in Weka).

| Export | Accuracy | Phishing recall | AUC |
|---|---|---|---|
| 26 features, Random Forest / boosting | about 75-77% | about 69% | - |
| 26 + 4096 n-gram columns | 81.4% | 76.7% | 0.892 |
| 26 + 16384 n-gram columns (recommended) | 83.2% | 78.7% | 0.909 |
| 26 + 65536 n-gram columns | 84.4% | 80.1% | 0.919 |

Tried and rejected (no gain): word tokens, "shape" n-grams, TLD tokens, and stacking a tree model on top of the n-gram model.
These numbers came from scikit-learn on the real ARFF files, not from Weka itself, so expect similar but not identical results.

## Features
* B: six extra host features, appended after the original 20: host_entropy, max_digit_run, vowel_ratio, free_hosting,
  longest_label, tld_length.
* C: optional hashed character n-gram columns (sizes 1-6, host only, sparse ARFF): 1024 / 4096 / 16384 / 65536 columns.

## Architecture (see ARCHITECTURE.md)
* features/: FeatureExtractor is now a thin public facade over UrlParts, HostInfo, HostStats, NgramHasher, Lexicon and
  FeatureConfig. The exporter copies all of them together.
* core/: DatasetBuilder only decides which rows to keep; ArffWriter writes the file; ExportReport writes the .info.txt.
  Every public method the UI used before is unchanged.
* src/test/java/.../SelfCheck.java: 25 plain-Java checks, no libraries needed.
* Refactor proof: the old and new extractors gave identical output on 822,001 real URLs (26 features in both modes,
  host, domain form and n-gram counts).

## Compatibility
ARFF files and Weka models from 1.0 (20 columns) or 1.1 (2-4 letter n-grams) do not match 1.2. Rebuild and retrain.

## What was and was not tested
Compiled with -Xlint:all without warnings and run headless on the full CSV: core/ and features/.
ConvertPane.java uses JavaFX, which could not be downloaded in the test environment: it was parse-checked (no syntax
errors) but NOT compiled or run. Run `mvn compile` once and try the Export card before relying on it.
