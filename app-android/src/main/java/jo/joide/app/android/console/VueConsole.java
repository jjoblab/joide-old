package jo.joide.app.android.console;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;

import java.util.List;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.R;
import jo.joide.app.android.console.EtatConsole.Etat;
import jo.joide.app.android.console.EtatConsole.Filtre;
import jo.joide.app.android.databinding.CarteProblemeConsoleBinding;
import jo.joide.app.android.databinding.VueConsoleBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Le panneau console de build (lot K, ADR-É6 ; lot A5, ADR-085 — la
 * vue quitte le code) : en-tête « Console de build » + projet + pilule
 * d'état + actions (arrêter, vider), bande de phases cliquables (tap =
 * filtre), filtres « Tout / Erreurs (n) / Système », transcript en
 * grille deux colonnes colorée par nature — chaque ligne reste du texte
 * sélectionnable/copiable —, lignes de problème cliquables, rangée
 * d'action « Ouvrir le fichier à la ligne » et plafond annoncé. La vue
 * ne décide rien : l'état pur {@link EtatConsole} arrive posé, elle le
 * traduit.
 *
 * <p>La hiérarchie vit dans {@code vue_console.xml} (un
 * {@code <merge>} : cette classe EST le conteneur vertical, un niveau
 * aplati — constat I3) ; le transcript et la bande de phases sont
 * RECYCLÉS ({@code RecyclerView + ListAdapter}, décision 11 — plus de
 * {@code removeAllViews()} suivie d'une ré-inflation à chaque
 * {@link #maj}, constat I4) ; la carte du problème localisé est gonflée
 * UNE fois, sa visibilité suit l'état. Les couleurs par RÔLE de la
 * pilule d'état se posent au bind (décision 8 : l'état du build
 * décide).</p>
 */
public final class VueConsole extends LinearLayout {

    /** L'auditeur des actions de la console. */
    public interface AuditeurConsole {
        /** Arrêter le build en cours. */
        void surArreter();

        /** Vider le transcript (confirmation portée par l'écran). */
        void surVider();

        /** Le filtre change. */
        void surFiltre(Filtre filtre);

        /** Le filtre par phase change (null = toutes les phases). */
        void surPhase(ConsoleBuild.Phase phase);

        /** Ouvrir la cible d'un problème (« fichier:ligne »). */
        void surOuvrir(String cible);
    }

    /** La coque gonflée du XML (vue_console.xml, un <merge>). */
    private final VueConsoleBinding binding;

    /** La bande de phases recyclée (≤ 3 pilules, tap = filtre). */
    private final AdaptateurPhases phases;

    /** Le transcript recyclé (plafond 400 conservé). */
    private final AdaptateurConsole transcript;

    /** La carte du problème localisé (include de vue_console). */
    private final CarteProblemeConsoleBinding carteProbleme;

    private final Graphite.Palette palette;
    private AuditeurConsole auditeur;

    public VueConsole(Context contexte) {
        this(contexte, null);
    }

    public VueConsole(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(VERTICAL);
        palette = GraphiteVue.palette(contexte);
        setBackgroundColor(palette.puits);

        binding = VueConsoleBinding.inflate(
                LayoutInflater.from(contexte), this);
        carteProbleme = binding.zoneProbleme;

        // la bande de phases : pilules cliquables (tap = filtre)
        phases = new AdaptateurPhases(phase -> {
            if (auditeur != null) {
                auditeur.surPhase(phase);
            }
        });
        binding.bandePhases.setLayoutManager(
                new LinearLayoutManager(contexte,
                        LinearLayoutManager.HORIZONTAL, false));
        binding.bandePhases.setAdapter(phases);

        // le transcript : les lignes changent par DiffUtil, le flux
        // vit au bas (le défilement suit le dernier événement)
        transcript = new AdaptateurConsole(cible -> {
            if (auditeur != null) {
                auditeur.surOuvrir(cible);
            }
        });
        binding.transcript.setLayoutManager(
                new LinearLayoutManager(contexte));
        binding.transcript.setAdapter(transcript);

        // les filtres « Tout / Erreurs (n) / Système » : le cochage
        // suit l'état, le tap prévient l'auditeur (l'état décide)
        poserFiltre(binding.filtreTout, Filtre.TOUT);
        poserFiltre(binding.filtreErreurs, Filtre.ERREURS);
        poserFiltre(binding.filtreSysteme, Filtre.SYSTEME);

        binding.arreterConsole.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surArreter();
            }
        });
        binding.viderConsole.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surVider();
            }
        });
        carteProbleme.ouvrirProbleme.setOnClickListener(vue -> {
            Object cible = carteProbleme.ouvrirProbleme.getTag();
            if (auditeur != null && cible instanceof String chaine) {
                auditeur.surOuvrir(chaine);
            }
        });
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurConsole ecouteur) {
        this.auditeur = ecouteur;
    }

    /** Le tap d'un filtre prévient l'auditeur (l'état pose le cochage). */
    private void poserFiltre(Chip puce, Filtre nature) {
        puce.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surFiltre(nature);
            }
        });
    }

    /**
     * Traduit l'état pur : pilule d'état, phases, filtres, transcript
     * filtré, plafond, rangée du problème localisé.
     */
    public void maj(EtatConsole etat, String nomProjet) {
        Context contexte = getContext();
        binding.projetConsole.setText(
                nomProjet == null ? "" : " · " + nomProjet);

        String libelle;
        int couleur;
        switch (etat.etat()) {
            case EN_COURS -> {
                libelle = contexte.getString(R.string.console_etat_encours,
                        LibellesConsole.libellePhase(contexte,
                                etat.phaseCourante()));
                couleur = palette.accent;
            }
            case REUSSI -> {
                libelle = contexte.getString(R.string.console_etat_reussi,
                        EtatConsole.secondes(etat.dureeDernierBuildMs()));
                couleur = palette.reussite;
            }
            case ECHOUE -> {
                libelle = contexte.getString(R.string.console_etat_echoue);
                couleur = palette.erreur;
            }
            case INTERROMPU -> {
                libelle = contexte.getString(
                        R.string.console_etat_interrompu);
                couleur = palette.alerte;
            }
            default -> {
                libelle = contexte.getString(R.string.console_etat_vide);
                couleur = palette.texteSecondaire;
            }
        }
        binding.libelleEtat.setText(libelle);
        binding.libelleEtat.setTextColor(
                etat.etat() == Etat.VIDE ? palette.texteSecondaire
                        : couleur);
        Drawable point = binding.pointEtat.getBackground();
        if (point != null) {
            point.setTint(couleur);
        }
        binding.arreterConsole.setVisibility(
                etat.etat() == Etat.EN_COURS ? VISIBLE : GONE);

        phases.submitList(etat.phases());

        binding.filtreTout.setText(LibellesConsole.libelleFiltre(
                contexte, Filtre.TOUT, 0));
        binding.filtreErreurs.setText(LibellesConsole.libelleFiltre(
                contexte, Filtre.ERREURS, etat.compteErreurs()));
        binding.filtreTout.setChecked(etat.filtre() == Filtre.TOUT);
        binding.filtreErreurs.setChecked(etat.filtre() == Filtre.ERREURS);
        binding.filtreSysteme.setChecked(etat.filtre() == Filtre.SYSTEME);

        List<ConsoleBuild.Ligne> visibles = etat.lignesFiltrees();
        // le transcript suit la fin (les événements vivants) — le RAPPEL
        // DE COMMIT porte le scroll : le diff court en tâche de fond, un
        // simple post() passerait AVANT le commit et raterait la dernière
        // ligne (reprise d'audit : le post() du travail hérité courait
        // la course)
        transcript.submitList(visibles, () -> {
            int nombre = transcript.getItemCount();
            if (nombre > 0) {
                binding.transcript.scrollToPosition(nombre - 1);
            }
        });

        binding.legendPlafond.setText(etat.plafonne()
                ? contexte.getString(R.string.console_plafond,
                        ConsoleBuild.PLAFOND_LIGNES)
                : (etat.lignes().isEmpty()
                        ? contexte.getString(R.string.console_etat_vide)
                        : ""));
        binding.legendPlafond.setVisibility(
                etat.plafonne() || etat.lignes().isEmpty()
                        ? VISIBLE : GONE);

        ConsoleBuild.Ligne probleme = etat.premierProblemeLocalise();
        carteProbleme.getRoot().setVisibility(
                probleme == null ? GONE : VISIBLE);
        if (probleme != null) {
            carteProbleme.titreProbleme.setText(
                    contexte.getString(R.string.console_probleme_titre,
                            probleme.cible()));
            carteProbleme.detailProbleme.setText(
                    probleme.texte());
            carteProbleme.ouvrirProbleme.setTag(probleme.cible());
        }
    }
}
