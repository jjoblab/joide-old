package jo.joide.app.android.ui.atelier;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EtatFeuille;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.databinding.AtelierFeuilleBinding;
import jo.joide.app.android.databinding.FeuilleEnteteBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L3 (ADR-083) — la feuille basse (§3.6), qui
 * REMPLACE le Dock au câblage (le retrait du Dock mort est prévu au
 * lot L6). Toute la géométrie vit dans le modèle PUR
 * {@link EtatFeuille} (détentes, ancrage, fling, section active) ;
 * la vue applique : poignée (toucher = cycle, glisser = suit le
 * doigt puis s'ancre, fling > 1 200 dp/s = détente suivante), rail
 * de sections, en-tête unique à zone vivante (tap = barre ↔ mi,
 * glissement horizontal = section suivante/précédente avec
 * bouclage), corps avec la barre d'outils puis le contenu posé par
 * l'écran. Les sections portent leurs états purs (EtatConsole
 * existant, EtatProblemes, EtatJournal, EtatLogcat) ; la vue est
 * MINCE et lisible.
 */
public final class FeuilleBasse extends LinearLayout {

    /** Un auditeur de la feuille (les actions qui sortent de la vue). */
    public interface Auditeur {
        /** Un problème est demandé : ouvre le fichier à la ligne et
         *  replie la feuille à la barre (§3.6). */
        void surOuvrirProbleme(String chemin, int ligne);

        /** Le verdict console est demandé (tap = filtre de phase,
         *  comportement existant). */
        void surPipeline();

        /** Le flux logcat doit démarrer (section ouverte) / s'arrêter
         *  (section quittée) — le relais se tranche dans l'ADR dédié. */
        void surLogcat(boolean actif);

        /** Lot A6 (décision 1 — zones différées) : la feuille montre
         *  son CORPS et la section active n'a pas de contenu (la
         *  console vit derrière un {@code ViewStub}, les listes non) —
         *  l'écran monte la zone AVANT que le corps apparaisse, jamais
         *  une feuille ouverte sur un vide muet. Idempotent : le
         *  montage est gardé, l'appel répété ne fait rien. */
        void surOuverture();
    }

    /** Le binding de la feuille. */
    public final AtelierFeuilleBinding binding;

    /** L'en-tête (rail + zone vivante), réexposé pour l'écran. */
    public final FeuilleEnteteBinding entete;

    private final EtatFeuille etatFeuille = new EtatFeuille();
    private Auditeur auditeur;
    /** Les contenus de section posés par l'écran (console = VueConsole
     *  existant ; problèmes/journal/logcat = listes internes). */
    private final View[] contenus = new View[4];
    private final ScrollView[] corps = new ScrollView[4];
    private int hauteurUtileDp;
    private boolean gesturesInstalles;
    private float toucherInitialeY;
    private int hauteurInitiale;
    private boolean aGlisse;

    public FeuilleBasse(Context contexte) {
        this(contexte, null);
    }

    public FeuilleBasse(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        binding = AtelierFeuilleBinding.inflate(
                LayoutInflater.from(contexte), this, true);
        entete = binding.entete;
        // lot A6 (ADR-085 décision 1) : les trois listes de section
        // vivent dans le LAYOUT de la feuille (la feuille les adopte à
        // sa construction — l'écran n'instancie plus rien ; la console
        // reste posée par l'écran au gonflage de son stub)
        contenus[EtatFeuille.Section.PROBLEMES.ordinal()] =
                binding.listeProblemes;
        contenus[EtatFeuille.Section.JOURNAL.ordinal()] =
                binding.listeJournal;
        contenus[EtatFeuille.Section.LOGCAT.ordinal()] =
                binding.listeLogcat;
        installerGestes();
        installerRail();
        appliquerDetente(false);
    }

