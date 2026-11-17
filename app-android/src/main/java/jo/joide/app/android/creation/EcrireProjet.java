package jo.joide.app.android.creation;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * L'écriture atomique d'un projet créé (ADR-É4 : « tout ou rien ») :
 * l'arborescence COMPLÈTE est écrite dans un répertoire temporaire
 * voisin, puis déplacée atomiquement vers son nom final — un projet
 * à moitié créé n'existe jamais. En cas d'échec, le temporaire est
 * effacé et l'erreur remonte nominale ; le répertoire final, s'il
 * existe déjà, est refusé (l'identifiant unique est la règle).
 *
 * <p>Classe PURE (java.nio, aucun type Android) : testée JVM avec
 * un répertoire temporaire, y compris l'échec d'écriture au milieu
 * de l'arborescence.</p>
 */
public final class EcrireProjet {

    private EcrireProjet() {
    }

    /** Le résultat d'une écriture : le chemin créé ou l'erreur. */
    public record Resultat(Path chemin, String erreur) {

        /** @return vrai si le projet a été écrit */
        public boolean ecrit() {
            return chemin != null;
        }
    }

    /**
     * Écrit l'arborescence générée, tout ou rien (aucun répertoire
     * déclaré en plus).
     *
     * @param dossierProjets le répertoire racine des projets
     *        (filesDir/projets)
     * @param id l'identifiant du dossier final (unique, déjà vérifié)
     * @param fichiers l'arborescence complète produite par
     *        {@link GenerateurProjet}
     * @return le chemin du projet créé, ou l'erreur nominale
     */
    public static Resultat ecrire(Path dossierProjets, String id,
                                  List<GenerateurProjet.FichierGenere> fichiers) {
        return ecrire(dossierProjets, id, fichiers, List.of());
    }

    /**
     * Écrit l'arborescence générée, tout ou rien — fichiers ET
     * répertoires déclarés : les racines sources sans fichier (p.
     * ex. {@code core/src/main/kotlin} du modèle application-android)
     * existent dès la création, le build ne dépend jamais de la
     * tolérance du moteur (lot IX, rapport du 2026-11-17).
     *
     * @param dossierProjets le répertoire racine des projets
     *        (filesDir/projets)
     * @param id l'identifiant du dossier final (unique, déjà vérifié)
     * @param fichiers l'arborescence complète produite par
     *        {@link GenerateurProjet}
     * @param dossiers les répertoires déclarés à créer (chemins
     *        relatifs au projet, fournis par
     *        {@link GenerateurProjet.Generation#dossiers()})
     * @return le chemin du projet créé, ou l'erreur nominale
     */
    public static Resultat ecrire(Path dossierProjets, String id,
                                  List<GenerateurProjet.FichierGenere> fichiers,
                                  List<String> dossiers) {
        Objects.requireNonNull(dossierProjets, "dossierProjets");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fichiers, "fichiers");
        Objects.requireNonNull(dossiers, "dossiers");
        Path final_ = dossierProjets.resolve(id);
        if (Files.exists(final_)) {
            return new Resultat(null,
                    "le dossier existe deja : " + id);
        }
        Path temporaire = dossierProjets.resolve(
                ".tmp-creation-" + id);
        try {
            supprimerRecursif(temporaire);
            for (GenerateurProjet.FichierGenere fichier : fichiers) {
                if (fichier.chemin() == null
                        || fichier.chemin().isBlank()) {
                    return new Resultat(null, "chemin de fichier vide");
                }
                Path cible = temporaire.resolve(fichier.chemin())
                        .normalize();
                if (!cible.startsWith(temporaire)) {
                    return new Resultat(null,
                            "chemin hors du projet : "
                                    + fichier.chemin());
                }
                Files.createDirectories(cible.getParent());
                // Files.writeString exige le niveau 33 : les octets
                // suffisent (Files.write est niveau 26)
                Files.write(cible, fichier.contenu().getBytes(
                        StandardCharsets.UTF_8));
            }
            for (String dossier : dossiers) {
                if (dossier == null || dossier.isBlank()) {
                    return new Resultat(null, "chemin de dossier vide");
                }
                Path cible = temporaire.resolve(dossier).normalize();
                if (!cible.startsWith(temporaire)) {
                    return new Resultat(null,
                            "chemin hors du projet : " + dossier);
                }
                Files.createDirectories(cible);
            }
            Files.createDirectories(dossierProjets);
            deplacementAtomique(temporaire, final_);
            return new Resultat(final_, null);
        } catch (IOException e) {
            // tout ou rien : le temporaire part, le final n'existe pas
            try {
                supprimerRecursif(temporaire);
            } catch (IOException nettoyage) {
                // le temporaire résiduel porte le préfixe .tmp-creation
                // : il ne se confond jamais avec un projet
            }
            return new Resultat(null, e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static void deplacementAtomique(Path source, Path cible)
            throws IOException {
        try {
            Files.move(source, cible,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // même système de fichiers : le move ordinaire reste sûr
            // (le final n'existe pas, la source est complète)
            Files.move(source, cible);
        }
    }

    private static void supprimerRecursif(Path racine) throws IOException {
        if (!Files.exists(racine)) {
            return;
        }
        try (var parcours = Files.walk(racine)) {
            parcours.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // suppression au mieux
                        }
                    });
        }
    }
}
