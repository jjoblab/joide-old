package jo.joide.app.android.ecrans.atelier;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ItemCompteurUsagesBinding;
import jo.joide.app.android.databinding.PopupUsagesBinding;

/**
 * Lot C5 (mandat du propriétaire : « l'écran usage, mêmes
 * fonctionnalités, design identique ») — l'écran des USAGES au dessin
 * de référence : la grande carte centrée (460 dp, rayon 24, conteneur
 * tonal haut, liseré bord-fin) porte le titre « Limites d'usage »,
 * l'Actualisation et la Fermeture 30 dp ; le sous-titre honnête dit
 * ce que l'écran rapporte ; UN COMPTEUR par fenêtre plafonnée du
 * régulateur (requêtes, jetons) : le nom 14 sp semi-gras, la puce « En
 * cours » sur la fenêtre qui PORTE le modèle (la plus chargée), le
 * pourcentage, la bande 8 dp (accent, alerte au seuil de 80 %,
 * erreur au plafond — jamais en dessous), « N sur M utilisés » et
 * « réinitialisé dans X » qui DÉCOMPTE à la seconde (la sortie de la
 * plus ancienne requête de la fenêtre glissante) ; la note des étapes
 * (« chaque étape de l'agent est une requête — un message peut en
 * compter plusieurs ») ; le bouton Terminé plein cadre.
 */
final class ControleurUsagesAgent {

    /** Le seuil d'avertissement de la bande (0,8 — la couleur
     *  change à 80 %, l'erreur au plafond). */
    private static final double SEUIL_BANDE = 0.8;

    private final HoteAtelier hote;

    /** L'horloge du décompte (posée au bind, retirée à la fermeture). */
    private android.os.Handler horloge;

    ControleurUsagesAgent(HoteAtelier hote) {
        this.hote = hote;
    }

    /**
     * L'écran Usages : la grande carte centrée — le popup se suffit
     * (Actualisation, Fermeture, Terminé).
     */
    void ouvrir() {
        if (hote.agent() == null || hote.vueAgent() == null) {
            return;
        }
        arreterHorloge();
        PopupUsagesBinding corps = PopupUsagesBinding.inflate(
                LayoutInflater.from(hote.contexte()));
        corps.sousTitreUsages.setText(hote.texte(
                R.string.agent_usages_sous_titre));
        corps.rafraichirUsages.setOnClickListener(vue -> construire(corps));
        corps.fermerUsages.setOnClickListener(vue -> {
            arreterHorloge();
            hote.popups().fermer();
        });
        corps.terminerUsages.setOnClickListener(vue -> {
            arreterHorloge();
            hote.popups().fermer();
        });
        construire(corps);
        hote.popups().centrerLarge(corps.getRoot(),
                hote.texte(R.string.agent_usages_titre), 460, false,
                this::arreterHorloge);
    }

    /** Le corps de l'écran : compteurs et notes. */
    private void construire(PopupUsagesBinding corps) {
        // ---- les compteurs du régulateur (l'occupation des fenêtres
        // du modèle courant — la bande prévient AVANT le 429) ----
        double[] occupations = hote.agent().occupationsModele();
        long[] compteurs = hote.agent().compteursModele();
        long[] relances = hote.agent().relancesModele();
        boolean plafondConnu = compteurs[1] > 0 || compteurs[3] > 0;
        // les compteurs se montrent PLEINS ou pas du tout : sans plafond
        // appris, deux lignes vides ne s'affichent jamais — l'écran le
        // dit honnêtement à la place
        corps.compteurRequetesUsages.getRoot().setVisibility(
                plafondConnu ? View.VISIBLE : View.GONE);
        corps.compteurJetonsUsages.getRoot().setVisibility(
                plafondConnu ? View.VISIBLE : View.GONE);
        corps.sansPlafondUsages.setVisibility(
                plafondConnu ? View.GONE : View.VISIBLE);
        if (!plafondConnu) {
            corps.sansPlafondUsages.setText(hote.texte(
                    R.string.agent_usages_sans_plafond));
        } else {
            // la fenêtre qui PORTE le modèle (la plus chargée) reçoit
            // la puce « En cours » — elle passe AUSSI en premier
            boolean requetesPortent = compteurs[1] > 0
                    && occupations[0] >= occupations[1];
            boolean jetonsPortent = !requetesPortent && compteurs[3] > 0;
            long maintenant = System.currentTimeMillis();
            if (!requetesPortent && !jetonsPortent) {
                // aucune fenêtre ne porte : l'ordre du régulateur reste
                requetesPortent = true;
            }
            ItemCompteurUsagesBinding premier = corps.compteurRequetesUsages;
            ItemCompteurUsagesBinding second = corps.compteurJetonsUsages;
            if (jetonsPortent) {
                premier = corps.compteurJetonsUsages;
                second = corps.compteurRequetesUsages;
            }
            LinearLayout conteneur = corps.corpsUsages;
            conteneur.removeView(premier.getRoot());
            conteneur.removeView(second.getRoot());
            conteneur.addView(premier.getRoot(), 0);
            conteneur.addView(second.getRoot(), 1);
            poserCompteur(corps.compteurRequetesUsages,
                    hote.texte(R.string.agent_usages_minute_requetes),
                    occupations[0], compteurs[0], compteurs[1],
                    requetesPortent,
                    relances[0] > 0 ? relances[0] - maintenant : -1);
            poserCompteur(corps.compteurJetonsUsages,
                    hote.texte(R.string.agent_usages_minute_jetons),
                    occupations[1], compteurs[2], compteurs[3],
                    jetonsPortent,
                    relances[1] > 0 ? relances[1] - maintenant : -1);
            relancerHorloge(corps);
        }
    }

