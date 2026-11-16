package jo.joide.agent.mcp;

/**
 * Les constantes du protocole MCP (lot R, ADR-063) : la révision la
 * plus récente parlée par le client, les délais, les bornes — les
 * valeurs du comportement analysé en ADR-É9 et consignées en note
 * clean-room n° 3 § 5.
 */
public final class ProtocoleMcp {

    /** La révision du protocole que le client propose (le serveur répond avec celle qu'il fixe). */
    public static final String VERSION_PROTOCOLE = "2025-06-18";

    /** Le délai d'une poignée de main initialize (ms). */
    public static final long DELAI_CONNEXION_MS = 30_000L;

    /** Le délai d'un appel d'outil distant (ms). */
    public static final long DELAI_APPEL_MS = 180_000L;

    /** Le plafond de pages d'une liste d'outils paginée (un curseur qui boucle ne peut pas suspendre le client). */
    public static final int PAGES_MAX = 20;

    /** Le plafond de caractères d'un résultat d'outil (un résultat déjà borné part une fois dans le cache). */
    public static final int PLAFOND_RESULTAT = 40_000;

    /** Le plafond d'octets d'un corps de requête au serveur local. */
    public static final int PLAFOND_CORPS_OCTETS = 16 * 1024 * 1024;

    /** Au-delà de ce délai (ms), un outil long du serveur local répond en task_status — le travail continue. */
    public static final long SEUIL_JOB_LONG_MS = 90_000L;

    /** Le code JSON-RPC d'une méthode inconnue. */
    public static final int METHODE_INCONNUE = -32601;

    private ProtocoleMcp() {
    }
}
