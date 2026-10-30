package com.example.security

// ============================================================
// VÉRIFICATEUR / ANALYSEUR ARCHITECTURAL
//
// Rôle différent du RecommendationEngine : celui-ci corrige chaque
// problème isolément. Celui-ci lit l'ENSEMBLE du RapportGlobal et :
//  1. Repère des combinaisons de signaux qui, ensemble, forment un
//     risque réel plus grave que la somme de ses parties (corrélation)
//  2. Décrit chaque faille combinée en langage clair, avec le scénario
//     d'exploitation concret qu'elle permet
//  3. Propose des solutions ARCHITECTURALES (reverse proxy, segmentation
//     réseau, gestion centralisée des secrets...) plutôt que des
//     correctifs ponctuels — c'est ce niveau qui manquait au projet
//     initial selon le retour de l'encadrant.
// ============================================================

data class ChaineDeRisque(
    val nom: String,
    val signauxImpliques: List<String>,   // titres des constats combinés
    val scenarioExploitation: String,     // comment un attaquant enchaînerait ces faiblesses concrètement
    val priorite: Int                     // 1 = urgent, 2 = important, 3 = structurel
)

data class AxeArchitectural(
    val nom: String,
    val problemeDeFond: String,           // pourquoi corriger les symptômes un par un ne suffit pas
    val solutionConcrete: String,         // la solution architecturale, avec des outils/techniques nommés
    val dejaPartielleemntCouvert: Boolean  // true si certains correctifs ponctuels existent déjà dans le rapport
)

data class RapportArchitectural(
    val domain: String,
    val chainesDeRisque: List<ChaineDeRisque>,
    val axesArchitecturaux: List<AxeArchitectural>,
    val syntheseGlobale: String
)

/**
 * Point d'entrée : prend le RapportGlobal (sortie de l'étape 9, qui contient
 * déjà tous les constats de toutes les étapes) et produit l'analyse architecturale.
 */