    /**
     * Un compteur d'occupation : le nom, la puce « En cours » sur la
     * fenêtre qui porte, le pourcentage, la bande dont le REMPLISSAGE
     * suit la fraction et la TEINTE suit le seuil (accent sous 80 %,
     * alerte au seuil, erreur au plafond), le pied « N sur M utilisés »
     * et « réinitialisé dans X » (la sortie de la plus ancienne
     * requête de la fenêtre glissante).
     */
    private void poserCompteur(
            ItemCompteurUsagesBinding compteur, String nom, double fraction,
            long utilise, long plafond, boolean portant,
            long relanceMs) {
        compteur.nomCompteur.setText(nom);
        compteur.activeCompteur.setVisibility(
                portant ? View.VISIBLE : View.GONE);
        if (portant) {
            compteur.activeCompteur.setText(
                    hote.texte(R.string.agent_usages_actif));
            compteur.activeCompteur.poserTeinte(
                    jo.joide.app.android.ui.Graphite.melange(
                            hote.palette().accent, 0.16f,
                            hote.palette().surface),
                    hote.palette().accent);
        }
        compteur.pourcentageCompteur.setText(hote.texte(
                R.string.agent_usages_occupation,
                (int) Math.round(fraction * 100)));
        float rempli = (float) Math.max(0.02, Math.min(1, fraction));
        LinearLayout.LayoutParams paramsRemplissage =
                (LinearLayout.LayoutParams)
                        compteur.remplissageCompteur.getLayoutParams();
        paramsRemplissage.weight = rempli;
        compteur.remplissageCompteur.setLayoutParams(paramsRemplissage);
        LinearLayout.LayoutParams paramsReste =
                (LinearLayout.LayoutParams)
                        compteur.resteCompteur.getLayoutParams();
        paramsReste.weight = 1f - rempli;
        compteur.resteCompteur.setLayoutParams(paramsReste);
        // la teinte suit le seuil : accent sous 80 %, alerte au seuil,
        // erreur au plafond
        int couleur = fraction >= 1 ? hote.palette().erreur
                : fraction >= SEUIL_BANDE ? hote.palette().alerte
                : hote.palette().accent;
        compteur.remplissageCompteur.getBackground().mutate()
                .setTint(couleur);
        // le pied : « N sur M utilisés » + « réinitialisé dans X »
        compteur.utiliseCompteur.setText(plafond > 0
                ? hote.texte(R.string.agent_usages_utilise,
                        utilise, plafond) : "");
        majRelance(compteur, relanceMs);
    }

    /** Le « réinitialisé dans X » d'un compteur (−1 : rien). */
    private void majRelance(
            ItemCompteurUsagesBinding compteur, long relanceMs) {
        compteur.relanceCompteur.setText(relanceMs > 0
                ? hote.texte(R.string.agent_usages_relance,
                        texteRelance(hote, relanceMs)) : "");
    }

    /**
     * L'horloge du décompte (une seconde par pas) : la fenêtre
     * glissante se vide au fil de l'eau, le « réinitialisé dans X »
     * DÉCOMPTE à l'écran tant que le popup vit.
     */
    private void relancerHorloge(PopupUsagesBinding corps) {
        arreterHorloge();
        horloge = new android.os.Handler(
                android.os.Looper.getMainLooper());
        horloge.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (horloge == null) {
                    return;
                }
                long[] relances = hote.agent().relancesModele();
                long maintenant = System.currentTimeMillis();
                majRelance(corps.compteurRequetesUsages,
                        relances[0] > 0 ? relances[0] - maintenant : -1);
                majRelance(corps.compteurJetonsUsages,
                        relances[1] > 0 ? relances[1] - maintenant : -1);
                horloge.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /** Retire l'horloge (fermeture du popup, rafraîchissement). */
    private void arreterHorloge() {
        if (horloge != null) {
            horloge.removeCallbacksAndMessages(null);
            horloge = null;
        }
    }

    /**
     * Le texte de relance lisible (« 12 s », puis « 3 min 05 », puis
     * « 2 h 14 », puis « 1 j 3 h » — la borne 48 h).
     *
     * @param hote l'hôte (les chaînes localisées)
     * @param ms   l'écart en millisecondes
     * @return la forme compacte (« » si l'écart est nul ou négatif)
     */
    static String texteRelance(HoteAtelier hote, long ms) {
        if (ms <= 0) {
            return "";
        }
        long secondes = (ms + 999) / 1000;
        if (secondes < 60) {
            return hote.texte(
                    R.string.agent_usages_relance_secondes,
                    (int) secondes);
        }
        long minutes = secondes / 60;
        if (minutes < 60) {
            return hote.texte(R.string.agent_usages_relance_minutes,
                    (int) minutes, (int) (secondes % 60));
        }
        if (minutes < 48 * 60) {
            return hote.texte(R.string.agent_usages_relance_heures,
                    (int) (minutes / 60), (int) (minutes % 60));
        }
        return hote.texte(R.string.agent_usages_relance_jours,
                (int) (minutes / 1440), (int) (minutes % 1440 / 60));
    }
}
