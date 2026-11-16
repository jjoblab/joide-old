package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import jo.joide.platform.CancelToken;

/**
 * La bascule multi-clés à horloge fausse (lot Q) : rotation sur les
 * genres qui peuvent différer par clé, JAMAIS de re-émission après
 * sortie, message agrégé honnête quand tout le banc est assis,
 * attente traversable si la première clé revient sous 120 s, et la
 * régulation préventive qui apprend des erreurs.
 */
class FournisseurBasculeTest {

    /** Un fournisseur factice qui répond ou jette, scénario par clé. */
    private static final class Faux implements FournisseurLlm {
        final List<RuntimeException> script;
        final List<MessageAgent> reponses;
        final List<RequeteLlm> requetesRecues = new ArrayList<>();
        int index;

        Faux(List<RuntimeException> script) {
            this.script = script;
            this.reponses = List.of();
        }

        @Override
        public String nom() {
            return "faux";
        }

        @Override
        public MessageAgent envoyer(TransportHttp transport,
                                    RequeteLlm requete,
                                    CancelToken jeton,
                                    Consumer<EvenementFlux> ecouteur) {
            requetesRecues.add(requete);
            if (index < script.size()) {
                RuntimeException e = script.get(index);
                index++;
                if (e != null) {
                    throw e;
                }
            }
            index++;
            return MessageAgent.assistant("ok");
        }
    }

    /** Un script avec une position sans exception (List.of refuse null). */
    private static List<RuntimeException> script(RuntimeException e) {
        List<RuntimeException> l = new ArrayList<>();
        l.add(e);
        return l;
    }

    private static RequeteLlm requete() {
        return new RequeteLlm("modele-x", "SYS", List.of(), "NOTE",
                List.of(), 128);
    }

