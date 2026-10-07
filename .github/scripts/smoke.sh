#!/usr/bin/env bash
# Устанавливает APK на эмулятор, заполняет примером, открывает экраны и снимает скриншоты.
set -x
PKG=com.dasein.poryadok
mkdir -p shots
# Проверка обновления: сначала ставим предыдущий выпуск с примером данных, потом новую сборку поверх.
curl -sSL -o old.apk https://github.com/dasein00/outfit-creator/releases/download/android-v1.0.20/DASEIN.apk || true
if [ -s old.apk ]; then
  adb install old.apk
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
  adb shell am start -W -n $PKG/.MainActivity --ez demo true
  sleep 20
  adb exec-out screencap -p > shots/00_old_version.png
  adb shell am force-stop $PKG
fi
adb logcat -c
adb install -r apk/DASEIN.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
# Погода: разрешение на геолокацию и точка на карте (Ереван), чтобы экран не ждал системного диалога.
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
adb emu geo fix 44.51 40.18 || true

adb shell am start -W -n $PKG/.MainActivity --ez demo true
sleep 15
adb exec-out screencap -p > shots/00_today.png

routes=(
  "tasks" "calendar" "finance" "more" "habits" "goals"
  "health?tab=0" "health?tab=1" "health?tab=2" "health?tab=3" "health?tab=4" "health?tab=5"
  "wellbeing?tab=0" "wellbeing?tab=1" "wellbeing?tab=2"
  "notes" "tops" "top/1" "wardrobe" "outfits" "wheel" "review" "settings" "search"
  "finance?tab=1" "finance?tab=2" "finance?tab=3" "finance?tab=4" "finance?tab=5" "finance?tab=6" "calendar?tab=1"
  "task/1" "txn/0" "habit/1" "habitEdit/0" "goal/1" "event/1" "note/2"
  "recipes" "recipes?tab=1&mode=0" "recipes?tab=1&mode=1" "recipes?tab=1&mode=2" "recipes?tab=2" "recipes?tab=3"
  "recipe/1" "recipe/20" "recipeEdit/0" "menuCreate" "presets" "preset/1" "shopping" "cookHistory"
  "steps" "sber" "finNotebook"
  "health?tab=1" "wellbeing?tab=1" "habitEdit/1" "tasks" "tops"
  "healthHub" "plan?tab=0" "plan?tab=1" "plan?tab=2" "topsHub?tab=0" "topsHub?tab=1" "topsHub?tab=2" "topsHub?tab=3"
  "media/0?kind=0" "media/1?kind=0" "mediaSearch?kind=0" "mediaSearch?kind=2" "recipe/5" "health?tab=0"
  "bodyDetail/0" "weightTrend" "bodyCompare" "kpImport?kind=0" "topsHub?tab=0" "recipeEdit/1"
  "bodyScience" "widgetEditor" "plan?tab=2" "notes" "page/1" "page/2"
  "training?tab=0" "training?tab=1" "training?tab=2" "training?tab=3" "trainingPlan/1" "exercise/1" "session/1"
  "calendar?tab=2" "holiday/ru_newyear" "holiday/arm_vardavar" "health?tab=2" "bodyScience" "weightTrend" "recipe/3" "recipe/140" "recipe/170" "weather" "widgetEditor" "smallTalk" "sber" "tasks" "backup" "steps" "craft" "pressure" "tutor" "tutorWords" "tutorDict" "tutorTranslate?tab=0" "tutorTranslate?tab=1" "calendar?tab=3" "calendar?tab=4" "likes"
)
i=1
for r in "${routes[@]}"; do
  adb shell "am start -n $PKG/.MainActivity --es route '$r'"
  sleep 4
  name=$(printf "%02d_%s" "$i" "$(echo "$r" | tr '?=/' '___')")
  adb exec-out screencap -p > "shots/$name.png"
  i=$((i + 1))
done

