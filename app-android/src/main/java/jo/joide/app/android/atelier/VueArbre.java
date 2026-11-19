package jo.joide.app.android.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence.Noeud;
import jo.joide.app.android.databinding.VueArbreBinding;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Le tiroir arborescence de l'atelier (lot J, ADR-É5 ; lot A5,
 * ADR-085 — la vue quitte le code) : « Nouveau » (fichier/dossier) en
 * tête, l'arbre de {@link Arborescence} avec entrée active surlignée,
 * et les sorties de build estompées avec leur raison — jamais
 * présentées comme sources. La vue ne décide rien : elle reçoit les
 * nœuds composés par l'état pur.
 *
 * <p>La hiérarchie vit dans {@code vue_arbre.xml} (un
 * {@code <merge>} : cette classe EST le conteneur vertical, un niveau
 * aplati — constat I3) ; l'arbre est RECYCLÉ
 * ({@code RecyclerView + ListAdapter}, décision 11 — plus de
 * {@code removeAllViews()} suivie d'une ré-inflation de toutes les
 * lignes à chaque dépliage, constat I4) ; la COMPOSITION des éléments
 * (racine masquée, note de franchise, plafond 400 annoncé) est pure
 * dans {@link ElementArbre}.</p>
 */
public final class VueArbre extends LinearLayout {

    /**
     * Le plafond d'affichage du tiroir (lignes visibles) — un arbre
     * déplié de fond se coupe avec sa raison, jamais un affichage
     * rendu muet ; la note invite à refermer des dossiers.
     */
    public static final int PLAFOND_NOEUDS = 400;

    /** L'auditeur des actions du tiroir. */
    public interface AuditeurArbre {
        /** L'appui long demande le menu contextuel du nœud (§3.8). */
        void surMenuContextuel(String chemin, boolean dossier);
        /** Un dossier est plié/déplié. */
        void surDossier(String chemin, boolean ouvrir);

        /** Un fichier est ouvert. */
        void surFichier(String chemin);

        /** « Nouveau » : fichier (vrai) ou dossier (faux). */
        void surNouveau(boolean fichier);
    }

    /** La coque gonflée du XML (vue_arbre.xml, un <merge>). */
    private final VueArbreBinding binding;

    /** L'arbre recyclé. */
    private final AdaptateurArbre adaptateur;

    private AuditeurArbre auditeur;
    private boolean sortiesEnCours;

    public VueArbre(Context contexte) {
        this(contexte, null);
    }

    public VueArbre(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(VERTICAL);
        setBackgroundColor(GraphiteVue.palette(contexte).surface);

        binding = VueArbreBinding.inflate(
                LayoutInflater.from(contexte), this);
        binding.nouveauArbre.setOnClickListener(vue -> proposerNouveau());

        adaptateur = new AdaptateurArbre(new AdaptateurArbre.Auditeur() {
            @Override
            public void surMenuContextuel(String chemin, boolean dossier) {
                if (auditeur != null) {
                    auditeur.surMenuContextuel(chemin, dossier);
                }
            }

            @Override
            public void surDossier(String chemin, boolean ouvrir) {
                if (auditeur != null) {
                    auditeur.surDossier(chemin, ouvrir);
                }
            }

            @Override
            public void surFichier(String chemin) {
                if (auditeur != null) {
                    auditeur.surFichier(chemin);
                }
            }
        });
        binding.arbre.setLayoutManager(
                new LinearLayoutManager(contexte));
        binding.arbre.setAdapter(adaptateur);
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurArbre ecouteur) {
        auditeur = ecouteur;
    }

    /** Estompe les sorties pendant qu'elles se construisent (leçon 5). */
    public void setSortiesEnCours(boolean enCours) {
        this.sortiesEnCours = enCours;
    }

    /** Pose l'arbre : les nœuds visibles composés par l'état pur. */
    public void setArbre(List<Noeud> noeuds, String nomProjet) {
        binding.nomArbre.setText(nomProjet == null ? "" : nomProjet);
        adaptateur.submitList(ElementArbre.composer(noeuds, sortiesEnCours,
                PLAFOND_NOEUDS));
    }

    private void proposerNouveau() {
        Context contexte = getContext();
        String fichier = contexte.getString(R.string.atelier_nouveau_fichier);
        String dossier = contexte.getString(R.string.atelier_nouveau_dossier);
        Dialogue.menu(contexte, GraphiteVue.palette(contexte),
                contexte.getString(R.string.atelier_nouveau),
                new CharSequence[]{fichier, dossier},
                (dialog, lequel) -> {
                    if (auditeur != null) {
                        auditeur.surNouveau(lequel == 0);
                    }
                });
    }
}
