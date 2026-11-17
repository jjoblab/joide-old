package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import jo.joide.agent.DiffMyers.Resultat;

/**
 * Le diff Myers borné éprouvé (lot P, ADR-É11) : la reconstruction
 * — appliquer le diff unifié à AVANT doit redonner APRÈS octet pour
 * octet — plus les bornes et le repli honnête. Le fuzz aléatoire
 * couvre les cas que la main n'écrit pas.
 */
class DiffMyersTest {

    @Test
    void textesIdentiquesAucuneDifference() {
        Resultat r = DiffMyers.unifie("a\nb\nc\n", "a\nb\nc\n");
        assertFalse(r.tronque);
        assertEquals(0, r.lignesChangees);
        assertTrue(r.texte.contains("aucune diff"));
    }

    @Test
    void creationDepuisRien() {
        Resultat r = DiffMyers.unifie("", "un\ndeux\n");
        assertEquals(2, r.lignesChangees);
        assertEquals(List.of("+un", "+deux"), lignesDeDiff(r));
        assertEquals("un\ndeux\n", reappliquer("", r.texte));
    }

    @Test
    void suppressionTotale() {
        Resultat r = DiffMyers.unifie("un\ndeux\n", "");
        assertEquals(2, r.lignesChangees);
        assertEquals(List.of("-un", "-deux"), lignesDeDiff(r));
        assertEquals("", reappliquer("un\ndeux\n", r.texte));
    }

    @Test
    void modificationUneLigne() {
        String avant = "ligne1\nligne2\nligne3\n";
        String apres = "ligne1\nligne2 modifiee\nligne3\n";
        Resultat r = DiffMyers.unifie(avant, apres);
        assertFalse(r.tronque);
        assertEquals(2, r.lignesChangees);
        List<String> l = lignesDeDiff(r);
        assertTrue(l.contains("-ligne2"));
        assertTrue(l.contains("+ligne2 modifiee"));
        // deux lignes de contexte autour
        assertTrue(l.contains(" ligne1"));
        assertTrue(l.contains(" ligne3"));
        assertEquals(apres, reappliquer(avant, r.texte));
    }