    /**
     * Le CÂBLAGE DU PARENT (le 2026-11-18 : « pas de layout parent
     * pour les activités » — le diagnostic de l'utilisateur). Les
     * détentes MI et PLEINE sont des FRACTIONS de la hauteur utile,
     * fournie par personne : la feuille vivait avec 0 et tout
     * dépliage la réduisait à zéro pixel. La feuille se câble
     * ELLE-MÊME à son parent : la zone utile = la feuille + les
     * voisins à poids (le corps qui se resserre quand elle s'ouvre)
     * — leur somme est constante à n'importe quelle détente, donc
     * mesurable à chaque mise en page, convertie en dp.
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) {
            return;
        }
        parent.addOnLayoutChangeListener(
                (vue, gauche, haut, droite, bas, og, oh, od, ob) -> {
                    int pixels = 0;
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        View enfant = parent.getChildAt(i);
                        if (enfant == this) {
                            pixels += getHeight();
                            continue;
                        }
                        ViewGroup.LayoutParams params =
                                enfant.getLayoutParams();
                        if (params instanceof LinearLayout.LayoutParams
                                && ((LinearLayout.LayoutParams) params)
                                        .weight > 0f) {
                            pixels += enfant.getHeight();
                        }
                    }
                    if (pixels > 0) {
                        poserHauteurUtile(Math.round(pixels
                                / getResources().getDisplayMetrics()
                                        .density));
                    }
                });
    }

    /** Pose l'auditeur (l'écran garde le câblage). */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** La liste des problèmes (l'écran câble ses auditions). */
    public ListeProblemes listeProblemes() {
        return binding.listeProblemes;
    }

    /** La liste du journal de l'IDE. */
    public ListeJournal listeJournal() {
        return binding.listeJournal;
    }

    /** La liste du logcat de l'application. */
    public ListeLogcat listeLogcat() {
        return binding.listeLogcat;
    }

    /** Pose le contenu d'une section (la VueConsole existant pour la
     *  console, une liste interne sinon). */
    public void poserContenu(EtatFeuille.Section section, View vue) {
        contenus[section.ordinal()] = vue;
        majCorps();
    }

    /** La hauteur utile (l'écran la fournit après mise en page).
     *  Idempotent : pendant une traîne ou une animation, chaque
     *  frame relance la mise en page — la valeur inchangée NE doit
     *  PAS reposer la détente (la feuille reviendrait d'un coup). */
    public void poserHauteurUtile(int hauteurUtileDp) {
        if (this.hauteurUtileDp == hauteurUtileDp) {
            return;
        }
        this.hauteurUtileDp = hauteurUtileDp;
        appliquerDetente(false);
    }

    /** La détente pilotée par l'état (§3.7 : le FAB et le verdict
     *  console suivent) — vrai = mi-hauteur, faux = replié. */
    public void poserDeplie(boolean depliee) {
        EtatFeuille.Detente cible = depliee ? EtatFeuille.Detente.MI
                : EtatFeuille.Detente.REPLIE;
        if (etatFeuille.detente() != cible) {
            etatFeuille.poserDetente(cible);
            appliquerDetente(true);
        }
    }

    /** La section demandée (le rail ou le glissement d'en-tête) :
     *  feuille repliée, le choix DÉPLIE à mi-hauteur (le 2026-11-18 :
     *  le contenu changeait dans une feuille à 72 dp — invisible, la
     *  barre paraissait morte). Déjà ouverte : la section seule
     *  change, la hauteur reste. */
    public void poserSection(EtatFeuille.Section section) {
        etatFeuille.poserSection(section);
        if (etatFeuille.detente() == EtatFeuille.Detente.REPLIE) {
            etatFeuille.poserDetente(EtatFeuille.Detente.MI);
            appliquerDetente(true);
        }
        majSection();
    }

    /** @return la section active. */
    public EtatFeuille.Section section() {
        return etatFeuille.section();
    }

    /** @return l'état pur de la feuille (pour les tests d'écran). */
    public EtatFeuille etat() {
        return etatFeuille;
    }

    // ---- gestes ------------------------------------------------------

    private void installerGestes() {
        GestureDetector detecteur = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1,
                                           MotionEvent e2, float vx,
                                           float vy) {
                        // un glisser vers le HAUT (vy négatif) agrandit
                        int vitesseDpS = (int) (-vy / getResources()
                                .getDisplayMetrics().density);
                        EtatFeuille.Detente cible =
                                etatFeuille.fling(vitesseDpS);
                        if (cible != null) {
                            appliquerDetente(true);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(
                            MotionEvent e) {
                        // toucher la poignée : le cycle §3.6
                        etatFeuille.cycle();
                        appliquerDetente(true);
                        return true;
                    }
                });
        binding.poigneeFeuille.setOnTouchListener((vue, evenement) -> {
            switch (evenement.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    gesteVerticalDebut(evenement.getY());
                    // le geste se RÉCLAME dès le contact : sinon le
                    // parent intercepte le flux et rien ne suit
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    gesteVerticalDeplacement(evenement.getY());
                    // le flux reste à la feuille pendant la traîne :
                    // un false ici rendait la feuille au parent dès le
                    // premier pixel (le 2026-11-18, seconde remontée)
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (aGlisse) {
                        etatFeuille.ancrer(hauteurCouranteDp(),
                                hauteurUtileDp);
                        appliquerDetente(true);
                    }
                    aGlisse = false;
                }
                default -> {
                }
            }
            return detecteur.onTouchEvent(evenement);
        });

