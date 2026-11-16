package jo.joide.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;

/**
 * L'exécuteur d'appels d'outils de la boucle (ADR-É9, leçon 9) : les
 * outils de LECTURE s'exécutent en parallèle (un tour qui lit cinq
 * fichiers paie une latence, pas cinq), les MUTATIONS en séquence
 * (les demandes de permission ne se court-circuitent pas, les
 * écritures restent ordonnées). L'ordre des résultats suit TOUJOURS
 * l'ordre des appels du modèle.
 */
public final class ExecuteurOutils implements AutoCloseable {

    /** La source d'outils (le port du lot O derrière). */
    public interface PortOutils {
        /** @param nom le nom d'outil @return l'outil, ou null (résultat d'erreur honnête) */
        OutilAgent outil(String nom);

        /** @return les outils proposés au modèle */
        List<OutilAgent> outils();
    }

    private final PortOutils port;
    private final java.util.concurrent.ExecutorService lectures;

    /**
     * @param port la source d'outils
     */
    public ExecuteurOutils(PortOutils port) {
        this(port, Math.max(2,
                Runtime.getRuntime().availableProcessors()));
    }

    /**
     * @param port            la source d'outils
     * @param parallellismeLectures les fils des lectures en parallèle
     *                              (au moins 2 ; les tests barrent à 4)
     */
    public ExecuteurOutils(PortOutils port, int parallellismeLectures) {
        this.port = Objects.requireNonNull(port, "port");
        this.lectures = WorkerPools.newFixed("agent-outils",
                Math.max(2, parallellismeLectures));
    }

    /** Les descriptions à proposer au modèle. */
    public List<DescOutil> descriptions() {
        List<DescOutil> descriptions = new ArrayList<>();
        for (OutilAgent outil : port.outils()) {
            descriptions.add(outil.description());
        }
        return descriptions;
    }

    /**
     * Exécute les appels d'un pas de boucle.
     *
     * @param appels     les appels du modèle, dans son ordre
     * @param permission le mode et le gate
     * @param jeton      l'annulation du tour
     * @param ecouteur   reçoit début/fin/refus de chaque appel
     * @return les résultats, dans l'ordre des appels
     */
    public List<PartieContenu> executer(List<PartieContenu> appels,
                                        Permissions.Mode permission,
                                        Permissions.PortePermission gate,
                                        CancelToken jeton,
                                        java.util.function.Consumer<EvenementAgent> ecouteur) {
        List<PartieContenu> resultats =
                new ArrayList<>(appels.size());
        // les lectures en parallèle, les mutations en séquence : on
        // partitionne en tranches contiguës de même nature
        int i = 0;
        while (i < appels.size()) {
            jeton.checkCancelled();
            boolean trancheMutante = estMutant(appels.get(i));
            int fin = i;
            while (fin < appels.size()
                    && estMutant(appels.get(fin)) == trancheMutante) {
                fin++;
            }
            if (trancheMutante) {
                for (int k = i; k < fin; k++) {
                    resultats.add(executerUn(appels.get(k), permission,
                            gate, jeton, ecouteur));
                }
            } else {
                resultats.addAll(executerTrancheLecture(
                        appels.subList(i, fin), jeton, ecouteur));
            }
            i = fin;
        }
        return resultats;
    }

    private boolean estMutant(PartieContenu appel) {
        OutilAgent outil = port.outil(appel.nomOutil);
        return outil != null && outil.description().mutant;
    }

    private List<PartieContenu> executerTrancheLecture(
            List<PartieContenu> appels, CancelToken jeton,
            java.util.function.Consumer<EvenementAgent> ecouteur) {
        List<java.util.concurrent.Future<PartieContenu>> futures =
                new ArrayList<>();
        for (PartieContenu appel : appels) {
            futures.add(lectures.submit(() ->
                    executerUn(appel, Permissions.Mode.ACCEPTATION_AUTO,
                            null, jeton, ecouteur)));
        }
        List<PartieContenu> resultats = new ArrayList<>();
        for (java.util.concurrent.Future<PartieContenu> future
                : futures) {
            try {
                resultats.add(future.get());
            } catch (java.util.concurrent.ExecutionException e) {
                resultats.add(PartieContenu.resultat("",
                        "tool execution failed: "
                                + e.getCause(), true));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException(
                        "tool execution interrupted");
            }
        }
        return resultats;
    }

