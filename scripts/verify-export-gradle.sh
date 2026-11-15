#!/usr/bin/env bash
# Vérification de l'export JoIDE dans un vrai Gradle (P7, §6.9 du
# prompt : « l'export se construit dans un vrai Gradle, vérifié
# manuellement ou par script hors appareil »).
#
# Usage :
#   scripts/verify-export-gradle.sh            # partie JVM (offline
#                                              # avec le cache local)
#   scripts/verify-export-gradle.sh --android  # + partie Android
#                                              # (réseau + ANDROID_HOME)
#
# La partie JVM exporte un espace multi-modules (dépendance externe
# Gson + dépendance de module), ajoute des sources, puis lance la
# distribution Gradle du wrapper sur compileJava + jar. La partie
# Android exporte l'échantillon multi-modules importé et lance la
# tâche help : tous les scripts s'évaluent, AGP et Kotlin sont
# résolus et configurés.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ]; then
    JAVAC="$(command -v javac || true)"
    if [ -z "${JAVAC}" ]; then
        echo "JAVA_HOME requis (JDK 21 avec javac)" >&2
        exit 1
    fi
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "${JAVAC}")")")"
fi

INIT_ARGS=()
if [ -f scripts/export-verification-init.gradle.kts ]; then
    # miroir de Central pour la machine de développement (429) — voir
    # le fichier ; retirer cet argument sur une machine saine
    INIT_ARGS=(-Djoide.export.init.script="$(pwd)/scripts/export-verification-init.gradle.kts")
fi

echo "== Export JVM dans un vrai Gradle =="
./gradlew :import-gradle:test \
    --tests 'jo.joide.imports.gradle.ExportRealGradleTest.jvmExportCompilesInRealGradle' \
    -Djoide.export.realgradle=true "${INIT_ARGS[@]}" --console=plain

if [ "${1:-}" = "--android" ]; then
    if [ -z "${ANDROID_HOME:-}" ]; then
        echo "ANDROID_HOME requis pour la partie Android" >&2
        exit 1
    fi
    echo "== Export Android dans un vrai Gradle (configuration AGP) =="
    ./gradlew :import-gradle:test \
        --tests 'jo.joide.imports.gradle.ExportRealGradleTest.androidExportConfiguresInRealGradle' \
        -Djoide.export.realandroid=true -Djoide.export.android.home="${ANDROID_HOME}" \
        "${INIT_ARGS[@]}" --console=plain
fi

echo "Vérification de l'export terminée."
