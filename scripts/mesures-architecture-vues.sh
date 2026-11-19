#!/bin/sh
# mesures-architecture-vues.sh — inventaire chiffré de l'architecture des
# vues d'app-android (chantier ADR-085, Annexe B du prompt v2 du
# 2026-11-19). Reproduit chaque mesure de l'annexe B et ajoute la
# répartition par fichier (la liste d'exceptions d'ArchitectureVuesTest
# s'en nourrit). Sortie : texte brut, une ligne « clé valeur » par mesure
# pour grep facile.
#
# Usage : sh scripts/mesures-architecture-vues.sh [racine-du-dépôt]
# (défaut : le parent du dossier scripts/)
set -u

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
SRC="$ROOT/app-android/src/main/java/jo/joide/app/android"
RES="$ROOT/app-android/src/main/res"
APP="$ROOT/app-android"

# La liste des classes de vues du prompt (cadre + AndroidX + Material)
VUES='LinearLayout|FrameLayout|RelativeLayout|ScrollView|HorizontalScrollView|NestedScrollView|TextView|Button|ImageView|ImageButton|EditText|View|GridLayout|ProgressBar|MaterialButton|MaterialCardView|MaterialToolbar|MaterialSwitch|Chip|ChipGroup|TextInputLayout|TextInputEditText|TabLayout|FloatingActionButton|ExtendedFloatingActionButton|ConstraintLayout|CoordinatorLayout|DrawerLayout|RecyclerView|AppCompat[A-Za-z]*'

echo "### vues instanciées en code (cible 0 dans les écrans)"
total=$(grep -rhoE "new ($VUES)\(" --include=*.java "$SRC" | wc -l | tr -d ' ')
echo "vues_code_total $total"
grep -rcE "new ($VUES)\(" --include=*.java "$SRC" 2>/dev/null | awk -F: '$2 > 0' | sort -t: -k2,2nr

echo "### répartition par classe"
grep -rhoE "new ($VUES)\(" --include=*.java "$SRC" | sort | uniq -c | sort -rn

echo "### dialogues (cible 0 ; Dialogue.java supprimée en A7)"
d=$(grep -rnE "AlertDialog|MaterialAlertDialogBuilder|android\.app\.Dialog|new Dialog\(|DialogFragment|BottomSheetDialog|DatePickerDialog|TimePickerDialog|ProgressDialog" --include=*.java "$SRC" | wc -l | tr -d ' ')
echo "dialogues_cadre $d"
d2=$(grep -rn "Dialogue\." --include=*.java "$SRC" | wc -l | tr -d ' ')
echo "usages_dialogue_maison $d2"
grep -rlE "Dialogue\." --include=*.java "$SRC" 2>/dev/null

echo "### imports AndroidX / Material (liste blanche ADR-085 ; 0 compose)"
grep -rhoE "^import (androidx|com\.google\.android\.material)\.[A-Za-z0-9.]*" --include=*.java "$SRC" | sort | uniq -c
compose=$(grep -rlE "^import androidx\.compose" --include=*.java "$SRC" | wc -l | tr -d ' ')
echo "imports_compose $compose"

echo "### socle (C6)"
grep -nE "compileSdk|minSdk|targetSdk" "$APP/build.gradle.kts" "$ROOT/build-logic/convention/src/main/kotlin/joide.android-application-conventions.gradle.kts" 2>/dev/null
grep -nE "androidx|material" "$APP/build.gradle.kts" 2>/dev/null || echo "dependances_androidx_material_aucune"

echo "### thémage et dimensions injectés en code"
p=$(grep -rho "palette\.[a-zA-Z]*" --include=*.java "$SRC" | wc -l | tr -d ' ')
echo "palette_code $p"
dp=$(grep -rho "GraphiteVue\.dp(" --include=*.java "$SRC" | wc -l | tr -d ' ')
echo "graphitevue_dp $dp"

echo "### couplage contrôleurs -> activité (cible 0 en A6)"
for f in ControleurAgent ControleurBuild ControleurEdition ControleurFichiers; do
  n=$(grep -o 'atelier\.[a-zA-Z]*' "$SRC/ecrans/atelier/$f.java" 2>/dev/null | wc -l | tr -d ' ')
  echo "couplage_$f $n"
done

echo "### taille des écrans (lignes)"
wc -l "$SRC/MainActivity.java" "$SRC"/ecrans/*/Ecran*.java 2>/dev/null

echo "### ressources (littéraux, factorisation, qualificatifs)"
echo "dp_litteraux $(grep -rhoE '"[0-9.]+dp"' "$RES/layout" | wc -l | tr -d ' ')"
echo "sp_litteraux $(grep -rhoE '"[0-9.]+sp"' "$RES/layout" | wc -l | tr -d ' ')"
grep -rhoE "<merge|<include|<ViewStub" "$RES/layout" | sort | uniq -c
ls -d "$RES"/layout-* "$RES"/values-w* 2>/dev/null || echo "qualificatifs_aucun"
echo "layouts_dialog $(ls "$RES/layout" | grep -c '^dialog_')"
echo "layouts $(ls "$RES/layout" | grep -c xml)"

echo "### état de configuration (C3)"
echo "onSaveInstanceState $(grep -rho 'onSaveInstanceState' --include=*.java "$SRC" | wc -l | tr -d ' ')"
echo "onConfigurationChanged $(grep -rho 'onConfigurationChanged' --include=*.java "$SRC" | wc -l | tr -d ' ')"
echo "manifest_configChanges $(grep -c 'configChanges' "$APP/src/main/AndroidManifest.xml" | tr -d ' ')"

echo "### divers"
echo "findViewById $(grep -rho 'findViewById' --include=*.java "$SRC" | wc -l | tr -d ' ')"
echo "runOnUiThread $(grep -rho 'runOnUiThread' --include=*.java "$SRC" | wc -l | tr -d ' ')"
echo "bindings_consommes $(grep -rhoE '[A-Za-z]+Binding' --include=*.java "$SRC" | sort -u | wc -l | tr -d ' ')"
echo "layouts_sans_binding $(ls "$RES/layout" | sed 's/\.xml//' | while read -r nom; do
  grep -rq "$(echo "$nom" | sed 's/^\([a-z]*\)_\([a-z]*\)\(.*\)/\u\1\2\3/' 2>/dev/null)Binding" --include=*.java "$SRC" 2>/dev/null || true
done | wc -l | tr -d ' ')"
echo "contentDescription $(grep -rho 'contentDescription' "$RES/layout" | wc -l | tr -d ' ')"
echo "ids_plus $(grep -rho '@+id/' "$RES/layout" | sort -u | wc -l | tr -d ' ')"

echo "### API > 28 sans garde (C5)"
grep -rnE "WindowInsets\.Type|getInsets\(" --include=*.java "$SRC" || echo "aucune"
