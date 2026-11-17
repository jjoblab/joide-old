package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Le banc de clés à horloge fausse (lot Q, critère : « tests à
 * horloge fausse (failover, pacer) ») : les bancs différenciés par
 * nature de limite, le retour de la clé principale dès qu'elle
 * guérit, le message agrégé par raison.
 */
class BancClesTest {

    @Test
    void laClePrincipaleRevientDesQuElleGuerit() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(
                List.of("principale", "secours"), horloge);
        banc.asseoir(0, ErreursLlm.Genre.LIMITE_DEBIT, 20_000);
        assertEquals(1, banc.prochaine(),
                "la secours prend le relais");
        horloge.avancer(20_001);
        assertEquals(0, banc.prochaine(),
                "la principale revient en première ligne : "
                        + "failover, pas round-robin");
    }

    @Test
    void lesBancsSontDifferenciesParNature() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a", "b", "c", "d"),
                horloge);
        banc.asseoir(0, ErreursLlm.Genre.LIMITE_DEBIT, -1);
        banc.asseoir(1, ErreursLlm.Genre.LIMITE_JOUR, -1);
        banc.asseoir(2, ErreursLlm.Genre.CLE_INVALIDE, -1);
        banc.asseoir(3, ErreursLlm.Genre.QUOTA_EPUISE, -1);
        // limite à la minute sans énoncé : 60 s
        assertEquals(BancCles.ASSISE_MINUTE_MS,
                banc.delaiProchaine());
        assertEquals(ErreursLlm.Genre.LIMITE_DEBIT,
                banc.raisonProchaine());
        // une heure plus tard : la minute a expiré aussi — une clé
        // revient, JAMAIS l'auth
        horloge.avancer(BancCles.ASSISE_JOUR_MS + 1);
        int libre = banc.prochaine();
        assertTrue(libre != 2,
                "une clé rejetée ne revient jamais seule : " + libre);
        // on assied tout sauf l'auth : le banc reste fermé quand même
        for (int k = 0; k < banc.taille(); k++) {
            if (k != 2) {
                banc.asseoir(k, ErreursLlm.Genre.LIMITE_JOUR, -1);
            }
        }
        assertEquals(-1, banc.prochaine(),
                "l'auth à vie ferme le banc : il ne se libère que "
                        + "par édition des clés");
    }

    @Test
    void leDelaiEnoncePrimePourUneLimiteMinute() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(List.of("a"), horloge);
        banc.asseoir(0, ErreursLlm.Genre.LIMITE_DEBIT, 12_000);
        assertEquals(12_000, banc.delaiProchaine(),
                "le délai énoncé par le fournisseur est la borne");
        banc.asseoir(0, ErreursLlm.Genre.LIMITE_DEBIT, 400);
        assertEquals(BancCles.ASSISE_MINIMUM_MS,
                banc.delaiProchaine(),
                "jamais moins d'une seconde : retenter tout de suite "
                        + "ne sert à rien");
    }

    @Test
    void leMessageAggregeCompteParRaison() {
        HorlogeFausse horloge = new HorlogeFausse();
        BancCles banc = new BancCles(
                List.of("a", "b", "c", "d"), horloge);
        banc.asseoir(0, ErreursLlm.Genre.LIMITE_DEBIT, 5_000);
        banc.asseoir(1, ErreursLlm.Genre.LIMITE_JOUR, -1);
        banc.asseoir(2, ErreursLlm.Genre.LIMITE_JOUR, -1);
        banc.asseoir(3, ErreursLlm.Genre.CLE_INVALIDE, -1);
        Map<ErreursLlm.Genre, Integer> parRaison = banc.bancParRaison();
        assertEquals(1, parRaison.get(ErreursLlm.Genre.LIMITE_DEBIT));
        assertEquals(2, parRaison.get(ErreursLlm.Genre.LIMITE_JOUR));
        assertEquals(1, parRaison.get(ErreursLlm.Genre.CLE_INVALIDE));
    }

    @Test
    void lesGenresQuiBasculentSontCeuxQuiPeuventDiffererParCle() {
        assertTrue(BancCles.bascule(ErreursLlm.Genre.LIMITE_DEBIT));
        assertTrue(BancCles.bascule(ErreursLlm.Genre.LIMITE_JOUR));
        assertTrue(BancCles.bascule(ErreursLlm.Genre.QUOTA_EPUISE));
        assertTrue(BancCles.bascule(ErreursLlm.Genre.CLE_INVALIDE));
        assertTrue(BancCles.bascule(ErreursLlm.Genre.CLE_SANS_DROIT));
        assertFalse(BancCles.bascule(ErreursLlm.Genre.CONTEXTE_TROP_LONG),
                "échouerait pareil sur toutes les clés");
        assertFalse(BancCles.bascule(ErreursLlm.Genre.RESEAU),
                "échouerait pareil sur toutes les clés");
        assertFalse(BancCles.bascule(ErreursLlm.Genre.MODELE_INCONNU));
    }

    @Test
    void lesClesVidesEtDoublonsSontEcarts() {
        BancCles banc = new BancCles(
                List.of("  a ", "", "a", "b"), null);
        assertEquals(2, banc.taille());
        assertEquals(List.of("a", "b"), banc.cles);
    }
}
