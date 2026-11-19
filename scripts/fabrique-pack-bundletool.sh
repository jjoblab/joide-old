#!/bin/bash
# Lot XXIII — reconstruction du pack bundletool avec les SIX
# dépendances runtime RESTANTES du POM (aapt2-proto, dagger,
# javax.inject, jose4j, slf4j-api, auto-value-annotations).
#
# Le terrain du 2026-11-22 : « app:bundle[debug] :
# NoClassDefFoundError: Failed resolution of:
# Lcom/android/aapt/Resources$XmlNode; » — le pack .2 (lot XXII)
# portait bundletool + la fermeture GUAVA mais PAS les six
# dépendances runtime restantes : aapt2-proto porte les classes
# com.android.aapt (le manifeste proto du BuildBundleCommand),
# le bouton build mourrait au premier XmlNode.
#
# Ce script est le MIROIR de la tâche :app-android:dexPacksOutils
# (mêmes jars, même ordre canonique, même D8 — celui du jar r8 du
# catalogue, jamais le d8 du SDK : deux D8 différents font deux
# empreintes différentes) : la tâche reproduit l'artefact octet pour
# octet. Règle dorée tenue : l'artefact publié ne change JAMAIS — le
# pack corrigé vit dans le NOUVEAU fichier versionné
# bundletool-1.18.3.3.dex.zip (les .0, .1 et .2 restent en place).
set -euo pipefail

# Environnements par défaut (surchargeables) :
G=${GROUPE_JARS:-/home/z/.gradle/caches/modules-2/files-2.1}
SDK=${SDK_ANDROID:-/home/z/my-project/reference/sdk}
REPO=${REPO_JOIDE:-/home/z/my-project/joide}
SORTIE=${SORTIE:-/tmp/pack/nouveau}
# Le D8 du catalogue (le jar r8 résolu par la tâche Gradle) :
R8_JAR=${R8_JAR:-$G/com.android.tools/r8/9.5.32/$(ls "$G/com.android.tools/r8/9.5.32" 2>/dev/null | head -1)/r8-9.5.32.jar}

# Les jars EXACTS (versions de l'ère guava-32.0.1, alignées sur le
# POM runtime de bundletool 1.18.3 + leurs transitives runtime) —
# L'ORDRE EST CANONIQUE : le même que jarsBundletool dans la tâche.
# Lot XXIII : les SIX dépendances runtime RESTANTES du POM suivent
# (aapt2-proto d'abord : com.android.aapt, le manifeste proto).
JARS=(
  "$G/com.android.tools.build/bundletool/1.18.3/8d7bee8e57a4158a872ed99190a60a498c2adc8d/bundletool-1.18.3.jar"
  "$G/com.google.guava/guava/32.0.1-jre/6e5d51a72d142f2d40a57dfb897188b36a95b489/guava-32.0.1-jre.jar"
  "$G/com.google.protobuf/protobuf-java/3.22.3/fdee98b8f6abab73f146a4edb4c09e56f8278d03/protobuf-java-3.22.3.jar"
  "$G/com.google.protobuf/protobuf-java-util/3.22.3/749cd4fe8ab52f37bc186193802ba19f5b284647/protobuf-java-util-3.22.3.jar"
  "$G/com.google.guava/failureaccess/1.0.2/c4a06a64e650562f30b7bf9aaec1bfed43aca12b/failureaccess-1.0.2.jar"
  "$G/com.google.guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/b421526c5f297295adef1c886e5246c39d4ac629/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"
  "$G/com.google.code.findbugs/jsr305/3.0.2/25ea2e8b0c338a877313bd4672d3fe056ea78f0d/jsr305-3.0.2.jar"
  "$G/com.google.errorprone/error_prone_annotations/2.18.0/89b684257096f548fa39a7df9fdaa409d4d4df91/error_prone_annotations-2.18.0.jar"
  "$G/org.checkerframework/checker-qual/2.5.8/41c6fa63cba0fea55ebd3581d42b3d2c5cca98b2/checker-qual-2.5.8.jar"
  "${AAPT2_PROTO_JAR:-$G/com.android.tools.build/aapt2-proto/7.3.0-alpha07-8248216/$(ls "$G/com.android.tools.build/aapt2-proto/7.3.0-alpha07-8248216" 2>/dev/null | head -1)/aapt2-proto-7.3.0-alpha07-8248216.jar}"
  "${DAGGER_JAR:-$G/com.google.dagger/dagger/2.28.3/$(ls "$G/com.google.dagger/dagger/2.28.3" 2>/dev/null | head -1)/dagger-2.28.3.jar}"
  "${JAVAX_INJECT_JAR:-$G/javax.inject/javax.inject/1/$(ls "$G/javax.inject/javax.inject/1" 2>/dev/null | head -1)/javax.inject-1.jar}"
  "${JOSE4J_JAR:-$G/org.bitbucket.b_c/jose4j/0.9.5/$(ls "$G/org.bitbucket.b_c/jose4j/0.9.5" 2>/dev/null | head -1)/jose4j-0.9.5.jar}"
  "${SLF4J_JAR:-$G/org.slf4j/slf4j-api/1.7.30/$(ls "$G/org.slf4j/slf4j-api/1.7.30" 2>/dev/null | head -1)/slf4j-api-1.7.30.jar}"
  "${AUTO_VALUE_JAR:-$G/com.google.auto.value/auto-value-annotations/1.6.2/$(ls "$G/com.google.auto.value/auto-value-annotations/1.6.2" 2>/dev/null | head -1)/auto-value-annotations-1.6.2.jar}"
)
for jar in "${JARS[@]}"; do
  [ -f "$jar" ] || { echo "JAR ABSENT : $jar (surchargez G=… ou le chemin)" >&2; exit 1; }
