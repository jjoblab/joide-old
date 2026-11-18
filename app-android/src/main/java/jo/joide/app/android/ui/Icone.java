package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import jo.joide.app.android.R;

/**
 * Icône du kit : un glyphe dessiné au canvas (chemins en unités 24,
 * trait 1,8 dp, bouts et jointures arrondis), à la couleur demandée.
 * Aucune ressource externe (ADR-É2 : maquettes autonomes, kit aussi).
 * Le glyphe et la couleur se posent après construction — la vue ne
 * décide rien.
 */
public final class Icone extends View {

    /** Les glyphes du kit (barre d'application, cartes, assistant). */
    public enum Glyphe {
        /** Loupe (recherche). */
        RECHERCHE,
        /** Trois points verticaux (menu ⋮). */
        POINTS,
        /** Chevron vers la droite (ouvrir). */
        CHEVRON,
        /** Chevron vers la gauche (retour). */
        RETOUR,
        /** Croix plus (créer, ajouter). */
        PLUS,
        /** Croix (fermer). */
        CROIX,
        /** Coche (sélection). */
        COCHE,
        /** Triangle alerte (franchise). */
        TRIANGLE,
        /** Glyphe dossier (états vides, tuiles). */
        DOSSIER,
        /** Terminal (console de build, rail de l'atelier — lot J). */
        TERMINAL,
        /** Flèches circulaires (rafraîchir — lot L). */
        RAFRAICHIR,
        /** Flèches d'agrandissement (plein écran — lot L). */
        AGRANDIR,
        /** Bulle de chat (l'agent — lot P). */
        BULLE,
        /** Étoile pleine (favori — lot T). */
        ETOILE,
        /** Trois barres horizontales (tiroir fichiers — atelier v2). */
        MENU,
        /** Trois barres, la dernière courte (journal — atelier v2). */
        JOURNAL,
        /** Encadré à lignes (logcat — atelier v2). */
        LOGCAT
    }

    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Glyphe glyphe = Glyphe.CROIX;
    private int couleur;
    private float traitDp = 1.8f;

    public Icone(Context contexte) {
        this(contexte, null);
    }

