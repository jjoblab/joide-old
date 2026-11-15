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
    // Éditeur (celeris) — implementation : jamais dans l'API publique.
    implementation(libs.celeris.android.host)
    implementation(libs.celeris.android.widgets)
    implementation(libs.celeris.lsp.protocole)
    // Serveurs de langage (code-lsp) : moteurs + socle, SANS :host
    // (ADR-044). Le compagnon ecj-art (EPL-2.0) reste au classpath
    // runtime du moteur Java — jamais compilé contre.
    implementation(libs.codelsp.java)
    implementation(libs.codelsp.kotlin)
    implementation(libs.codelsp.xml)
    implementation(libs.codelsp.model)
    implementation(libs.codelsp.index)
    runtimeOnly(libs.codelsp.ecj.art)
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
