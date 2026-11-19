package jo.joide.app.android.ecrans.atelier;

import android.view.LayoutInflater;
import android.view.View;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.databinding.ItemAssiseUsagesBinding;
import jo.joide.app.android.databinding.PopupUsagesBinding;

/**
 * Lot A7 (ADR-085, découpage de {@code ControleurAgent}) — le
 * contrôleur des USAGES de l'agent : un COMPTEUR par fenêtre plafonnée
 * (requêtes, jetons), sa bande colorée et son pourcentage, puis les
 * trois pieds de la conversation et l'état du banc de clés (lot T,
 * ADR-065 — la manière CodeAssist). Le popup porte son bouton
 * d'actualisation — l'occupation se relit sans rouvrir. Corps dans
 * {@code popup_usages.xml} gonflé par binding : aucune vue du cadre
 * n'est instanciée ici.
 *
 * <p>Lot C4 (2026-11-20, demande du terrain : « l'écran usage, mêmes
 * fonctionnalités » — consultation clean-room n° 4, mandat du
 * propriétaire) : chaque compteur porte son « N sur M utilisés » et
 * son « réinitialisé dans X » (la sortie de la plus ancienne requête
 * de la fenêtre glissante — l'horloge d'écran le fait décompter à la
 * seconde) ; la puce « En cours » marque la fenêtre qui PORTE le
 * modèle (la plus chargée — le chip « In use » de CodeAssist).</p>
 */
final class ControleurUsagesAgent {

    private final HoteAtelier hote;

    /** L'horloge du décompte (posée au bind, retirée à la fermeture). */
    private android.os.Handler horloge;

    ControleurUsagesAgent(HoteAtelier hote) {
        this.hote = hote;
    }

    /**
     * L'écran Usages (centré avec voile au lot A7, décision 12) :
     * le popup se suffit (Actualiser, Fermeture), jamais un seul OK.
     */
    void ouvrir() {
        if (hote.agent() == null || hote.vueAgent() == null) {
            return;
        }
        arreterHorloge();
        PopupUsagesBinding corps = PopupUsagesBinding.inflate(
                LayoutInflater.from(hote.contexte()));
        corps.titreUsages.setText(R.string.agent_usages_titre);
        corps.rafraichirUsages.setOnClickListener(vue -> construire(corps));
        corps.fermerUsages.setOnClickListener(vue -> {
            arreterHorloge();
            hote.popups().fermer();
        });
        construire(corps);
        hote.popups().centrer(corps.getRoot(),
                hote.texte(R.string.agent_usages_titre));
    }

