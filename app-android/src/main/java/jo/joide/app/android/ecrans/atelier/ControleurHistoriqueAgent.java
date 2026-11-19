package jo.joide.app.android.ecrans.atelier;

import android.view.LayoutInflater;
import android.view.View;

import java.util.List;

import jo.joide.app.android.AgentService;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.agent.ConversationsAgent;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.databinding.ItemConversationHistoriqueBinding;
import jo.joide.app.android.databinding.PopupHistoriqueBinding;
import jo.joide.app.android.parametres.Reglages;

/**
 * Lot A7 (ADR-085, découpage de {@code ControleurAgent}) — le
 * contrôleur de l'HISTORIQUE des conversations : la liste des
 * conversations du service (titre dérivé du premier message, nombre
 * d'échanges, la courante marquée, la suppression demandée nommément),
 * le cycle de vie (nouvelle conversation, reprise) et le JOURNAL
 * REJOUÉ (un écran qui revient retrouve l'état exact — lot P,
 * ADR-É11). Le popup vit dans {@code popup_historique.xml} gonflé
 * par binding : aucune vue du cadre n'est instanciée ici.
 */
final class ControleurHistoriqueAgent {

    private final HoteAtelier hote;

    ControleurHistoriqueAgent(HoteAtelier hote) {
        this.hote = hote;
    }

    /**
     * L'historique des conversations (centré avec voile au lot A7,
     * décision 12) : la courante marquée, la suppression nommée.
     */
    void ouvrir() {
        if (hote.agent() == null) {
            return;
        }
        List<ConversationsAgent.Resume> resumes =
                hote.agent().conversations();
        PopupHistoriqueBinding corps = PopupHistoriqueBinding.inflate(
                LayoutInflater.from(hote.contexte()));
        corps.titreHistorique.setText(R.string.agent_historique_titre);
        corps.fermerHistorique.setOnClickListener(
                vue -> hote.popups().fermer());
        corps.videHistorique.setVisibility(
                resumes.isEmpty() ? View.VISIBLE : View.GONE);
        if (resumes.isEmpty()) {
            corps.videHistorique.setText(hote.texte(
                    R.string.agent_historique_vide,
                    ConversationsAgent.CONVERSATIONS_MAX));
        }
        String active = hote.agent().conversationActive();
        java.text.DateFormat formatDate = java.text.DateFormat
                .getDateInstance(java.text.DateFormat.SHORT);
        java.text.DateFormat formatHeure = java.text.DateFormat
                .getTimeInstance(java.text.DateFormat.SHORT);
        LayoutInflater gonfleur = LayoutInflater.from(hote.contexte());
        for (ConversationsAgent.Resume resume : resumes) {
            boolean estActive = resume.id.equals(active);
            ItemConversationHistoriqueBinding ligne =
                    ItemConversationHistoriqueBinding.inflate(gonfleur,
                            corps.listeConversations, true);
            poserLigne(ligne, resume, estActive,
                    formatDate, formatHeure);
        }
        hote.popups().centrer(corps.getRoot(),
                hote.texte(R.string.agent_historique_titre));
    }

