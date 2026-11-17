package jo.joide.store;

import java.util.List;

/**
 * Une migration versionnée du schéma du store.
 *
 * @param version    numéro croissant de la migration
 * @param statements instructions de création ou de transformation,
 *                   appliquées ensemble dans une transaction
 */
record Migration(int version, List<String> statements) {
}
