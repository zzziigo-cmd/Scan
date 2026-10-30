package com.example.security

// ============================================================
// MAPPING OWASP TOP 10 (édition 2021)
// Classe chaque constat du pipeline dans la bonne catégorie officielle,
// et fournit la description + le conseil de résolution général associé
// à chaque catégorie (en complément des solutions déjà concrètes et
// contextualisées de RecommendationEngine).
// ============================================================

enum class CategorieOwasp(val code: String, val titre: String) {
    A01_BROKEN_ACCESS_CONTROL("A01:2021", "Violation du contrôle d'accès"),
    A02_CRYPTOGRAPHIC_FAILURES("A02:2021", "Défaillances cryptographiques"),
    A03_INJECTION("A03:2021", "Injection"),
    A04_INSECURE_DESIGN("A04:2021", "Conception non sécurisée"),
    A05_SECURITY_MISCONFIGURATION("A05:2021", "Mauvaise configuration de sécurité"),
    A06_VULNERABLE_COMPONENTS("A06:2021", "Composants vulnérables et obsolètes"),
    A07_AUTH_FAILURES("A07:2021", "Défaillances d'identification et d'authentification"),
    A08_INTEGRITY_FAILURES("A08:2021", "Défaillances d'intégrité des données et des logiciels"),
    A09_LOGGING_FAILURES("A09:2021", "Carence des systèmes de journalisation et de surveillance"),
    A10_SSRF("A10:2021", "Server-Side Request Forgery (SSRF)")
}

data class DescriptionOwasp(
    val categorie: CategorieOwasp,
    val description: String,
    val conseilGeneral: String
)

val descriptionsOwasp: Map<CategorieOwasp, DescriptionOwasp> = mapOf(
    CategorieOwasp.A01_BROKEN_ACCESS_CONTROL to DescriptionOwasp(
        CategorieOwasp.A01_BROKEN_ACCESS_CONTROL,
        "Des ressources ou fonctionnalités censées être restreintes (page admin, fichiers de configuration, API) sont accessibles sans vérification suffisante des droits.",
        "Appliquer le principe du moindre privilège, vérifier les droits côté serveur à chaque requête (jamais seulement côté client), et restreindre l'accès aux ressources sensibles par défaut."
    ),
    CategorieOwasp.A02_CRYPTOGRAPHIC_FAILURES to DescriptionOwasp(
        CategorieOwasp.A02_CRYPTOGRAPHIC_FAILURES,
        "Données sensibles transmises ou stockées sans chiffrement adéquat : certificat SSL invalide, contenu mixte, cookies non sécurisés, chiffrement faible.",
        "Forcer HTTPS partout (HSTS inclus), utiliser des suites cryptographiques à jour, chiffrer les données sensibles au repos, et ne jamais transmettre d'identifiants en clair."
    ),
    CategorieOwasp.A03_INJECTION to DescriptionOwasp(
        CategorieOwasp.A03_INJECTION,
        "Des entrées utilisateur non validées/échappées sont interprétées comme du code par le serveur ou le navigateur (SQL, XSS, commande système...).",
        "Utiliser des requêtes paramétrées (jamais de concaténation SQL), échapper systématiquement les sorties HTML (contexte XSS), valider les entrées côté serveur avec une liste blanche plutôt qu'une liste noire."
    ),
    CategorieOwasp.A04_INSECURE_DESIGN to DescriptionOwasp(
        CategorieOwasp.A04_INSECURE_DESIGN,
        "La faille vient d'un choix de conception en amont, pas d'un bug d'implémentation — corriger le code sans revoir l'architecture ne suffit pas.",
        "Intégrer la modélisation de menaces dès la conception, documenter les hypothèses de sécurité, et prévoir des contrôles redondants (défense en profondeur) plutôt qu'un point de défaillance unique."
    ),
    CategorieOwasp.A05_SECURITY_MISCONFIGURATION to DescriptionOwasp(
        CategorieOwasp.A05_SECURITY_MISCONFIGURATION,
        "Paramètres par défaut laissés tels quels, fonctionnalités inutiles activées, en-têtes de sécurité absents, messages d'erreur trop détaillés.",
        "Durcir la configuration par défaut de chaque composant, désactiver ce qui n'est pas utilisé, automatiser le durcissement via de l'Infrastructure as Code pour qu'il survive aux redéploiements."
    ),
    CategorieOwasp.A06_VULNERABLE_COMPONENTS to DescriptionOwasp(
        CategorieOwasp.A06_VULNERABLE_COMPONENTS,
        "Des versions obsolètes de CMS, plugins, bibliothèques ou du serveur lui-même exposent des vulnérabilités déjà publiques (CVE connues).",
        "Maintenir un inventaire des composants et de leurs versions, appliquer les mises à jour de sécurité rapidement, et masquer les bannières de version pour ne pas faciliter le ciblage."
    ),
    CategorieOwasp.A07_AUTH_FAILURES to DescriptionOwasp(
        CategorieOwasp.A07_AUTH_FAILURES,
        "Mécanismes d'authentification ou de gestion de session insuffisants : pas de limitation de tentatives, sessions mal protégées, pas de 2FA.",
        "Limiter les tentatives de connexion (rate limiting), activer l'authentification à deux facteurs, protéger les cookies de session (Secure, HttpOnly, SameSite), et invalider les sessions correctement à la déconnexion."
    ),
    CategorieOwasp.A08_INTEGRITY_FAILURES to DescriptionOwasp(
        CategorieOwasp.A08_INTEGRITY_FAILURES,
        "Code ou données dont l'intégrité n'est pas vérifiée : mises à jour non signées, dépendances non vérifiées, désérialisation non sécurisée.",
        "Vérifier les signatures/checksums des dépendances et mises à jour, utiliser des pipelines CI/CD avec contrôle d'intégrité, éviter la désérialisation de données non fiables."
    ),
    CategorieOwasp.A09_LOGGING_FAILURES to DescriptionOwasp(
        CategorieOwasp.A09_LOGGING_FAILURES,
        "Absence de journalisation ou de surveillance suffisante pour détecter une attaque en cours — les incidents ne sont découverts que trop tard.",
        "Centraliser les logs (accès, erreurs, authentification), mettre en place des alertes sur les comportements anormaux (tentatives répétées, erreurs en masse), et conserver les journaux suffisamment longtemps pour une investigation."
    ),
    CategorieOwasp.A10_SSRF to DescriptionOwasp(
        CategorieOwasp.A10_SSRF,
        "L'application peut être amenée à effectuer des requêtes vers des URL contrôlées par un attaquant, exposant potentiellement le réseau interne.",
        "Valider et restreindre strictement les URL/domaines que l'application est autorisée à appeler, isoler le réseau interne, et désactiver les redirections automatiques non contrôlées."
    )
)

