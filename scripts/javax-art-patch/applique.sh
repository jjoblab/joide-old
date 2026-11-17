#!/bin/bash
# Patch ART de javax.lang.model.SourceVersion dans java-compiler.jar
# (lot XX/b, terrain du 2026-11-17) — Runtime.version() est une API
# JDK 9+ ABSENTE d'ART : le <clinit> de la classe extraite du JDK 21
# tuait :app:generateRJar (« NoSuchMethodError: No static method
# version()Ljava/lang/Runtime$Version; »). La recette recompile la
# classe avec la version figée (le jar EST celui du JDK 21) et la
# réinjecte dans le jar committé. Idempotent. Usage, depuis le dépôt :
#   bash scripts/javax-art-patch/applique.sh
# Prérequis : un JDK 21 (JAVA_HOME ou javac sur le PATH).
set -euo pipefail

# La racine du dépôt se déduit de l'EMPLACEMENT du script (pas du
# cwd : un cwd extérieur remonterait vers un dépôt englobant).
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && git rev-parse --show-toplevel)"
REPO="$(pwd)"
JAR="$REPO/app-android/libs/java-compiler.jar"
SRC="$REPO/scripts/javax-art-patch/SourceVersion.java"
OUT=/tmp/sv-patch-out

if [ -n "${JAVA_HOME:-}" ]; then
  JBIN="$JAVA_HOME/bin"
elif command -v javac > /dev/null 2>&1; then
  JBIN="$(dirname "$(readlink -f "$(command -v javac)")")"
else
  echo "ERREUR : JAVA_HOME ou javac requis (JDK 21)" >&2; exit 1
fi

# 1. Compilation (javac 21 → major 65, comme l'extraction d'origine).
#    javax.lang.model appartient au module java.compiler du JDK :
#    recompilation par --patch-module (sinon « package exists in
#    another module » et conflits SourceVersion/SourceVersion).
rm -rf "$OUT" && mkdir -p "$OUT/srcroot/javax/lang/model" "$OUT/classes"
cp "$SRC" "$OUT/srcroot/javax/lang/model/SourceVersion.java"
"$JBIN/javac" --patch-module java.compiler="$OUT/srcroot" \
    -d "$OUT/classes" "$OUT/srcroot/javax/lang/model/SourceVersion.java"
echo "== javac OK"

# 2. La référence interdite doit avoir DISPARU du bytecode
if "$JBIN/javap" -c -p "$OUT/classes/javax/lang/model/SourceVersion.class" \
     | grep -q "Runtime.version"; then
  echo "ERREUR : Runtime.version() encore présent" >&2; exit 1
fi
echo "== bytecode sans Runtime.version() OK"

# 3. API identique à l'original (signature publique) — l'original est
#    extrait du jar committé, avant réinjection
rm -rf "$OUT/avant" && mkdir -p "$OUT/avant"
(cd "$OUT/avant" && unzip -q -o -j "$JAR" 'javax/lang/model/SourceVersion.class')
"$JBIN/javap" -cp "$OUT/avant" -p javax.lang.model.SourceVersion > /tmp/api-avant.txt
"$JBIN/javap" -cp "$OUT/classes" -p javax.lang.model.SourceVersion > /tmp/api-apres.txt
diff /tmp/api-avant.txt /tmp/api-apres.txt && echo "== API identique OK"

# 4. Réinjection dans le jar committé
cd "$OUT/classes"
"$JBIN/jar" uf "$JAR" javax/lang/model/SourceVersion.class
echo "== jar mis à jour"

# 5. Preuve finale : AUCUNE INVOCATION de Runtime.version() dans le
#    jar. (Le descripteur « Ljava/lang/Runtime$Version; » peut rester
#    présent dans SourceVersion.valueOf(Runtime.Version) — un
#    descripteur seul ne plante pas ART, seule une INVOCATION le
#    ferait ; le scan dex post-build complète au niveau APK.)
rm -rf "$OUT/verif" && mkdir -p "$OUT/verif"
cd "$OUT/verif" && unzip -q -o "$JAR"
N=0
for c in $(find . -name '*.class'); do
  if "$JBIN/javap" -c -p "$c" 2>/dev/null | grep -q "Runtime.version:"; then
    echo "  invocation : $c"; N=$((N+1))
  fi
done
if [ "$N" -ne 0 ]; then
  echo "ERREUR : $N classe(s) INVOQUENT Runtime.version()" >&2; exit 1
fi
echo "== jar propre : 0 invocation Runtime.version()"

# 6. La classe s'initialise et répond (sur JVM ici ; ART lira pareil)
cat > "$OUT/SvTest.java" << 'EOF'
import javax.lang.model.SourceVersion;
public class SvTest {
    public static void main(String[] a) {
        System.out.println("latestSupported=" + SourceVersion.latestSupported());
        System.out.println("latest=" + SourceVersion.latest());
    }
}
EOF
"$JBIN/java" -cp "$JAR" "$OUT/SvTest.java"
