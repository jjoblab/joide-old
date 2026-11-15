package jo.joide.ide.langages;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jo.codeide.lsp.index.PhaseIndex;
import jo.codeide.lsp.index.VueIndex;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * Adaptateur JoIDE de l'index disque CIDX sur le port « IndexNoms » du
 * moteur Java — le miroir Java de l'adaptateur de référence de la brique
 * ({@code socle/testing}, non publié en artefact, recopié ici à
 * l'identique sémantique) : il surveille la transition CONSTRUCTION →
 * PRÊT/PARTIEL de la vue et traduit les requêtes du port en requêtes de
 * fusion LSM.
 *
 * <p>La surveillance d'état vit sur un fil démon Java ( interrogation
 * périodique du flux d'état) plutôt que sur une coroutine : le reste du
 * montage (service d'index) reçoit bien le {@code CoroutineScope} de
 * l'hôte, mais le côté port du moteur reste Java pur.</p>
 */
final class AdaptateurIndexNoms implements jo.codeide.lsp.java.IndexNoms {

    /** Vue d'index du classpath (fusion LSM à la requête). */
    private final VueIndex vue;

    /** Observateurs de la transition PRÊT (exécutés au plus une fois). */
    private final List<Function0<Unit>> observateurs = new ArrayList<>();

    /** La transition a-t-elle eu lieu ? */
    private final AtomicBoolean pret = new AtomicBoolean(false);

    /** Fil démon de surveillance (interrompu par {@link #fermer()}). */
    private final AtomicReference<Thread> surveillance = new AtomicReference<>();

    AdaptateurIndexNoms(VueIndex vue) {
        this.vue = Objects.requireNonNull(vue);
        evaluerPret();
        if (!pret.get()) {
            Thread fil = new Thread(this::surveiller,
                    "joide-index-observation");
            fil.setDaemon(true);
            if (surveillance.compareAndSet(null, fil)) {
                fil.start();
            }
        }
    }

    /** La vue ADAPTÉE — c'est ELLE que construire() doit recevoir. */
    VueIndex vue() {
        return vue;
    }

    @Override
    public boolean estPret() {
        return pret.get();
    }

    @Override
    public void ajouterObservateurPret(Function0<Unit> observateur) {
        Objects.requireNonNull(observateur);
        if (pret.get()) {
            observateur.invoke();
            return;
        }
        synchronized (observateurs) {
            if (pret.get()) {
                observateur.invoke();
                return;
            }
            observateurs.add(observateur);
        }
    }

    @Override
    public String jarDe(String fqcn) {
        var localisation = vue.localiser(fqcn);
        return localisation == null ? null
                : localisation.getConteneur().getChemin();
    }

    @Override
    public List<String> nomsParPrefixe(String prefixe, int limite) {
        List<String> noms = new ArrayList<>();
        for (var nom : vue.nomsParPrefixe(prefixe, Math.max(1, limite))) {
            noms.add(nom.getNomSimple());
        }
        return noms;
    }

    @Override
    public List<String> fqnsForSimpleName(String nom) {
        List<String> fqcns = new ArrayList<>();
        for (var simple : vue.nomsParPrefixe(nom, LIMITE_EXACTE)) {
            if (simple.getNomSimple().equals(nom)) {
                fqcns.addAll(simple.getFqcns());
            }
        }
        return fqcns;
    }

    @Override
    public boolean estPaquet(String paquet) {
        return vue.estPaquet(paquet);
    }

    @Override
    public List<String> sousPaquetsDirects(String paquet) {
        return vue.sousPaquetsDirects(paquet);
    }

    @Override
    public List<String> typesDansPaquet(String paquet) {
        return vue.typesDansPaquet(paquet);
    }

    @Override
    public List<String> membresStatiques(String fqcn) {
        List<String> noms = new ArrayList<>();
        for (var membre : vue.membresStatiques(fqcn)) {
            noms.add(membre.getNom());
        }
        return noms;
    }

    /** Libère la surveillance (la vue reste interrogeable). */
    void fermer() {
        Thread fil = surveillance.getAndSet(null);
        if (fil != null) {
            fil.interrupt();
        }
    }

    private void surveiller() {
        while (!pret.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(INTERROGATION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (evaluerPret()) {
                return;
            }
        }
    }

    /** Évalue l'état ; vrai si la transition vient d'avoir lieu. */
    private boolean evaluerPret() {
        if (pret.get()) {
            return true;
        }
        PhaseIndex phase = vue.getEtat().getValue().getPhase();
        if (phase != PhaseIndex.PRET && phase != PhaseIndex.PARTIEL) {
            return false;
        }
        List<Function0<Unit>> aExecuter;
        synchronized (observateurs) {
            if (!pret.compareAndSet(false, true)) {
                return false;
            }
            aExecuter = new ArrayList<>(observateurs);
            observateurs.clear();
        }
        Thread fil = surveillance.getAndSet(null);
        if (fil != null) {
            fil.interrupt();
        }
        for (Function0<Unit> observateur : aExecuter) {
            observateur.invoke();
        }
        return true;
    }

    /** Garde des recherches de nom exact (préfixe = nom, filtre aval). */
    private static final int LIMITE_EXACTE = 1_000;

    /** Période d'interrogation du flux d'état (ms). */
    private static final long INTERROGATION_MS = 200L;
}
