package jo.joide.app.android.ui.choix;

import jo.joide.app.android.ui.Graphite;

/**
 * Choix purs du mouvement (ADR-É2) : les transitions d'écran sont
 * latérales et directionnelles sur téléphone (axe X partagé), en
 * fondu discret sur grand écran — la distinction
 * « ScreenTransition » ; 240 ms entre écrans, 180 ms pour les
 * composants (tiroir, dock, pilules).
 */
public final class ChoixMouvement {

    private ChoixMouvement() {
    }

    /** Le type de transition d'écran. */
    public enum Transition {
        LATERALE, FONDU
    }

    /** Une transition : son type et sa durée (ms). */
    public static final class TransitionEcran {
        public final Transition type;
        public final int dureeMs;

        TransitionEcran(Transition type, int dureeMs) {
            this.type = type;
            this.dureeMs = dureeMs;
        }
    }

    /** La transition entre deux écrans selon la largeur d'appareil. */
    public static TransitionEcran ecran(boolean grandEcran) {
        return new TransitionEcran(
                grandEcran ? Transition.FONDU : Transition.LATERALE,
                Graphite.MOUVEMENT_ECRAN);
    }

    /** La durée d'un mouvement de composant (tiroir, dock). */
    public static int composant() {
        return Graphite.MOUVEMENT_STANDARD;
    }
}