    @Test
    void uneCleRateeBasculeVersLaSuivante() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("principale", "secours"),
                horloge);
        Faux principale = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT,
                "rate limited", 30)));
        Faux secours = new Faux(script(null));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> cle.equals("principale") ? principale : secours,
                horloge);
        bascule.veille = millisecondes -> {
        };
        MessageAgent assistant = bascule.envoyer(null, requete(),
                CancelToken.NEVER, null);
        assertEquals("ok", assistant.texte());
        assertEquals(1, principale.requetesRecues.size());
        assertEquals(1, secours.requetesRecues.size(),
                "la secours prend le relais");
        // et la clé principale est au banc : la requête SUIVANTE part
        // directement sur la secours (la principale reviendra après)
        bascule.envoyer(null, requete(), CancelToken.NEVER, null);
        assertEquals(1, principale.requetesRecues.size());
        assertEquals(2, secours.requetesRecues.size());
    }

    @Test
    void ceQuiEchouePareilPartoutNEBasculePas() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a", "b"), horloge);
        Faux fournisseur = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.CONTEXTE_TROP_LONG,
                "context too long", -1)));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> fournisseur, horloge);
        bascule.veille = millisecondes -> {
        };
        ErreursLlm.ErreurLlm erreur = assertThrows(
                ErreursLlm.ErreurLlm.class,
                () -> bascule.envoyer(null, requete(),
                        CancelToken.NEVER, null));
        assertEquals(ErreursLlm.Genre.CONTEXTE_TROP_LONG, erreur.genre);
        assertEquals(1, fournisseur.requetesRecues.size(),
                "la requête ne se re-émet pas : l'erreur est propre");
    }

    @Test
    void lePlafondZeroBasculeVersUneAutreCle() {
        // le cas d'école de l'ADR-É9 : le plafond zéro d'une clé
        // gratuite ne condamne pas le tour — une autre clé peut avoir
        // le modèle dans son plan
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("gratuite", "payante"),
                horloge);
        Faux gratuite = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.QUOTA_EPUISE,
                "quota is 0 for this model on this key (429)", -1)));
        Faux payante = new Faux(script(null));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> cle.equals("gratuite") ? gratuite : payante,
                horloge);
        bascule.veille = millisecondes -> {
        };
        assertEquals("ok", bascule.envoyer(null, requete(),
                CancelToken.NEVER, null).texte());
    }

    @Test
    void toutesClesAuBancLeMessageEstAggrege() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a", "b"), horloge);
        Faux a = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_JOUR, "daily limit", -1)));
        Faux b = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.CLE_INVALIDE, "invalid key", -1)));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> cle.equals("a") ? a : b, horloge);
        bascule.veille = millisecondes -> {
        };
        ErreursLlm.ErreurLlm erreur = assertThrows(
                ErreursLlm.ErreurLlm.class,
                () -> bascule.envoyer(null, requete(),
                        CancelToken.NEVER, null));
        assertTrue(erreur.getMessage().contains("all 2 API keys"),
                erreur.getMessage());
        assertTrue(erreur.getMessage().contains("1 at the daily limit"),
                "le compte par raison : " + erreur.getMessage());
        assertTrue(erreur.getMessage().contains("1 rejected"),
                "le compte par raison : " + erreur.getMessage());
        assertTrue(erreur.getMessage().contains("Add another key"),
                "le message conseille quoi faire");
    }

    @Test
    void uneAttenteCourteDevientUneLimiteDebitAttendue() {
        // deux clés limitées à la minute : les deux au banc, la
        // première revient sous 120 s — la boucle attendra
        // VISIBLEMENT au lieu d'échouer le tour
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a", "b"), horloge);
        Faux a = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT, "rate limited a", 45)));
        Faux b = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT, "rate limited b", 30)));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> cle.equals("a") ? a : b, horloge);
        bascule.veille = millisecondes -> {
        };
        ErreursLlm.ErreurLlm erreur = assertThrows(
                ErreursLlm.ErreurLlm.class,
                () -> bascule.envoyer(null, requete(),
                        CancelToken.NEVER, null));
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre,
                "l'attente courte se traverse, pas d'échec de tour");
        assertEquals(30, erreur.delaiSecondes,
                "le délai de la PREMIÈRE clé qui revient : "
                        + erreur.getMessage());
        assertTrue(erreur.getMessage().contains("all 2 API keys"),
                erreur.getMessage());
        assertTrue(erreur.getMessage().contains("Try again in 30s"),
                erreur.getMessage());
    }

    @Test
    void uneCleSeuleEstUnPassThroughHonnete() {
        // un banc d'une clé ne s'agrège pas : l'erreur du fournisseur
        // part telle quelle (le design de l'ADR-É9)
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a"), horloge);
        Faux a = new Faux(List.of(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT, "rate limited", 45)));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> a, horloge);
        bascule.veille = millisecondes -> {
        };
        ErreursLlm.ErreurLlm erreur = assertThrows(
                ErreursLlm.ErreurLlm.class,
                () -> bascule.envoyer(null, requete(),
                        CancelToken.NEVER, null));
        assertEquals("rate limited", erreur.getMessage());
        assertEquals(45, erreur.delaiSecondes);
    }

    @Test
    void laRegulationPreventiveEnregistreEtApprend() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a", "b"), horloge);
        // la clé a apprend une limite énoncée de 10 requêtes/minute
        List<RuntimeException> scriptA = new ArrayList<>();
        scriptA.add(new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT,
                "Requests per minute limit: 10", 30,
                QuotaFournisseur.raffiner(
                        "Requests per minute limit: 10")));
        scriptA.add(null);
        Faux a = new Faux(scriptA);
        Faux b = new Faux(script(null));
        FournisseurBascule bascule = new FournisseurBascule(banc,
                cle -> cle.equals("a") ? a : b, horloge);
        bascule.veille = millisecondes -> {
        };
        bascule.envoyer(null, requete(), CancelToken.NEVER, null);
        // la requête suivante va sur a qui est au banc 30 s, puis b ;
        // après la fin du banc de a, SA borne apprise tient : 10
        // requêtes dans la fenêtre font attendre
        horloge.avancer(30_001);
        RegulateurDebit regulateur = bascule
                .regulateur("a", "modele-x");
        assertEquals(10, regulateur.rpm(),
                "la limite énoncée est apprise pour ce couple "
                        + "clé+modèle et survit aux tours");
        for (int k = 0; k < 10; k++) {
            regulateur.enregistrer(50);
        }
        assertTrue(regulateur.delaiPour(50) > 0,
                "la fenêtre apprise est en vigueur");
    }
}
