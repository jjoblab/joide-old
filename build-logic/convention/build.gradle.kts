// Module de conventions : les plugins précompilés vivent dans
// src/main/kotlin ; les classes d'appui (règle de dépendances, contrôle
// API 28) dans src/main/java — en Java : le produit est Java.
plugins {
    `kotlin-dsl`
}

group = "jo.joide.buildlogic"

// Aucune dépendance externe : la chaîne de build vit du seul Gradle.
// JUnit arrive par le catalogue, côté modules.