fun analyserArchitectureSecurite(rapport: RapportGlobal): RapportArchitectural {

    fun present(motCle: String): Boolean =
        rapport.tousLesConstats.any { it.second.titre.contains(motCle, ignoreCase = true) && it.second.niveau != Niveau.CONFORME }

    fun constatsPour(motCle: String): List<Constat> =
        rapport.tousLesConstats.map { it.second }.filter { it.titre.contains(motCle, ignoreCase = true) }

    val chaines = mutableListOf<ChaineDeRisque>()

    // --- Chaîne 1 : fingerprinting complet → ciblage de CVE facilité ---
    val versionPhpExposee = present("Version PHP exposée")
    val logicielServeurExpose = present("Logiciel serveur") // avertissement = exposé
    if (versionPhpExposee || logicielServeurExpose) {
        val signaux = mutableListOf<String>()
        if (versionPhpExposee) signaux += "Version PHP exposée"
        if (logicielServeurExpose) signaux += "Logiciel serveur exposé"
        if (rapport.cmsDetecte != DetectedCms.UNKNOWN) signaux += "CMS identifié (${rapport.cmsDetecte})"

        chaines += ChaineDeRisque(
            nom = "Reconnaissance facilitée pour un attaquant",
            signauxImpliques = signaux,
            scenarioExploitation = "Pris isolément, chaque élément (version PHP, logiciel serveur, CMS) semble mineur. " +
                "Combinés, ils permettent à un attaquant de reconstituer la pile technique complète du site en quelques " +
                "secondes, puis de chercher directement les CVE publiques correspondant à ces versions précises — " +
                "sans avoir à deviner ni à scanner activement. C'est l'étape de reconnaissance qui précède 90% des " +
                "attaques automatisées (bots de scan de masse).",
            priorite = 2
        )
    }

    // --- Chaîne 2 : authentification faible combinée à une admin accessible ---
    val adminAccessible = present("non protégée")
    val cookiesFaibles = present("Secure") || present("HttpOnly") || present("SameSite")
    if (adminAccessible && cookiesFaibles) {
        chaines += ChaineDeRisque(
            nom = "Compte admin vulnérable au détournement de session",
            signauxImpliques = listOf("Page d'administration accessible sans restriction", "Attributs de cookies manquants"),
            scenarioExploitation = "La page de connexion admin est atteignable sans restriction réseau ET les cookies de " +
                "session ne sont pas correctement protégés. Même si le mot de passe admin est fort, un attaquant en " +
                "position d'interception (wifi public, réseau compromis) ou exploitant une faille XSS ailleurs sur le " +
                "site peut voler la session active sans jamais avoir besoin du mot de passe lui-même.",
            priorite = 1
        )
    }

    // --- Chaîne 3 : exposition directe des données backend ---
    val mysqlExpose = present("Port MySQL exposé")
    val listingOuvert = present("Directory listing ouvert")
    val cheminsSensibles = present("Chemins sensibles révélés")
    if (mysqlExpose || listingOuvert || cheminsSensibles) {
        val signaux = mutableListOf<String>()
        if (mysqlExpose) signaux += "Port MySQL exposé publiquement"
        if (listingOuvert) signaux += "Directory listing ouvert"
        if (cheminsSensibles) signaux += "Chemins sensibles révélés par robots.txt"

        chaines += ChaineDeRisque(
            nom = "Chemin direct vers l'exfiltration de données",
            signauxImpliques = signaux,
            scenarioExploitation = "Chacun de ces points offre, seul, un accès partiel : une base accessible depuis " +
                "internet, un dossier listant ses fichiers, ou un chemin de sauvegarde révélé. Mais réunis, ils " +
                "dessinent un chemin complet et direct vers les données : repérer le fichier de sauvegarde via le " +
                "listing, ou se connecter directement à la base si elle accepte des connexions externes — sans même " +
                "avoir besoin de compromettre l'application web elle-même.",
            priorite = 1
        )
    }

    // --- Chaîne 4 : chaîne HTTPS incomplète (bout en bout non garanti) ---
    val hstsAbsent = present("Strict-Transport-Security")
    val mixedContent = present("Mixed content")
    val redirectionManquante = present("Redirection HTTP") && rapport.tousLesConstats
        .any { it.second.titre.contains("Redirection HTTP", true) && it.second.niveau == Niveau.CRITIQUE }
    if ((hstsAbsent && mixedContent) || redirectionManquante) {
        val signaux = mutableListOf<String>()
        if (hstsAbsent) signaux += "HSTS absent"
        if (mixedContent) signaux += "Contenu mixte HTTP/HTTPS"
        if (redirectionManquante) signaux += "Redirection HTTP→HTTPS non forcée"

        chaines += ChaineDeRisque(
            nom = "Chiffrement non garanti de bout en bout",
            signauxImpliques = signaux,
            scenarioExploitation = "Le site utilise HTTPS, mais plusieurs maillons de la chaîne laissent des brèches : " +
                "sans HSTS, un visiteur peut rester piégé en HTTP lors d'une première visite ; le contenu mixte permet " +
                "d'injecter du code via une ressource non chiffrée ; sans redirection forcée, un lien HTTP oublié " +
                "quelque part reste un point d'entrée. Le chiffrement n'est fort que si CHAQUE maillon l'impose, pas " +
                "seulement la page principale.",
            priorite = 2
        )
    }

    // --- Chaîne 5 : absence de défense en profondeur (signal global) ---
    val categoriesAvecCritique = rapport.categories.count { it.critiques > 0 }
    if (categoriesAvecCritique >= 3) {
        chaines += ChaineDeRisque(
            nom = "Absence de défense en profondeur",
            signauxImpliques = rapport.categories.filter { it.critiques > 0 }.map { it.nom },
            scenarioExploitation = "Des failles critiques apparaissent dans $categoriesAvecCritique catégories " +
                "indépendantes (${rapport.categories.filter { it.critiques > 0 }.joinToString(", ") { it.nom }}). " +
                "Ce n'est pas un bug isolé à corriger, mais le signe qu'aucune couche de protection globale " +
                "(pare-feu applicatif, durcissement centralisé) ne compense les oublis ponctuels de configuration. " +
                "Une seule couche de sécurité qui tombe (ou un seul oubli de configuration) expose directement le site.",
            priorite = 1
        )
    }

    // ============================================================
    // AXES ARCHITECTURAUX — solutions structurelles, pas ponctuelles
    // ============================================================
    val axes = mutableListOf<AxeArchitectural>()

    axes += AxeArchitectural(
        nom = "Reverse proxy / pare-feu applicatif (WAF) en frontal",
        problemeDeFond = "La majorité des correctifs proposés (en-têtes manquants, version PHP exposée, logiciel " +
            "serveur visible) sont actuellement à appliquer un par un, dans la configuration de chaque composant. " +
            "C'est fragile : un redéploiement, une mise à jour, ou un nouvel environnement peut faire disparaître " +
            "ces réglages sans que personne ne s'en rende compte avant le prochain audit.",
        solutionConcrete = "Placer un reverse proxy (nginx) ou un WAF managé (Cloudflare, dans sa version gratuite " +
            "déjà) devant l'application. Il centralise l'ajout des en-têtes de sécurité (HSTS, CSP, X-Frame-Options) " +
            "pour TOUT le trafic sans toucher au code applicatif, masque automatiquement les bannières serveur/PHP, " +
            "et applique un rate limiting global contre le bruteforce — une seule configuration protège tous les " +
            "chemins, y compris ceux qu'on oublierait de durcir individuellement.",
        dejaPartielleemntCouvert = versionPhpExposee || logicielServeurExpose || hstsAbsent
    )

    axes += AxeArchitectural(
        nom = "Segmentation réseau pour la base de données",
        problemeDeFond = "Le scan vérifie si MySQL est exposé publiquement, mais la vraie question architecturale " +
            "est : pourquoi la base de données serait-elle, en premier lieu, sur le même réseau que l'internet " +
            "public ? Un mot de passe fort ne compense pas une exposition réseau qui n'aurait jamais dû exister.",
        solutionConcrete = "Placer la base de données dans un sous-réseau privé (VPC chez un cloud provider, ou " +
            "réseau Docker interne en auto-hébergé) qui n'accepte des connexions QUE depuis le serveur applicatif, " +
            "jamais depuis internet. L'accès administratif (phpMyAdmin, requêtes manuelles) passe par un tunnel SSH " +
            "ou un bastion, jamais par une exposition directe du port 3306.",
        dejaPartielleemntCouvert = mysqlExpose
    )

    axes += AxeArchitectural(
        nom = "Durcissement comme code (Infrastructure as Code)",
        problemeDeFond = "Les recommandations CMS-par-CMS (plugins WordPress, extensions Joomla, configuration " +
            "Laravel) sont justes, mais reposent sur le fait qu'un humain applique chaque réglage manuellement via " +
            "une interface d'administration — ce qui ne survit pas à une réinstallation, un nouveau serveur, ou une " +
            "migration d'hébergeur.",
        solutionConcrete = "Transformer les corrections en configuration versionnée : un template nginx/.htaccess " +
            "contenant tous les en-têtes de sécurité, un script Ansible/Docker qui applique les réglages PHP " +
            "(expose_php, display_errors) au déploiement, versionné dans le dépôt Git du projet. Le prochain " +
            "déploiement reproduit automatiquement le durcissement, sans dépendre de la mémoire de qui l'a fait " +
            "la première fois.",
        dejaPartielleemntCouvert = false
    )

    axes += AxeArchitectural(
        nom = "Authentification renforcée uniforme sur l'admin",
        problemeDeFond = "Les recommandations actuelles pour sécuriser l'admin dépendent du CMS (plugin Wordfence, " +
            "module Joomla, etc.) — ce qui fonctionne, mais crée une protection différente et inégale selon la " +
            "techno. Si demain le site change de CMS ou ajoute une seconde application, la protection ne suit pas.",
        solutionConcrete = "Ajouter une couche d'authentification indépendante du CMS, au niveau du reverse proxy " +
            "(authentification HTTP basique en plus du login applicatif, ou liste blanche d'IP) et, quand c'est " +
            "possible, une authentification à deux facteurs au niveau du compte. Cette couche protège l'admin " +
            "même si une faille CMS spécifique est découverte demain.",
        dejaPartielleemntCouvert = adminAccessible
    )

    axes += AxeArchitectural(
        nom = "Observabilité : détecter les tentatives, pas seulement les failles",
        problemeDeFond = "Le pipeline actuel est un audit ponctuel (\"photo\" de l'état à un instant donné). Il ne " +
            "dit rien sur ce qui se passe RÉELLEMENT entre deux scans — une tentative de bruteforce sur l'admin " +
            "passerait inaperçue tant que le prochain audit n'est pas lancé.",
        solutionConcrete = "Centraliser les logs d'accès (serveur web + application) vers un outil de suivi (même " +
            "simple : logs structurés + alerte sur pics de 401/403 sur les chemins admin). Combiné à l'historique " +
            "déjà stocké en base par l'application (via Room), ça permet de passer d'un audit \"instantané\" à un " +
            "suivi de tendance dans le temps — un vrai argument différenciant pour la soutenance.",
        dejaPartielleemntCouvert = false
    )

    val synthese = buildString {
        append("Analyse de $categoriesAvecCritique catégorie(s) avec faille(s) critique(s) sur ${rapport.categories.size} testées. ")
        if (chaines.isEmpty()) {
            append("Aucune combinaison de risques significative détectée — les problèmes identifiés restent isolés.")
        } else {
            append("${chaines.size} chaîne(s) de risque combiné(es) identifiée(s), dont ${chaines.count { it.priorite == 1 }} " +
                "jugée(s) urgente(s). Au-delà des correctifs ponctuels, ${axes.size} axes architecturaux sont proposés " +
                "pour que la sécurité ne dépende plus de corrections manuelles répétées à chaque audit.")
        }
    }

    return RapportArchitectural(
        domain = rapport.domain,
        chainesDeRisque = chaines.sortedBy { it.priorite },
        axesArchitecturaux = axes,
        syntheseGlobale = synthese
    )
}