adb shell am start -n $PKG/.MainActivity --es route "wardrobe"
sleep 3
adb shell input tap 385 1980
sleep 3
adb exec-out screencap -p > shots/99_wardrobe_tap.png

# Мастер нового рецепта: шаг «Ингредиенты» и выбор из базы.
adb shell am start -n $PKG/.MainActivity --es route "recipeEdit/0"
sleep 3
adb shell input text "Test"
adb exec-out screencap -p > shots/98_recipe_wizard.png

# Выбор иллюстрации рецепта: окно на весь экран с сеткой картинок.
adb shell am start -n $PKG/.MainActivity --es route "recipeEdit/1"
sleep 4
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(adb shell cat /sdcard/ui.xml | grep -o 'text="Иллюстрация"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 3; fi
adb exec-out screencap -p > shots/95_illustration_picker.png

# Добавить вес: кнопки шага на одной строке.
adb shell am start -n $PKG/.MainActivity --es route "health?tab=1"
sleep 4
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(adb shell cat /sdcard/ui.xml | grep -o 'text="+ Взвешивание"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 3; fi
adb exec-out screencap -p > shots/94_add_weight.png

# Погода: даём эмулятору координаты несколько раз и ждём загрузки прогноза.
adb shell am start -n $PKG/.MainActivity --es route "weather"
for k in 1 2 3 4 5 6; do adb emu geo fix 44.51 40.18 || true; sleep 4; done
adb exec-out screencap -p > shots/90_weather_loaded.png
adb shell input swipe 540 1700 540 500 400
sleep 2
adb exec-out screencap -p > shots/90_weather_loaded_2.png

# Праздники: раскрыть первый праздник в списке (нажатие по строке со стрелкой ▾).
adb shell am start -n $PKG/.MainActivity --es route "calendar?tab=2"
sleep 4
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(adb shell cat /sdcard/ui.xml | grep -o 'text="▾"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 - 300 )) $(( ($2 + $4) / 2 )); sleep 3; fi
adb exec-out screencap -p > shots/92_holiday_expanded.png
adb shell input swipe 540 1700 540 700 400
sleep 2
adb exec-out screencap -p > shots/92_holiday_expanded_2.png

# Рецепт: ссылка «Добавить видео рецепта» прокручивает к разделу видео.
adb shell am start -n $PKG/.MainActivity --es route "recipe/3"
sleep 4
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(adb shell cat /sdcard/ui.xml | grep -o 'text="▶ [^"]*"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
if [ -n "$b" ]; then set -- $b; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 3; fi
adb exec-out screencap -p > shots/91_recipe_videos.png

# Главная: метрики под первым экраном и плашка фактов внизу.
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity
sleep 6
adb shell input swipe 540 1700 540 700 400
sleep 2
adb exec-out screencap -p > shots/96_today_metrics.png
for k in 1 2 3 4 5 6 7 8; do adb shell input swipe 540 1900 540 300 200; sleep 1; done
sleep 2
adb exec-out screencap -p > shots/97_today_facts.png

adb logcat -d -b crash > shots/crash.txt || true
[ -s shots/crash.txt ] || echo "no crashes" > shots/crash.txt
adb logcat -d | grep -E "AndroidRuntime|FATAL|Poryadok|poryadok|System.err" | tail -300 > shots/log.txt || true
if adb shell pidof $PKG > /dev/null; then echo "APP ALIVE" > shots/status.txt; else echo "APP DEAD" > shots/status.txt; fi
cat shots/status.txt
echo "---- crash buffer ----"
cat shots/crash.txt
# Вылет приложения — ошибка автотеста (с первой строкой исключения в аннотации).
if grep -q "FATAL EXCEPTION" shots/crash.txt; then
  echo "::error title=App crash::$(grep -m1 -A4 'FATAL EXCEPTION' shots/crash.txt | sed 's/.*AndroidRuntime: //' | tr '\n' ' ')"
  exit 1
fi
