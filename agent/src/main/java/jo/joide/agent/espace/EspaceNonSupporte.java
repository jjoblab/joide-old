package jo.joide.agent.espace;

/**
 * La capacité n'est pas servie par cet espace de travail — le défaut
 * dégradé du port (leçon 7 de l'ADR-É9) : l'hôte n'implémente que ce
 * qu'il sait faire, l'outil le rend HONNÊTEMENT au modèle (« non
 * disponible ici »), jamais une liste vide qui se lirait « rien
 * trouvé ».
 */
public class EspaceNonSupporte extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** @param capacite la capacité demandée */
    public EspaceNonSupporte(String capacite) {
        super("workspace does not support: " + capacite);
    }
}
