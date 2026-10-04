# Memory keywords spike

Model: google/gemini-2.5-flash-lite through OpenRouter, temperature 0. Prompt: the app's extraction prompt (global rule on), one fact per call, no earlier facts.

## Numbers

- Facts sent: 20; answered: 20; answers that were not readable JSON: 0; answers with no add operation: 0.
- Bangla facts with a usable English keyword (a Latin word of 3+ letters): 3 of 10.
- English facts with a usable Bangla keyword (a Bangla-script word): 5 of 5.
- Mixed facts with an English keyword: 2 of 5; with a Bangla keyword: 3 of 5.
- Total cost: $0.001320 (cap $0.05).

The check is a script heuristic (script of the keywords), not a judgement of meaning; read the table.

## Keywords produced

| Kind | Fact | Keywords | Scope |
| --- | --- | --- | --- |
| bangla | আমার থিসিস জমা দেওয়ার শেষ তারিখ ১২ ডিসেম্বর। | থিসিস জমা শেষ তারিখ ডিসেম্বর | None |
| bangla | আমি সিলেটে থাকি। | সিলেট বাস করা | global |
| bangla | আমার সুপারভাইজার ড. রহমান, তিনি এপিএ স্টাইল পছন্দ করেন। | supervisor Dr. Rahman APA style preference | None, None |
| bangla | আমি দুধ ছাড়া লাল চা খাই। | দুধ ছাড়া লাল চা পান করা | global |
| bangla | আমার ল্যাপটপে আর্চ লিনাক্স চলে। | Arch Linux laptop | global |
| bangla | প্রতি শুক্রবার সকালে আমি বাজারে যাই। | বাজার কেনাকাটা শুক্রবার সকাল | None |
| bangla | আমার মেয়ের জন্মদিন ৫ মার্চ। | মেয়ে জন্মদিন মার্চ | global |
| bangla | আমি ঢাকা বিশ্ববিদ্যালয়ে পদার্থবিজ্ঞান পড়েছি। | ঢাকা বিশ্ববিদ্যালয় পদার্থবিজ্ঞান পড়াশোনা | global |
| bangla | আমার বাজেট মাসে বিশ হাজার টাকা। | budget monthly twenty thousand taka | global |
| bangla | আমি উত্তর দিতে বাংলা ভাষা পছন্দ করি। | বাংলা ভাষা উত্তর | global |
| english | My thesis is due on 12 December. | থিসিস জমা দেওয়ার তারিখ ডিসেম্বর | None |
| english | I live in Sylhet and work as a civil engineer. | সিলেট বাস সিভিল ইঞ্জিনিয়ার চাকরি | global, None |
| english | I prefer answers in short bullet points. | সংক্ষিপ্ত বুলেট পয়েন্ট উত্তর | global |
| english | My daughter's birthday is on 5 March. | মেয়ে জন্মদিন মার্চ | None |
| english | I use Kotlin and Jetpack Compose for my Android apps. | কোটলিন জেটপ্যাক কম্পোজ অ্যান্ড্রয়েড অ্যাপ | None |
| mixed | আমার thesis supervisor হলেন Dr. Rahman। | থিসিস সুপারভাইজার ডঃ রহমান | global |
| mixed | আমি প্রতিদিন সকালে gym এ যাই। | daily morning exercise | global |
| mixed | Amar exam 3 January theke shuru. | পরীক্ষা শুরু জানুয়ারি | global |
| mixed | আমার laptop এর battery দুই ঘণ্টা চলে। | laptop battery life hours | global |
| mixed | My বন্ধু Karim থাকে Chattogram এ। | বন্ধু করিম চট্টগ্রাম | global |

## Three wordings of the keyword rule, same 20 facts

This file is the third run, with the wording now in `MemoryExtraction.kt` (examples for both directions). The earlier runs are kept beside it.

| Run | Wording | Bangla fact, English keyword | English fact, Bangla keyword | Mixed, English / Bangla | Cost |
| --- | --- | --- | --- | --- | --- |
| `results-optional-rule.md` | optional, "leave it out when nothing useful" | 6 of 10 | 0 of 5 | 3 / 1 of 5 | $0.001164 |
| `results-bangla-script-rule.md` | always, "Bangla words in Bangla script" | 0 of 10 | 4 of 5 | 2 / 5 of 5 | $0.001278 |
| `results.md` | always, with two examples | 3 of 10 | 5 of 5 | 2 / 3 of 5 | $0.001320 |

Total spent on the three runs: $0.003762. Reading of the table: with the rule mandatory and an example shown, this model gives Bangla keywords for English facts reliably (5 of 5), but for a Bangla fact it mostly gives Bangla words again (7 of 10 had no English word), which adds nothing to a search. In the last run 14 of the 20 facts carried a `"scope":"global"` mark, including a budget and a laptop model; the app holds every such fact for the user's review.
