package jo.joide.app.android.atelier.agent;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le fournisseur de la cible caméra de l'agent (lot S, ADR-064) :
 * zéro AndroidX (ADR-É2) — l'action {@code ACTION_IMAGE_CAPTURE}
 * avec {@code EXTRA_OUTPUT} exige une URI {@code content://} sur
 * API 28, le cadre n'offre pas de FileProvider public. Ce
 * fournisseur sert UN fichier de cache par photo, non exporté,
 * l'accès passe par la permission URI accordée à l'intent.
 */
public final class FournisseurCameraAgent extends ContentProvider {

    /** L'autorité du fournisseur. */
    public static final String AUTORITE = "joide.agent.camera";

    /** La cible courante (posée avant le lancement, lue au retour). */
    private static volatile Path cible;

    /**
     * Prépare la cible d'une photo : un fichier neuf dans le cache,
     * l'URI à mettre dans {@code EXTRA_OUTPUT}.
     *
     * @param contexte le contexte de l'atelier
     * @return l'URI de sortie de la caméra
     * @throws IOException si le cache ne se crée pas
     */
    public static Uri preparer(android.content.Context contexte)
            throws IOException {
        Path dossier = contexte.getCacheDir().toPath()
                .resolve("agent-camera");
        Files.createDirectories(dossier);
        // les photos précédentes partent : une seule en vol
        try (var anciennes = Files.list(dossier)) {
            for (Path ancienne : anciennes.toList()) {
                Files.deleteIfExists(ancienne);
            }
        }
        Path fichier = dossier.resolve("photo-"
                + System.currentTimeMillis() + ".jpg");
        Files.createFile(fichier);
        cible = fichier;
        return Uri.parse("content://" + AUTORITE + "/photo.jpg");
    }

    /** @return la dernière photo prise, ou null si aucune */
    public static Path dernierePhoto() {
        return cible;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        Path fichier = cible;
        if (fichier == null || !Files.isRegularFile(fichier)) {
            throw new FileNotFoundException(
                    "camera target not prepared");
        }
        return ParcelFileDescriptor.open(fichier.toFile(),
                ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
                        String selection, String[] argumentsSelection,
                        String tri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues valeurs) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection,
                      String[] argumentsSelection) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues valeurs,
                      String selection, String[] argumentsSelection) {
        return 0;
    }
}
