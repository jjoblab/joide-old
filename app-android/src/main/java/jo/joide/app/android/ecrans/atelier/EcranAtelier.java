package jo.joide.app.android.ecrans.atelier;

import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;

import java.nio.file.Paths;

import jo.joide.app.android.ProprietesConstruction;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ActivityAtelierBinding;
import jo.joide.app.android.ecrans.Ecrans;
import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * L'atelier adaptatif (lot J, ADR-É5) — LA COQUE MINCE du lot A6
 * (ADR-085 décision 2) : le cycle de vie, les intents, le retour
 * système et les résultats d'activité, RIEN D'AUTRE. La mise en page
 * vit dans {@code activity_atelier.xml} (+ {@code layout-w720dp} :
 * le grand écran n'est plus assemblé en code), le câblage et les
 * mises à jour dans {@link VueAtelier}, la session (moteurs, sync,
 * scène, aperçu) dans {@link SessionAtelier}, les contrôleurs
 * dépendent de {@link HoteAtelier} — plus aucun champ partagé.
 *
 * <p>Lot A7 (ADR-085 décisions 12 et 13) : les résultats SAF/caméra
 * du tiroir agent passent par les CONTRATS TYPÉS
 * {@link Ecrans#CHOISIR_IMAGE} et {@link Ecrans#PRENDRE_PHOTO} — plus
 * de codes 47101/47102 ni d'{@code onActivityResult} déprécié ; le
 * retour système ferme un popup ouvert EN PREMIER (l'ordre vit dans
 * l'état pur, testé JVM) ; les popups ouverts se ferment explicitement
 * en {@code onPause}/{@code onDestroy}/{@code onConfigurationChanged}.</p>
 *
 * <p>L'atelier remplace l'empilement programmatique de P8 — barre
 * supérieure exacte (nom du projet, puce modules, pilule d'état de
 * l'index, ⋮), onglets multi-fichiers à état faible (ADR-051),
 * éditeur celeris dans le puits, tiroir arborescence avec sorties
 * estompées, dock de console (lot K, ADR-É6), FAB Construire qui
 * devient pilule de build, aperçu côte à côte au seuil 720 dp (lot
 * L, ADR-É7). Le retour système ferme les surcouches une à une —
 * jamais un état zombie.</p>
 */
public final class EcranAtelier extends ActiviteJoide {

    /** La vue d'écran de l'atelier (binding, zones, mises à jour). */
    private VueAtelier vue;

    /**
     * Le contrat SAF d'image du tiroir agent (décision 13 — lot A7) :
     * le code 47101 disparaît, l'image choisie arrive typée.
     */
    private final ActivityResultLauncher<String> choisirImage =
            registerForActivityResult(Ecrans.CHOISIR_IMAGE,
                    uri -> {
                        if (vue != null) {
                            vue.controleurAgent().surImageChoisie(uri);
                        }
                    });

    /**
     * Le contrat de photo caméra du tiroir agent (décision 13 — lot
     * A7) : le code 47102 disparaît, la photo prise arrive typée.
     */
    private final ActivityResultLauncher<Uri> prendrePhoto =
            registerForActivityResult(Ecrans.PRENDRE_PHOTO,
                    prise -> {
                        if (vue != null) {
                            vue.controleurAgent().surPhotoPrise(
                                    Boolean.TRUE.equals(prise));
                        }
                    });

    /** Le launcher du contrat d'image (l'hôte le délègue à l'agent). */
    ActivityResultLauncher<String> contratChoisirImage() {
        return choisirImage;
    }

    /** Le launcher du contrat de photo (l'hôte le délègue à l'agent). */
    ActivityResultLauncher<Uri> contratPrendrePhoto() {
        return prendrePhoto;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // lot U : l'outillage de build vit ICI — le cache dans les
        // fichiers privés (jamais user.home, absent sur Android) et
        // nativeLibraryDir où ART seul exécute les binaires.
        // lot XX : ProprietesConstruction, UNE vérité pour les deux
        // processus (l'écran atelier, le service) — l'APK du fork
        // (registrar Compose dexé) et le jar du plugin extrait en plus
        ProprietesConstruction.poser(this);
        Graphite.Palette palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        ActivityAtelierBinding binding =
                monter(ActivityAtelierBinding::inflate);
        vue = new VueAtelier(this, binding, palette);
        // avant TOUT retour anticipé : l'état d'erreur garde son
        // retour système (l'ordre surRetour() est celui d'hier)
        getOnBackPressedDispatcher().addCallback(this, retourSysteme);
        // les extras : les noms vivent dans Ecrans (décision 13) —
        // l'atelier n'est plus leur unique lecteur depuis le lot A6
        String racine = getIntent().getStringExtra(Ecrans.EXTRA_RACINE);
        String banniere = getIntent().getStringExtra(
                Ecrans.EXTRA_BANNIERE);
        if (racine == null || racine.isEmpty()) {
            vue.montrerErreur(
                    getString(R.string.projet_illisible_sans_detail));
            return;
        }
        if (banniere != null && !banniere.isEmpty()) {
            vue.poserBanniere(banniere);
            return;
        }
        vue.poserEtatVide(R.string.ide_chargement,
                R.string.atelier_etat_vide_aide);
        vue.demarrerSession(Paths.get(racine),
                getIntent().getStringExtra(Ecrans.EXTRA_FICHIER));
    }

    // ------------------------------------------------------------------
    // Retour système : les surcouches une à une (ADR-É5) — l'ordre
    // VIT dans l'état pur (EtatPopups.surRetour, testé JVM) : le
    // popup d'abord (décision 12 du lot A7), puis le tiroir agent,
    // puis les surcouches de l'état (feuille, tiroir, aperçu,
    // confirmation, sortie). Décision 13 : le
    // OnBackPressedDispatcher remplace l'override déprécié ; le
    // callback est TOUJOURS armé — l'atelier consomme chaque retour,
    // la sortie passe par la confirmation ou QUITTER
    // ------------------------------------------------------------------

    private final OnBackPressedCallback retourSysteme =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    switch (vue.surRetourSysteme()) {
                        case FERMER_POPUP -> vue.fermerPopups();
                        case FERMER_TIROIR_AGENT ->
                                vue.controleurAgent().fermerAgent();
                        case FERMER_CONSOLE -> {
                            vue.etat().replierConsole();
                            vue.majVolets();
                        }
                        case FERMER_TIROIR -> {
                            vue.etat().fermerTiroir();
                            if (vue.tiroir() != null) {
                                vue.tiroir().fermer();
                            }
                        }
                        case FERMER_APERCU -> {
                            vue.etat().fermerApercuPleinEcran();
                            vue.majVolets();
                        }
                        case DEMANDER_FERMETURE -> vue.demanderFermeture();
                        case QUITTER -> finish();
                    }
                }
            };

    // ------------------------------------------------------------------
    // Cycle de vie : les popups se ferment EXPLICITEMENT (décision
    // 12 — jamais une fuite au détachement, jamais restaurés)
    // ------------------------------------------------------------------

    @Override
    protected void onPause() {
        vue.fermerPopups();
        super.onPause();
    }

    /**
     * Un changement de configuration FERME les popups ouverts
     * (décision 12 — jamais restaurés). Frontière du lot : le manifest
     * ne porte pas encore {@code configChanges} (lot A8) — un
     * changement de géométrie recrée aujourd'hui l'activité et
     * {@code onDestroy} ferme déjà tout ; l'override armé ici devient
     * porteur le jour où A8 déclare les changements retenus.
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration
                                              nouvelle) {
        vue.fermerPopups();
        super.onConfigurationChanged(nouvelle);
    }

    @Override
    protected void onDestroy() {
        vue.fermer();
        super.onDestroy();
    }
}
