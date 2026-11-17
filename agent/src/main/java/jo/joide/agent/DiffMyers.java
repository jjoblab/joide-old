package jo.joide.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le diff unifié borné (lot P, ADR-É11 point 4) : la permission porte
 * sur l'édition RÉELLE, pas sur un nom de fichier — le calcul est
 * Myers en avant avec trace (le coût O(N·D) et une mémoire
 * {@code O(D²)} bornée par le budget d'édition : au-delà, le repli
 * honnête remplace le bloc), la sortie est unifiée avec deux lignes
 * de contexte, et BORNÉE : 400 lignes et 20 000 caractères au plus,
 * l'amputation s'annonce — un diff infini est un outil de refus de
 * permission.
 *
 * <p>Purement JVM, aucune dépendance : le test éprouve le script
 * d'édition ligne par ligne et les bornes.</p>
 */
public final class DiffMyers {

    /** Lignes de diff au plus (borne ADR-É11). */
    public static final int PLAFOND_LIGNES = 400;

    /** Caractères de diff au plus. */
    public static final int PLAFOND_CARACTERES = 20_000;

    /** Lignes de contexte autour de chaque hunk. */
    public static final int CONTEXTE = 2;

    /**
     * Le budget d'édition : au-delà, le repli honnête (le diff de
     * sortie est borné à 400 lignes — un budget double suffit
     * toujours à montrer ce qui sera montré).
     */
    private static final int PLAFOND_D = 2 * PLAFOND_LIGNES;

    /** Le milieu restant au plus par côté après la coupe commune (défense du budget mémoire). */
    private static final int PLAFOND_MILIEU = 8 * PLAFOND_LIGNES;

    /** Le résultat borné. */
    public static final class Resultat {

        /** Le diff unifié (amputation annoncée dedans). */
        public final String texte;

        /** Vrai si le diff a été amputé (le texte le dit aussi). */
        public final boolean tronque;

        /** Le nombre de lignes réellement changées (moins + plus). */
        public final int lignesChangees;

        /**
         * @param texte         le diff unifié borné
         * @param tronque       l'amputation a eu lieu
         * @param lignesChangees les lignes -/+ réelles
         */
        public Resultat(String texte, boolean tronque,
                        int lignesChangees) {
            this.texte = texte;
            this.tronque = tronque;
            this.lignesChangees = lignesChangees;
        }
    }

    /** Les trois natures de ligne du script d'édition. */
    enum Nature { EGAL, MOINS, PLUS }

    /** Une ligne du script d'édition. */
    static final class LigneOp {
        final Nature nature;
        final String texte;

        LigneOp(Nature nature, String texte) {
            this.nature = nature;
            this.texte = texte;
        }
    }

    private DiffMyers() {
    }

    /**
     * Calcule le diff unifié borné entre deux textes.
     *
     * @param avant le contenu courant
     * @param apres le contenu proposé
     * @return le diff borné, amputation annoncée
     */
    public static Resultat unifie(String avant, String apres) {
        String[] a = lignes(avant);
        String[] b = lignes(apres);

        // 1. la coupe commune : préfixe et suffixe (le cas courant —
        //    un fichier presque identique — ne paie presque rien)
        int debut = 0;
        while (debut < a.length && debut < b.length
                && a[debut].equals(b[debut])) {
            debut++;
        }
        int finA = a.length;
        int finB = b.length;
        while (finA > debut && finB > debut
                && a[finA - 1].equals(b[finB - 1])) {
            finA--;
            finB--;
        }

        // 2. le milieu : Myers avec trace si le budget tient, repli
        //    honnête sinon (annoncé)
        boolean repli = false;
        List<LigneOp> milieu = new ArrayList<>();
        int n = finA - debut;
        int m = finB - debut;
        if (n == 0 && m == 0) {
            return new Resultat("(aucune diff\u00e9rence)", false, 0);
        }
        if (n > PLAFOND_MILIEU || m > PLAFOND_MILIEU) {
            repli = true;
            for (int i = 0; i < n; i++) {
                milieu.add(new LigneOp(Nature.MOINS,
                        a[debut + i]));
            }
            for (int j = 0; j < m; j++) {
                milieu.add(new LigneOp(Nature.PLUS,
                        b[debut + j]));
            }
        } else {
            // le milieu en tableaux LOCAUX : la trace et le
            // remontage partagent les mêmes coordonnées
            String[] milieuA = java.util.Arrays.copyOfRange(
                    a, debut, finA);
            String[] milieuB = java.util.Arrays.copyOfRange(
                    b, debut, finB);
            int[][] trace = myersTrace(milieuA, milieuB);
            if (trace == null) {
                repli = true;
                for (int i = 0; i < n; i++) {
                    milieu.add(new LigneOp(Nature.MOINS,
                            a[debut + i]));
                }
                for (int j = 0; j < m; j++) {
                    milieu.add(new LigneOp(Nature.PLUS,
                            b[debut + j]));
                }
            } else {
                retracer(trace, milieuA, milieuB, milieu);
            }
        }

        // 3. le script complet : préfixe + milieu + suffixe
        List<LigneOp> ops = new ArrayList<>(
                debut + milieu.size() + (a.length - finA));
        for (int i = 0; i < debut; i++) {
            ops.add(new LigneOp(Nature.EGAL, a[i]));
        }
        ops.addAll(milieu);
        for (int i = finA; i < a.length; i++) {
            ops.add(new LigneOp(Nature.EGAL, a[i]));
        }
        return formater(ops, repli);
    }

