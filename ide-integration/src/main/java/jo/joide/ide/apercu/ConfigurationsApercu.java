package jo.joide.ide.apercu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jo.codeide.lsp.model.ModuleLsp;
import jo.codeide.lsp.model.ProjetLsp;
import jo.joide.ide.sync.LanguageModelSnapshot;

/**
 * Constitution d'une configuration d'aperçu DEPUIS le modèle de
 * langage : le premier module Android actif portant des dossiers
 * {@code res/} fournit le dossier principal et les contenus de ses
 * fichiers de thèmes/styles/attributs (lus du disque — les chemins du
 * modèle sont absolus).
 *
 * <p>Le nom du thème n'est PAS devinu : la configuration sort avec un
 * thème null (repli hôte de la brique, jamais un échec) — l'hôte le
 * pose par {@link ConfigurationApercu#avecTheme(String)} quand il le
 * connaît (manifest, réglage utilisateur).</p>
 */
public final class ConfigurationsApercu {

    private ConfigurationsApercu() {
    }

    /**
     * La configuration de l'aperçu d'un instantané du modèle.
     *
     * @param snapshot l'instantané du modèle de langage (lot B)
     * @return la configuration constituée (vide si aucun module
     *         Android ne porte de ressources)
     */
    public static ConfigurationApercu depuisSnapshot(
            LanguageModelSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        ProjetLsp projet = snapshot.raw();
        List<String> avertissements = new ArrayList<>();
        for (ModuleLsp module : projet.getModules()) {
            if (module.getIgnore() || module.getAndroid() == null) {
                continue;
            }
            List<String> dossiers = module.getAndroid().getDossiersRes();
            if (dossiers.isEmpty()) {
                continue;
            }
            return deDossierRes(java.nio.file.Paths.get(dossiers.get(0)),
                    avertissements);
        }
        return ConfigurationApercu.vide();
    }

    /**
     * La configuration constituée d'un dossier {@code res/} : les
     * fichiers de thèmes, styles et attributs y sont lus par leurs
     * noms canoniques ({@code values/themes.xml},
     * {@code values-night/themes.xml}, {@code values/styles.xml},
     * {@code values/attrs.xml}).
     *
     * @param res            le dossier res/ (chemin absolu)
     * @param avertissements les écarts à compléter (jamais muets)
     * @return la configuration
     */
    public static ConfigurationApercu deDossierRes(Path res,
            List<String> avertissements) {
        if (!Files.isDirectory(res)) {
            avertissements.add("res directory not found: " + res);
            return new ConfigurationApercu(null, null, null, null, null,
                    null, ConfigurationApercu.LARGEUR_DEFAUT,
                    ConfigurationApercu.HAUTEUR_DEFAUT,
                    ConfigurationApercu.DENSITE_CONTEXTE, false,
                    List.copyOf(avertissements));
        }
        String themesJour = lire(res.resolve("values/themes.xml"),
                avertissements);
        String themesNuit = lire(res.resolve("values-night/themes.xml"),
                avertissements);
        String styles = lire(res.resolve("values/styles.xml"),
                avertissements);
        String attrs = lire(res.resolve("values/attrs.xml"),
                avertissements);
        return new ConfigurationApercu(res.toString(), null, themesJour,
                themesNuit, styles, attrs,
                ConfigurationApercu.LARGEUR_DEFAUT,
                ConfigurationApercu.HAUTEUR_DEFAUT,
                ConfigurationApercu.DENSITE_CONTEXTE, false,
                List.copyOf(avertissements));
    }

    private static String lire(Path fichier, List<String> avertissements) {
        if (!Files.isRegularFile(fichier)) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(fichier),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            avertissements.add("unreadable resource file: " + fichier
                    + " (" + e.getMessage() + ")");
            return null;
        }
    }
}
