package jo.joide.agent.mcp;

/**
 * Le store des signatures MCP, clé par l'IDENTITÉ du serveur (lot R,
 * ADR-063) : pointer ailleurs (une URL différente, une commande
 * différente) est un AUTRE serveur — les signatures stockées ne
 * suivent pas, l'approbation non plus. Implémenté côté hôte
 * (stockage chiffré).
 */
public interface StoreJetonsMcp {

    /** @param identite l'identité du serveur @return les jetons, ou null */
    JetonsMcp lire(String identite);

    /** @param identite l'identité du serveur @param jetons les jetons à poser */
    void poser(String identite, JetonsMcp jetons);

    /** @param identite l'identité du serveur à oublier */
    void retirer(String identite);
}
