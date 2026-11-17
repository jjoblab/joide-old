// core-deps — résolution Maven + Gradle Module Metadata, lockfile
// joide.lock, checksums vérifiés, cache disque partagé adressé par
// contenu, extraction AAR protégée zip-slip, transport HTTPS vérifié
// TLS, hors-ligne strict, téléchargements parallèles plafonnés.
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-platform"))
}
