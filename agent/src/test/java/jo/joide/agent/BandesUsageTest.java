package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Les bandes d'alerte de l'usage (lot Q) : trois bandes sur
 * l'occupation de la fenêtre du régulateur — l'alerte prévient avant
 * le 429, sans plafond connu il n'y a jamais d'alerte inventée.
 */
class BandesUsageTest {

    @Test
    void lesSeuilsTranchentLesTroisBandes() {
        assertEquals(BandesUsage.Bande.NORMALE,
                BandesUsage.bandeDe(0));
        assertEquals(BandesUsage.Bande.NORMALE,
                BandesUsage.bandeDe(0.69));
        assertEquals(BandesUsage.Bande.ATTENTION,
                BandesUsage.bandeDe(0.70));
        assertEquals(BandesUsage.Bande.ATTENTION,
                BandesUsage.bandeDe(0.89));
        assertEquals(BandesUsage.Bande.CRITIQUE,
                BandesUsage.bandeDe(0.90));
        assertEquals(BandesUsage.Bande.CRITIQUE,
                BandesUsage.bandeDe(1.0));
    }

    @Test
    void sansPlafondConnuLaBandeEstToujoursNormale() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(null, null,
                horloge);
        assertEquals(BandesUsage.Bande.NORMALE,
                BandesUsage.bandeRequetes(regulateur),
                "une alerte sans référence mesurée serait une "
                        + "invention");
        assertEquals(BandesUsage.Bande.NORMALE,
                BandesUsage.bandeJetons(regulateur, 50_000));
    }

    @Test
    void laBandeSuitLOccupationReelleDuCoupleCleModele() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(10, 1_000L,
                horloge);
        assertEquals(BandesUsage.Bande.NORMALE,
                BandesUsage.bandeRequetes(regulateur));
        for (int k = 0; k < 8; k++) {
            regulateur.enregistrer(100);
        }
        assertEquals(BandesUsage.Bande.ATTENTION,
                BandesUsage.bandeRequetes(regulateur),
                "8 requêtes sur 10");
        assertEquals(BandesUsage.Bande.ATTENTION,
                BandesUsage.bandeJetons(regulateur, 10),
                "800 + 10 jetons sur 1 000");
        assertEquals(BandesUsage.Bande.CRITIQUE,
                BandesUsage.bandeJetons(regulateur, 120),
                "800 + 120 jetons sur 1 000 : la requête à venir "
                        + "compte, prévenir AVANT");
    }
}
