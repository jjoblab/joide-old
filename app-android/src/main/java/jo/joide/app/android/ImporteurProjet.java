package jo.joide.app.android;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Import d'un projet choisi par le sélecteur système (arbre SAF) vers
 * le stockage privé de l'application : les moteurs et le pipeline
 * travaillent sur de vrais chemins de fichiers (java.nio), pas des
 * URI de fournisseur — la copie est le pont honnête de P8 (l'accès
 * direct SAF est au backlog).
 *
 * <p>Les sélecteurs SAF ne donnent pas de racine en écriture fiable :
 * l'application importe (copie) puis travaille chez elle ; l'original
 * n'est jamais modifié.</p>
 */
public final class ImporteurProjet {

    private ImporteurProjet() {
    }

    /**
     * Importe l'arbre choisi dans le stockage privé.
     *
     * @param contexte  contexte pour le résolveur de contenu
     * @param arbre     URI d'arbre accordée par le sélecteur
     * @param racine    destination (répertoire privé, un sous-dossier
     *                  par projet)
     * @param progres   récepteur du nombre de fichiers copiés (appelé
     *                  sur le fil appelant), peut être null
     * @return le répertoire racine du projet importé
     * @throws IOException si la copie échoue
     */
    public static Path importer(Context contexte, Uri arbre, Path racine,
                                java.util.function.IntConsumer progres)
            throws IOException {
        String documentRacine = DocumentsContract.getTreeDocumentId(arbre);
        Files.createDirectories(racine);
        copierDossier(contexte, arbre, documentRacine, racine, progres);
        return racine;
    }

    private static void copierDossier(Context contexte, Uri arbre,
                                      String documentId, Path destination,
                                      java.util.function.IntConsumer progres)
            throws IOException {
        Files.createDirectories(destination);
        Uri enfants = DocumentsContract.buildChildDocumentsUriUsingTree(
                arbre, documentId);
        try (Cursor curseur = contexte.getContentResolver().query(
                enfants,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (curseur == null) {
                return;
            }
            while (curseur.moveToNext()) {
                String id = curseur.getString(0);
                String typeMime = curseur.getString(1);
                String nom = curseur.getString(2);
                if (nom == null || nom.startsWith(".")) {
                    continue;
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR
                        .equals(typeMime)) {
                    copierDossier(contexte, arbre, id,
                            destination.resolve(nom), progres);
                } else {
                    copierFichier(contexte, arbre, id,
                            destination.resolve(nom));
                    if (progres != null) {
                        progres.accept(1);
                    }
                }
            }
        }
    }

    private static void copierFichier(Context contexte, Uri arbre,
                                      String documentId, Path cible)
            throws IOException {
        Uri fichier = DocumentsContract.buildDocumentUriUsingTree(
                arbre, documentId);
        try (InputStream entree = contexte.getContentResolver()
                .openInputStream(fichier)) {
            if (entree == null) {
                // le fournisseur refuse ce fichier : il n'arrive pas,
                // l'écart se verra à l'usage (fichier absent), jamais
                // un import qui échoue en bloc
                return;
            }
            try (OutputStream sortie = Files.newOutputStream(cible)) {
                copier(entree, sortie);
            }
        }
    }

    /**
     * @param fichiers répertoire des fichiers internes
     * @return le répertoire des projets importés
     */
    public static Path dossierProjets(java.io.File fichiers) {
        return Paths.get(fichiers.getAbsolutePath(), "projets");
    }

    /** Copie par tampon — InputStream.transferTo exige le niveau 33. */
    private static void copier(InputStream entree, OutputStream sortie)
            throws IOException {
        byte[] tampon = new byte[64 * 1024];
        int lu;
        while ((lu = entree.read(tampon)) >= 0) {
            sortie.write(tampon, 0, lu);
        }
    }
}
