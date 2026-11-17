package jo.joide.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Le parseur SSE maison (ADR-É9 : le transport unique rend la couche
 * testable ; les trames se rejouent en tests). Grammaire stricte du
 * format « text/event-stream » : une trame est un ensemble de
 * lignes terminé par une ligne vide ; {@code event:} nomme
 * l'événement, les {@code data:} concatènent avec un saut de ligne,
 * les lignes {@code :} sont des commentaires de battement de cœur
 * (ignorées, jamais une erreur), un {@code BOM} d'entête est toléré.
 */
public final class ParseurSse {

    private ParseurSse() {
    }

    /**
     * Lit toutes les trames d'un flux.
     *
     * @param lecteur le lecteur du corps de réponse
     * @return les trames dans l'ordre
     * @throws IOException si la lecture échoue
     */
    public static List<TransportHttp.TrameSse> trames(Reader lecteur)
            throws IOException {
        List<TransportHttp.TrameSse> trames = new ArrayList<>();
        try (BufferedReader tampon = lecteur instanceof BufferedReader b
                ? b : new BufferedReader(lecteur)) {
            String evenement = null;
            StringBuilder donnees = new StringBuilder();
            boolean vide = true;
            String ligne;
            while ((ligne = tampon.readLine()) != null) {
                if (ligne.isEmpty()) {
                    if (!vide) {
                        trames.add(new TransportHttp.TrameSse(evenement,
                                donnees.toString()));
                        evenement = null;
                        donnees.setLength(0);
                        vide = true;
                    }
                    continue;
                }
                if (ligne.startsWith(":")) {
                    // battement de cœur : garder le flux vivant —
                    // une trame de commentaires seuls reste VIDE
                    continue;
                }
                vide = false;
                int deuxPoints = ligne.indexOf(':');
                String champ = deuxPoints < 0 ? ligne
                        : ligne.substring(0, deuxPoints);
                String valeur = deuxPoints < 0 ? ""
                        : ligne.substring(deuxPoints + 1);
                if (valeur.startsWith(" ")) {
                    valeur = valeur.substring(1);
                }
                if ("event".equals(champ)) {
                    evenement = valeur;
                } else if ("data".equals(champ)) {
                    if (donnees.length() > 0) {
                        donnees.append('\n');
                    }
                    donnees.append(valeur);
                }
                // id:/retry: hors périmètre — jamais une erreur
            }
            if (!vide) {
                // un flux qui finit sans ligne vide livre sa dernière
                // trame telle quelle — c'est l'ADAPTATEUR qui juge la
                // complétude (événement de fin manquant = flux
                // interrompu), jamais le parseur
                trames.add(new TransportHttp.TrameSse(evenement,
                        donnees.toString()));
            }
        }
        return trames;
    }
}
