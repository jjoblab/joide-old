package jo.joide.app.android.logcat;

import android.content.ContentProvider;
import android.content.ContentValues;

import java.util.ArrayList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * ADR-084 — le provider du relais de journal (autorité
 * {@code joide.logcat}), exporté et gardé par la permission SIGNATURE
 * {@code joide.permission.RELAIS_LOGCAT} : l'application construite
 * par JoIDE porte la même signature (ADR-047) et est donc la SEULE à
 * pouvoir y écrire. Le protocole est {@code call("ecrire", bundle)}
 * avec les lignes « N|Tag|Message » en ArrayList&lt;String&gt; — le
 * dépôt part vers le tampon {@link FluxLogcat} (fil Binder) et ne
 * bloque jamais l'appelant.
 */
public final class RelaisLogcatProvider extends ContentProvider {

    /** La méthode du protocole : écrire un lot de lignes. */
    public static final String METHODE_ECRIRE = "ecrire";
    /** La clé du bundle : les lignes du lot. */
    public static final String CLE_LIGNES = "lignes";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String methode, String argument,
                       Bundle extras) {
        if (!METHODE_ECRIRE.equals(methode) || extras == null) {
            return null;
        }
        ArrayList<String> lignes = extras
                .getStringArrayList(CLE_LIGNES);
        if (lignes != null) {
            FluxLogcat.tampon().deposer(lignes);
        }
        Bundle reponse = new Bundle();
        reponse.putBoolean("ok", true);
        return reponse;
    }

    // ---- les voies de requête classiques : refusées (le protocole
    // est call() — rien à requêter, rien à insérer) ----

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] arguments, String tri) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues valeurs) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] arguments) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues valeurs, String selection,
                      String[] arguments) {
        return 0;
    }
}