    @Test
    void deuxHunksSepares() {
        StringBuilder avant = new StringBuilder();
        StringBuilder apres = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            avant.append("ligne").append(i).append('\n');
            apres.append("ligne").append(i).append('\n');
        }
        apres.replace(0, 6 + 1, "LIGNE1\n");
        apres.replace(apres.indexOf("ligne25"),
                apres.indexOf("ligne25") + 7, "LIGNE25");
        Resultat r = DiffMyers.unifie(avant.toString(),
                apres.toString());
        assertFalse(r.tronque);
        // deux fenêtres : deux LIGNES d'entête @@
        assertEquals(2, r.texte.split("\n@@", -1).length - 1
                + (r.texte.startsWith("@@") ? 1 : 0));
        assertEquals(apres.toString(),
                reappliquer(avant.toString(), r.texte));
    }

    @Test
    void numerosDeLigneDesEntetes() {
        String avant = "a\nb\nc\nd\ne\nf\ng\nh\n";
        String apres = "a\nb\nc\nD\ne\nf\ng\nh\n";
        Resultat r = DiffMyers.unifie(avant, apres);
        assertTrue(r.texte.contains("@@ -2 +2 @@"),
                "entete attendu @@ -2 +2 @@, recu: " + r.texte);
    }

    @Test
    void bornesUnDiffInfiniEstAnnonce() {
        StringBuilder avant = new StringBuilder();
        StringBuilder apres = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            avant.append("avant").append(i).append('\n');
            apres.append("apres").append(i).append('\n');
        }
        Resultat r = DiffMyers.unifie(avant.toString(),
                apres.toString());
        assertTrue(r.tronque);
        assertTrue(r.texte.contains("diff born"),
                "l'amputation doit s'annoncer");
        assertTrue(r.lignesChangees >= DiffMyers.PLAFOND_LIGNES);
        // la sortie respecte ses bornes
        int lignes = r.texte.split("\n", -1).length;
        assertTrue(lignes <= DiffMyers.PLAFOND_LIGNES + 2,
                "le diff emis depasse son plafond: " + lignes);
        assertTrue(r.texte.length()
                <= DiffMyers.PLAFOND_CARACTERES + 200);
    }

    @Test
    void grosFichierPetiteModificationPresDeLaFin() {
        StringBuilder avant = new StringBuilder();
        StringBuilder apres = new StringBuilder();
        for (int i = 0; i < 3_000; i++) {
            String ligne = "ligne numero " + i + " du fichier\n";
            avant.append(ligne);
            apres.append(i == 2_990
                    ? "ligne changee a la fin\n" : ligne);
        }
        Resultat r = DiffMyers.unifie(avant.toString(),
                apres.toString());
        assertFalse(r.tronque, "une petite modification loin dans un "
                + "gros fichier doit produire un petit diff (coupe "
                + "commune)");
        assertTrue(r.lignesChangees <= 2);
        assertEquals(apres.toString(),
                reappliquer(avant.toString(), r.texte));
    }

    @Test
    void repliHonneFichierTropRemanie() {
        // un milieu de plus de 3200 lignes par cote : le repli honnete
        StringBuilder avant = new StringBuilder();
        StringBuilder apres = new StringBuilder();
        for (int i = 0; i < 4_000; i++) {
            avant.append("a").append(i).append('\n');
            apres.append("b").append(i).append('\n');
        }
        Resultat r = DiffMyers.unifie(avant.toString(),
                apres.toString());
        assertTrue(r.tronque);
        assertTrue(r.texte.contains("trop remani"),
                "le repli doit se nommer: " + r.texte);
    }

    @Test
    void fuzzLaReconstructionRedonneApres() {
        Random alea = new Random(20261116);
        for (int essai = 0; essai < 200; essai++) {
            int taille = 1 + alea.nextInt(120);
            List<String> lignes = new ArrayList<>();
            for (int i = 0; i < taille; i++) {
                lignes.add("l" + alea.nextInt(20));
            }
            StringBuilder avant = new StringBuilder();
            for (String l : lignes) {
                avant.append(l).append('\n');
            }
            // edite : supprime, insere, remplace
            for (int e = 0; e < 1 + alea.nextInt(8); e++) {
                int type = alea.nextInt(3);
                if (lignes.isEmpty()) {
                    type = 1;
                }
                int position = lignes.isEmpty() ? 0
                        : alea.nextInt(lignes.size());
                if (type == 0) {
                    lignes.remove(position);
                } else if (type == 1) {
                    lignes.add(position, "n" + alea.nextInt(20));
                } else {
                    lignes.set(position, "r" + alea.nextInt(20));
                }
            }
            StringBuilder apres = new StringBuilder();
            for (String l : lignes) {
                apres.append(l).append('\n');
            }
            Resultat r = DiffMyers.unifie(avant.toString(),
                    apres.toString());
            assertFalse(r.tronque, "un petit fuzz ne tronque pas");
            assertEquals(apres.toString(),
                    reappliquer(avant.toString(), r.texte),
                    "essai " + essai + " — avant=["
                            + avant + "] apres=[" + apres + "]");
        }
    }

    // ------------------------------------------------------------------
    // Les outils du test : lire et réappliquer un diff unifié
    // ------------------------------------------------------------------

    /** Les lignes de diff (sans les entêtes @@ ni la borne). */
    private static List<String> lignesDeDiff(Resultat r) {
        List<String> l = new ArrayList<>();
        for (String ligne : r.texte.split("\n")) {
            if (ligne.startsWith("@@") || ligne.startsWith("[")
                    || ligne.isEmpty()) {
                continue;
            }
            l.add(ligne);
        }
        return l;
    }

    /**
     * Réapplique le diff unifié à AVANT — la reconstruction doit
     * redonner APRÈS (le contrat du test).
     */
    private static String reappliquer(String avant, String diff) {
        String[] lignesAvant = avant.split("\n", -1);
        // ignore la derniere entree vide d'un texte fini par \n
        int tailleAvant = avant.isEmpty() ? 0 : lignesAvant.length;
        if (avant.endsWith("\n")) {
            tailleAvant--;
        }
        StringBuilder resultat = new StringBuilder();
        int i = 0;
        boolean premier = true;
        for (String ligne : diff.split("\n")) {
            if (ligne.startsWith("@@")) {
                // l'entete « @@ -A +C @@ » : A = ligne AVANT du bloc
                // (c'est elle qui positionne la reconstruction)
                int tiret = ligne.indexOf('-');
                int plus = ligne.indexOf('+');
                int debut = Integer.parseInt(ligne
                        .substring(tiret + 1, plus).trim());
                while (i < debut - 1 && i < tailleAvant) {
                    if (!premier) {
                        resultat.append('\n');
                    }
                    resultat.append(lignesAvant[i]);
                    premier = false;
                    i++;
                }
            } else if (ligne.startsWith("-")) {
                i++;
            } else if (ligne.startsWith("+")) {
                if (!premier) {
                    resultat.append('\n');
                }
                resultat.append(ligne.substring(1));
                premier = false;
            } else if (ligne.startsWith(" ")
                    && !ligne.startsWith("[") && !ligne.isEmpty()) {
                if (!premier) {
                    resultat.append('\n');
                }
                resultat.append(ligne.substring(1));
                premier = false;
                i++;
            } else if (ligne.startsWith("[")) {
                break;
            }
        }
        while (i < tailleAvant) {
            if (!premier) {
                resultat.append('\n');
            }
            resultat.append(lignesAvant[i]);
            premier = false;
            i++;
        }
        if (resultat.length() > 0) {
            resultat.append('\n');
        }
        return resultat.toString();
    }
}