    public Icone(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setClickable(false);
        // atelier v2 (lot L2, ADR-083) : le glyphe et la couleur se
        // posent DEPUIS LE XML — ?attr/jo_* pour la couleur (jamais
        // une valeur codée en dur, franchise §0.4) ; sans attribut,
        // la couleur du thème texte2 s'applique.
        if (attributs != null) {
            android.content.res.TypedArray pose = contexte
                    .obtainStyledAttributes(attributs,
                            R.styleable.JoIcone);
            int glyphePose = pose.getInt(R.styleable.JoIcone_jo_icone_glyphe,
                    -1);
            if (glyphePose >= 0 && glyphePose < Glyphe.values().length) {
                glyphe = Glyphe.values()[glyphePose];
            }
            if (pose.hasValue(R.styleable.JoIcone_jo_icone_couleur)) {
                TypedValue valeur = new TypedValue();
                pose.getValue(R.styleable.JoIcone_jo_icone_couleur,
                        valeur);
                couleur = resolveCouleur(valeur);
            } else {
                TypedValue valeur = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        R.attr.jo_texte2, valeur, true);
                couleur = resolveCouleur(valeur);
            }
            pose.recycle();
        } else {
            TypedValue valeur = new TypedValue();
            getContext().getTheme().resolveAttribute(
                    R.attr.jo_texte2, valeur, true);
            couleur = resolveCouleur(valeur);
        }
    }

    /** Résout une valeur de couleur : un ?attr résolu par le thème
     *  (TypedValue.type = ATTRIBUTE) se re-résout dans les ressources. */
    private int resolveCouleur(TypedValue valeur) {
        if (valeur.type == TypedValue.TYPE_ATTRIBUTE) {
            int[] requete = {valeur.data};
            android.content.res.TypedArray suivant =
                    getContext().obtainStyledAttributes(requete);
            int resolue = suivant.getColor(0, 0);
            suivant.recycle();
            return resolue;
        }
        return valeur.data;
    }

    /** Pose le glyphe. */
    public void setGlyphe(Glyphe nouveau) {
        glyphe = nouveau == null ? Glyphe.CROIX : nouveau;
        invalidate();
    }

    /** Pose la couleur du trait. */
    public void setCouleur(int nouvelle) {
        couleur = nouvelle;
        invalidate();
    }

    /** Pose l'épaisseur du trait (dp). */
    public void setTraitDp(float dp) {
        traitDp = dp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unite = Math.min(getWidth(), getHeight()) / 24f;
        if (unite <= 0f) {
            return;
        }
        float dx = (getWidth() - 24f * unite) / 2f;
        float dy = (getHeight() - 24f * unite) / 2f;
        pinceau.setStyle(Paint.Style.STROKE);
        pinceau.setColor(couleur);
        pinceau.setStrokeWidth(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        traitDp, getResources().getDisplayMetrics()));
        pinceau.setStrokeCap(Paint.Cap.ROUND);
        pinceau.setStrokeJoin(Paint.Join.ROUND);
        canvas.save();
        canvas.translate(dx, dy);
        dessiner(canvas, pinceau, unite);
        canvas.restore();
    }

    private void dessiner(Canvas canvas, Paint pinceau, float u) {
        Path chemin = new Path();
        switch (glyphe) {
            case RECHERCHE -> {
                canvas.drawCircle(9f * u, 9f * u, 6f * u, pinceau);
                chemin.moveTo(13.5f * u, 13.5f * u);
                chemin.lineTo(17f * u, 17f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case POINTS -> {
                pinceau.setStyle(Paint.Style.FILL);
                for (float y : new float[]{4.5f, 10f, 15.5f}) {
                    canvas.drawCircle(10f * u, y * u, 1.7f * u, pinceau);
                }
            }
            case CHEVRON -> {
                chemin.moveTo(7.5f * u, 4.5f * u);
                chemin.lineTo(13f * u, 10f * u);
                chemin.lineTo(7.5f * u, 15.5f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case RETOUR -> {
                chemin.moveTo(16.5f * u, 4.5f * u);
                chemin.lineTo(11f * u, 10f * u);
                chemin.lineTo(16.5f * u, 15.5f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case PLUS -> {
                chemin.moveTo(10f * u, 3.5f * u);
                chemin.lineTo(10f * u, 16.5f * u);
                chemin.moveTo(3.5f * u, 10f * u);
                chemin.lineTo(16.5f * u, 10f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case CROIX -> {
                chemin.moveTo(5f * u, 5f * u);
                chemin.lineTo(15f * u, 15f * u);
                chemin.moveTo(15f * u, 5f * u);
                chemin.lineTo(5f * u, 15f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case COCHE -> {
                chemin.moveTo(3.5f * u, 10.5f * u);
                chemin.lineTo(8f * u, 15f * u);
                chemin.lineTo(16.5f * u, 6f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case TRIANGLE -> {
                chemin.moveTo(10f * u, 3f * u);
                chemin.lineTo(18f * u, 18f * u);
                chemin.lineTo(2f * u, 18f * u);
                chemin.close();
                canvas.drawPath(chemin, pinceau);
                // le point d'exclamation du panneau
                chemin.reset();
                chemin.moveTo(10f * u, 8f * u);
                chemin.lineTo(10f * u, 13f * u);
                canvas.drawPath(chemin, pinceau);
                canvas.drawPoint(10f * u, 15.8f * u, pinceau);
            }
            case DOSSIER -> Carte.dessinerDossier(canvas, pinceau, u, 0f, 0f);
            case TERMINAL -> {
                chemin.moveTo(4f * u, 4f * u);
                chemin.lineTo(16f * u, 4f * u);
                chemin.lineTo(16f * u, 16f * u);
                chemin.lineTo(4f * u, 16f * u);
                chemin.close();
                canvas.drawPath(chemin, pinceau);
                chemin.reset();
                chemin.moveTo(6.5f * u, 8f * u);
                chemin.lineTo(8.5f * u, 10f * u);
                chemin.lineTo(6.5f * u, 12f * u);
                chemin.moveTo(10.5f * u, 12f * u);
                chemin.lineTo(13f * u, 12f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case RAFRAICHIR -> {
                chemin.moveTo(16.5f * u, 10f * u);
                chemin.arcTo(new android.graphics.RectF(3.5f * u,
                        3.5f * u, 16.5f * u, 16.5f * u), -30f, 300f);
                canvas.drawPath(chemin, pinceau);
                chemin.reset();
                chemin.moveTo(16.5f * u, 6f * u);
                chemin.lineTo(16.5f * u, 10.5f * u);
                chemin.lineTo(12f * u, 10.5f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case BULLE -> {
                android.graphics.RectF bulle = new android.graphics
                        .RectF(2.5f * u, 3.5f * u, 17.5f * u, 14.5f * u);
                canvas.drawRoundRect(bulle, 4.5f * u, 4.5f * u,
                        pinceau);
                chemin.reset();
                chemin.moveTo(6.5f * u, 14.2f * u);
                chemin.lineTo(5.5f * u, 17.5f * u);
                chemin.lineTo(9.5f * u, 14.5f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case ETOILE -> {
                // l'étoile du favori se dessine PLEINE : cinq branches,
                // centre (10, 10), rayon extérieur 7.2, intérieur 3
                pinceau.setStyle(Paint.Style.FILL);
                float[][] sommets = {
                        {10f, 2.8f}, {11.76f, 7.57f},
                        {16.85f, 7.77f}, {12.85f, 10.93f},
                        {14.23f, 15.83f}, {10f, 13f},
                        {5.77f, 15.83f}, {7.15f, 10.93f},
                        {3.15f, 7.77f}, {8.24f, 7.57f}};
                chemin.moveTo(sommets[0][0] * u, sommets[0][1] * u);
                for (float[] sommet : sommets) {
                    chemin.lineTo(sommet[0] * u, sommet[1] * u);
                }
                chemin.close();
                canvas.drawPath(chemin, pinceau);
            }
            case MENU -> {
                // trois barres horizontales (tiroir fichiers, §3.1)
                for (float y : new float[]{6f, 12f, 18f}) {
                    chemin.moveTo(4.5f * u, y * u);
                    chemin.lineTo(19.5f * u, y * u);
                    canvas.drawPath(chemin, pinceau);
                }
            }
            case JOURNAL -> {
                // trois barres, la dernière courte (journal, §3.6)
                chemin.moveTo(4.5f * u, 6f * u);
                chemin.lineTo(19.5f * u, 6f * u);
                canvas.drawPath(chemin, pinceau);
                chemin.moveTo(4.5f * u, 12f * u);
                chemin.lineTo(19.5f * u, 12f * u);
                canvas.drawPath(chemin, pinceau);
                chemin.moveTo(4.5f * u, 18f * u);
                chemin.lineTo(13.5f * u, 18f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case LOGCAT -> {
                // l'encadré du flux + deux lignes (logcat, §3.6)
                chemin.moveTo(4.5f * u, 5f * u);
                chemin.lineTo(19.5f * u, 5f * u);
                chemin.lineTo(19.5f * u, 19f * u);
                chemin.lineTo(4.5f * u, 19f * u);
                chemin.close();
                canvas.drawPath(chemin, pinceau);
                chemin.moveTo(7.5f * u, 9f * u);
                chemin.lineTo(16.5f * u, 9f * u);
                canvas.drawPath(chemin, pinceau);
                chemin.moveTo(7.5f * u, 13f * u);
                chemin.lineTo(13.5f * u, 13f * u);
                canvas.drawPath(chemin, pinceau);
            }
            case AGRANDIR -> {
                chemin.moveTo(4.5f * u, 9f * u);
                chemin.lineTo(4.5f * u, 4.5f * u);
                chemin.lineTo(9f * u, 4.5f * u);
                chemin.moveTo(15.5f * u, 15f * u);
                chemin.lineTo(15.5f * u, 19.5f * u);
                chemin.lineTo(11f * u, 19.5f * u);
                canvas.drawPath(chemin, pinceau);
            }
            default -> {
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent evenement) {
        return isClickable() && super.onTouchEvent(evenement);
    }
}
