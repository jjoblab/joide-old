package jo.joide.tasks;

import java.nio.file.Path;

import jo.joide.platform.CancelToken;
import jo.joide.platform.Clock;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.platform.RootSet;

/**
 * Contexte d'exécution d'une tâche, fourni par le moteur.
 *
 * <p>Le contexte expose le delta des entrées depuis la dernière
 * exécution réussie : les compilateurs l'utilisent directement.
 * Les exceptions internes ne traversent pas ce contexte : une tâche
 * qui échoue le déclare par son résultat.</p>
 */
public interface TaskContext {

    /**
     * @return le jeton d'annulation de l'exécution en cours
     */
    CancelToken cancellation();

    /**
     * @return l'horloge injectée, source unique du temps
     */
    Clock clock();

    /**
     * @return le delta des entrées depuis la dernière exécution
     *         réussie de la tâche
     */
    InputDelta inputDelta();

    /**
     * Résout un chemin ancré en chemin réel de l'installation.
     *
     * @param path chemin ancré
     * @return le chemin absolu correspondant
     */
    Path resolve(RootedPath path);

    /**
     * @return l'ensemble des racines de l'installation courante
     */
    RootSet roots();

    /**
     * Signale un problème pendant l'exécution sans faire échouer la
     * tâche : avertissements et informations remontés dans le
     * rapport.
     *
     * @param problem problème à signaler
     */
    void report(Problem problem);
}