    /** Le corps de l'écran Usages : compteurs, pieds, banc. */
    private void construire(PopupUsagesBinding corps) {
        // ---- les compteurs du régulateur (l'occupation des fenêtres
        // du modèle courant — la bande prévient AVANT le 429) ----
        double[] occupations = hote.agent().occupationsModele();
        long[] compteurs = hote.agent().compteursModele();
        long[] relances = hote.agent().relancesModele();
        boolean plafondConnu = compteurs[1] > 0 || compteurs[3] > 0;
        // les compteurs se montrent PLEINS ou pas du tout : sans plafond
        // appris, deux lignes vides ne s'affichent jamais — l'écran le
        // dit honnêtement à la place (reprise d'audit : le libellé et
        // la visibilité avaient été perdus à la migration)
        corps.compteurRequetesUsages.getRoot().setVisibility(
                plafondConnu ? View.VISIBLE : View.GONE);
        corps.compteurJetonsUsages.getRoot().setVisibility(
                plafondConnu ? View.VISIBLE : View.GONE);
        corps.sansPlafondUsages.setVisibility(
                plafondConnu ? View.GONE : View.VISIBLE);
        if (!plafondConnu) {
            corps.sansPlafondUsages.setText(hote.texte(
                    R.string.agent_usages_sans_plafond));
        }
        if (plafondConnu) {
            // la fenêtre qui PORTE le modèle (la plus chargée) reçoit
            // la puce « En cours » — le chip In use de CodeAssist
            boolean requetesPortent = compteurs[1] > 0
                    && occupations[0] >= occupations[1];
            boolean jetonsPortent = !requetesPortent && compteurs[3] > 0;
            long maintenant = System.currentTimeMillis();
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

        // ---- les trois pieds de la conversation (ce qui est facturé
        // est facturé — la légende compacte du précédent CodeAssist) ----
        jo.joide.agent.UsageLlm usage = hote.agent().usageConversation();
        corps.conversationUsages.setText(hote.texte(
                R.string.agent_usages_conversation_compacte,
                EtatAgentChat.jetonsCompacts(usage.entrees),
                EtatAgentChat.jetonsCompacts(usage.sorties),
                EtatAgentChat.jetonsCompacts(usage.lecturesCache)));

        // ---- l'état du banc de clés ----
        int taille = hote.agent().tailleBanc();
        corps.bancUsages.setText(taille == 0
                ? hote.texte(R.string.agent_usages_banc_vide)
                : hote.texte(R.string.agent_usages_banc, taille));

        corps.assisesUsages.removeAllViews();
        LayoutInflater gonfleur = LayoutInflater.from(hote.contexte());
        for (var assise : hote.agent().bancParRaison().entrySet()) {
            ItemAssiseUsagesBinding ligne = ItemAssiseUsagesBinding
                    .inflate(gonfleur, corps.assisesUsages, true);
            ligne.assiseUsages.setText(hote.texte(
                    R.string.agent_usages_assis,
                    assise.getValue(), libelleGenre(assise.getKey())));
        }
    }

    /**
     * Un compteur d'occupation (lot C4 — la LIGNE COMPLÈTE de
     * CodeAssist) : le nom, la puce « En cours » sur la fenêtre qui
     * porte, le pourcentage, la bande dont le REMPLISSAGE suit la
     * fraction et la teinte (accent, alerte, erreur — BandesUsage,
     * ADR-062 : l'alerte précède le 429 ; couleur calculée par ligne,
     * décision 8), le pied « N sur M utilisés » et « réinitialisé
     * dans X » (la sortie de la plus ancienne requête de la fenêtre
     * glissante).
     */
    private void poserCompteur(
            jo.joide.app.android.databinding.ItemCompteurUsagesBinding
                    compteur, String nom, double fraction,
            long utilise, long plafond, boolean portant,
            long relanceMs) {
        compteur.nomCompteur.setText(nom);
        compteur.activeCompteur.setVisibility(
                portant ? View.VISIBLE : View.GONE);
        if (portant) {
            compteur.activeCompteur.setText(
                    hote.texte(R.string.agent_usages_actif));
        }
        compteur.pourcentageCompteur.setText(hote.texte(
                R.string.agent_usages_occupation,
                (int) Math.round(fraction * 100)));
        float rempli = (float) Math.max(0.02, Math.min(1, fraction));
        android.widget.LinearLayout.LayoutParams paramsRemplissage =
                (android.widget.LinearLayout.LayoutParams)
                        compteur.remplissageCompteur.getLayoutParams();
        paramsRemplissage.weight = rempli;
        compteur.remplissageCompteur.setLayoutParams(paramsRemplissage);
        android.widget.LinearLayout.LayoutParams paramsReste =
                (android.widget.LinearLayout.LayoutParams)
                        compteur.resteCompteur.getLayoutParams();
        paramsReste.weight = 1f - rempli;
        compteur.resteCompteur.setLayoutParams(paramsReste);
        // la teinte dit la bande (accent, alerte, erreur)
        jo.joide.agent.BandesUsage.Bande bande =
                jo.joide.agent.BandesUsage.bandeDe(fraction);
        int couleur = switch (bande) {
            case CRITIQUE -> hote.palette().erreur;
            case ATTENTION -> hote.palette().alerte;
            default -> hote.palette().accent;
        };
        compteur.remplissageCompteur.getBackground().mutate()
                .setTint(couleur);
        // le pied (lot C4) : « N sur M utilisés » + « réinitialisé dans X »
        compteur.utiliseCompteur.setText(plafond > 0
                ? hote.texte(R.string.agent_usages_utilise,
                        utilise, plafond) : "");
        majRelance(compteur, relanceMs);
    }

    /** Le « réinitialisé dans X » d'un compteur (−1 : rien). */
    private void majRelance(
            jo.joide.app.android.databinding.ItemCompteurUsagesBinding
                    compteur, long relanceMs) {
        compteur.relanceCompteur.setText(relanceMs > 0
                ? hote.texte(R.string.agent_usages_relance,
                        texteRelance(hote, relanceMs)) : "");
    }

    /**
     * L'horloge du décompte (une seconde par pas, lot C4) : la fenêtre
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
     * Le texte de relance lisible (CodeAssist : « 12 s », puis
     * « 3 min 05 », puis « 2 h 14 », puis « 1 j 3 h » — la borne 48 h
     * de CodeAssist).
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

    /** Le libellé localisé d'un genre d'assise du banc. */
    private CharSequence libelleGenre(
            jo.joide.agent.ErreursLlm.Genre genre) {
        return hote.texte(switch (genre) {
            case LIMITE_DEBIT -> R.string.agent_genre_limite_debit;
            case LIMITE_JOUR -> R.string.agent_genre_limite_jour;
            case QUOTA_EPUISE -> R.string.agent_genre_quota;
            case CLE_INVALIDE -> R.string.agent_genre_cle_invalide;
            case CLE_SANS_DROIT -> R.string.agent_genre_cle_sans_droit;
            default -> R.string.agent_genre_autre;
        });
    }
}