/**
 * Classe un constat existant (toutes étapes confondues) dans sa catégorie OWASP.
 * Retourne null si le constat ne correspond à aucune catégorie pertinente (ex: un simple "conforme" neutre).
 */
fun classifierOwasp(nomEtape: String, constat: Constat): CategorieOwasp? {
    if (constat.niveau == Niveau.CONFORME) return null

    return when {
        // A01 — contrôle d'accès
        constat.titre.contains("non protégée", true) ||
        constat.titre.contains("Directory listing", true) ||
        constat.titre.contains("Chemins sensibles", true) ||
        constat.titre.contains("Port MySQL exposé", true) -> CategorieOwasp.A01_BROKEN_ACCESS_CONTROL

        // A02 — cryptographie / transport
        nomEtape == "SSL / TLS" ||
        constat.titre.contains("HSTS", true) || constat.titre.contains("Strict-Transport-Security", true) ||
        constat.titre.contains("Mixed content", true) ||
        constat.titre.contains("Redirection HTTP", true) ||
        constat.titre.contains("Secure", true) && nomEtape == "Cookies" -> CategorieOwasp.A02_CRYPTOGRAPHIC_FAILURES

        // A03 — injection (réservé aux futures sondes actives XSS/SQLi)
        constat.titre.contains("XSS", true) || constat.titre.contains("injection", true) ||
        constat.titre.contains("SQL", true) && !constat.titre.contains("MySQL exposé", true) -> CategorieOwasp.A03_INJECTION

        // A05 — mauvaise configuration
        constat.titre.contains("Content Security Policy", true) ||
        constat.titre.contains("X-Frame-Options", true) ||
        constat.titre.contains("X-Content-Type-Options", true) ||
        constat.titre.contains("Referrer-Policy", true) ||
        constat.titre.contains("Erreurs PHP verbeuses", true) ||
        constat.titre.contains("Directory listing", true) -> CategorieOwasp.A05_SECURITY_MISCONFIGURATION

        // A06 — composants vulnérables (versions exposées)
        constat.titre.contains("Version PHP exposée", true) ||
        constat.titre.contains("Version serveur MySQL exposée", true) ||
        (constat.titre.contains("Logiciel serveur", true) && constat.niveau == Niveau.AVERTISSEMENT) ||
        constat.titre.contains("Renouvelez votre nom de domaine", true) -> CategorieOwasp.A06_VULNERABLE_COMPONENTS

        // A07 — authentification / session
        constat.titre.contains("HttpOnly", true) || constat.titre.contains("SameSite", true) ||
        constat.titre.contains("Session Laravel", true) -> CategorieOwasp.A07_AUTH_FAILURES

        else -> null
    }
}

data class SyntheseOwasp(
    val categorie: CategorieOwasp,
    val description: DescriptionOwasp,
    val constatsAssocies: List<Pair<String, Constat>> // (nom étape, constat)
)

/**
 * Regroupe tous les constats du RapportGlobal par catégorie OWASP, pour
 * affichage dans un écran dédié (vision "conformité OWASP Top 10").
 */
fun genererSyntheseOwasp(rapport: RapportGlobal): List<SyntheseOwasp> =
    genererSyntheseOwaspComplet(rapport, rapportInjection = null)

/**
 * Même chose, mais inclut aussi les constats du RapportInjection (sondes XSS/SQL)
 * quand il est disponible — ils alimentent spécifiquement la catégorie A03.
 */
fun genererSyntheseOwaspComplet(rapport: RapportGlobal, rapportInjection: RapportInjection?): List<SyntheseOwasp> {
    val regroupement = mutableMapOf<CategorieOwasp, MutableList<Pair<String, Constat>>>()

    rapport.tousLesConstats.forEach { (nomEtape, constat) ->
        val categorie = classifierOwasp(nomEtape, constat) ?: return@forEach
        regroupement.getOrPut(categorie) { mutableListOf() } += nomEtape to constat
    }

    rapportInjection?.constats?.forEach { constat ->
        val categorie = classifierOwasp("Tests d'entrée", constat) ?: return@forEach
        regroupement.getOrPut(categorie) { mutableListOf() } += "Tests d'entrée" to constat
    }

    return regroupement.entries
        .sortedBy { it.key.ordinal }
        .map { (cat, constats) ->
            SyntheseOwasp(
                categorie = cat,
                description = descriptionsOwasp.getValue(cat),
                constatsAssocies = constats
            )
        }
}
