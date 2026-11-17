package jo.joide.ide.langages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jo.codeide.lsp.kotlin.api.ClasspathKotlin;
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin;
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin;
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin;
import jo.codeide.lsp.kotlin.api.SessionLangageKotlin;
import jo.codeide.lsp.model.Empreintes;
import jo.codeide.lsp.model.LecteurStatistiquesSysteme;
import jo.codeide.lsp.model.ModuleLsp;
import jo.codeide.lsp.model.ProjetLsp;

/**
 * Sessions Kotlin du projet — une session ÉTANCHE par module (le moteur
 * livre des sessions neuves et closes ; le classpath d'un fichier
 * dépend de SON module, une session unique le changerait à chaque
 * fichier ouvert). Les racines incluent les frères dépendus, la même
 * règle que le moteur Java.
 *
 * <p>L'index d'appelables maison du moteur Kotlin vit en mémoire seule
 * ({@code dossierIndex} nul) : sa persistance partageable est une
 * décision à mesurer, pas un défaut (backlog).</p>
 */
final class SessionsKotlin implements AutoCloseable {

    private final ServiceMoteurLangageKotlin service;
    private final Map<String, SessionLangageKotlin> sessions =
            new ConcurrentHashMap<>();

    SessionsKotlin() {
        this.service = ServiceMoteurLangageKotlin.Companion.getUnique();
    }

    /**
     * Session du module propriétaire du fichier (créée au besoin).
     *
     * @param projet   projet courant (résolution des frères)
     * @param nomModule module propriétaire, {@code null} si le fichier
     *                  n'est sous aucune racine
     */
    SessionLangageKotlin sessionDe(ProjetLsp projet, String nomModule) {
        ModuleLsp module = module(projet, nomModule);
        if (module == null) {
            // Fichier hors module : session sans classpath, honnête —
            // les requêtes syntaxiques y répondent, la résolution y est
            // réduite, jamais fausse.
            return sessions.computeIfAbsent("", cle -> creer(null, null));
        }
        ProjetLsp projetFinal = projet;
        return sessions.computeIfAbsent(module.getNom(),
                cle -> creer(projetFinal, module));
    }

    /** Re-nourrit les sessions vivantes après une synchronisation. */
    void renourrir(ProjetLsp projet) {
        for (Map.Entry<String, SessionLangageKotlin> entree
                : sessions.entrySet()) {
            String nom = entree.getKey();
            if (nom.isEmpty()) {
                continue;
            }
            SessionLangageKotlin session = entree.getValue();
            ModuleLsp module = module(projet, nom);
            if (module == null) {
                sessions.remove(nom);
                session.close();
                continue;
            }
            session.definirClasspath(classpathDe(projet, module));
        }
    }

    /** Ferme toutes les sessions (idempotent côté moteur). */
    void toutFermer() {
        for (SessionLangageKotlin session : sessions.values()) {
            session.close();
        }
        sessions.clear();
    }

    @Override
    public void close() {
        toutFermer();
    }

    private SessionLangageKotlin creer(ProjetLsp projet, ModuleLsp module) {
        SessionLangageKotlin session = service.creerSession();
        if (module != null) {
            session.definirClasspath(classpathDe(projet, module));
        }
        return session;
    }

    private ClasspathKotlin classpathDe(ProjetLsp projet,
            ModuleLsp module) {
        List<EntreeClasspathKotlin> entrees = new ArrayList<>();
        LecteurStatistiquesSysteme stats = new LecteurStatistiquesSysteme();
        for (var entree : module.getClasspath().getEntrees()) {
            if (entree.getKind() != jo.codeide.lsp.model.KindEntree.JAR
                    || !entree.getExiste()) {
                continue;
            }
            entrees.add(new EntreeClasspathKotlin(
                    KindEntreeKotlin.JAR, entree.getChemin(),
                    entree.getEmpreinte()));
        }
        String plateforme = module.getAndroidJar();
        if (plateforme != null && stats.existe(plateforme)) {
            entrees.add(new EntreeClasspathKotlin(KindEntreeKotlin.JAR,
                    plateforme, Empreintes.INSTANCE.empreinteFichier(
                            plateforme, stats)));
        }
        return new ClasspathKotlin(entrees, racinesDe(projet, module),
                null);
    }

    /** Racines du module : principal + générés + frères dépendus. */
    private List<String> racinesDe(ProjetLsp projet, ModuleLsp module) {
        List<String> racines = new ArrayList<>();
        racines.addAll(module.getSources().getPrincipal());
        racines.addAll(module.getSources().getGeneres());
        for (var dep : module.getDependancesModules()) {
            ModuleLsp frere = module(projet, dep.getCible());
            if (frere != null) {
                racines.addAll(frere.getSources().getPrincipal());
            }
        }
        return racines;
    }

    private ModuleLsp module(ProjetLsp projet, String nom) {
        if (projet == null || nom == null) {
            return null;
        }
        for (ModuleLsp module : projet.getModules()) {
            if (module.getNom().equals(nom) && !module.getIgnore()) {
                return module;
            }
        }
        return null;
    }
}
