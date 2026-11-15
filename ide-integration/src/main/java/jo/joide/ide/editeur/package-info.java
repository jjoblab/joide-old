/**
 * Éditeur celeris derrière interfaces JoIDE (lot D du câblage P8) : la
 * scène d'édition (vue + couche de popups) est montée par ce module et
 * exposée à l'application sous des types du CADRE Android uniquement
 * ({@code android.view.View}, {@code android.content.Context}) — les
 * types de la brique ne quittent jamais le module (ADR-044/010).
 *
 * <p>Le pont de langage : la session celeris (contrat
 * {@code LanguageSession} de la brique — complétion, survol,
 * signature, diagnostics poussés) est implémentée par ce module AU-DESSUS
 * de la façade {@link jo.joide.ide.langages.MoteursLangage} du lot C —
 * l'éditeur ne connaît jamais code-lsp, les moteurs ne connaissent
 * jamais celeris, les deux ne se rencontrent que ici.</p>
 */
package jo.joide.ide.editeur;
