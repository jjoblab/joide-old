package jo.joide.app.android;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture des noms d'entrées à la racine d'une arborescence SAF
 * (ACTION_OPEN_DOCUMENT_TREE) : les noms seuls, sans arbre complet —
 * assez pour {@link ProjectDetector}.
 *
 * <p>Échecs silencieux interdits : toute anomalie du fournisseur rend
 * une liste vide, signalée par le sélecteur comme projet non reconnu.
 */
public final class RootNames {

    private RootNames() {
    }

    /**
     * Liste les noms d'entrées visibles à la racine de l'arborescence.
     *
     * @param context contexte pour résoudre le fournisseur (jamais
     *        {@code null})
     * @param tree Uri accordée par le sélecteur (jamais {@code null})
     * @return les noms affichés ; jamais {@code null}, vide si le
     *         fournisseur ne répond pas
     */
    public static List<String> list(Context context, Uri tree) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree));
        List<String> out = new ArrayList<>();
        android.database.Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    children,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (c == null) {
                return out;
            }
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        } catch (RuntimeException unreadable) {
            return out;
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }
}
