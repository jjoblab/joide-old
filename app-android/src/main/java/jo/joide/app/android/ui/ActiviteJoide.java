package jo.joide.app.android.ui;

import android.app.Activity;
import android.os.Bundle;

/**
 * L'activité de base des ÉCRANS (lot L2+, correction du crash du
 * 2026-11-18). Le manifeste déclare un thème framework (le kit
 * programmatique n'a jamais dépendu du thème) ; les layouts XML de
 * l'atelier v2 lisent désormais les ?attr/jo_* — ils exigent le thème
 * de l'application POSÉ AVANT TOUTE INFLATION. Chaque écran hérite
 * ici : le thème suit les réglages (Sombre/Clair/Système) et la
 * chaîne de parents framework reste identique.
 */
public abstract class ActiviteJoide extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        setTheme(GraphiteVue.resTheme(this));
        super.onCreate(state);
    }
}
