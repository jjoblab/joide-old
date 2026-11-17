package jo.joide.agent;

/**
 * Les permissions de l'agent (ADR-É9) : trois modes persistés par
 * projet, un gate unique pour les outils mutants. Le défaut est le
 * DÉFAUT SÛR : sans décision, l'écriture est refusée — « fermer =
 * refuser ». Le mode PLAN_SEULEMENT refuse les écritures avec un
 * résultat qui DIT au modèle de décrire ses changements au lieu de
 * les appliquer.
 */
public final class Permissions {

    private Permissions() {
    }

    /** Le mode de permission persisté par projet. */
    public enum Mode {
        /** Chaque outil mutant bloque sur le gate. */
        DEMANDE_CHAQUE,
        /** Appliqué automatiquement, visible dans le transcript. */
        ACCEPTATION_AUTO,
        /** Les écritures sont refusées avec un résultat qui le dit. */
        PLAN_SEULEMENT
    }

    /** Une demande d'écriture soumise au gate : l'outil, la cible, le diff. */
    public static final class DemandeEcriture {
        /** Le nom de l'outil mutant. */
        public final String outil;
        /** La cible nommée (chemin relatif, lisible). */
        public final String cible;
        /** Le diff de prévisualisation (l'approbation porte sur l'édition, pas sur un nom de fichier). */
        public final String diffApercu;

        /** @param outil le nom de l'outil @param cible la cible @param diffApercu le diff déplié */
        public DemandeEcriture(String outil, String cible,
                               String diffApercu) {
            this.outil = outil;
            this.cible = cible;
            this.diffApercu = diffApercu;
        }
    }

    /** La décision du gate. */
    public enum Decision {
        /** Accordée pour CET appel — jamais mémorisée au-delà. */
        ACCORDEE,
        /** Refusée : l'outil reçoit un résultat d'erreur qui le dit. */
        REFUSEE
    }

    /** Le gate : implémenté par l'hôte (dialogue au-dessus de tout écran, lot P). */
    public interface PortePermission {
        /**
         * @param demande la demande d'écriture avec son diff déplié
         * @return la décision — fermer le dialogue doit REFUSER
         */
        Decision demander(DemandeEcriture demande);
    }

    /** Le message de refus motivé du mode PLAN_SEULEMENT (dit au modèle quoi faire à la place). */
    public static final String REFUS_PLAN =
            "Plan-only mode is active: writes are refused. Describe "
                    + "the changes you would make (files, edits) "
                    + "instead of applying them.";

    /** Le message de refus du gate (l'utilisateur a dit non — jamais un échec muet). */
    public static final String REFUS_GATE =
            "The user declined this write. Do not retry it unless "
                    + "asked.";
}
