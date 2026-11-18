package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import jo.joide.app.android.R;
import jo.joide.app.android.databinding.TiroirFichiersBinding;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L4 (ADR-083) — le tiroir fichiers (§3.8) : la vue
 * MINCE qui gonfle {@code tiroir_fichiers} et accueille l'arbre
 * existant ({@link jo.joide.app.android.atelier.VueArbre}). Actions
 * réelles (nouveau fichier/dossier via l'écran, tout réduire,
 * localiser le fichier actif), champ de filtre branché sur
 * {@code VueArbre.filtrer} (pur). Pas de rangée projet (§3.8 : le
 * nom vit dans la barre supérieure) ; le sélecteur attend qu'une
 * deuxième section soit réellement implémentée (franchise).
 */
public final class TiroirFichiers extends FrameLayout {

    /** Un auditeur des actions du tiroir. */
    public interface Auditeur {
        /** Nouveau fichier / dossier (l'écran ouvre son dialogue). */
        void surNouveau(boolean fichier);

        /** Tout réduire (les dossiers ouverts se ferment). */
        void surToutReduire();

        /** Localiser le fichier actif (les ancêtres s'ouvrent). */
        void surLocaliser();

        /** Le filtre texte a changé (vide = tout). */
        void surFiltre(String texte);
    }

    /** Le binding du tiroir. */
    public final TiroirFichiersBinding binding;

    private Auditeur auditeur;

    public TiroirFichiers(Context contexte) {
        this(contexte, null);
    }

    public TiroirFichiers(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        binding = TiroirFichiersBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        binding.actionNouveauFichier.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surNouveau(true);
            }
        });
        binding.actionNouveauDossier.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surNouveau(false);
            }
        });
        binding.actionToutReduire.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surToutReduire();
            }
        });
        binding.actionLocaliser.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surLocaliser();
            }
        });
        binding.champFiltre.addTextChangedListener(
                new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s,
                                                  int start, int count,
                                                  int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s,
                                              int start, int before,
                                              int count) {
                        if (auditeur != null) {
                            auditeur.surFiltre(String.valueOf(s));
                        }
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s) {
                    }
                });
    }

    /** Pose l'auditeur (l'écran garde le câblage). */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** L'arbre (l'écran le pose ici — VueArbre existant). */
    public FrameLayout cadreArbre() {
        return binding.cadreArbre;
    }

    /** Le champ de filtre (l'écran le vide à la fermeture). */
    public EditText champFiltre() {
        return binding.champFiltre;
    }
}
