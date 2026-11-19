package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import jo.joide.app.android.atelier.ModeleOnglets;
import jo.joide.app.android.databinding.AtelierOngletsBinding;

/**
 * Atelier v2, lot L2 (ADR-083) — la bande d'onglets (§3.2), recyclée
 * au lot A6 (ADR-085 décisions 10 et 11) : la vue est MINCE, elle
 * gonfle {@code atelier_onglets} et DESSINE depuis le modèle pur
 * {@link ModeleOnglets} par un {@code RecyclerView} horizontal +
 * {@code ListAdapter}/DiffUtil (onglets fermables : l'item_onglet du
 * lot L2 garde son indicateur 2 dp, ses points d'état et sa croix).
 * Le propriétaire de l'état reste {@code EtatAtelier} ; le fil
 * d'Ariane vit dans sa propre vue ({@link FilAriane}).
 */
public final class BarreOnglets extends android.widget.FrameLayout {

    /** Un auditeur d'onglets (activation, fermeture). */
    public interface Auditeur {
        /** Un onglet est activé (tap). */
        void surActivation(String chemin);

        /** L'onglet demandé est fermé (×). */
        void surFermeture(String chemin);

        /** Le bouton d'aperçu est demandé. */
        void surApercu();
    }

    /** Le binding de la bande. */
    public final AtelierOngletsBinding binding;

    private Auditeur auditeur;
    private final AdaptateurOnglets adaptateur;

    public BarreOnglets(Context contexte) {
        this(contexte, null);
    }

    public BarreOnglets(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        binding = AtelierOngletsBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        adaptateur = new AdaptateurOnglets(new AdaptateurOnglets.Auditeur() {
            @Override
            public void surActivation(String chemin) {
                if (auditeur != null) {
                    auditeur.surActivation(chemin);
                }
            }

            @Override
            public void surFermeture(String chemin) {
                if (auditeur != null) {
                    auditeur.surFermeture(chemin);
                }
            }
        });
        binding.rangeeOnglets.setLayoutManager(
                new LinearLayoutManager(contexte,
                        LinearLayoutManager.HORIZONTAL, false));
        binding.rangeeOnglets.setAdapter(adaptateur);
        binding.boutonApercu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surApercu();
            }
        });
    }

    /** Pose l'auditeur (l'écran garde le câblage). */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** Le bouton d'aperçu (visible téléphone, onglet XML actif). */
    public View boutonApercu() {
        return binding.boutonApercu;
    }

    /**
     * Redessine la bande depuis le modèle pur : le diff borne le coût
     * (l'activation ne re-lier que l'onglet changé), l'actif visible
     * par défilement minimal — le RAPPEL DE COMMIT de
     * {@code submitList} porte le scroll (le diff court en tâche de
     * fond : un post() direct passerait avant le commit et raterait
     * l'onglet, la leçon du lot A5).
     */
    public void poser(ModeleOnglets modele) {
        adaptateur.submitList(OngletVu.depuis(modele), () -> {
            int index = modele.indexActif();
            if (index < 0) {
                return;
            }
            binding.rangeeOnglets.post(() -> {
                LinearLayoutManager rangement =
                        (LinearLayoutManager)
                                binding.rangeeOnglets.getLayoutManager();
                if (rangement == null) {
                    return;
                }
                View actifVue = rangement.findViewByPosition(index);
                if (actifVue != null) {
                    int cible = Math.max(0, actifVue.getLeft()
                            - binding.rangeeOnglets.getWidth() / 2
                            + actifVue.getWidth() / 2);
                    View premier = rangement.getChildAt(0);
                    int courant = premier == null ? 0
                            : premier.getLeft();
                    binding.rangeeOnglets.smoothScrollBy(
                            cible - courant, 0);
                } else {
                    // hors écran : la position défile, l'onglet entre
                    // par la moitié de la bande
                    rangement.scrollToPositionWithOffset(index,
                            Math.max(0, binding.rangeeOnglets.getWidth()
                                    / 2));
                }
            });
        });
    }
}
