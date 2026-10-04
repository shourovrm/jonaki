# Memory keywords spike

Model: google/gemini-2.5-flash-lite through OpenRouter, temperature 0. Prompt: the app's extraction prompt (global rule on), one fact per call, no earlier facts.

## Numbers

- Facts sent: 20; answered: 20; answers that were not readable JSON: 0; answers with no add operation: 0.
- Bangla facts with a usable English keyword (a Latin word of 3+ letters): 0 of 10.
- English facts with a usable Bangla keyword (a Bangla-script word): 4 of 5.
- Mixed facts with an English keyword: 2 of 5; with a Bangla keyword: 5 of 5.
- Total cost: $0.001278 (cap $0.05).

The check is a script heuristic (script of the keywords), not a judgement of meaning; read the table.

## Keywords produced

| Kind | Fact | Keywords | Scope |
| --- | --- | --- | --- |
| bangla | আমার থিসিস জমা দেওয়ার শেষ তারিখ ১২ ডিসেম্বর। | থিসিস, জমা, শেষ তারিখ, ডিসেম্বর | None |
| bangla | আমি সিলেটে থাকি। | সিলেট, থাকি | global |
| bangla | আমার সুপারভাইজার ড. রহমান, তিনি এপিএ স্টাইল পছন্দ করেন। | সুপারভাইজার, ড. রহমান এপিএ স্টাইল, পছন্দ | None, None |
| bangla | আমি দুধ ছাড়া লাল চা খাই। | লাল চা, দুধ ছাড়া লাল চা, দুধ ছাড়া | None, global |
| bangla | আমার ল্যাপটপে আর্চ লিনাক্স চলে। | ল্যাপটপ, আর্চ লিনাক্স | None |
| bangla | প্রতি শুক্রবার সকালে আমি বাজারে যাই। | বাজার, প্রতি শুক্রবার, সকাল | None |
| bangla | আমার মেয়ের জন্মদিন ৫ মার্চ। | মেয়ে, জন্মদিন, ৫ মার্চ | None |
| bangla | আমি ঢাকা বিশ্ববিদ্যালয়ে পদার্থবিজ্ঞান পড়েছি। | ঢাকা বিশ্ববিদ্যালয়, পদার্থবিজ্ঞান | global |
| bangla | আমার বাজেট মাসে বিশ হাজার টাকা। | বাজেট, বিশ হাজার, টাকা, মাস | global |
| bangla | আমি উত্তর দিতে বাংলা ভাষা পছন্দ করি। | বাংলা, ভাষা, পছন্দ | global |
| english | My thesis is due on 12 December. | থিসিস, জমা দেওয়ার তারিখ | None |
| english | I live in Sylhet and work as a civil engineer. | সিলেট সিভিল ইঞ্জিনিয়ার নাম | None, global, global |
| english | I prefer answers in short bullet points. | short bullet points, পছন্দ | global |
| english | My daughter's birthday is on 5 March. | daughter, birthday, 5 March | None |
| english | I use Kotlin and Jetpack Compose for my Android apps. | kotlin, jetpack compose, android, apps, ব্যবহার, কোটলিন, জেপ্যাক কম্পোজ | None |
| mixed | আমার thesis supervisor হলেন Dr. Rahman। | thesis supervisor ডঃ রহমান | global |
| mixed | আমি প্রতিদিন সকালে gym এ যাই। | প্রতিদিন সকালে | global |
| mixed | Amar exam 3 January theke shuru. | পরীক্ষা, শুরু, ৩ জানুয়ারি | None |
| mixed | আমার laptop এর battery দুই ঘণ্টা চলে। | laptop, battery, দুই ঘণ্টা | None |
| mixed | My বন্ধু Karim থাকে Chattogram এ। | বন্ধু করিম চট্টগ্রাম | global |
