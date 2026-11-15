package jo.joide.build.jvm.compile;

/**
 * Pilote de compilateur : compile un lot de sources d'un même
 * langage vers un répertoire de sortie, avec un classpath ordonné.
 *
 * <p>Le pilote est sans état et sans fuite de ressource entre deux
 * lots ; l'annulation coopérative est assurée par l'appelant (le
 * lot est court) ou par l'interruption du fil.</p>
 */
public interface CompilerDriver {

    /**
     * @return l'identifiant de version de l'outil piloté, chaîne
     *         stable qui entre dans les empreintes de tâche
     */
    String toolVersion();

    /**
     * @param request demande de compilation
     * @return le résultat du lot
     */
    CompileResult compile(CompileRequest request);
}
