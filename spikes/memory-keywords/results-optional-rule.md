# Memory keywords spike

Model: google/gemini-2.5-flash-lite through OpenRouter, temperature 0. Prompt: the app's extraction prompt (global rule on), one fact per call, no earlier facts.

## Numbers

- Facts sent: 20; answered: 20; answers that were not readable JSON: 0; answers with no add operation: 0.
- Bangla facts with a usable English keyword (a Latin word of 3+ letters): 6 of 10.
- English facts with a usable Bangla keyword (a Bangla-script word): 0 of 5.
- Mixed facts with an English keyword: 3 of 5; with a Bangla keyword: 1 of 5.
- Total cost: $0.001164 (cap $0.05).

The check is a script heuristic (script of the keywords), not a judgement of meaning; read the table.

## Keywords produced

| Kind | Fact | Keywords | Scope |
| --- | --- | --- | --- |
| bangla | আমার থিসিস জমা দেওয়ার শেষ তারিখ ১২ ডিসেম্বর। | (none) | None |
| bangla | আমি সিলেটে থাকি। | (none) | None |
| bangla | আমার সুপারভাইজার ড. রহমান, তিনি এপিএ স্টাইল পছন্দ করেন। | supervisor Dr. Rahman APA style preference | None, None |
| bangla | আমি দুধ ছাড়া লাল চা খাই। | lal cha, dudh chara | None, global |
| bangla | আমার ল্যাপটপে আর্চ লিনাক্স চলে। | Arch Linux, laptop, laptop Arch Linux | None |
| bangla | প্রতি শুক্রবার সকালে আমি বাজারে যাই। | shokale, bajare, protibar shukrobar | None |
| bangla | আমার মেয়ের জন্মদিন ৫ মার্চ। | (none) | None |
| bangla | আমি ঢাকা বিশ্ববিদ্যালয়ে পদার্থবিজ্ঞান পড়েছি। | Dhaka Bishwabidyalay, podarthobiggan | None |
| bangla | আমার বাজেট মাসে বিশ হাজার টাকা। | (none) | None |
| bangla | আমি উত্তর দিতে বাংলা ভাষা পছন্দ করি। | Bangla bhasha pochondo kori Bangla bhasha pochondo kori | None, global |
| english | My thesis is due on 12 December. | (none) | None |
| english | I live in Sylhet and work as a civil engineer. | (none) | global, None |
| english | I prefer answers in short bullet points. | short bullet points, choto bullet point | None |
| english | My daughter's birthday is on 5 March. | (none) | None |
| english | I use Kotlin and Jetpack Compose for my Android apps. | kotlin, jetpack compose, android apps | None |
| mixed | আমার thesis supervisor হলেন Dr. Rahman। | (none) | None |
| mixed | আমি প্রতিদিন সকালে gym এ যাই। | gym, roj sokal, daily morning | None |
| mixed | Amar exam 3 January theke shuru. | exam, January 3 | None |
| mixed | আমার laptop এর battery দুই ঘণ্টা চলে। | (none) | None |
| mixed | My বন্ধু Karim থাকে Chattogram এ। | Karim, Chattogram, বন্ধু | None |
