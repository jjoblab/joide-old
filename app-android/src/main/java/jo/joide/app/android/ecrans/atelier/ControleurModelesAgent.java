package jo.joide.app.android.ecrans.atelier;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.AgentService;
import jo.joide.app.android.R;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.databinding.ChampSaisieParamBinding;
import jo.joide.app.android.databinding.ItemEnteteSectionBinding;
import jo.joide.app.android.databinding.ItemModeleBinding;
import jo.joide.app.android.databinding.PopupModelesBinding;
import jo.joide.app.android.databinding.PopupSaisieModeleBinding;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Puce;

/**
 * Lot A7 (ADR-085, découpage de {@code ControleurAgent}) — le
 * contrôleur des MODÈLES de l'agent : le sélecteur ancré à la puce du
 * tiroir (fournisseurs pointés, recherche, favoris à l'étoile,
 * récents, coche du courant — la manière CodeAssist, §3.9), le choix
 * et la persistance (fournisseur PUIS modèle, retenu PAR fournisseur),
 * le modèle PERSONNALISÉ (nom + URL OpenAI-compatible, popup centré à
 * saisie — le brouillon vit dans {@code EtatPopups}). La vérité reste
 * dans {@link Reglages} et le service ; aucune vue du cadre
 * n'est instanciée ici — les corps vivent dans {@code popup_*.xml}
 * gonflés par binding.
 */
final class ControleurModelesAgent {

    /** La clé du brouillon du nom de modèle personnalisé. */
    private static final String BROUILLON_MODELE = "agent.modele";

    /** La clé du brouillon de l'URL personnalisée. */
    private static final String BROUILLON_URL = "agent.url";

    private final HoteAtelier hote;

    ControleurModelesAgent(HoteAtelier hote) {
        this.hote = hote;
    }

