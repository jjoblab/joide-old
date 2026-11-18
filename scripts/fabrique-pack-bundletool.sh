#!/bin/bash
# Lot XXI/b — reconstruction du pack bundletool avec TOUTES ses
# dépendances runtime (la doctrine : un pack est AUTOSUFFISANT —
# manière CodeAssist, precedent du pack Kotlin qui porte STAX).
#
# Le terrain du 2026-11-18 : « bundle[debug] : NoClassDefFoundError:
# Failed resolution of: Lcom/google/protobuf/GeneratedMessageV3; » —
# le pack d'origine portait bundletool + guava SANS protobuf-java :
# les 60 descripteurs com/google/protobuf/* des dex n'étaient que des
# références de bundletool, jamais des définitions. Le chemin bundle
# étant nouveau, l'omission n'avait jamais explosé.
#
# Règle dorée respectée : l'artefact publié ne change JAMAIS — le
# pack corrigé vit dans un NOUVEAU fichier versionné
# (bundletool-1.18.3.1.dex.zip), publié à côté de l'ancien.
set -euo pipefail

G=/home/z/.gradle/caches/modules-2/files-2.1
SDK=/home/z/my-project/reference/sdk
D8="$SDK/build-tools/37.0.0/d8"
DEXDUMP="$SDK/build-tools/37.0.0/dexdump"
SORTIE=/tmp/pack/nouveau

# Les jars EXACTS (versions de l'ère guava-32.0.1, alignées sur le
# POM runtime de bundletool 1.18.3 + leurs transitives runtime).
JARS=(
  "$G/com.android.tools.build/bundletool/1.18.3/8d7bee8e57a4158a872ed99190a60a498c2adc8d/bundletool-1.18.3.jar"
  "$G/com.google.guava/guava/32.0.1-jre/6e5d51a72d142f2d40a57dfb897188b36a95b489/guava-32.0.1-jre.jar"
  "$G/com.google.guava/failureaccess/1.0.2/c4a06a64e650562f30b7bf9aaec1bfed43aca12b/failureaccess-1.0.2.jar"
  "$G/com.google.guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/b421526c5f297295adef1c886e5246c39d4ac629/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"
  "$G/com.google.code.findbugs/jsr305/3.0.2/25ea2e8b0c338a877313bd4672d3fe056ea78f0d/jsr305-3.0.2.jar"
  "$G/com.google.errorprone/error_prone_annotations/2.18.0/89b684257096f548fa39a7df9fdaa409d4d4df91/error_prone_annotations-2.18.0.jar"
  "$G/org.checkerframework/checker-qual/2.5.8/41c6fa63cba0fea55ebd3581d42b3d2c5cca98b2/checker-qual-2.5.8.jar"
  "$G/com.google.protobuf/protobuf-java/3.22.3/fdee98b8f6abab73f146a4edb4c09e56f8278d03/protobuf-java-3.22.3.jar"
  "$G/com.google.protobuf/protobuf-java-util/3.22.3/749cd4fe8ab52f37bc186193802ba19f5b284647/protobuf-java-util-3.22.3.jar"
)
for jar in "${JARS[@]}"; do
  [ -f "$jar" ] || { echo "JAR ABSENT : $jar" >&2; exit 1; }
done

rm -rf "$SORTIE" && mkdir -p "$SORTIE/out"

# Le jar Maven de bundletool embarque des dex de FIXTURES de test
# (com/android/tools/build/bundletool/archive/dex/*/classes.dex, ~7 Ko)
# — D8 refuse une archive mixte dex + bytecode. Copie PURIFIÉE : tout
# sauf les *.dex (les fixtures restent des ressources du jar purifié,
# au cas où bundletool les lirait — elles ne sont jamais des classes
# de son classpath runtime).
BT_PURIFIE="$SORTIE/bundletool-purifie.jar"
python3 - "$G/com.android.tools.build/bundletool/1.18.3/8d7bee8e57a4158a872ed99190a60a498c2adc8d/bundletool-1.18.3.jar" "$BT_PURIFIE" << 'PYEOF'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z, \
     zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as out:
    for info in z.infolist():
        if info.filename.endswith(".dex"):
            continue
        out.writestr(info, z.read(info.filename))
print(f"jar purifié : {len(z.infolist())} entrées lues")
PYEOF

"$D8" --release --min-api 28 \
  --lib "$SDK/platforms/android-36/android.jar" \
  --output "$SORTIE/out" \
  "$BT_PURIFIE" \
  "${JARS[@]:1}"

# Les marqueurs DOIVENT être des DÉFINITIONS (dexdump -f, ligne
# « Class descriptor ») — jamais seulement des références.
MARQUEURS=(
  "Lcom/google/protobuf/GeneratedMessageV3;"
  "Lcom/android/tools/build/bundletool/commands/BuildBundleCommand;"
  "Lcom/android/tools/build/bundletool/commands/ValidateBundleCommand;"
  "Lcom/google/common/collect/ImmutableList;"
  "Lcom/google/common/util/concurrent/internal/InternalFutureFailureAccess;"
)
echo "--- vérification des définitions ---"
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

# Archive déterministe : entrées triées, horodatages fixes.
cd "$SORTIE/out" && rm -f "../bundletool-1.18.3.1.dex.zip"
touch -d "2000-01-01 00:00:00" ./*.dex
ls ./*.dex | sort | zip -q -X "../bundletool-1.18.3.1.dex.zip" -@ \
  || zip -q -X "../bundletool-1.18.3.1.dex.zip" ./*.dex

echo "--- empreintes du nouveau pack ---"
F="$SORTIE/bundletool-1.18.3.1.dex.zip"
ls -la "$F"
echo "TAILLE = $(stat -c %s "$F")"
echo "SHA256 = $(sha256sum "$F" | cut -d' ' -f1)"
