package jo.joide.agent.espace;

/**
 * Le confinement PAR CONSTRUCTION (ADR-É9, risque n° 2 : aucun
 * confinement chez l'IDE de référence — le mode auto-accept pouvait sortir
 * du projet). Tout chemin d'outil passe ICI avant d'atteindre le
 * port : relatif, sans « .. », sans racine, sans séparateur
 * retour, borné en longueur — un chemin refusé ne touche JAMAIS le
 * disque.
 */
public final class CheminsConfines {

    /** La longueur maximale d'un chemin d'outil. */
    public static final int LONGUEUR_MAX = 300;

    private CheminsConfines() {
    }

    /**
     * Valide et normalise un chemin d'outil.
     *
     * @param chemin le chemin demandé par le modèle
     * @return le chemin normalisé (séparateurs « / », sans doublons)
     * @throws IllegalArgumentException avec la RAISON si le chemin
     *                                  sort du projet ou est mal formé
     */
    public static String confiner(String chemin) {
        if (chemin == null || chemin.isEmpty()) {
            throw new IllegalArgumentException(
                    "path must not be empty");
        }
        if (chemin.length() > LONGUEUR_MAX) {
            throw new IllegalArgumentException("path too long");
        }
        if (chemin.contains("\\") || chemin.contains(":")
                || chemin.startsWith("/") || chemin.startsWith("~")) {
            throw new IllegalArgumentException(
                    "path must be relative to the project root, "
                            + "got: " + chemin);
        }
        StringBuilder normal = new StringBuilder(chemin.length());
        for (String segment : chemin.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException(
                        "path must stay inside the project, "
                                + "no '..': " + chemin);
            }
            if (normal.length() > 0) {
                normal.append('/');
            }
            normal.append(segment);
        }
        if (normal.length() == 0) {
            throw new IllegalArgumentException(
                    "path must not be empty");
        }
        for (int i = 0; i < normal.length(); i++) {
            char c = normal.charAt(i);
            if (c < 0x20) {
                throw new IllegalArgumentException(
                        "path contains control characters");
            }
        }
        return normal.toString();
    }
}
