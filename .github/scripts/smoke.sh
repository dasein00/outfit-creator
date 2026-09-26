#!/usr/bin/env bash
# Устанавливает APK на эмулятор, заполняет примером, открывает экраны и снимает скриншоты.
set -x
PKG=com.dasein.poryadok
mkdir -p shots
adb install -r apk/DASEIN.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

adb shell am start -W -n $PKG/.MainActivity --ez demo true
sleep 15
adb exec-out screencap -p > shots/00_today.png

routes=(
  "tasks" "calendar" "finance" "more" "habits" "goals" "focus"
  "health?tab=0" "health?tab=1" "health?tab=2" "health?tab=3" "health?tab=4" "health?tab=5"
  "wellbeing?tab=0" "wellbeing?tab=1" "wellbeing?tab=2"
  "notes" "tops" "top/1" "wardrobe" "outfits" "wheel" "review" "settings" "search"
  "finance?tab=1" "finance?tab=2" "finance?tab=3" "finance?tab=4" "calendar?tab=1"
  "task/1" "txn/0" "habit/1" "habitEdit/0" "goal/1" "event/1" "note/2"
  "recipes" "recipes?tab=1&mode=0" "recipes?tab=1&mode=1" "recipes?tab=1&mode=2" "recipes?tab=2" "recipes?tab=3"
  "recipe/1" "recipe/20" "recipeEdit/0" "menuCreate" "presets" "preset/1" "shopping" "cookHistory"
  "steps" "sber" "finNotebook"
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

adb logcat -d -b crash > shots/crash.txt || true
[ -s shots/crash.txt ] || echo "no crashes" > shots/crash.txt
adb logcat -d | grep -E "AndroidRuntime|FATAL|Poryadok|poryadok|System.err" | tail -300 > shots/log.txt || true
if adb shell pidof $PKG > /dev/null; then echo "APP ALIVE" > shots/status.txt; else echo "APP DEAD" > shots/status.txt; fi
cat shots/status.txt
echo "---- crash buffer ----"
cat shots/crash.txt
