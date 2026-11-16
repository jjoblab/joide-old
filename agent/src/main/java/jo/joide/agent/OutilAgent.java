package jo.joide.agent;

import jo.joide.platform.CancelToken;

/**
 * Un outil de l'agent (SPI, lot N) : le noyau connaît la DESCRIPTION
 * et le marqueur mutant, l'implémentation vit derrière le port
 * d'espace de travail (lot O). La méthode ne s'appelle jamais
 * directement depuis la boucle : l'{@code ExecuteurOutils}
 * l'exécute dans la politique parallèle-lecture / séquence-mutation.
 */
public interface OutilAgent {

    /** @return la description proposée au modèle */
    DescOutil description();

    /**
     * Exécute l'outil.
     *
     * @param arguments les arguments analysés (objet JSON)
     * @param jeton     le jeton d'annulation du tour
     * @return le résultat BORNÉ côté outil (texte, jamais null)
     * @throws Exception l'échec de l'outil (le résultat d'erreur sera
     *                   honnête, jamais une exception avalée)
     */
    String invoquer(jo.joide.deps.json.JsonValue arguments,
                    CancelToken jeton) throws Exception;
}
