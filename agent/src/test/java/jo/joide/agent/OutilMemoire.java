package jo.joide.agent;

import java.util.ArrayList;
import java.util.List;

import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * Un outil de test : il enregistre ses invocations (fil, arguments,
 * instant) — les évaluations jugent l'état final (appels reçus,
 * ordre, parallélisme réel), jamais le transcript.
 */
final class OutilMemoire implements OutilAgent {

    /** Une invocation enregistrée. */
    record Invocation(String arguments, String fil, long instant) {
    }

    private final String nom;
    private final boolean mutant;
    private final String reponse;
    final List<Invocation> invocations = new ArrayList<>();

    /** Un effet de bord à l'invocation (le test y pose ses compteurs). */
    Runnable effet;

    /** @param nom le nom @param mutant le marqueur @param reponse le résultat borné constant */
    OutilMemoire(String nom, boolean mutant, String reponse) {
        this.nom = nom;
        this.mutant = mutant;
        this.reponse = reponse;
    }

    @Override
    public DescOutil description() {
        return new DescOutil(nom, "outil de test " + nom,
                JsonValue.ofNull(), mutant, reponse.length());
    }

    @Override
    public String invoquer(JsonValue arguments, CancelToken jeton)
            throws Exception {
        synchronized (invocations) {
            invocations.add(new Invocation(
                    jo.joide.deps.json.JsonEcrire.valeur(arguments),
                    Thread.currentThread().getName(),
                    System.nanoTime()));
        }
        if (effet != null) {
            effet.run();
        }
        return reponse;
    }
}