        // la zone vivante (le comportement à 3 états demandé le
        // 2026-11-18) : le tap CYCLE les détentes (replié → mi →
        // plein → replié, comme la poignée), le glissement vertical
        // TRAÎNE la feuille et s'ancre à la détente la plus proche,
        // le fling saute une détente ; les balayages horizontaux
        // gardent les sections (prioritaires quand |dx| > |dy|).
        // Le geste vertical est le MÊME que celui de la poignée :
        // les trois détentes répondent partout dans la barre.
        final float[] xInitial = new float[1];
        final float[] yInitial = new float[1];
        GestureDetector detecteurEntete = new GestureDetector(
                getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1,
                                           MotionEvent e2, float vx,
                                           float vy) {
                        int vitesseDpS = (int) (-vy / getResources()
                                .getDisplayMetrics().density);
                        EtatFeuille.Detente cible =
                                etatFeuille.fling(vitesseDpS);
                        if (cible != null) {
                            appliquerDetente(true);
                            return true;
                        }
                        return false;
                    }
                });
        entete.zoneVivante.setOnTouchListener((vue, evenement) -> {
            switch (evenement.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    xInitial[0] = evenement.getX();
                    yInitial[0] = evenement.getY();
                    gesteVerticalDebut(evenement.getY());
                    // le geste se RÉCLAME dès le contact : le
                    // glissement vertical, le fling et le tap du
                    // cycle des trois détentes suivent (le 2026-11-18)
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    gesteVerticalDeplacement(evenement.getY());
                    // le flux reste à la barre pendant la traîne (le
                    // balayage horizontal se juge au relâché)
                    return true;
                }
                case MotionEvent.ACTION_UP,
                     MotionEvent.ACTION_CANCEL -> {
                    float dx = evenement.getX() - xInitial[0];
                    float dy = evenement.getY() - yInitial[0];
                    float seuil = GraphiteVue.dp(getContext(), 40f);
                    if (Math.abs(dx) > seuil
                            && Math.abs(dx) > Math.abs(dy)) {
                        // le balayage horizontal gagne : les sections
                        aGlisse = false;
                        glisserSection(dx < 0);
                        break;
                    }
                    if (aGlisse) {
                        // la feuille traînée s'ancre à la détente la
                        // plus proche (replié, mi, plein)
                        etatFeuille.ancrer(hauteurCouranteDp(),
                                hauteurUtileDp);
                        appliquerDetente(true);
                        aGlisse = false;
                        break;
                    }
                    // tap : le CYCLE COMPLET des trois détentes
                    etatFeuille.poserSection(etatFeuille.section());
                    etatFeuille.cycle();
                    appliquerDetente(true);
                }
                default -> {
                }
            }
            return detecteurEntete.onTouchEvent(evenement)
                    || aGlisse;
        });
        gesturesInstalles = true;
    }

    /** Le début du geste vertical partagé (poignée et en-tête). */
    private void gesteVerticalDebut(float y) {
        toucherInitialeY = y;
        hauteurInitiale = etatFeuille.hauteurDp(hauteurUtileDp);
        aGlisse = false;
    }

    /** Le déplacement du geste vertical partagé : la feuille suit le
     *  doigt, bornée entre le replié et le plein. */
    private void gesteVerticalDeplacement(float y) {
        int voulu = Math.max(
                EtatFeuille.HAUTEUR_REPLIE_DP,
                (int) Math.min(
                        EtatFeuille.hauteurDe(
                                EtatFeuille.Detente.PLEINE,
                                hauteurUtileDp),
                        hauteurInitiale
                                + (toucherInitialeY - y)
                                / getResources()
                                .getDisplayMetrics().density));
        if (Math.abs(voulu - hauteurInitiale) > 2) {
            aGlisse = true;
        }
        if (aGlisse) {
            poserHauteur(voulu);
        }
    }

    private void installerRail() {
        entete.railConsole.setOnClickListener(vue -> poserSection(
                EtatFeuille.Section.CONSOLE));
        entete.railProblemes.setOnClickListener(vue -> poserSection(
                EtatFeuille.Section.PROBLEMES));
        entete.railJournal.setOnClickListener(vue -> poserSection(
                EtatFeuille.Section.JOURNAL));
        entete.railLogcat.setOnClickListener(vue -> poserSection(
                EtatFeuille.Section.LOGCAT));
    }

    private void glisserSection(boolean suivante) {
        if (suivante) {
            etatFeuille.sectionSuivante();
        } else {
            etatFeuille.sectionPrecedente();
        }
        if (etatFeuille.detente() == EtatFeuille.Detente.REPLIE) {
            etatFeuille.poserDetente(EtatFeuille.Detente.MI);
            appliquerDetente(true);
        }
        majSection();
        // le contenu entre en glissant de 14 dp + fondu (220 ms)
        View contenu = binding.corpsFeuille;
        contenu.setAlpha(0f);
        contenu.setTranslationX(GraphiteVue.dp(getContext(),
                suivante ? 14f : -14f));
        contenu.animate().alpha(1f).translationX(0f)
                .setDuration(220)
                .setInterpolator(new PathInterpolator(.2f, .8f, .2f, 1f))
                .start();
        if (auditeur != null) {
            auditeur.surLogcat(etatFeuille.section()
                    == EtatFeuille.Section.LOGCAT);
        }
    }

    // ---- rendu -------------------------------------------------------

    /** Applique la détente : la hauteur animée (260 ms), la
     *  visibilité du corps, le rail et la zone vivante. */
    private void appliquerDetente(boolean anime) {
        int cible = etatFeuille.hauteurDp(hauteurUtileDp);
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            return;
        }
        if (anime) {
            int depart = getHeight();
            ValueAnimator animateur =
                    ValueAnimator.ofInt(depart, cible);
            animateur.setDuration(260);
            animateur.setInterpolator(
                    new PathInterpolator(.2f, .8f, .2f, 1f));
            animateur.addUpdateListener(anim -> {
                params.height = (int) anim.getAnimatedValue();
                setLayoutParams(params);
            });
            animateur.start();
        } else {
            params.height = GraphiteVue.dp(getContext(), cible);
            setLayoutParams(params);
        }
        binding.corpsFeuille.setVisibility(
                etatFeuille.cacheLeFab() ? View.VISIBLE : View.GONE);
        majRail();
        majSection();
    }

    /** La hauteur réellement appliquée (dp), pour l'ancrage. */
    private int hauteurCouranteDp() {
        int pixels = getHeight() > 0 ? getHeight()
                : GraphiteVue.dp(getContext(),
                etatFeuille.hauteurDp(hauteurUtileDp));
        return Math.round(pixels / getResources()
                .getDisplayMetrics().density);
    }

    private void poserHauteur(int dp) {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            return;
        }
        params.height = GraphiteVue.dp(getContext(), dp);
        setLayoutParams(params);
    }

    private void majRail() {
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        stylerRail(entete.railConsole, palette,
                etatFeuille.section() == EtatFeuille.Section.CONSOLE);
        stylerRail(entete.railProblemes, palette,
                etatFeuille.section()
                        == EtatFeuille.Section.PROBLEMES);
        stylerRail(entete.railJournal, palette,
                etatFeuille.section() == EtatFeuille.Section.JOURNAL);
        stylerRail(entete.railLogcat, palette,
                etatFeuille.section() == EtatFeuille.Section.LOGCAT);
    }

    private void stylerRail(jo.joide.app.android.ui.Icone bouton,
                            Graphite.Palette palette, boolean actif) {
        bouton.setBackgroundResource(actif
                ? R.drawable.rail_item_actif_fond
                : android.R.color.transparent);
        int[] requete = {R.attr.jo_accent, R.attr.jo_texte2};
        TypedValue valeur = new TypedValue();
        getContext().getTheme().resolveAttribute(
                actif ? requete[0] : requete[1], valeur, true);
        bouton.setCouleur(resoudreCouleur(valeur));
    }

    private int resoudreCouleur(TypedValue valeur) {
        if (valeur.type == TypedValue.TYPE_ATTRIBUTE) {
            android.content.res.TypedArray suivant = getContext()
                    .obtainStyledAttributes(new int[]{valeur.data});
            int couleur = suivant.getColor(0, Color.TRANSPARENT);
            suivant.recycle();
            return couleur;
        }
        return valeur.data;
    }

    /** La zone vivante et le corps suivent la section active. */
    private void majSection() {
        entete.zoneConsole.setVisibility(
                etatFeuille.section() == EtatFeuille.Section.CONSOLE
                        ? View.VISIBLE : View.GONE);
        entete.zoneProblemes.setVisibility(
                etatFeuille.section()
                        == EtatFeuille.Section.PROBLEMES
                        ? View.VISIBLE : View.GONE);
        entete.zoneJournal.setVisibility(
                etatFeuille.section() == EtatFeuille.Section.JOURNAL
                        ? View.VISIBLE : View.GONE);
        entete.zoneLogcat.setVisibility(
                etatFeuille.section() == EtatFeuille.Section.LOGCAT
                        ? View.VISIBLE : View.GONE);
        majCorps();
    }

    /** Le corps montre le contenu de la section active. */
    private void majCorps() {
        FrameLayout receptacle = binding.contenuFeuille;
        if (receptacle.getChildCount() == 1
                && receptacle.getChildAt(0) == contenus[
                etatFeuille.section().ordinal()]) {
            return;
        }
        View contenu = contenus[etatFeuille.section().ordinal()];
        if (contenu == null
                && etatFeuille.detente() != EtatFeuille.Detente.REPLIE) {
            // lot A6 : le contenu de la section CONSOLE se monte À LA
            // DEMANDE (ViewStub de l'écran) — la feuille LE DEMANDE dès
            // que son corps doit se montrer, jamais un vide muet (pas
            // de boucle : l'auditeur monte la zone, la pose par
            // poserContenu et le contenu devient non null)
            if (auditeur != null) {
                auditeur.surOuverture();
            }
            contenu = contenus[etatFeuille.section().ordinal()];
        }
        receptacle.removeAllViews();
        if (contenu != null) {
            receptacle.addView(contenu, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    // ---- zones vivantes (posées par l'écran) -------------------------

    /** Le verdict console (§3.6) : ligne 1 colorée, ligne 2, les 3
     *  segments du pipeline (réussite / erreur / bord = non atteint,
     *  accent = en cours) — visible replié seulement. */
    public void poserVerdictConsole(String verdict, int couleurVerdict,
                                    String detail, boolean enCours,
                                    boolean resolutionOk,
                                    boolean compilationOk,
                                    boolean compilationErreur,
                                    boolean assemblageAtteint,
                                    boolean assemblageErreur) {
        entete.consoleVerdict.setText(verdict);
        entete.consoleVerdict.setTextColor(couleurVerdict);
        entete.consoleDetail.setText(detail);
        entete.consolePipeline.setVisibility(
                etatFeuille.detente() == EtatFeuille.Detente.REPLIE
                        ? View.VISIBLE : View.GONE);
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        teinterPipe(entete.pipeResolution, resolutionOk
                ? palette.reussite : palette.bord);
        teinterPipe(entete.pipeCompilation, compilationErreur
                ? palette.erreur : (compilationOk ? palette.reussite
                : (enCours ? palette.accent : palette.bord)));
        teinterPipe(entete.pipeAssemblage, assemblageErreur
                ? palette.erreur : (assemblageAtteint
                ? palette.reussite
                : (enCours ? palette.accent : palette.bord)));
    }

    private void teinterPipe(View segment, int couleur) {
        GradientDrawable fond = new GradientDrawable();
        fond.setCornerRadius(GraphiteVue.dp(getContext(), 3f));
        fond.setColor(couleur);
        segment.setBackground(fond);
    }

    /** Le badge d'erreurs LSP du rail (⚠, §3.6) ; 0 ou négatif le
     *  cache. */
    public void poserBadgeProblemes(int erreurs) {
        if (erreurs <= 0) {
            entete.badgeProblemes.setVisibility(View.GONE);
            return;
        }
        entete.badgeProblemes.setText(String.valueOf(erreurs));
        entete.badgeProblemes.setVisibility(View.VISIBLE);
    }

    /** La zone vivante des problèmes (§3.6). */
    public void poserVerdictProblemes(String verdict, String detail) {
        entete.problemesVerdict.setText(verdict);
        entete.problemesDetail.setText(detail);
    }

    /** La zone vivante du journal (§3.6). */
    public void poserVerdictJournal(String verdict, String detail) {
        entete.journalVerdict.setText(verdict);
        entete.journalDetail.setText(detail);
    }

    /** La zone vivante du logcat (§3.6) : état du flux, dernière
     *  ligne (couleur posée par l'écran), histogramme du débit. */
    public void poserVerdictLogcat(String verdict, int couleurVerdict,
                                   String detail, int couleurDetail,
                                   EtatLogcat.Histogramme histogramme) {
        entete.logcatVerdict.setText(verdict);
        entete.logcatVerdict.setTextColor(couleurVerdict);
        entete.logcatDetail.setText(detail);
        entete.logcatDetail.setTextColor(couleurDetail);
        // lot A6 : les 9 barres se dessinent au Canvas
        // (HistogrammeLogcat), plus de rangée posée vue par vue
        entete.logcatHistogramme.poser(histogramme);
    }

    /** Le bouton ↓ « problème suivant » (§3.6) : branché par
     *  l'écran (il connaît la position courante). */
    public View boutonProblemeSuivant() {
        return entete.btnProblemeSuivant;
    }

    /** La cible des problèmes (tap zone vivante = ouvrir à la
     *  ligne). */
    public View cibleProblemes() {
        return entete.problemesCible;
    }
}
