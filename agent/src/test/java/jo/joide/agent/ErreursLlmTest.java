package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * La taxonomie d'erreurs (ADR-É9, robustesse étage 1) : le cas
 * d'école du 429 (limite à la minute vs quota du jour vs crédit
 * épuisé), les codes d'abord et le wording en repli, chaque genre
 * sait s'il se retente et chaque message dit quoi faire.
 */
class ErreursLlmTest {

    @Test
    void un429AvecRetryAfterEstUneLimiteDebitAvecDelai() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of("retry-after", List.of("7")), "rate limited");
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
        assertTrue(erreur.genre.retentable);
        assertEquals(7, erreur.delaiSecondes,
                "le délai vient de l'EN-TÊTE, pas du wording");
    }

    @Test
    void un429AvecDelaiDansLeCorpsEstLuEnRepli() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(), "Please try again in 3s.");
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
        assertEquals(3, erreur.delaiSecondes);
    }

    @Test
    void un429QuotidienEstUneLimiteJourNonRetentable() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(), "You hit your daily rate limit, resets "
                        + "tomorrow");
        assertEquals(ErreursLlm.Genre.LIMITE_JOUR, erreur.genre);
        assertFalse(erreur.genre.retentable,
                "attendre la minute ne sert à rien : c'est le jour");
    }

    @Test
    void un429DeFacturationEstUnCreditEpuise() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(), "billing hard limit reached — credit "
                        + "exhausted");
        assertEquals(ErreursLlm.Genre.QUOTA_EPUISE, erreur.genre);
        assertFalse(erreur.genre.retentable);
    }

    @Test
    void le404GuideVersLEtNonVersLeModele() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(404,
                Map.of(), "Not found");
        assertEquals(ErreursLlm.Genre.MODELE_INCONNU, erreur.genre);
        assertTrue(erreur.getMessage().contains("base URL"),
                "un 404 sans mention de modèle guide vers l'URL de "
                        + "base au lieu de blâmer le modèle");
    }

    @Test
    void lesClesOntTroisGenresDistincts() {
        assertEquals(ErreursLlm.Genre.CLE_INVALIDE,
                ErreursLlm.classifier(401, Map.of(), "").genre);
        assertEquals(ErreursLlm.Genre.CLE_SANS_DROIT,
                ErreursLlm.classifier(403, Map.of(), "").genre);
        assertEquals(ErreursLlm.Genre.CLE_MANQUANTE,
                ErreursLlm.cleManquante("anthropic").genre);
    }

    @Test
    void les5xxSeRetentent() {
        for (int statut : new int[]{500, 502, 503, 504, 529}) {
            ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(statut,
                    Map.of(), "upstream");
            assertEquals(ErreursLlm.Genre.ERREUR_SERVEUR, erreur.genre,
                    "statut " + statut);
            assertTrue(erreur.genre.retentable);
        }
    }

    @Test
    void leContexteTropLongSeDetecteAuCodeEtAuWording() {
        assertEquals(ErreursLlm.Genre.CONTEXTE_TROP_LONG,
                ErreursLlm.classifier(413, Map.of(), "").genre);
        assertEquals(ErreursLlm.Genre.CONTEXTE_TROP_LONG,
                ErreursLlm.classifier(400, Map.of(),
                        "prompt is too long: 213772 tokens > 200000 "
                                + "maximum").genre);
    }

    @Test
    void unFluxCoupeNEstJamaisRetente() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(
                new java.io.IOException("unexpected end of stream"),
                true);
        assertEquals(ErreursLlm.Genre.FLUX_INTERROMPU, erreur.genre);
        assertFalse(erreur.genre.retentable,
                "retenter dupliquerait le texte déjà affiché");
    }

    @Test
    void unDelaiAvantPremierOctetSeRetente() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(
                new java.io.IOException("connect timed out"), false);
        assertEquals(ErreursLlm.Genre.DELAI, erreur.genre);
        assertTrue(erreur.genre.retentable);
    }

    @Test
    void unReseauInjoignableSeRetente() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(
                new java.io.IOException(
                        "Failed to connect to localhost"), false);
        assertEquals(ErreursLlm.Genre.RESEAU, erreur.genre);
        assertTrue(erreur.genre.retentable);
    }

    @Test
    void quatorzeGenresExactementEtChacunTranche() {
        assertEquals(14, ErreursLlm.Genre.values().length);
        long retentables = java.util.Arrays.stream(
                        ErreursLlm.Genre.values())
                .filter(g -> g.retentable).count();
        assertEquals(4, retentables,
                "réseau, délai, débit, 5xx — et c'est tout");
    }

    // ---- Révision N/O (lot Q) : les quotas énoncés voyagent ----

    @Test
    void leCasDEcoleGeminiPlafondZeroEstHorsPlan() {
        // ADR-É9 : un 429 gratuit de Gemini annonce un plafond zéro —
        // le modèle n'est pas dans le plan de la clé ; retenter ne
        // sert à rien, CHANGER DE CLÉ oui (la bascule du lot Q
        // tournera sur QUOTA_EPUISE)
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(),
                "Rate limit exceeded. Limit: 0 requests per minute. "
                        + "Please retry in 21s.");
        assertEquals(ErreursLlm.Genre.QUOTA_EPUISE, erreur.genre);
        assertTrue(erreur.getMessage().contains("use another model"),
                "le message dit quoi faire : modèle ou clé");
    }

    @Test
    void un429JetonsParMinutePorteLeQuotaAuRegulateur() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(),
                "Resource has been exhausted (e.g. check quota). "
                        + "Tokens per minute limit: 250000");
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
        assertEquals(QuotaFournisseur.Metrique.JETONS_ENTREE,
                erreur.quota.metrique);
        assertEquals(QuotaFournisseur.Fenetre.MINUTE,
                erreur.quota.fenetre);
        assertEquals(250000, erreur.quota.limite,
                "la limite énoncée devient la borne apprise");
    }

    @Test
    void un429RequetesParMinutePorteLaMetriqueRequetes() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(), "Requests per minute limit: 15 reached");
        assertEquals(QuotaFournisseur.Metrique.REQUETES,
                erreur.quota.metrique);
        assertEquals(15, erreur.quota.limite);
    }

    @Test
    void un429SansWordingClassablePorteUnQuotaNull() {
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(), "slow down");
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
        assertNull(erreur.quota, "pas de wording classable : pas de quota énoncé");
    }

    @Test
    void leWordingFacturationRaffineMaisLe429ResteDebitSansCredit() {
        // la précédence explicite : « quota exceeded » ne gagne que
        // sans « rate » — un 429 rate limit qui cite le crédit reste
        // transitoire
        ErreursLlm.ErreurLlm erreur = ErreursLlm.classifier(429,
                Map.of(),
                "rate limit hit: quota exceeded for requests per minute");
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
        assertTrue(erreur.genre.retentable);
    }
}
