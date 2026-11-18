package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.List;

/**
 * Lot L2 de l'atelier v2 (ADR-083) — le modèle PUR du fil d'Ariane
 * (§3.3 du prompt v2) : segments texte2 séparés par « › », le DERNIER
 * segment (le symbole sous le curseur) en couleur fonction. La vue
 * applique les couleurs ; le découpage se teste en JVM.
 */
public final class FormateurFilAriane {

    /** Un segment de fil : son texte et sa couleur de rôle. */
    public record Segment(String texte, boolean symbole) {
    }

    /** Le séparateur entre segments. */
    public static final String SEPARATEUR = "›";

    /** Construit les segments : module › paquets › classe › symbole.
     *  Les éléments nuls ou vides sont omis ; SANS symbole, le dernier
     *  segment (la classe) reste en couleur de segment normal — la
     *  couleur fonction ne s'applique qu'au symbole du curseur. */
    public static List<Segment> segments(String module, String paquet,
                                         String classe, String symbole) {
        List<Segment> segments = new ArrayList<>();
        if (nonVide(module)) {
            segments.add(new Segment(module, false));
        }
        if (nonVide(paquet)) {
            for (String partie : paquet.split("\\.")) {
                if (nonVide(partie)) {
                    segments.add(new Segment(partie, false));
                }
            }
        }
        if (nonVide(classe)) {
            segments.add(new Segment(classe, false));
        }
        if (nonVide(symbole)) {
            segments.add(new Segment(symbole, true));
        }
        return segments;
    }

    /** Les segments DEPUIS un chemin de source : «
     *  app/src/main/java/jo/compta/Facture.java » → module « app »,
     *  paquet « jo.compta », classe « Facture ». Le module = le
     *  premier segment du chemin ; le paquet = ce qui suit
     *  src/main/java (ou src/main/kotlin) ; la classe = le nom de
     *  fichier sans extension. Hors source-set standard : le paquet
     *  est vide, la classe reste le nom de fichier. */
    public static List<Segment> segmentsDepuisChemin(String chemin,
                                                     String symbole) {
        if (chemin == null || chemin.isBlank()) {
            return List.of();
        }
        String[] parties = chemin.split("/");
        String module = parties.length > 0 ? parties[0] : "";
        String paquet = "";
        String classe = parties[parties.length - 1];
        int point = classe.lastIndexOf('.');
        if (point > 0) {
            classe = classe.substring(0, point);
        }
        StringBuilder paquets = new StringBuilder();
        boolean dansPaquet = false;
        for (int i = 1; i < parties.length - 1; i++) {
            String partie = parties[i];
            if (dansPaquet) {
                if (paquets.length() > 0) {
                    paquets.append('.');
                }
                paquets.append(partie);
                continue;
            }
            if (partie.equals("java") || partie.equals("kotlin")) {
                if (i >= 2 && parties[i - 1].equals("main")) {
                    dansPaquet = true;
                }
            }
        }
        paquet = paquets.toString();
        return segments(module, paquet, classe, symbole);
    }

    private static boolean nonVide(String texte) {
        return texte != null && !texte.isBlank();
    }
}
