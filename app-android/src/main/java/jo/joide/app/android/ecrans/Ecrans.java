package jo.joide.app.android.ecrans;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;

import jo.joide.app.android.ecrans.atelier.EcranAtelier;
import jo.joide.app.android.ecrans.creation.EcranCreation;
import jo.joide.app.android.ecrans.parametres.EcranParametres;

/**
 * La navigation typée d'ADR-085 (décision 13, lot A1) : les fabriques
 * d'{@code Intent}, les extras et les CONTRATS de résultat vivent ICI —
 * plus de codes de requête éparpillés ({@code 41}, {@code 47101},
 * {@code 47102} disparaissent à mesure que les écrans migrent) ni
 * d'{@code onActivityResult} déprécié : chaque écran enregistre son
 * contrat par {@code registerForActivityResult} et reçoit son résultat
 * typé.
 *
 * <p>Par discipline de transition (lots A2 à A6), les NOMS d'extras sont
 * les mêmes que les constantes d'{@link EcranAtelier} —
 * {@code EcransTest} (JVM) vérifie l'égalité, A6 les retire d'EIAtelier
 * au moment où l'atelier n'est plus leur unique lecteur.</p>
 */
public final class Ecrans {

    /** La racine du projet ouvert dans l'atelier. */
    public static final String EXTRA_RACINE = "joide.racine";

    /** La bannière d'avertissement (import Gradle) de l'atelier. */
    public static final String EXTRA_BANNIERE = "joide.banniere";

    /** Le fichier ouvert en premier dans l'atelier. */
    public static final String EXTRA_FICHIER = "joide.fichier";

    /**
     * Le contrat SAF d'import d'un dossier de projet
     * ({@code ACTION_OPEN_DOCUMENT_TREE}) — remplace le code
     * {@code PICK_IMPORT = 41} de l'accueil : l'entrée est l'URI
     * initiale (null : la dernière), la sortie l'arbre choisi.
     */
    public static final ActivityResultContract<Uri, Uri> IMPORTER_DOSSIER =
            new ActivityResultContracts.OpenDocumentTree();

    /**
     * Le contrat de choix d'une image SAF ({@code ACTION_GET_CONTENT}
     * image/*) — remplace le code {@code REQUETE_IMAGE = 47101} du
     * tiroir agent : l'entrée est le type MIME, la sortie l'URI.
     */
    public static final ActivityResultContract<String, Uri> CHOISIR_IMAGE =
            new ActivityResultContracts.GetContent();

    /**
     * Le contrat de photo caméra ({@code ACTION_IMAGE_CAPTURE} avec
     * EXTRA_OUTPUT) — remplace le code {@code REQUETE_PHOTO = 47102}
     * du tiroir agent : l'entrée est l'URI de sortie, la sortie dit si
     * la photo a été prise.
     */
    public static final ActivityResultContract<Uri, Boolean> PRENDRE_PHOTO =
            new ActivityResultContracts.TakePicture();

    private Ecrans() {
    }

    /** L'assistant de création de projet (trois étapes, lot I). */
    public static Intent creation(Context contexte) {
        return new Intent(contexte, EcranCreation.class);
    }

    /** L'écran des paramètres (lot M). */
    public static Intent parametres(Context contexte) {
        return new Intent(contexte, EcranParametres.class);
    }

    /**
     * L'atelier sur un projet existant.
     *
     * @param contexte le contexte appelant
     * @param racine    la racine du projet (extra {@link #EXTRA_RACINE})
     * @param banniere  la bannière d'avertissement ou null
     * @param fichier   le fichier ouvert en premier ou null
     * @return l'intent prêt à démarrer
     */
    public static Intent atelier(Context contexte, String racine,
                                 String banniere, String fichier) {
        Intent intention = new Intent(contexte, EcranAtelier.class);
        intention.putExtra(EXTRA_RACINE, racine);
        if (banniere != null) {
            intention.putExtra(EXTRA_BANNIERE, banniere);
        }
        if (fichier != null) {
            intention.putExtra(EXTRA_FICHIER, fichier);
        }
        return intention;
    }
}
