# Memory keywords spike

Model: google/gemini-2.5-flash-lite through OpenRouter, temperature 0. Prompt: the app's extraction prompt (global rule on), one fact per call, no earlier facts.

## Numbers

- Facts sent: 20; answered: 20; answers that were not readable JSON: 0; answers with no add operation: 0.
- Bangla facts with a usable English keyword (a Latin word of 3+ letters): 10 of 10.
- English facts with a usable Bangla keyword (a Bangla-script word): 5 of 5.
- Mixed facts with an English keyword: 5 of 5; with a Bangla keyword: 5 of 5.
- Total cost: $0.001286 (cap $0.05).

The check is a script heuristic (script of the keywords), not a judgement of meaning; read the table.

## Keywords produced

| Kind | Fact | Keywords | Scope |
| --- | --- | --- | --- |
| bangla | আমার থিসিস জমা দেওয়ার শেষ তারিখ ১২ ডিসেম্বর। | thesis deadline December থিসিস জমা ডিসেম্বর | None |
| bangla | আমি সিলেটে থাকি। | live Sylhet থাকি সিলেট | global |
| bangla | আমার সুপারভাইজার ড. রহমান, তিনি এপিএ স্টাইল পছন্দ করেন। | supervisor Dr. Rahman সুপারভাইজার ড. রহমান APA style preference এপিএ স্টাইল পছন্দ | None, None |
| bangla | আমি দুধ ছাড়া লাল চা খাই। | red tea no milk লাল চা দুধ ছাড়া | None |
| bangla | আমার ল্যাপটপে আর্চ লিনাক্স চলে। | laptop Arch Linux ল্যাপটপ আর্চ লিনাক্স | None |
| bangla | প্রতি শুক্রবার সকালে আমি বাজারে যাই। | market Friday morning বাজার শুক্রবার সকাল | None |
| bangla | আমার মেয়ের জন্মদিন ৫ মার্চ। | daughter birthday March মেয়ে জন্মদিন মার্চ | None |
| bangla | আমি ঢাকা বিশ্ববিদ্যালয়ে পদার্থবিজ্ঞান পড়েছি। | Physics University Dhaka পদার্থবিজ্ঞান বিশ্ববিদ্যালয় ঢাকা | None |
| bangla | আমার বাজেট মাসে বিশ হাজার টাকা। | budget monthly twenty thousand taka বাজেট মাসিক বিশ হাজার টাকা | None |
| bangla | আমি উত্তর দিতে বাংলা ভাষা পছন্দ করি। | prefer Bengali language পছন্দ বাংলা ভাষা | global |
| english | My thesis is due on 12 December. | thesis deadline December থিসিস শেষ তারিখ ডিসেম্বর | None |
| english | I live in Sylhet and work as a civil engineer. | live Sylhet বাস করা সিলেট civil engineer চাকরি প্রকৌশলী | global, global |
| english | I prefer answers in short bullet points. | short bullet points সংক্ষিপ্ত বুলেট পয়েন্ট | global |
| english | My daughter's birthday is on 5 March. | daughter birthday March মেয়ে জন্মদিন মার্চ | None |
| english | I use Kotlin and Jetpack Compose for my Android apps. | Kotlin Jetpack Compose Android Kotlin জেটপ্যাক কম্পোজ অ্যান্ড্রয়েড | None |
| mixed | আমার thesis supervisor হলেন Dr. Rahman। | thesis supervisor Dr. Rahman থিসিস সুপারভাইজার ড. রহমান | None |
| mixed | আমি প্রতিদিন সকালে gym এ যাই। | gym morning প্রতিদিন সকালে জিম | None |
| mixed | Amar exam 3 January theke shuru. | exam start January পরীক্ষা শুরু জানুয়ারি | None |
| mixed | আমার laptop এর battery দুই ঘণ্টা চলে। | laptop battery life hours ল্যাপটপ ব্যাটারি লাইফ ঘন্টা | None |
| mixed | My বন্ধু Karim থাকে Chattogram এ। | friend Karim Chattogram বন্ধু করিম চট্টগ্রাম | None |

## Four wordings of the keyword rule, same 20 facts

This file is the fourth run, with the wording now in `MemoryExtraction.kt`: keywords always in both scripts, whatever language the fact is in. The earlier runs are kept beside it.

| Run | Wording | Bangla fact, English keyword | English fact, Bangla keyword | Mixed, English / Bangla | Cost |
| --- | --- | --- | --- | --- | --- |
| `results-optional-rule.md` | optional, "leave it out when nothing useful" | 6 of 10 | 0 of 5 | 3 / 1 of 5 | $0.001164 |
| `results-bangla-script-rule.md` | always, "Bangla words in Bangla script" | 0 of 10 | 4 of 5 | 2 / 5 of 5 | $0.001278 |
| `results-other-script-rule.md` | always, "the other script", two examples | 3 of 10 | 5 of 5 | 2 / 3 of 5 | $0.001320 |
| `results.md` | always both scripts, two examples | 10 of 10 | 5 of 5 | 5 / 5 of 5 | $0.001286 |

Total spent on the four runs: $0.005048. Asking for the other script made the model decide which script a fact is in, and it often got that wrong for Bangla; asking for both scripts every time removes that decision. The fourth run also narrowed the rule for facts that reach every thread: 4 of the 20 facts were marked global (where the user lives, their work, their language and how they want answers), against 14 of 20 in the third run.

