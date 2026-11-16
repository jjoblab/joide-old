// ide-integration — le SEUL module qui connaît les briques (ADR-010) :
// adaptateurs celeris / code-lsp / LayoutLib derrière des interfaces
// JoIDE (jo.joide.ide). Aucun type tiers ne fuit : les briques vivent en
// implementation, l'API publique est jo.joide.ide.* exclusivement.
plugins {
    id("joide.android-library-conventions")
}

dependencies {
    api(project(":core-model"))
    api(project(":build-daemon"))
    // Lot O (ADR-059) : l'implémentation du port EspaceTravailAgent
    // vit ICI — au-dessus des briques, sous l'application.
    api(project(":agent"))
    // Éditeur (celeris) — implementation : jamais dans l'API publique.
    implementation(libs.celeris.android.host)
    implementation(libs.celeris.android.widgets)
    implementation(libs.celeris.lsp.protocole)
    // Grammaires TextMate du registre de langages (lot D) : les
    // paquets intégrés exigent la fabrique pour les langages sans
    // analyseur écrit.
    implementation(libs.celeris.core.syntax.grammars)
    // Serveurs de langage (code-lsp) : moteurs + socle, SANS :host
    // (ADR-044). Le compagnon ecj-art (EPL-2.0) reste au classpath
    // runtime du moteur Java — jamais compilé contre.
    implementation(libs.codelsp.java)
    implementation(libs.codelsp.kotlin)
    implementation(libs.codelsp.xml)
    implementation(libs.codelsp.model)
    implementation(libs.codelsp.index)
    runtimeOnly(libs.codelsp.ecj.art)
    // Lot C : le moteur Java tourne réellement dans les tests (ecj
    // patché ART au classpath de test — EPL-2.0, jamais compilé contre).
    testImplementation(libs.codelsp.ecj.art)
    // Lot O : l'espace de travail de l'agent exécute le VRAI moteur
    // de build dans les tests — le store journal de bureau de
    // core-store (configuration jvmApi, comme les tests du démon).
    testImplementation(
        project(mapOf("path" to ":core-store",
            "configuration" to "jvmApi")))
    // Parseur XmlPull des tests JVM des résolveurs de la
    // brique LayoutLib (lot E) ; sur appareil le framework
    // fournit l'implémentation.
    testImplementation(libs.kxml2)
    // Le socle :model publie coroutines en runtime seulement ; la
    // synchronisation lit le StateFlow du dépôt d'instantanés.
    implementation(libs.kotlinx.coroutines.core)
    // Aperçu de layouts (LayoutLib) : les 6 modules de rendu — l'app
    // layout-editor-app et le spike Compose sont exclus par contrat
    // (docs/integration/layoutlib.md). Livraison conditionnée à D-1.
    implementation(libs.layoutlib.inflater)
    implementation(libs.layoutlib.resources)
    implementation(libs.layoutlib.drawables)
    implementation(libs.layoutlib.themes)
    implementation(libs.layoutlib.attributes)
    implementation(libs.layoutlib.layout)
}
