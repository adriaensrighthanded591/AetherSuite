pluginManagement {
    repositories {
        google { content {
            includeGroupByRegex("com\\.android.*")
            includeGroupByRegex("com\\.google.*")
            includeGroupByRegex("androidx.*")
        }}
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "AetherSuite"

// ── Les 9 modules de la suite ──────────────────────────────────────────────
include(":aether-core")       // Design system + sécurité + Intents partagés
include(":aether-sms")        // SMS/MMS — messagerie principale
include(":aether-contacts")   // Contacts
include(":aether-phone")      // Téléphone / Appels
include(":aether-notes")      // Notes chiffrées AES-256-GCM
include(":aether-files")      // Gestionnaire de fichiers
include(":aether-gallery")    // Galerie photos & vidéos
include(":aether-music")      // Lecteur de musique local
include(":aether-calendar")   // Agenda local chiffré
