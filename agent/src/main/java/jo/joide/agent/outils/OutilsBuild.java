package jo.joide.agent.outils;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.agent.espace.ResultatExecution;

/**
 * Les outils d'exécution v1 (lot O, ADR-059) : trois outils BORNÉS —
 * programme 120 s, tâche 300 s, tests 600 s (les bornes de
 * CodeAssist). La QUEUE de la sortie est conservée (les journaux
 * intéressants sont à la fin), l'amputation s'annonce.
 */
public final class OutilsBuild {

    /** Le délai maximum d'un programme (secondes). */
    public static final long DELAI_PROGRAMME = 120;

    /** Le délai maximum d'une tâche de build (secondes). */
    public static final long DELAI_TACHE = 300;

    /** Le délai maximum des tests (secondes). */
    public static final long DELAI_TESTS = 600;

    /** Le plafond de caractères de sortie conservé. */
    public static final int PLAFOND_SORTIE = 20_000;

    private OutilsBuild() {
    }

    /** run_program : exécute le programme du projet, borné 120 s. */
    public static OutilAgent executerProgramme(
            EspaceTravailAgent espace) {
        return executer(espace, "run_program",
                "Run the main program of the project on the build "
                        + "engine. Bounded to 120 seconds.",
                DELAI_PROGRAMME);
    }

    /** run_task : exécute une tâche de build, borné 300 s. */
    public static OutilAgent executerTache(EspaceTravailAgent espace) {
        return executer(espace, "run_task",
                "Run a build task (e.g. compile, jar) on the build "
                        + "engine. Bounded to 300 seconds.",
                DELAI_TACHE);
    }

    /** run_tests : exécute les tests, borné 600 s. */
    public static OutilAgent executerTests(EspaceTravailAgent espace) {
        return executer(espace, "run_tests",
                "Run the project tests on the build engine. Bounded "
                        + "to 600 seconds.",
                DELAI_TESTS);
    }

    private static OutilAgent executer(EspaceTravailAgent espace,
                                       String nom, String description,
                                       long delaiMaxSecondes) {
        return OutilSimple.de(
                new DescOutil(nom, description,
                        Schemas.objet("{\"target\":{\"type\":\"string\","
                                + "\"description\":\"what to run\"}}"),
                        false, PLAFOND_SORTIE),
                (arguments, jeton) -> {
                    String cible = Schemas.texte(arguments, "target",
                            nom);
                    ResultatExecution resultat = espace.executer(cible,
                            delaiMaxSecondes);
                    StringBuilder sortie = new StringBuilder();
                    sortie.append(resultat.succes ? "success"
                            : "failure")
                            .append(" (exit ")
                            .append(resultat.codeSortie)
                            .append(", ").append(resultat.dureeMs)
                            .append(" ms)");
                    if (resultat.interrompu) {
                        sortie.append(" — interrupted at the "
                                + delaiMaxSecondes
                                + "s deadline");
                    }
                    String texte = resultat.sortie;
                    if (texte.length() > PLAFOND_SORTIE) {
                        texte = "[output truncated, tail kept] "
                                + texte.substring(texte.length()
                                - PLAFOND_SORTIE);
                    }
                    return sortie + "\n" + texte;
                });
    }
}
