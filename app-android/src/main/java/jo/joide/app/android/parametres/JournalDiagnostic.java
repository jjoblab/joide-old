package jo.joide.app.android.parametres;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Le journal de diagnostic (lot M, ADR-É8) : les lignes de logcat du
 * PROPRE compte de l'application (depuis Android 4.1, un processus
 * sans {@code READ_LOGS} ne voit que ses propres lignes — le service
 * « :build » partage l'UID). Classe PURE pour le bornage et le
 * nettoyage ; l'exécution de {@code logcat} vit dans l'écran (elle ne
 * se teste pas en JVM).
 *
 * <p>La franchise s'applique : un journal vide est un journal vide
 * (affiché tel quel), une commande qui échoue est une erreur nommée —
 * jamais un faux journal silencieux.</p>
 */
public final class JournalDiagnostic {

    /** Plafond de lignes conservées (les plus récentes). */
    public static final int PLAFOND_LIGNES = 400;

    /** Plafond de caractères par ligne (une pile d'exception reste lisible). */
    public static final int PLAFOND_LIGNE = 2000;

    private JournalDiagnostic() {
    }

    /**
     * Lit le journal brut du processus.
     *
     * @param commande la commande exécutée (injected pour l'écran)
     * @return le texte brut du journal
     * @throws IOException si la commande échoue ou est indisponible
     */
    public static String lire(Commande commande) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        try (InputStream flux = commande.demarrer()) {
            int lu;
            while ((lu = flux.read(tampon)) >= 0) {
                sortie.write(tampon, 0, lu);
            }
        }
        return new String(sortie.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Garde les {@link #PLAFOND_LIGNES} dernières lignes NON VIDES,
     * chacune bornée — un journal honnête dit ses plafonds, il ne
     * tronque pas en silence.
     *
     * @param brut le journal brut
     * @return les lignes conservées, dans l'ordre
     */
    public static List<String> borner(String brut) {
        if (brut == null || brut.isEmpty()) {
            return List.of();
        }
        List<String> gardees = new ArrayList<>();
        for (String ligne : brut.split("\n", -1)) {
            String nette = ligne.trim();
            if (!nette.isEmpty()) {
                gardees.add(nette);
            }
        }
        int debut = Math.max(0, gardees.size() - PLAFOND_LIGNES);
        List<String> dernieres = new ArrayList<>(
                gardees.subList(debut, gardees.size()));
        for (int i = 0; i < dernieres.size(); i++) {
            String ligne = dernieres.get(i);
            if (ligne.length() > PLAFOND_LIGNE) {
                dernieres.set(i,
                        ligne.substring(0, PLAFOND_LIGNE) + "…");
            }
        }
        return dernieres;
    }

    /** Une commande externe dont seule l'exécution importe (l'écran fournit logcat). */
    public interface Commande {
        /**
         * @return le flux de sortie de la commande
         * @throws IOException si la commande ne part pas
         */
        InputStream demarrer() throws IOException;
    }
}
