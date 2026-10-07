# Словари репетитора

`{en,es,fr}_N.txt` — базовые темы, составленные вручную. Формат строки: `слово|перевод|пример|перевод примера`, строка `#тема` начинает тему.

`build.py OUT_DIR` собирает `content-tutor-{en,es,fr}.js`. Скрипту нужна папка `../dict/` рядом с ним:
- OpenRussian `nouns.csv`, `verbs.csv`, `adjectives.csv`, `others.csv` (CC-BY-SA) — переводы;
- hermitdave/FrequencyWords `en_50k.txt`, `es_50k.txt`, `fr_50k.txt` (MIT) — частотность;
- `cmudict.dict` (BSD) — транскрипция;
- FreeDict `fra-eng.tei` (GPL-2.0+) — французский: значения, транскрипция, род;
- doozan/spanish_data `es-en.data`, `es_frequency.csv`, `sentences.tsv` (CC-BY-SA / Tatoeba CC-BY 2.0 FR) — испанский и примеры.
