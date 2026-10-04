# Message index spike

Test: `core/storage/src/test/kotlin/app/jonaki/core/storage/MessageIndexSizeSpikeTest.kt`.
Run it with `MESSAGE_INDEX_SPIKE=1 gradle :core:storage:testReleaseUnitTest --tests '*MessageIndexSizeSpikeTest*'`;
without the variable it is skipped. Bundled SQLite 3.46 (JVM build), file database, `VACUUM` before measuring.

## Data

20,000 messages (Random seed 42): 40 percent Bangla, 40 percent English, 20 percent mixed, built from
73 hand-written sentences; user messages have 1 to 3 sentences, assistant messages 3 to 11. Message ids are
36-character UUIDs (TEXT primary key, as in the app). Text is 11,391,307 bytes (mean 569 bytes; Bangla is
3 bytes per character in UTF-8). The table without any index is 15,642,624 bytes.

## Sizes (index bytes = file size minus 15,642,624)

| # | Design | File bytes | Index bytes | Index / text |
|---|--------|-----------:|------------:|-------------:|
| 1 | external content on `messages.rowid`, trigram (unsafe key) | 35,287,040 | 19,644,416 | 1.72 |
| 2 | same, `detail=none` | 20,697,088 | 5,054,464 | 0.44 |
| 3 | same, `detail=column` | 30,150,656 | 14,508,032 | 1.27 |
| 4 | own text copy, `messageId` and `threadId` UNINDEXED | 49,700,864 | 34,058,240 | 2.99 |
| 5 | same as 4, `detail=column` | 44,568,576 | 28,925,952 | 2.54 |
| 6 | same as 4, `detail=none` | 35,110,912 | 19,468,288 | 1.71 |
| 7 | contentless (`contentless_delete=1`) plus id map table | 37,400,576 | 21,757,952 | 1.91 |
| 8 | external content over a view of a stable id map | 37,089,280 | 21,446,656 | 1.88 |

## Query checks

Ground truth by `LIKE '%word%'`: the OR query `ট্রেন ticket বৃষ্টি deadline` matches 5,442 messages; the
Bangla word `থিসিসের` matches 1,063.

| # | OR query count | Bangla count | `bm25` ordering | `snippet()` |
|---|---------------:|-------------:|-----------------|-------------|
| 1, 4, 8 | 5,442 | 1,063 | works (-6.88, -6.77, -6.71 ...) | works (`...ার [থিসিসের] সুপ...`) |
| 7 | 5,442 | 1,063 | works | returns NULL (no stored text) |
| 2, 3, 5, 6 | error | error | error | error |

Variants 2, 3, 5 and 6 throw an `SQLException` on every quoted trigram query. The message text is empty in the
Kotlin test; the same statements run in Python's SQLite 3.53 give `fts5: phrase queries are not supported
(detail!=full)`. A trigram query for a word is a phrase of trigrams, so a smaller `detail` cannot serve
`FtsQuery.anyWordOf`, which quotes every word. Without the quotes the words would be read as FTS5 syntax.
The size savings of 2 and 3 are therefore not usable.

## Choice

Design 7 (contentless table plus an id map). Of the designs whose queries all work, 1 is the smallest (1.72)
but is keyed on the implicit rowid of `messages`, which SQLite may renumber on `VACUUM` because the primary
key is TEXT; after that the index would point at the wrong messages. Of the designs keyed on something
stable, 8 (1.88) and 7 (1.91) are about 12 MB smaller than 4 (2.99) at 20,000 messages. The stable key is
`idx_map.indexRowId`, an INTEGER PRIMARY KEY that `VACUUM` keeps, mapped to the message id.

7 was chosen over 8 for two reasons, although 8 is 0.03 smaller and keeps `snippet()`:

- Design 8 needs a view that names `messages`. Room rebuilds a table by creating a new one, dropping the old
  one and renaming; checked in Python's SQLite 3.53, with a view that names `messages` that rename fails with
  `error in view v: no such table: main.messages` (it works with `legacy_alter_table=1`). A future migration
  of `messages` would then fail. Design 7 has only triggers, which go away with the dropped table.
- `snippet()` takes at most 64 tokens, and a trigram token is one character, so it cannot give the 300
  characters the tool shows. The tool cuts its excerpt from the message text in Kotlin either way.

Because a table rebuild drops the triggers of design 7 too, `MessageSearchIndex.ensure` rebuilds the index
on any open where the index table or the insert trigger is missing.
