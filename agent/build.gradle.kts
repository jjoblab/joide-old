// agent — le noyau de l'agent IA (lot N, ADR-É10/055) : module PUR
// JVM, pair de core-tasks — modèle neutre, boucle, erreurs,
// permissions, sessions, fournisseurs Anthropic et OpenAI-compatible.
// Zéro Android, zéro brique : le port EspaceTravailAgent (lot O) est
// implémenté côté ide-integration/app-android. La SEULE dépendance
// tierce est le transport OkHttp (décision D-5 du propriétaire,
// ADR-056) ; le JSON passe par le parseur maison de core-deps
// (ADR-028) et l'écriture compacte qui l'accompagne.
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-platform"))
    api(project(":core-deps"))
    // D-5 (propriétaire, 2026-11-16) : OkHttp retenu comme transport
    // HTTP/SSE — l'implémentation maison sur HttpURLConnection est
    // écartée. Apache-2.0, NOTICE et docs/third-party.md à jour.
    implementation(libs.okhttp)
}