    /**
     * Le sélecteur de modèle de la manière CodeAssist (lot T,
     * ADR-065) : une popup ANCRÉE à la puce du tiroir — la rangée de
     * fournisseurs, la recherche qui filtre, les favoris (étoile) et
     * les récents devant les suggestions, la coche sur le modèle
     * courant. Un modèle se choisit D'UN TAP : le couple part au
     * service, la puce se rafraîchit, la conversation continue.
     */
    void ouvrir() {
        if (hote.vueAgent() == null) {
            return;
        }
        Reglages reglages = Reglages.lire(hote.sourceReglages());
        PopupWindow[] fenetre = new PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        Runnable[] rafraichir = new Runnable[1];
        int[] fournisseurIndex = {reglages.fournisseurIa.ordinal()};

        // ---- la coque du XML : titre, fermer, la rangée des
        // fournisseurs, l'état de clé, la recherche, la liste — les
        // PUCES (état pointé) restent posées par code, la vérité
        // reste dans les réglages
        PopupModelesBinding coque = PopupModelesBinding.inflate(
                LayoutInflater.from(hote.contexte()));
        // la coque du XML est un ScrollView BORNÉ (la liste défile
        // dedans quand la place manque — PopupAncre borne la fenêtre)
        android.widget.ScrollView colonne = coque.getRoot();
        Icone fermerIcone = coque.btnFermerModeles;
        fermerIcone.setOnClickListener(vue -> fermer.run());

        // la rangée des fournisseurs : une puce pointée dit le choix —
        // le TAP PERSISTE le fournisseur (fix du 2026-11-17 : avant,
        // seule la puce bougeait, la requête partait chez Anthropic
        // avec une clé absente — « no key for anthropic ») ; l'état de
        // clé se lit du stockage chiffré et se voit sous la rangée
        Reglages.FournisseurIa[] fournisseurs =
                Reglages.FournisseurIa.values();
        // l'état de clé du fournisseur pointé (un tap ouvre les
        // réglages IA) : le TextView vient du XML (typographie
        // Legende posée par le layout, couleur au bind)
        TextView etatCles = coque.etatCles;
        etatCles.setOnClickListener(vue -> {
            try {
                hote.ouvrirEcran(new android.content.Intent(
                        hote.contexte(),
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class));
            } catch (RuntimeException e) {
                // les réglages restent atteignables par le menu
            }
        });
        Runnable[] majEtatCles = new Runnable[1];
        majEtatCles[0] = () -> {
            Reglages.FournisseurIa courant =
                    fournisseurs[fournisseurIndex[0]];
            boolean posee = false;
            try {
                posee = jo.joide.app.android.parametres.KeystoreCles
                        .sur(hote.contexte()).estPosee(
                                nomStockageCle(courant));
            } catch (RuntimeException e) {
                // Keystore cassé : l'état reste « absente », jamais
                // un crash du sélecteur
            }
            etatCles.setTextColor(posee
                    ? hote.palette().texteSecondaire
                    : hote.palette().alerte);
            etatCles.setText(hote.texte(
                    posee ? R.string.agent_cle_etat_posee
                            : R.string.agent_cle_etat_absente,
                    nomFournisseur(courant)));
        };

        // les pastilles de la rangée (restaurées le 2026-11-18 : la
        // rangée reste posée par code, seule sa coque vient du XML)
        LinearLayout rangeeFournisseurs = coque.rangeeFournisseurs;
        Puce[] puces = new Puce[fournisseurs.length];
        for (int i = 0; i < fournisseurs.length; i++) {
            final int index = i;
            Puce puce = new Puce(hote.contexte());
            puce.setText(nomFournisseur(fournisseurs[i]));
            puce.setPointee(i == fournisseurIndex[0]);
            puce.setOnClickListener(vue -> {
                fournisseurIndex[0] = index;
                for (int j = 0; j < puces.length; j++) {
                    puces[j].setPointee(j == index);
                }
                // fix 2026-11-17 : le fournisseur se PERSISTE au tap —
                // puce seule ne suffisait pas (voir choisirFournisseur)
                choisirFournisseur(fournisseurs[index]);
                majEtatCles[0].run();
                rafraichir[0].run();
            });
            puces[i] = puce;
            LinearLayout.LayoutParams paramsPuce =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            paramsPuce.rightMargin = jo.joide.app.android.ui.GraphiteVue
                    .dp(hote.contexte(), 6);
            paramsPuce.topMargin = jo.joide.app.android.ui.GraphiteVue
                    .dp(hote.contexte(), 8);
            rangeeFournisseurs.addView(puce, paramsPuce);
        }
        majEtatCles[0].run();

        // la recherche : le filtre des listes longues (couleurs et
        // police du composeur d'hier posées au bind — le champ vient
        // du XML, l'état d'hier)
        EditText recherche = coque.rechercheModeles;
        recherche.setTextColor(hote.palette().texte);
        recherche.setHintTextColor(hote.palette().texteEstompe);
        recherche.setTypeface(jo.joide.app.android.ui.GraphiteVue
                .police(hote.contexte(), 400, false));

        LinearLayout liste = coque.listeModeles;
        rafraichir[0] = () -> remplirListe(coque, fournisseurIndex[0],
                recherche.getText().toString(), fermer,
                rafraichir[0]);
        recherche.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                    int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                    int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                rafraichir[0].run();
            }
        });
        rafraichir[0].run();

        // le modèle hors catalogue reste atteignable : le geste
        // personnalisé garde son popup (nom, URL de base)
        coque.personnaliseModeles.setOnClickListener(vue -> {
            fermer.run();
            ouvrirPersonnalise(fournisseurIndex[0]);
        });
        fenetre[0] = hote.popups().ancrer(
                hote.vueAgent().ancreModele(), colonne, 320);
    }

    /** Les sections du sélecteur : favoris, récents, suggestions —
     *  l'état vide (sections vides SANS filtre) se dit par le footer
     *  fixe de la coque ; une recherche sans résultat se voit au
     *  filtre posé. */
    private void remplirListe(PopupModelesBinding coque,
                              int fournisseurIndex,
                              String requete, Runnable fermer,
                              Runnable rafraichir) {
        LinearLayout liste = coque.listeModeles;
        liste.removeAllViews();
        Reglages reglages = Reglages.lire(hote.sourceReglages());
        String[] catalogue = catalogueModele(fournisseurIndex);
        List<String> favoris = lireListeModeles(
                cleFavorisModeles(fournisseurIndex));
        List<String> recents = lireListeModeles(
                cleRecentsModeles(fournisseurIndex));
        String filtre = requete == null ? ""
                : requete.trim().toLowerCase(java.util.Locale.ROOT);
        LayoutInflater gonfleur = LayoutInflater.from(hote.contexte());
        List<String> sectionFavoris = new ArrayList<>();
        List<String> sectionRecents = new ArrayList<>();
        List<String> sectionSuggestions = new ArrayList<>();
        for (String modele : catalogue) {
            if (!filtre.isEmpty() && !modele.toLowerCase(
                    java.util.Locale.ROOT).contains(filtre)) {
                continue;
            }
            if (favoris.contains(modele)) {
                sectionFavoris.add(modele);
            } else if (recents.contains(modele)) {
                sectionRecents.add(modele);
            } else {
                sectionSuggestions.add(modele);
            }
        }
        boolean vide = sectionFavoris.isEmpty() && sectionRecents.isEmpty()
                && sectionSuggestions.isEmpty();
        if (!sectionFavoris.isEmpty()) {
            ajouterEntete(liste, gonfleur,
                    hote.texte(R.string.agent_modeles_favoris));
        }
        for (String modele : sectionFavoris) {
            ajouterModele(liste, gonfleur, modele,
                    modele.equals(reglages.modeleIa), true,
                    fournisseurIndex, fermer, rafraichir);
        }
        if (!sectionRecents.isEmpty()) {
            ajouterEntete(liste, gonfleur,
                    hote.texte(R.string.agent_modeles_recents));
        }
        for (String modele : sectionRecents) {
            ajouterModele(liste, gonfleur, modele,
                    modele.equals(reglages.modeleIa), false,
                    fournisseurIndex, fermer, rafraichir);
        }
        if (!sectionSuggestions.isEmpty()) {
            ajouterEntete(liste, gonfleur,
                    hote.texte(R.string.agent_modeles_suggestions));
        }
        for (String modele : sectionSuggestions) {
            ajouterModele(liste, gonfleur, modele,
                    modele.equals(reglages.modeleIa), false,
                    fournisseurIndex, fermer, rafraichir);
        }
        coque.videModeles.setVisibility(
                vide && filtre.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** L'en-tête discret d'une section du sélecteur. */
    private void ajouterEntete(LinearLayout liste,
                               LayoutInflater gonfleur,
                               CharSequence titre) {
        ItemEnteteSectionBinding entete =
                ItemEnteteSectionBinding.inflate(gonfleur, liste,
                        true);
        entete.titreSection.setText(titre);
    }

    /** Une ligne de modèle : la coche du courant, l'étoile du favori. */
    private void ajouterModele(LinearLayout liste,
                               LayoutInflater gonfleur, String modele,
                               boolean courant, boolean favori,
                               int fournisseurIndex, Runnable fermer,
                               Runnable rafraichir) {
        ItemModeleBinding ligne = ItemModeleBinding.inflate(gonfleur,
                liste, true);
        ligne.nomModele.setText(modele);
        if (courant) {
            ligne.cocheModele.setVisibility(View.VISIBLE);
        }
        ligne.etoileModele.setCouleur(favori
                ? hote.palette().alerte : hote.palette().bord);
        ligne.etoileModele.setTraitDp(1.8f);
        ligne.getRoot().setOnClickListener(vue -> {
            fermer.run();
            choisirModele(fournisseurIndex, modele);
        });
        ligne.etoileModele.setOnClickListener(vue -> {
            basculeFavori(fournisseurIndex, modele, !favori);
            rafraichir.run();
        });
    }

    /**
     * Le catalogue suggéré d'un fournisseur (l'honnêteté : des
     * suggestions nommées, jamais une liste prétendue vivante).
     */
    private static String[] catalogueModele(int fournisseurIndex) {
        return switch (Reglages.FournisseurIa
                .values()[fournisseurIndex]) {
            case ANTHROPIC -> new String[]{
                    "claude-opus-4-1", "claude-sonnet-4-5",
                    "claude-haiku-4-5", "claude-3-7-sonnet",
                    "claude-3-5-haiku"};
            case GEMINI -> new String[]{
                    "gemini-2.5-pro", "gemini-2.5-flash",
                    "gemini-2.5-flash-lite", "gemini-2.0-flash"};
            case OPENROUTER -> new String[]{
                    "anthropic/claude-sonnet-4.5",
                    "openai/gpt-5.1", "google/gemini-2.5-pro",
                    "deepseek/deepseek-chat-v3.1",
                    "z-ai/glm-4.6", "qwen/qwen3-max"};
            default -> new String[]{
                    "gpt-5.1", "gpt-5", "gpt-4.1",
                    "gpt-4.1-mini", "gpt-4o", "o4-mini"};
        };
    }

    /** Le choix d'un modèle : écrit, pousse au service, retient. */
    private void choisirModele(int fournisseurIndex, String modele) {
        Reglages.FournisseurIa choisi = Reglages.FournisseurIa
                .values()[fournisseurIndex];
        Reglages.ecrireFournisseurIa(hote.sourceReglages(), choisi);
        Reglages.ecrireModeleIa(hote.sourceReglages(), modele);
        // lot U/7 : le modèle se retient POUR CE fournisseur aussi —
        // le modèle partagé ne traverse jamais les fournisseurs
        // (manière CodeAssist)
        Reglages.ecrireModelePour(hote.sourceReglages(), choisi,
                modele);
        if (hote.agent() != null) {
            hote.agent().poserModele(choisi, modele);
        }
        hote.etatAgent().poserModele(modele);
        hote.vueAgent().rafraichir();
        pousserRecent(fournisseurIndex, modele);
    }

    /**
     * Le choix d'un fournisseur SEUL (tap de puce, fix du
     * 2026-11-17) : le fournisseur se PERSISTE à l'instant du choix —
     * avant, une puce tapée puis le popup fermé laissait ANTHROPIC et
     * la requête partait avec une clé absente.
     *
     * <p>Le modèle suit : le modèle posé POUR CE fournisseur d'abord,
     * le modèle partagé s'il lui APPARTIENT, sinon la première
     * suggestion du catalogue — le garde d'appartenance
     * ({@link Reglages#modeleAppartient}) empêche « claude-sonnet-4-5 »
     * de partir chez OpenRouter.</p>
     */
    private void choisirFournisseur(Reglages.FournisseurIa choisi) {
        Reglages.ecrireFournisseurIa(hote.sourceReglages(), choisi);
        String modele = Reglages.lireModelePour(
                hote.sourceReglages(), choisi);
        if (modele.isEmpty()) {
            String partage = Reglages.lire(
                    hote.sourceReglages()).modeleIa;
            if (partage != null
                    && Reglages.modeleAppartient(partage, choisi)) {
                modele = partage;
            }
        }
        if (!Reglages.modeleAppartient(modele, choisi)) {
            String[] catalogue = catalogueModele(choisi.ordinal());
            modele = catalogue.length > 0 ? catalogue[0] : "";
        }
        if (modele.isEmpty()) {
            // aucun modèle plausible pour ce fournisseur : le
            // fournisseur est déjà persisté, le modèle reste celui
            // des réglages — le garde du service refusera proprement
            return;
        }
        Reglages.ecrireModeleIa(hote.sourceReglages(), modele);
        Reglages.ecrireModelePour(hote.sourceReglages(), choisi,
                modele);
        if (hote.agent() != null) {
            hote.agent().poserModele(choisi, modele);
        }
        hote.etatAgent().poserModele(modele);
        hote.vueAgent().rafraichir();
    }

    /**
     * Le MODÈLE PERSONNALISÉ (lot S conservé, popup centré à saisie
     * au lot A7) : le nom libre et l'URL OpenAI-compatible s'écrivent
     * au tap sur Valider — le couple part au service sans quitter la
     * conversation ; les BROUILLONS vivent dans l'état pur (le popup
     * n'est jamais restauré, le texte frappé survit).
     */
    private void ouvrirPersonnalise(int fournisseurIndex) {
        Reglages reglages = Reglages.lire(hote.sourceReglages());
        Reglages.FournisseurIa[] fournisseurs =
                Reglages.FournisseurIa.values();
        int indexCourant = Math.max(0, Math.min(fournisseurIndex,
                fournisseurs.length - 1));
        PopupSaisieModeleBinding corps =
                PopupSaisieModeleBinding.inflate(
                        LayoutInflater.from(hote.contexte()));
        corps.valeurFournisseurModele.setText(nomFournisseur(
                fournisseurs[indexCourant]));

        // les deux champs gonflés de champ_saisie_param par les
        // <include> du popup (binding typé) : hint, ligne unique et
        // BROUILLON posés au bind — le champ vit dans le XML
        ChampSaisieParamBinding champModele = corps.champModelePopup;
        EditText saisieModele = champModele.editionSaisie;
        saisieModele.setHint(R.string.parametres_modele_indice);
        saisieModele.setSingleLine(true);
        String brouillonModele = hote.etatPopups().brouillon(
                BROUILLON_MODELE);
        saisieModele.setText(brouillonModele != null
                ? brouillonModele : reglages.modeleIa);
        saisieModele.addTextChangedListener(brouillon(BROUILLON_MODELE));

        ChampSaisieParamBinding champUrl = corps.champUrlPopup;
        EditText saisieUrl = champUrl.editionSaisie;
        saisieUrl.setHint(R.string.parametres_url_indice);
        saisieUrl.setSingleLine(true);
        // lot U : l'URL d'OpenRouter est propre à la passerelle —
        // préremplie pour la voir, jamais devinée de l'utilisateur
        String defautUrl = reglages.urlIa.isEmpty()
                && Reglages.FournisseurIa.OPENROUTER
                        == fournisseurs[indexCourant]
                ? AgentService.URL_OPENROUTER : reglages.urlIa;
        String brouillonUrl = hote.etatPopups().brouillon(BROUILLON_URL);
        saisieUrl.setText(brouillonUrl != null ? brouillonUrl
                : defautUrl);
        saisieUrl.addTextChangedListener(brouillon(BROUILLON_URL));

        corps.gererClesModele.setOnClickListener(vue -> {
            try {
                hote.ouvrirEcran(new android.content.Intent(
                        hote.contexte(),
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class));
            } catch (RuntimeException e) {
                // les réglages restent atteignables par le menu
            }
        });
        corps.annulerSaisieModele.setText(R.string.action_annuler);
        corps.annulerSaisieModele.setOnClickListener(
                vue -> hote.popups().fermer());
        corps.validerSaisieModele.setText(R.string.action_ok);
        corps.validerSaisieModele.setOnClickListener(vue -> {
            hote.popups().fermer();
            Reglages.FournisseurIa choisi =
                    fournisseurs[Math.max(0, Math.min(fournisseurIndex,
                            fournisseurs.length - 1))];
            String modele = saisieModele.getText().toString().trim();
            if (modele.isEmpty()) {
                modele = reglages.modeleIa;
            }
            // le choix PERSISTE D'ABORD : une URL refusée ne doit
            // JAMAIS annuler le fournisseur et le modèle (rapport du
            // 2026-11-17 — l'URL écartée laissait tout le dialogue
            // sur Anthropic par défaut)
            Reglages.ecrireFournisseurIa(hote.sourceReglages(), choisi);
            Reglages.ecrireModeleIa(hote.sourceReglages(), modele);
            // lot U/7 : le modèle se retient POUR CE fournisseur aussi
            Reglages.ecrireModelePour(hote.sourceReglages(), choisi,
                    modele);
            String url = saisieUrl.getText().toString().trim();
            try {
                // la correction du 2026-11-18 : l'API personnalisée se
                // rattache au provider choisi (une URL OpenRouter ne
                // part plus chez OpenAI)
                Reglages.ecrireUrlPour(hote.sourceReglages(), choisi,
                        url);
            } catch (IllegalArgumentException e) {
                hote.popups().information(
                        hote.texte(R.string.agent_modele_titre),
                        hote.texte(R.string.parametres_valeur_invalide),
                        hote.texte(R.string.action_ok));
                // pas de return : le choix est déjà persisté
            }
            hote.etatPopups().oublierBrouillon(BROUILLON_MODELE);
            hote.etatPopups().oublierBrouillon(BROUILLON_URL);
            if (hote.agent() != null) {
                hote.agent().poserModele(choisi, modele);
            }
            hote.etatAgent().poserModele(modele);
            hote.vueAgent().rafraichir();
        });
        hote.popups().centrerSaisie(corps.getRoot(),
                hote.texte(R.string.agent_modele_titre));
    }

    /** Le relais de brouillon : chaque frappe vit dans l'état pur. */
    private TextWatcher brouillon(String cle) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                    int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                    int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                hote.etatPopups().poserBrouillon(cle, s.toString());
            }
        };
    }

    /** L'identifiant de stockage chiffré d'un fournisseur (mêmes
     *  noms qu'AgentService et Paramètres : anthropic, openai,
     *  gemini, openrouter). */
    private static String nomStockageCle(
            Reglages.FournisseurIa fournisseur) {
        return switch (fournisseur) {
            case ANTHROPIC -> "anthropic";
            case GEMINI -> "gemini";
            case OPENROUTER -> "openrouter";
            default -> "openai";
        };
    }

    /** Le nom lisible d'un fournisseur. */
    private CharSequence nomFournisseur(
            Reglages.FournisseurIa fournisseur) {
        return hote.texte(switch (fournisseur) {
            case ANTHROPIC -> R.string.agent_fournisseur_anthropic;
            case GEMINI -> R.string.agent_fournisseur_gemini;
            case OPENROUTER -> R.string.agent_fournisseur_openrouter;
            default -> R.string.agent_fournisseur_openai;
        });
    }

    /** L'étoile : basculer un favori (devant les suggestions). */
    private void basculeFavori(int fournisseurIndex, String modele,
                               boolean favori) {
        String cle = cleFavorisModeles(fournisseurIndex);
        List<String> favoris = lireListeModeles(cle);
        favoris.remove(modele);
        if (favori) {
            favoris.add(0, modele);
        }
        ecrireListeModeles(cle, favoris);
    }

    /** Un modèle choisi rejoint les récents (devant, borné à cinq). */
    private void pousserRecent(int fournisseurIndex, String modele) {
        String cle = cleRecentsModeles(fournisseurIndex);
        List<String> recents = lireListeModeles(cle);
        recents.remove(modele);
        recents.add(0, modele);
        while (recents.size() > 5) {
            recents.remove(recents.size() - 1);
        }
        ecrireListeModeles(cle, recents);
    }

    /** La liste persistée d'une clé (séparateur |, jamais vide). */
    private List<String> lireListeModeles(String cle) {
        String brut = hote.sourceReglages().lire(cle);
        List<String> valeurs = new ArrayList<>();
        if (brut != null && !brut.isEmpty()) {
            for (String morceau : brut.split("\\|")) {
                if (!morceau.isEmpty()) {
                    valeurs.add(morceau);
                }
            }
        }
        return valeurs;
    }

    /** La liste persistée s'écrit d'un trait (séparateur |). */
    private void ecrireListeModeles(String cle, List<String> valeurs) {
        hote.sourceReglages().ecrire(cle, String.join("|", valeurs));
    }

    /** La clé des favoris d'un fournisseur (identifiant de stockage
     *  du lot U/7 — les favoris déjà posés restent lus). */
    private static String cleFavorisModeles(int fournisseurIndex) {
        return "atelier.agent.modeles.favoris." + fournisseurIndex;
    }

    /** La clé des récents d'un fournisseur (idem lot U/7). */
    private static String cleRecentsModeles(int fournisseurIndex) {
        return "atelier.agent.modeles.recents." + fournisseurIndex;
    }
}