    private PartieContenu executerUn(PartieContenu appel,
                                     Permissions.Mode permission,
                                     Permissions.PortePermission gate,
                                     CancelToken jeton,
                                     java.util.function.Consumer<EvenementAgent> ecouteur) {
        if (ecouteur != null) {
            ecouteur.accept(EvenementAgent.outilDebut(appel.idAppel,
                    appel.nomOutil));
        }
        OutilAgent outil = port.outil(appel.nomOutil);
        if (outil == null) {
            return refus(appel, "unknown tool: " + appel.nomOutil
                    + " — list the tools you need and use only those",
                    ecouteur);
        }
        if (outil.description().mutant) {
            if (permission == Permissions.Mode.PLAN_SEULEMENT) {
                return refus(appel, Permissions.REFUS_PLAN, ecouteur);
            }
            if (permission == Permissions.Mode.DEMANDE_CHAQUE
                    && gate != null) {
                Permissions.Decision decision = gate.demander(
                        new Permissions.DemandeEcriture(appel.nomOutil,
                                cible(appel), diff(appel)));
                if (decision != Permissions.Decision.ACCORDEE) {
                    return refus(appel, Permissions.REFUS_GATE,
                            ecouteur);
                }
            }
        }
        String resultat;
        try {
            jo.joide.deps.json.JsonValue arguments =
                    jo.joide.deps.json.Json.parse(appel.arguments)
                            .root;
            if (arguments == null) {
                arguments = jo.joide.deps.json.JsonValue.ofNull();
            }
            resultat = outil.invoquer(arguments, jeton);
        } catch (jo.joide.agent.espace.EspaceNonSupporte e) {
            // le défaut dégradé du port : l'outil existe, l'hôte ne
            // le sert pas — dit honnêtement, jamais « rien trouvé »
            resultat = "not available in this workspace: "
                    + e.getMessage();
            PartieContenu partie = PartieContenu.resultat(
                    appel.idAppel, resultat, true);
            if (ecouteur != null) {
                ecouteur.accept(EvenementAgent.outilFin(appel.idAppel,
                        appel.nomOutil, resultat));
            }
            return partie;
        } catch (Exception e) {
            resultat = "tool failed: " + e.getMessage();
            PartieContenu partie = PartieContenu.resultat(
                    appel.idAppel, resultat, true);
            if (ecouteur != null) {
                ecouteur.accept(EvenementAgent.outilFin(appel.idAppel,
                        appel.nomOutil, resultat));
            }
            return partie;
        }
        PartieContenu partie = PartieContenu.resultat(appel.idAppel,
                resultat, false);
        if (ecouteur != null) {
            ecouteur.accept(EvenementAgent.outilFin(appel.idAppel,
                    appel.nomOutil, resultat));
        }
        return partie;
    }

    private PartieContenu refus(PartieContenu appel, String motif,
                                java.util.function.Consumer<EvenementAgent> ecouteur) {
        if (ecouteur != null) {
            ecouteur.accept(EvenementAgent.outilRefuse(appel.idAppel,
                    appel.nomOutil, motif));
        }
        return PartieContenu.resultat(appel.idAppel, motif, true);
    }

    private static String cible(PartieContenu appel) {
        jo.joide.deps.json.JsonValue arguments =
                jo.joide.deps.json.Json.parse(appel.arguments).root;
        for (String cle : new String[]{"path", "file", "fichier",
                "chemin", "to", "cible"}) {
            String valeur = arguments.member(cle).asString();
            if (valeur != null) {
                return valeur;
            }
        }
        return appel.nomOutil;
    }

    private static String diff(PartieContenu appel) {
        jo.joide.deps.json.JsonValue arguments =
                jo.joide.deps.json.Json.parse(appel.arguments).root;
        StringBuilder sortie = new StringBuilder(appel.nomOutil);
        sortie.append('(');
        sortie.append(arguments.type() == jo.joide.deps.json.JsonValue
                .Type.OBJECT
                ? jo.joide.deps.json.JsonEcrire.valeur(arguments)
                : appel.arguments);
        sortie.append(')');
        return sortie.toString();
    }

    @Override
    public void close() {
        lectures.shutdownNow();
    }
}