    // ------------------------------------------------------------------
    // Myers en avant avec trace — la version canonique : chaque
    // profondeur d garde sa tranche de V (m\u00e9moire (D+1)\u00b2 born\u00e9e).
    // ------------------------------------------------------------------

    /**
     * La trace des V par profondeur, ou null si le budget d'édition
     * dépasse {@link #PLAFOND_D} (le repli honnête s'applique).
     */
    private static int[][] myersTrace(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        List<int[]> trace = new ArrayList<>();
        int[] v = new int[2 * (n + m) + 3];
        int centre = n + m + 1;
        for (int d = 0; d <= PLAFOND_D; d++) {
            // la tranche couvre k-1..k+1 (le remontage lit les
            // diagonales voisines) : 2d+3 entrées, décalage d+1
            int[] tranche = new int[2 * d + 3];
            System.arraycopy(v, centre - d - 1, tranche, 0,
                    2 * d + 3);
            trace.add(tranche);
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d
                        && v[centre + k - 1] < v[centre + k + 1])) {
                    x = v[centre + k + 1];
                } else {
                    x = v[centre + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m
                        && a[x].equals(b[y])) {
                    x++;
                    y++;
                }
                v[centre + k] = x;
                if (x >= n && y >= m) {
                    return trace.toArray(new int[0][]);
                }
            }
        }
        return null;
    }

    /** Remonte la trace : le script d'édition complet du milieu. */
    private static void retracer(int[][] trace, String[] a,
                                 String[] b, List<LigneOp> ops) {
        String[] script = new String[a.length + b.length + 2];
        int nb = 0;
        int x = a.length;
        int y = b.length;
        for (int d = trace.length - 1; d >= 0; d--) {
            int[] v = trace[d];
            int decalage = d + 1;
            int k = x - y;
            if (k < -d || k > d) {
                // diagonale hors tranche (ne doit pas arriver : la
                // remontée reste dans le diamant) — repli défensif
                continue;
            }
            int kPrecedent;
            if (k == -d || (k != d
                    && v[decalage + k - 1] < v[decalage + k + 1])) {
                kPrecedent = k + 1;
            } else {
                kPrecedent = k - 1;
            }
            int xPrecedent = v[decalage + kPrecedent];
            int yPrecedent = xPrecedent - kPrecedent;
            while (x > xPrecedent && y > yPrecedent) {
                script[nb++] = "=" + a[x - 1];
                x--;
                y--;
            }
            if (d > 0) {
                if (x == xPrecedent) {
                    script[nb++] = "+" + b[y - 1];
                    y--;
                } else {
                    script[nb++] = "-" + a[x - 1];
                    x--;
                }
            }
            x = xPrecedent;
            y = yPrecedent;
        }
        for (int i = nb - 1; i >= 0; i--) {
            char nature = script[i].charAt(0);
            ops.add(new LigneOp(
                    nature == '=' ? Nature.EGAL
                            : nature == '-' ? Nature.MOINS
                            : Nature.PLUS,
                    script[i].substring(1)));
        }
    }

    // ------------------------------------------------------------------
    // Le formatage unifié borné
    // ------------------------------------------------------------------

    private static Resultat formater(List<LigneOp> ops, boolean repli) {
        int changements = 0;
        for (LigneOp op : ops) {
            if (op.nature != Nature.EGAL) {
                changements++;
            }
        }
        if (changements == 0) {
            return new Resultat("(aucune diff\u00e9rence)", false, 0);
        }
        // les fenêtres : un changement appartient à la fenêtre en
        // cours tant qu'il est à moins de 2×CONTEXTE lignes du
        // précédent ; sinon la fenêtre se ferme
        List<int[]> fenetres = new ArrayList<>();
        int dernierChangement = -1;
        int ouverture = -1;
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).nature == Nature.EGAL) {
                continue;
            }
            if (dernierChangement < 0
                    || i - dernierChangement > 2 * CONTEXTE + 1) {
                if (dernierChangement >= 0) {
                    fenetres.add(new int[]{ouverture,
                            dernierChangement});
                }
                ouverture = i;
            }
            dernierChangement = i;
        }
        fenetres.add(new int[]{ouverture, dernierChangement});

        StringBuilder sortie = new StringBuilder();
        int lignesEmises = 0;
        boolean tronque = repli;
        int ligneAvant = 1;
        int ligneApres = 1;
        int index = 0;
        for (int f = 0; f < fenetres.size() && !tronque; f++) {
            int debutFenetre = fenetres.get(f)[0];
            int finFenetre = fenetres.get(f)[1];
            int debutBloc = Math.max(0, debutFenetre - CONTEXTE);
            int finBloc = Math.min(ops.size() - 1,
                    finFenetre + CONTEXTE);
            if (debutBloc < index) {
                debutBloc = index;
            }
            // les numéros au début du bloc
            int numAvant = ligneAvant;
            int numApres = ligneApres;
            for (int i = index; i < debutBloc; i++) {
                if (ops.get(i).nature != Nature.PLUS) {
                    numAvant++;
                }
                if (ops.get(i).nature != Nature.MOINS) {
                    numApres++;
                }
            }
            if (sortie.length() > 0) {
                sortie.append('\n');
            }
            sortie.append("@@ -").append(numAvant).append(" +")
                    .append(numApres).append(" @@");
            lignesEmises++;
            for (int i = debutBloc; i <= finBloc; i++) {
                if (lignesEmises >= PLAFOND_LIGNES
                        || sortie.length() >= PLAFOND_CARACTERES) {
                    tronque = true;
                    break;
                }
                LigneOp op = ops.get(i);
                if (op.nature == Nature.MOINS) {
                    sortie.append("\n-").append(op.texte);
                } else if (op.nature == Nature.PLUS) {
                    sortie.append("\n+").append(op.texte);
                } else {
                    sortie.append("\n ").append(op.texte);
                }
                lignesEmises++;
            }
            // avance les numéros au-delà du bloc consommé
            for (int i = index; i <= finBloc; i++) {
                if (ops.get(i).nature != Nature.PLUS) {
                    ligneAvant++;
                }
                if (ops.get(i).nature != Nature.MOINS) {
                    ligneApres++;
                }
            }
            index = finBloc + 1;
        }
        if (tronque) {
            sortie.append("\n[\u2026 diff born\u00e9 \u00e0 ")
                    .append(PLAFOND_LIGNES).append(" lignes / ")
                    .append(PLAFOND_CARACTERES)
                    .append(" caract\u00e8res]");
            if (repli) {
                sortie.append(
                        " \u2014 fichier trop remani\u00e9 pour un "
                                + "diff fin");
            }
        }
        return new Resultat(sortie.toString(), tronque, changements);
    }

    /**
     * Les lignes d'un texte, convention git : le saut de ligne FINAL
     * n'est pas une ligne (un pur ajout/retrait de newline final
     * n'est donc pas montré — l'écriture réelle reste exacte, seul
     * l'aperçu l'ignore).
     */
    private static String[] lignes(String texte) {
        if (texte == null || texte.isEmpty()) {
            return new String[0];
        }
        String[] brut = texte.split("\n", -1);
        if (brut.length > 1 && brut[brut.length - 1].isEmpty()) {
            String[] coupe = new String[brut.length - 1];
            System.arraycopy(brut, 0, coupe, 0, coupe.length);
            return coupe;
        }
        return brut;
    }
}
