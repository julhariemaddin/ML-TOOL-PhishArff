<p align="center"><img src="src/main/resources/logo.png" alt="PhishArff" width="260"></p>

# PhishArff

Turn **link** and **email** CSV files into **Weka-ready ARFF** files, with data checks, checksums, and an exportable Java feature extractor.

Author: **Julharie M. Maddin** ([GitHub](https://github.com/julhariemaddin), [ORCID 0009-0009-3366-1271](https://orcid.org/0009-0009-3366-1271))

## Why

A Weka model is only as good as its data. Many public phishing datasets are biased (for example every legitimate link is a bare `https://www.site.com`), so a model can reach 99% accuracy by learning that shortcut and then fail on real websites. PhishArff catches this before training and makes the result reproducible.

## What it does

- **Import** a CSV. Required column names are checked and a SHA-256 checksum is shown.
- **Check** the data. Every row is rated 0-100 (is it a URL? is it a valid email?), labels are normalised, duplicates counted, and link data is tested for class bias. You confirm before anything is exported.
- **Export** the `.arff` file (plus an `.info.txt` report with checksums, counts and the feature list), and the **Java feature extractor** used, so your website computes the same numbers.

## Run it

Double-click **`run.bat`** (Windows). It needs Java 17+ and an internet connection on the first run (it downloads Maven and JavaFX once). If Maven is not installed, `run.bat` fetches a private copy into `.tools/`.

Or with Maven: `mvn compile javafx:run`

## CSV format

| Type  | Required columns | Optional columns (add features when present) |
|-------|------------------|-----------------------------------------------|
| Link  | `url`, `label`   | none                                          |
| Email | `body`, `label`  | `subject`, `sender`                           |

Names are case-insensitive and have aliases (for example `url` = `link`, `website`; `sender` = `from`). Delimiter (comma, semicolon, tab) is detected. Labels: `benign / legitimate / good / safe / ham / 0` become `legitimate`; `phishing / malicious / bad / spam / scam / 1` become `phishing`. Rows with other labels are dropped and counted.

Try the files in `examples/`.

## Features

- **Link (20):** length counts, dots, hyphens, digits, special characters, `@`, IP address, https, port, subdomains, suspicious words and TLDs, shorteners, brand mismatch. Two modes: *Full URL* or *Domain only* (ignores scheme, www, path, query; use when the data is biased).
- **Email:** 14 body features (links, suspicious links, urgency / money / credential words, HTML, capitals, ...), 5 subject features, 6 sender features. Subject and sender groups are used only if the CSV has those columns.

Exact definitions are in the source: `features/FeatureExtractor.java` and `features/EmailFeatureExtractor.java`. If you change a feature, rebuild the ARFF file and retrain.

## Data and storage

PhishArff keeps a small SQLite database at `~/.phisharff/phisharff.db` (export history, last folder, package name). If SQLite cannot start, the app still works without history.

## Limitations

- Features come from the text of the URL or email only: no DNS, WHOIS, page content, or attachments.
- The row rating checks that data is well-formed. It does not say whether a link or email is phishing.
- Bias detection looks at scheme, `www` and path for links only.
- Large CSVs are loaded into memory (email bodies are cut at 20,000 characters).

## Citing

See `CITATION.cff`.

## License

MIT, see `LICENSE`. Weka is developed by the University of Waikato; PhishArff is independent and not affiliated with the Weka project.
