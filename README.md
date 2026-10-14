<p align="center"><img src="src/main/resources/logo.png" alt="PhishArff" width="260"></p>

# PhishArff

Turn **link** and **email** CSV files into **Weka-ready ARFF** files, with data checks, checksums, and an exportable Java feature extractor.

Author: **Julharie M. Maddin** ([GitHub](https://github.com/julhariemaddin), [ORCID 0009-0009-3366-1271](https://orcid.org/0009-0009-3366-1271))

## Why

A Weka model is only as good as its data. Many public phishing datasets are biased (for example every legitimate link is a bare `https://www.site.com`), so a model can reach 99% accuracy by learning that shortcut and then fail on real websites. PhishArff catches this before training and makes the result reproducible.

## What it does

- **Import** a CSV. Required column names are checked and a SHA-256 checksum is shown.
- **Check** the data. Every row is rated 0-100 (is it a URL? is it a valid email?), labels are normalised, duplicates counted, and link data is tested for class bias. You confirm before anything is exported.
- **Memory check (links).** Before you export, the Export box shows how much RAM and Java heap Weka needs for the chosen n-gram size, the Weka algorithms that fit it, and turns yellow with a warning when this computer's RAM, Weka's Java heap (`maxheap` in `RunWeka.ini`, or the value you type) or this tool's own heap is too small. With n-grams on, only `functions.SGD` (`-F 1`), `functions.SMO` and `bayes.NaiveBayesMultinomial` are listed: other algorithms and filters expand every row to all columns (for 286,370 rows and 4096 columns that is about 9 GB, and for 65536 columns about 150 GB), which is what ends in Weka's OutOfMemory dialog.
- **Export** the `.arff` file (plus an `.info.txt` report with checksums, counts and the feature list), and the **Java feature extractor** used, so your website computes the same numbers.

## Run it

Double-click **`run.bat`** (Windows). The window is a plain, boxed tool in the spirit of the Weka Explorer; it uses up to 60% of the RAM for its own Java heap. It needs Java 17+ and an internet connection on the first run (it downloads Maven and JavaFX once). If Maven is not installed, `run.bat` fetches a private copy into `.tools/`.

Or with Maven: `mvn compile javafx:run`

## CSV format

| Type  | Required columns | Optional columns (add features when present) |
|-------|------------------|-----------------------------------------------|
| Link  | `url`, `label`   | none                                          |
| Email | `body`, `label`  | `subject`, `sender`                           |

Names are case-insensitive and have aliases (for example `url` = `link`, `website`; `sender` = `from`). Delimiter (comma, semicolon, tab) is detected. Labels: `benign / legitimate / good / safe / ham / 0` become `legitimate`; `phishing / malicious / bad / spam / scam / 1` become `phishing`. Rows with other labels are dropped and counted.

Try the files in `examples/`.

## Features

- **Link (26):** length counts, dots, hyphens, digits, special characters, `@`, IP address, https, port, subdomains, suspicious words and TLDs, shorteners, brand mismatch, plus six host features (added in v1.1): `host_entropy`, `max_digit_run`, `vowel_ratio`, `free_hosting` (duckdns, 000webhostapp, workers.dev, ...), `longest_label`, `tld_length`. Two modes: *Full URL* or *Domain only* (ignores scheme, www, path, query; use when the data is biased).
- **Link, optional character n-grams (v1.2):** 1024, 4096, 16384 or 65536 extra columns `ng_0 ... ng_N`. Each column counts the 1-6 letter pieces of the host name that hash into it (fixed FNV-1a hash, no training, no labels, host only so Domain only mode stays unbiased). The ARFF is then written in Weka's **sparse** format. Train it with `functions.SGD` (log loss, `-F 1`), `functions.SMO` or `bayes.NaiveBayesMultinomial`; `trees.RandomForest` works but is slow on thousands of sparse columns. The website must use the same number of columns: `FeatureExtractor.extractAll(url, new FeatureConfig(domainMode, buckets))`.
- **Email:** 14 body features (links, suspicious links, urgency / money / credential words, HTML, capitals, ...), 5 subject features, 6 sender features. Subject and sender groups are used only if the CSV has those columns.

Exact definitions are in the source: `features/FeatureExtractor.java` and `features/EmailFeatureExtractor.java`. If you change a feature, rebuild the ARFF file and retrain.

## Data and storage

PhishArff keeps a small SQLite database at `~/.phisharff/phisharff.db` (export history, last folder, package name). If SQLite cannot start, the app still works without history.

## Limitations

- Features come from the text of the URL or email only: no DNS, WHOIS, page content, or attachments.
- The row rating checks that data is well-formed. It does not say whether a link or email is phishing.
- Bias detection looks at scheme, `www` and path for links only.
- The `free_hosting` list is plain data inside `FeatureExtractor.java`. It was written with phishing-abuse knowledge and checked against one dataset; edit it for yours, and treat scores on that same dataset as optimistic.
- Character n-grams can learn the source of your data (for example "looks like a popular site") as well as phishing itself. Test on links from a different source before trusting a score.
- ARFF files and models built with v1.0 (20 columns) or v1.1 (2-4 letter n-grams) are not compatible with v1.2 or v1.3: rebuild the ARFF and retrain.
- Code layout and the self-check program: see ARCHITECTURE.md.
- The memory numbers are estimates (data size x 2 for sparse files). They are meant to catch a clearly too small heap, not to promise that a run will fit.
- Large CSVs are loaded into memory (email bodies are cut at 20,000 characters).

## Citing

See `CITATION.cff`.

## License

MIT, see `LICENSE`. Weka is developed by the University of Waikato; PhishArff is independent and not affiliated with the Weka project.