done
[ -f "$R8_JAR" ] || { echo "JAR R8 ABSENT : $R8_JAR (surchargez R8_JAR=…)" >&2; exit 1; }
[ -f "$SDK/platforms/android-36/android.jar" ] || ANDROID_JAR=$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort | tail -1) || ANDROID_JAR=""
[ -n "${ANDROID_JAR:-}" ] || { echo "android.jar introuvable sous $SDK/platforms" >&2; exit 1; }

rm -rf "$SORTIE" && mkdir -p "$SORTIE/out" "$SORTIE/san"

# 1) Les jars assainis (dex embarqué de bundletool, module-info,
#    variantes multi-release hors) — entrées triées, horodatage fixe :
#    le miroir exact de « assainir » dans la tâche.
python3 - "$SORTIE" "${JARS[@]}" <<'PYEOF'
import sys, zipfile
sortie, jars = sys.argv[1], sys.argv[2:]
for i, jar in enumerate(jars):
    cible = f"{sortie}/san/{i}.jar"
    with zipfile.ZipFile(jar) as src, \
         zipfile.ZipFile(cible, "w", zipfile.ZIP_DEFLATED) as out:
        noms = sorted(e.filename for e in src.infolist()
                      if not e.filename.endswith(".dex")
                      and e.filename != "module-info.class"
                      and not e.filename.startswith("META-INF/versions/"))
        for nom in noms:
            info = zipfile.ZipInfo(nom, date_time=(2000, 1, 1, 0, 0, 0))
            out.writestr(info, src.read(nom))
    print(f"assaini : {jar.rsplit('/', 1)[-1]} -> {cible}")
PYEOF

