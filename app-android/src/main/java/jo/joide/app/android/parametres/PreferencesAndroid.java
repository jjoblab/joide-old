package jo.joide.app.android.parametres;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Les préférences privées de l'application vues comme une
 * {@link Reglages.Source} (lot M, ADR-054) : le seul pont entre le
 * cadre Android et les réglages purs. {@code apply()} écrit en
 * mémoire puis sur disque — jamais un écran bloqué sur l'écriture
 * d'une préférence.
 */
public final class PreferencesAndroid implements Reglages.Source {

    private static final String NOM = "joide.reglages";

    private final SharedPreferences preferences;

    private PreferencesAndroid(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * @param contexte le contexte de l'application
     * @return la source sur les préférences privées
     */
    public static PreferencesAndroid source(Context contexte) {
        return new PreferencesAndroid(
                contexte.getApplicationContext()
                        .getSharedPreferences(NOM,
                                Context.MODE_PRIVATE));
    }

    @Override
    public String lire(String cle) {
        return preferences.getString(cle, null);
    }

    @Override
    public void ecrire(String cle, String valeur) {
        preferences.edit().putString(cle, valeur).apply();
    }
}
