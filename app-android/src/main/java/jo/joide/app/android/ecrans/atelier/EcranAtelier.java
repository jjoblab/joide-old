package jo.joide.app.android.ecrans.atelier;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;

import java.nio.file.Paths;

import jo.joide.app.android.ProprietesConstruction;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ActivityAtelierBinding;
import jo.joide.app.android.ecrans.Ecrans;
import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * L'atelier adaptatif (lot J, ADR-É5) — LA COQUE MINCE du lot A6
 * (ADR-085 décision 2) : le cycle de vie, les intents, le retour
 * système et les résultats d'activité, RIEN D'AUTRE. La mise en page
 * vit dans {@code activity_atelier.xml} (+ {@code layout-w720dp} :
 * le grand écran n'est plus assemblé en code), le câblage et les
 * mises à jour dans {@link VueAtelier}, la session (moteurs, sync,
 * scène, aperçu) dans {@link SessionAtelier}, les quatre contrôleurs
 * dépendent de {@link HoteAtelier} — plus aucun champ partagé.
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

    @Override
    protected void onActivityResult(int requete, int resultat,
                                    Intent donnees) {
        super.onActivityResult(requete, resultat, donnees);
        vue.controleurAgent().surResultatActivite(requete, resultat,
                donnees);
    }

    // ------------------------------------------------------------------
    // Retour système : les surcouches une à une (ADR-É5) — l'ordre est
    // inchangé (les popups rejoignent au lot A7). Décision 13 de
    // l'ADR-085 : le OnBackPressedDispatcher remplace l'override
    // déprécié (motif des lots A2-A4) ; le callback est TOUJOURS
    // armé — l'atelier consomme chaque retour, la sortie passe
    // par la confirmation ou QUITTER
    // ------------------------------------------------------------------

    private final OnBackPressedCallback retourSysteme =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // le tiroir agent est la surcouche la plus
                    // récente : le retour la ferme d'abord
                    // (ADR-É11 point 1, ordre ADR-É5)
                    if (vue.tiroirAgentOuvert()) {
                        vue.controleurAgent().fermerAgent();
                        return;
                    }
                    switch (vue.etat().surRetour()) {
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
                        case DEMANDER_FERMETURE -> demanderFermeture();
                        case QUITTER -> finish();
                        default -> finish();
                    }
                }
            };

    // ------------------------------------------------------------------
    // Le menu ⋮ et la fermeture du projet
    // ------------------------------------------------------------------

    /** Le menu ⋮ de la barre haute (la vue d'écran y entre). */
    void ouvrirMenu() {
        // lot M (ADR-É8) : la case « en processus » du lot F vit
        // désormais dans Paramètres — ici il reste fermer le projet
        // et le chemin vers les réglages
        String fermer = getString(R.string.atelier_fermer_projet);
        Dialogue.menu(this, vue.palette(),
                vue.racineProjet() == null ? ""
                        : String.valueOf(
                        vue.racineProjet().getFileName()),
                new CharSequence[]{
                        getString(R.string.agent_demander_ia),
                        getString(R.string.parametres_titre), fermer},
                (dialog, lequel) -> {
                    if (lequel == 0) {
                        vue.controleurAgent().demanderIa();
                    } else if (lequel == 1) {
                        startActivity(new Intent(this,
                                jo.joide.app.android.ecrans.parametres
                                        .EcranParametres.class));
                    } else if (lequel == 2) {
                        demanderFermeture();
                    }
                });
    }

    private void demanderFermeture() {
        Dialogue.confirmation(this, vue.palette(),
                getString(R.string.atelier_fermer_titre),
                getString(R.string.atelier_fermer_message,
                        vue.etat().nombreModifies()),
                R.string.action_annuler,
                R.string.atelier_fermer_projet,
                (dialog, lequel) -> finish());
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Override
    protected void onDestroy() {
        vue.fermer();
        super.onDestroy();
    }
}