# 2) L'ADAPTATEUR — extrait du jar tools du dépôt (SEULES les classes
#    du préfixe partent au dexage : le mur de chargeurs reste étanche,
#    aucune classe jo.joide de l'APK dans le pack, l'adaptateur charge
#    ses SPI par nom à travers le chargeur parent).
TOOLS_JAR=${TOOLS_JAR:-$(ls "$REPO"/build-android/build/libs/build-android-tools.jar 2>/dev/null || true)}
[ -f "$TOOLS_JAR" ] || TOOLS_JAR=$(find "$G" -name "build-android-tools.jar" 2>/dev/null | head -1 || true)
[ -f "$TOOLS_JAR" ] || { echo "jar tools introuvable : lancez :build-android:toolsJar ou posez TOOLS_JAR=…" >&2; exit 1; }
mkdir -p "$SORTIE/adaptateurs"
python3 - "$TOOLS_JAR" "$SORTIE/adaptateurs" <<'PYEOF'
import sys, zipfile
source, dossier = sys.argv[1], sys.argv[2]
prefixe = "jo/joide/build/android/tools/BundletoolAssembler"
trouves = 0
with zipfile.ZipFile(source) as z:
    for info in z.infolist():
        if info.filename.startswith(prefixe):
            z.extract(info, dossier)
            trouves += 1
print(f"adaptateur : {trouves} classe(s) extraite(s)")
PYEOF

# 3) Le D8 du CATALOGUE (jamais le d8 du SDK : la tâche Gradle doit
#    reproduire l'artefact octet pour octet) — tout dexé ENSEMBLE.
java -cp "$R8_JAR" com.android.tools.r8.D8 --release --min-api 28 \
  --lib "$ANDROID_JAR" \
  --output "$SORTIE/out" \
  "$SORTIE"/san/*.jar \
  $(find "$SORTIE/adaptateurs" -name '*.class' | sort)

# 4) Les marqueurs DOIVENT être des DÉFINITIONS (dexdump -f, ligne
#    « Class descriptor ») — jamais seulement des références. Le
#    marqueur de L'ADAPTATEUR est la moitié du lot XXII.
MARQUEURS=(
  "Ljo/joide/build/android/tools/BundletoolAssembler;"
  'Lcom/android/aapt/Resources$XmlNode;'
  "Lcom/google/protobuf/GeneratedMessageV3;"
  "Lcom/android/tools/build/bundletool/commands/BuildBundleCommand;"
  "Lcom/android/tools/build/bundletool/commands/ValidateBundleCommand;"
  "Lcom/google/common/collect/ImmutableList;"
  "Lcom/google/common/util/concurrent/internal/InternalFutureFailureAccess;"
  "Lorg/slf4j/Logger;"
)
echo "--- vérification des définitions ---"
DEXDUMP=${DEXDUMP:-$(ls "$SDK"/build-tools/*/dexdump 2>/dev/null | sort | tail -1 || true)}
for m in "${MARQUEURS[@]}"; do
  trouve=0
  for d in "$SORTIE/out"/*.dex; do
    # grep -c lit JUSQU'À LA FIN (jamais de SIGPIPE vers dexdump :
    # avec pipefail, un grep -q qui sort tôt tue dexdump (141) et
    # transforme un succès en échec — le piège du premier essai)
    compte=$("$DEXDUMP" -f "$d" 2>/dev/null | \
        grep -c "Class descriptor.*$m" || true)
    if [ "${compte:-0}" -gt 0 ]; then
      trouve=1; echo "  défini ✓ $m ($(basename "$d"))"
      break
    fi
  done
  [ "$trouve" = 1 ] || { echo "MARQUEUR ABSENT : $m" >&2; exit 1; }
done

# 5) Archive déterministe : entrées triées, horodatages fixes.
cd "$SORTIE/out" && rm -f "../bundletool-1.18.3.3.dex.zip"
touch -d "2000-01-01 00:00:00" ./*.dex
ls ./*.dex | sort | zip -q -X "../bundletool-1.18.3.3.dex.zip" -@ \
  || zip -q -X "../bundletool-1.18.3.3.dex.zip" ./*.dex

echo "--- empreintes du nouveau pack ---"
F="$SORTIE/bundletool-1.18.3.3.dex.zip"
ls -la "$F"
echo "TAILLE = $(stat -c %s "$F")"
echo "SHA256 = $(sha256sum "$F" | cut -d' ' -f1)"
echo "À publier dans la release toolchain-v1, puis à épingler dans"
echo "PacksOutils.java (TAILLE_BUNDLETOOL / SHA_BUNDLETOOL) si divergent."
