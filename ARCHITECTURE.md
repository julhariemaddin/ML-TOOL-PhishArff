# PhishArff architecture

```
CSV --> DataLoader --> DataChecker --> DatasetBuilder.build --> ArffWriter --> .arff
                       (rates rows,    (which rows to keep:      (dense or    ExportReport --> .info.txt
                        finds bias)     labels, duplicates,       sparse)
                                        conflicts, balance)
                                              |
                                  FeatureExtractor (one URL -> numbers)
```

## features/  (self-contained: java.util only, copied together into your website by "Export extractor")
| Class | One job |
|---|---|
| FeatureExtractor | Public entry point. Puts the 26 values in order, builds full rows. |
| FeatureConfig | The two settings that define a row: Domain only on/off, number of n-gram columns. |
| UrlParts | Cuts a URL into scheme, host, port, path, query. |
| HostInfo | Is the host an IP? How many sub-domains? What is the real domain? |
| HostStats | Text measurements of the host (entropy, digit runs, vowels, free hosting). |
| NgramHasher | Hashed character n-gram counts of the host (sizes 1-6, FNV-1a). |
| Lexicon | The word lists. Data only. |
| EmailFeatureExtractor | Email features; reuses the link features for links inside emails. |

## core/
| Class | One job |
|---|---|
| DatasetBuilder | Decides which rows are kept (labels, duplicates, conflicts, balance, shuffle). Also the stable public entry for the UI. |
| ArffWriter | Writes the file. Dense rows, or sparse rows when n-gram columns are on. |
| ExportReport | Writes the .info.txt. |
| ExtractorExporter | Copies the extractor source files plus a usage note. |
| Environment | Total and free RAM, this tool's Java heap and where it comes from, Weka's heap read from RunWeka.ini. |
| WekaNeeds | Memory Weka needs for an export, the Weka algorithms that fit, and the warning texts. Pure functions, covered by SelfCheck. |
| DataLoader, CsvReader, LabelNormalizer, UrlRater, EmailRater, DataChecker, Keys, Checksum, Rating, Entry, DataKind | Unchanged helpers. |

## Rules that keep it clean
1. features/ never imports core/ or ui/. Dependencies point one way: ui -> core -> features.
2. The exported extractor and the ARFF writer call the same FeatureExtractor, so training and prediction cannot drift apart.
3. Column order is a contract. New features are appended; the first 20 never move.
4. Anything that changes a number (a list in Lexicon, a feature, the n-gram sizes) means: rebuild the ARFF and retrain.

## Checking a change
```
mvn compile
javac -d target/classes -cp target/classes src/test/java/com/julharie/phisharff/core/SelfCheck.java
java -cp target/classes com.julharie.phisharff.core.SelfCheck
```
It prints PASS/FAIL per check and exits with code 1 on any failure.