    /** Une ligne de l'historique : titre, méta, la courante marquée. */
    private void poserLigne(
            ItemConversationHistoriqueBinding ligne,
            ConversationsAgent.Resume resume, boolean active,
            java.text.DateFormat formatDate,
            java.text.DateFormat formatHeure) {
        // la carte COURANTE porte surface haute + liseré accent ; les
        // autres gardent surface + bord-fin (deux drawables déclarés)
        ligne.getRoot().setBackgroundResource(active
                ? R.drawable.ligne_popup_active
                : R.drawable.ligne_popup);
        ligne.titreConversation.setText(resume.titre.isEmpty()
                ? hote.texte(R.string.agent_historique_sans_titre)
                : resume.titre);
        ligne.metaConversation.setTextColor(active
                ? hote.palette().accent : hote.palette().texteEstompe);
        ligne.metaConversation.setText((active
                ? hote.texte(R.string.agent_historique_active) + " · "
                : "")
                + hote.texte(R.string.agent_historique_echanges,
                        resume.echanges)
                + " · " + formatDate.format(new java.util.Date(
                        resume.modifiee))
                + " " + formatHeure.format(new java.util.Date(
                        resume.modifiee)));
        if (active) {
            ligne.reprendreConversation.setVisibility(View.GONE);
            ligne.supprimerConversation.setVisibility(View.GONE);
        } else {
            // reprise d'audit : les libellés d'hier restaient à poser
            // (les lignes d'action se montraient MUETTES)
            ligne.reprendreConversation.setText(
                    R.string.agent_menu_historique);
            ligne.reprendreConversation.setOnClickListener(vue ->
                    reprendre(resume.id));
            ligne.getRoot().setOnClickListener(vue ->
                    reprendre(resume.id));
            ligne.supprimerConversation.setText(
                    R.string.agent_supprimer_conversation);
            ligne.supprimerConversation.setOnClickListener(vue ->
                    demanderSuppression(resume.id));
        }
    }

    /** Nouvelle conversation : le service garde la vérité, l'état suit. */
    void nouvelle() {
        if (hote.agent() == null) {
            return;
        }
        if (!hote.agent().nouvelleConversation()) {
            hote.popups().information(
                    hote.texte(R.string.agent_menu_titre),
                    hote.texte(R.string.agent_tour_en_cours),
                    hote.texte(R.string.action_ok));
            return;
        }
        rejouerJournal();
    }

    /** Reprend une conversation : l'état du tiroir suit le service. */
    void reprendre(String id) {
        if (hote.agent() == null) {
            return;
        }
        if (!hote.agent().reprendreConversation(id)) {
            hote.popups().information(
                    hote.texte(R.string.agent_menu_titre),
                    hote.texte(R.string.agent_tour_en_cours),
                    hote.texte(R.string.action_ok));
            return;
        }
        rejouerJournal();
    }

    /** Supprime une conversation : la demande est nommée et confirmée. */
    private void demanderSuppression(String id) {
        if (hote.agent() == null) {
            return;
        }
        hote.popups().confirmation(
                hote.texte(R.string.agent_supprimer_conversation),
                hote.texte(R.string.agent_supprimer_detail),
                hote.texte(R.string.action_annuler),
                hote.texte(R.string.agent_menu_historique),
                () -> {
                    if (hote.agent() != null) {
                        hote.agent().supprimerConversation(id);
                    }
                    ouvrir();
                });
    }

    /**
     * Rejoue le journal du service dans l'état pur (retour d'écran,
     * conversation nouvelle ou reprise) : l'écran retrouve l'état
     * EXACT (même mi-outils).
     */
    void rejouerJournal() {
        if (hote.agent() == null) {
            return;
        }
        hote.poserEtatAgent(new EtatAgentChat());
        for (AgentService.Entree entree : hote.agent().journal()) {
            if (entree.estMessage()) {
                hote.etatAgent().envoyer(entree.message, entree.pieces);
            } else if (entree.estReponse()) {
                hote.etatAgent().restaurerReponse(entree.reponse);
            } else {
                hote.etatAgent().surEvenement(entree.evenement);
            }
        }
        // rapporté le 2026-11-21 : l'état NEUF ci-dessus avait oublié
        // le modèle — le journal ne le porte pas, la pastille de
        // l'en-tête disait « aucun modèle » après chaque rejeu (le
        // redémarrage compris) : le choix PERSISTÉ rejoint l'état
        hote.etatAgent().poserModele(
                Reglages.lire(hote.sourceReglages()).modeleIa);
        majQuestions();
        hote.vueAgent().poserEtat(hote.etatAgent());
    }

    /** Les questions en attente deviennent une carte (ask_user). */
    void majQuestions() {
        if (hote.agent() == null) {
            return;
        }
        List<jo.joide.agent.espace.QuestionUtilisateur>
                questions = hote.agent().questionsEnAttente();
        if (questions != null && !questions.isEmpty()
                && hote.etatAgent().reponses() == null) {
            hote.etatAgent().poserQuestions(questions);
        }
    }
}
