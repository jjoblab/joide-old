package jo.joide.ide.sync;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import jo.codeide.lsp.model.DepotModeleLsp;
import jo.codeide.lsp.model.ProjetLsp;
import jo.joide.model.Workspace;

/**
 * Synchronisation du modèle de langage : à chaque synchronisation, la
 * résolution des dépendances externes puis l'adaptation produisent un
 * NOUVEL instantané immuable, publié dans le dépôt — jamais de
 * mutation sous les pieds d'un lecteur (§6.8 règle 3).
 *
 * <p>La fonction de résolution est injectée : production par le
 * résolveur P4 ({@link Resolutions#byResolver}), faux de banc pour les
 * tests.</p>
 */
public final class LanguageModelSync {

    private final Path root;
    private final ProjetLspAdapter adapter;
    private final Function<Workspace, ExternalResolution> resolution;
    private final DepotModeleLsp depot = new DepotModeleLsp();
    /** Dernière résolution des externes (lot F : le build s'en sert
     *  pour les jars du classpath). */
    private volatile ExternalResolution derniereResolution;

    /**
     * @param root       racine du projet sur le disque
     * @param adapter    adaptateur ProjetLsp
     * @param resolution fonction de résolution des externes
     */
    public LanguageModelSync(Path root, ProjetLspAdapter adapter,
            Function<Workspace, ExternalResolution> resolution) {
        this.root = root;
        this.adapter = adapter;
        this.resolution = resolution;
    }

    /**
     * Synchronise : résout, adapte, publie le nouvel instantané.
     *
     * @param workspace modèle du workspace
     * @return la vue JoIDE de l'instantané publié
     */
    public LanguageModelSnapshot synchroniser(Workspace workspace) {
        ExternalResolution externals = resolution.apply(workspace);
        derniereResolution = externals;
        ProjetLsp projet = adapter.adapt(root, workspace, externals);
        depot.remplacer(projet);
        return LanguageModelSnapshot.of(projet, externals.problems());
    }

    /**
     * @return la dernière résolution des externes (vide avant la
     *         première synchronisation) — les jars résolus du
     *         classpath de build (lot F)
     */
    public Optional<ExternalResolution> derniereResolution() {
        return Optional.ofNullable(derniereResolution);
    }

    /**
     * @return la vue du dernier instantané publié, ou vide avant la
     *         première synchronisation
     */
    public Optional<LanguageModelSnapshot> snapshot() {
        ProjetLsp current = depot.getInstantane().getValue();
        return current == null ? Optional.empty()
                : Optional.of(LanguageModelSnapshot.of(current,
                        List.of()));
    }

    /**
     * Dépôt brut pour le montage des moteurs (lot C) — le type de la
     * brique ne quitte jamais le module (ADR-044).
     *
     * @return le dépôt d'instantanés de la brique
     */
    public DepotModeleLsp depot() {
        return depot;
    }
}
