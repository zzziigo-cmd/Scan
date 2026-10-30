package com.example.security

// ============================================================
// MOTEUR DE RECOMMANDATIONS
// Transforme chaque Constat brut en une fiche actionnable :
// explication (pourquoi c'est un problème) + solution concrète
// (comment le corriger), adaptée au CMS/techno détecté quand c'est pertinent.
// ============================================================

data class Recommandation(
    val titreProbleme: String,
    val niveau: Niveau,
    val explication: String,      // POURQUOI c'est un risque, en langage clair
    val solution: String,         // COMMENT corriger, étapes concrètes
    val priorite: Int             // 1 = urgent, 2 = important, 3 = amélioration
)

fun genererRecommandation(
    nomEtape: String,
    constat: Constat,
    cms: DetectedCms
): Recommandation {

    val priorite = when (constat.niveau) {
        Niveau.CRITIQUE -> 1
        Niveau.AVERTISSEMENT -> 2
        else -> 3
    }

    val (explication, solution) = when {

        // --- SSL / TLS ---
        constat.titre.contains("Certificat SSL", true) && constat.niveau == Niveau.CRITIQUE ->
            "Un certificat expiré ou invalide déclenche un avertissement de sécurité dans tous les navigateurs, et le trafic n'est plus garanti chiffré de bout en bout. Les visiteurs perdent confiance et peuvent quitter le site." to
            "Renouvelez le certificat immédiatement. Si vous utilisez Let's Encrypt, vérifiez que le renouvellement automatique (certbot renew) fonctionne via une tâche cron. Chez un hébergeur mutualisé, contactez le support pour un renouvellement manuel."

        constat.titre.contains("Certificat SSL", true) && constat.niveau == Niveau.AVERTISSEMENT ->
            "Le certificat expire bientôt. Sans renouvellement à temps, le site basculera en HTTPS invalide, ce qui bloque l'accès pour la plupart des visiteurs." to
            "Planifiez le renouvellement dès maintenant plutôt que d'attendre l'expiration. Vérifiez aussi que l'auto-renouvellement est bien configuré pour éviter que ça se reproduise."

        // --- En-têtes de sécurité ---
        constat.titre.equals("Strict-Transport-Security", true) ->
            "Sans HSTS, un visiteur qui tape \"monsite.com\" sans https:// passe d'abord par une connexion non chiffrée avant la redirection — une fenêtre d'attaque possible (SSL stripping)." to
            "Ajoutez l'en-tête : Strict-Transport-Security: max-age=31536000; includeSubDomains. " + conseilHstsParCms(cms)

        constat.titre.equals("Content Security Policy", true) || constat.titre.equals("Content-Security-Policy", true) ->
            "Sans CSP, le navigateur exécute n'importe quel script injecté dans la page (ex: via une faille XSS), sans restriction sur son origine." to
            ("Démarrez avec une politique simple et permissive pour ne rien casser : Content-Security-Policy: default-src 'self'. Testez en mode Content-Security-Policy-Report-Only d'abord pour repérer ce qui serait bloqué avant de l'appliquer strictement. " +
                conseilCspParCms(cms))

        constat.titre.equals("X-Frame-Options", true) ->
            "Sans cet en-tête, le site peut être intégré dans une iframe sur un site malveillant (clickjacking) : un attaquant superpose des éléments invisibles pour piéger les clics des visiteurs." to
            "Ajoutez : X-Frame-Options: SAMEORIGIN (ou DENY si aucune iframe n'est nécessaire, y compris la vôtre)."

        constat.titre.equals("X-Content-Type-Options", true) ->
            "Sans cet en-tête, certains navigateurs devinent le type MIME d'un fichier au lieu de respecter celui déclaré, ce qui peut permettre d'exécuter un fichier malveillant déguisé." to
            "Ajoutez : X-Content-Type-Options: nosniff. Une ligne suffit, aucun effet de bord attendu."

        constat.titre.equals("Referrer-Policy", true) ->
            "Sans cette politique, l'URL complète de la page (pouvant contenir des tokens ou infos sensibles dans les paramètres) est transmise au site de destination quand un visiteur clique sur un lien sortant." to
            "Ajoutez : Referrer-Policy: strict-origin-when-cross-origin — bon compromis entre analytics et confidentialité."

        constat.titre.contains("X-Powered-By", true) || constat.titre.contains("Fuite d'information", true) ->
            "Exposer la techno et sa version exacte simplifie la tâche d'un attaquant : il peut chercher directement les CVE connues pour cette version précise." to
            conseilExpositionVersionParCms(cms)

        // --- Cookies ---
        constat.titre.contains("Secure", true) && nomEtape == "Cookies" ->
            "Un cookie sans l'attribut Secure peut être transmis en clair si jamais une page du site est chargée en HTTP (même par erreur), exposant la session à l'interception sur un réseau non fiable (wifi public par exemple)." to
            ("Ajoutez l'attribut Secure à la création du cookie. " + conseilCookieSecureParCms(cms))

        constat.titre.contains("HttpOnly", true) ->
            "Sans HttpOnly, un script JavaScript (y compris injecté via une faille XSS) peut lire ce cookie et l'exfiltrer vers un serveur contrôlé par un attaquant." to
            ("Ajoutez l'attribut HttpOnly à la création du cookie. " + conseilCookieHttpOnlyParCms(cms))

        constat.titre.contains("SameSite", true) ->
            "Sans SameSite, le cookie est envoyé même lors de requêtes initiées depuis un autre site, ce qui facilite les attaques CSRF (un site malveillant déclenche une action en votre nom)." to
            ("Définissez SameSite=Lax au minimum (Strict si aucune navigation inter-site n'est nécessaire). " + conseilCookieSameSiteParCms(cms))

        constat.titre.contains("Session Laravel", true) ->
            "Le cookie de session Laravel protège l'authentification de l'utilisateur. Sans Secure, un attaquant sur le même réseau peut voler la session et usurper l'identité du visiteur connecté." to
            "Dans config/session.php, mettez 'secure' => true et 'http_only' => true. Vérifiez aussi que APP_ENV=production dans le .env."

        // --- PHP ---
        constat.titre.contains("Version PHP exposée", true) ->
            "Une version PHP précise permet de chercher directement les vulnérabilités connues (CVE) associées, raccourcissant le travail de reconnaissance d'un attaquant." to
            "Dans php.ini : expose_php = Off, puis redémarrez PHP-FPM/Apache. Pensez aussi à maintenir PHP à jour (les versions obsolètes ne reçoivent plus de correctifs de sécurité)."

        constat.titre.contains("Erreurs PHP verbeuses", true) ->
            "Les erreurs détaillées révèlent des chemins serveur, la structure du code, parfois des requêtes SQL ou des identifiants dans les messages — une mine d'informations pour un attaquant." to
            ("Dans php.ini (environnement de production) : display_errors = Off et log_errors = On avec error_log pointant vers un fichier non accessible publiquement. " +
                conseilErreursVerbosesParCms(cms))

        // --- Indexation ---
        constat.titre.contains("Chemins sensibles révélés", true) ->
            "Lister ces chemins dans robots.txt les cache des moteurs de recherche, mais pas des attaquants qui lisent simplement ce fichier public — l'effet est inverse à celui recherché." to
            ("Ne comptez jamais sur robots.txt pour la confidentialité. Protégez réellement ces chemins : authentification HTTP, restriction par IP, ou déplacez-les hors du dossier public du serveur web. " +
                conseilCheminsSensiblesParCms(cms))

        constat.titre.contains("Directory listing ouvert", true) ->
            "Un listing de dossier ouvert expose tous les fichiers qu'il contient — noms, parfois sauvegardes ou fichiers de configuration oubliés deviennent visibles et téléchargeables par n'importe qui." to
            ("Désactivez le listing : Apache (Options -Indexes dans .htaccess), nginx (autoindex off;). Vérifiez aussi qu'aucun fichier sensible n'y est présent. " +
                conseilDirectoryListingParCms(cms))

        // --- MySQL ---
        constat.titre.contains("Port MySQL exposé", true) ->
            "Même avec un mot de passe fort, exposer MySQL directement sur internet multiplie les tentatives d'intrusion automatisées (bots qui scannent le port 3306 en continu) et les risques liés aux futures failles du moteur MySQL lui-même." to
            "Configurez MySQL pour n'écouter que sur localhost (bind-address = 127.0.0.1) si l'application est sur le même serveur, ou restreignez l'accès au port 3306 via le pare-feu/groupe de sécurité à la seule IP du serveur applicatif."

        constat.titre.contains("Version serveur MySQL exposée", true) ->
            "La version MySQL visible permet de cibler des failles connues pour cette version précise." to
            "Mettez à jour MySQL vers une version maintenue, et masquez la bannière si votre configuration le permet (certains proxys comme ProxySQL le permettent)."

        // --- Serveur & réseau ---
        constat.titre.contains("Logiciel serveur", true) && constat.niveau == Niveau.AVERTISSEMENT ->
            "Connaître le logiciel serveur exact (ex: Apache/2.4.41) permet de cibler directement les failles connues pour cette version." to
            ("Masquez la bannière : Apache (ServerTokens Prod), nginx (server_tokens off;). " + conseilLogicielServeurParCms(cms))

        constat.titre.contains("Mixed content", true) ->
            "Charger des ressources en HTTP sur une page HTTPS permet à un attaquant en position d'interception (même wifi public) de modifier ces ressources (ex: injecter du JavaScript malveillant via une image ou un script HTTP)." to
            ("Remplacez tous les liens http:// internes par https:// ou par des URLs relatives (//domaine.com/...). " + conseilMixedContentParCms(cms))

        constat.titre.contains("Redirection HTTP", true) && constat.niveau == Niveau.CRITIQUE ->
            "Sans redirection forcée, un visiteur qui tape l'URL sans https:// (ou suit un vieux lien HTTP) reste en clair, exposant ses données à l'interception." to
            ("Forcez la redirection côté serveur : Apache (RewriteRule), nginx (return 301 https://\$host\$request_uri;), ou au niveau du CDN/reverse proxy si vous en utilisez un. " +
                conseilRedirectionHttpsParCms(cms))

        // --- Page d'administration ---
        constat.titre.contains("non protégée", true) ->
            "Une page de connexion admin atteignable sans aucune restriction réseau est une cible directe pour des attaques par force brute ou credential stuffing (test automatisé de combinaisons identifiant/mot de passe)." to
            ("Ajoutez une couche de protection en amont : authentification HTTP basique côté serveur, restriction par IP si l'équipe a une IP fixe, ou VPN. Activez aussi l'authentification à deux facteurs côté application si disponible. " +
                conseilProtectionAdminParCms(cms))

        constat.titre.contains("Renouvelez votre nom de domaine", true) ->
            "Un domaine expiré peut être racheté par un tiers, qui récupère alors tout le trafic et la réputation du site original — un risque élevé souvent négligé." to
            "Vérifiez la date d'expiration chez votre registrar et activez le renouvellement automatique."

        // --- Injection (sondes actives) ---
        constat.titre.contains("Réflexion XSS", true) ->
            "Une entrée utilisateur est renvoyée dans la page sans échappement HTML. Un attaquant peut y injecter du code HTML/JavaScript qui s'exécutera dans le navigateur des autres visiteurs (vol de session, défiguration, redirection malveillante)." to
            ("Échappez systématiquement toute donnée utilisateur avant de l'insérer dans le HTML. " + conseilXssParCms(cms))

        constat.titre.contains("Erreur SQL exposée", true) ->
            "Une requête SQL mal paramétrée accepte une entrée utilisateur brute, ce qui a fait remonter un message d'erreur de base de données. Au-delà du message d'erreur, cela signifie que la requête est probablement vulnérable à l'injection SQL (lecture, modification, voire suppression de données par un attaquant)." to
            "Utilisez systématiquement des requêtes préparées/paramétrées (jamais de concaténation de chaînes dans une requête SQL). En PHP avec PDO : \$stmt = \$pdo->prepare('SELECT * FROM table WHERE id = ?'); \$stmt->execute([\$id]);. Désactivez aussi l'affichage des erreurs SQL en production (cf. recommandation \"Erreurs PHP verbeuses\")."

        // --- Cas par défaut : pas de recommandation spécifique écrite, mais pas de texte générique creux ---
        else -> constat.details to "Aucune solution type disponible pour ce constat précis — à analyser manuellement selon le contexte du site."
    }

    return Recommandation(
        titreProbleme = constat.titre,
        niveau = constat.niveau,
        explication = explication,
        solution = solution,
        priorite = priorite
    )
}

// ============================================================
// CONSEILS SPÉCIFIQUES PAR CMS
// Un conseil par CMS détecté — même logique que WordPress,
// étendue à Joomla, Drupal, Magento, PrestaShop, Laravel.
// ============================================================

private fun conseilHstsParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS ->
        "Sur WordPress/Apache, ajoutez-le dans le .htaccess : Header always set Strict-Transport-Security \"max-age=31536000\"."
    DetectedCms.JOOMLA ->
        "Sur Joomla, activez l'option \"Forcer HTTPS\" dans Système > Configuration globale > Serveur, puis ajoutez l'en-tête dans le .htaccess (Joomla utilise aussi Apache par défaut) : Header always set Strict-Transport-Security \"max-age=31536000\"."
    DetectedCms.DRUPAL ->
        "Sur Drupal, ajoutez l'en-tête dans la configuration du serveur web, ou via un module comme \"Security Kit (seckit)\" qui gère HSTS directement depuis l'interface d'administration."
    DetectedCms.MAGENTO ->
        "Sur Magento, HSTS se configure généralement au niveau du serveur (nginx recommandé pour Magento 2) : add_header Strict-Transport-Security \"max-age=31536000\" always; dans le bloc server."
    DetectedCms.PRESTASHOP ->
        "Sur PrestaShop (Apache par défaut), ajoutez dans le .htaccess : Header always set Strict-Transport-Security \"max-age=31536000\". Vérifiez aussi que \"Activer SSL\" est coché dans Préférences > Général."
    DetectedCms.LARAVEL ->
        "Sur Laravel, ajoutez un middleware dédié (ou utilisez le package fruitcake/laravel-cors en complément) qui injecte l'en-tête HSTS sur chaque réponse, ou configurez-le directement au niveau de nginx/Apache devant l'application."
    DetectedCms.UNKNOWN ->
        "Configurez-le au niveau du serveur web (nginx: add_header, Apache: Header set) ou via le framework applicatif utilisé."
}

private fun conseilExpositionVersionParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS ->
        "Dans php.ini, mettez expose_php = Off. Sur Apache, ajoutez ServerTokens Prod et ServerSignature Off dans la config serveur."
    DetectedCms.JOOMLA ->
        "Dans Système > Configuration globale > Serveur, désactivez l'affichage de la version Joomla dans le code source (métadonnée generator). Masquez aussi X-Powered-By côté PHP (expose_php = Off) et côté Apache (ServerTokens Prod)."
    DetectedCms.DRUPAL ->
        "Drupal masque déjà sa version par défaut dans le HTML en production ; vérifiez que le mode \"développement\" n'est pas actif. Masquez X-Powered-By (expose_php = Off) et l'en-tête X-Generator via les settings du serveur."
    DetectedCms.MAGENTO ->
        "Magento 2 : désactivez l'en-tête X-Magento-* en production (bin/magento config:set dans certaines versions, ou via le reverse proxy). Masquez aussi expose_php = Off et ServerTokens Prod."
    DetectedCms.PRESTASHOP ->
        "Désactivez le mode debug (_PS_MODE_DEV_ à false dans config/defines.inc.php), ce qui supprime beaucoup de fuites d'information. Masquez aussi expose_php = Off."
    DetectedCms.LARAVEL ->
        "Vérifiez que APP_DEBUG=false dans le .env de production (sinon Laravel affiche des traces complètes avec chemins serveur). Masquez X-Powered-By via expose_php = Off côté PHP."
    DetectedCms.UNKNOWN ->
        "Désactivez l'en-tête X-Powered-By côté serveur (php.ini : expose_php = Off, ou au niveau du reverse proxy avec proxy_hide_header)."
}

private fun conseilProtectionAdminParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS ->
        "Plugins recommandés : Wordfence ou Limit Login Attempts Reloaded (limitent les tentatives de connexion automatisées)."
    DetectedCms.JOOMLA ->
        "Activez l'extension native \"Two Factor Authentication\" de Joomla (Utilisateurs > Gérer), et envisagez de renommer le dossier /administrator via une extension comme \"Admin Tools\" (Akeeba)."
    DetectedCms.DRUPAL ->
        "Le module \"Login Security\" limite les tentatives de connexion ; le module \"TFA\" ajoute l'authentification à deux facteurs sur /user/login."
    DetectedCms.MAGENTO ->
        "Magento Admin propose nativement le 2FA depuis la version 2.3+ (à activer dans Stores > Configuration > Security > 2FA). Pensez aussi à changer le chemin /admin par défaut (admin/url/use_custom_path)."
    DetectedCms.PRESTASHOP ->
        "PrestaShop permet de renommer le dossier d'administration directement (renommez simplement le dossier /adminXXXX et mettez à jour la configuration) — première protection simple contre le scan automatisé."
    DetectedCms.LARAVEL ->
        "Si l'authentification admin est custom, ajoutez un rate limiting sur la route de login (Laravel : middleware \"throttle\") et envisagez un package 2FA comme laravel/fortify."
    DetectedCms.UNKNOWN -> ""
}

private fun conseilCspParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS ->
        "Sur WordPress, le plugin \"HTTP Headers\" ou \"Really Simple SSL\" (module pare-feu) permet de définir la CSP sans toucher au code. Attention aux plugins qui chargent des scripts externes (Google Analytics, polices) à inclure dans la politique."
    DetectedCms.JOOMLA ->
        "Sur Joomla, ajoutez la CSP via le .htaccess ou l'extension \"Admin Tools\" (Akeeba) qui propose une configuration guidée des en-têtes de sécurité."
    DetectedCms.DRUPAL ->
        "Le module \"Security Kit (seckit)\" gère la CSP directement depuis l'interface d'administration Drupal, avec un mode rapport pour tester avant application stricte."
    DetectedCms.MAGENTO ->
        "Magento 2 inclut un module CSP natif (Magento_Csp) configurable via des fichiers csp_whitelist.xml par module — évite de casser les scripts des extensions tierces."
    DetectedCms.PRESTASHOP ->
        "Ajoutez la CSP via le .htaccess ; vérifiez bien les domaines des modules de paiement (Stripe, PayPal) qui chargent des scripts externes à whitelister."
    DetectedCms.LARAVEL ->
        "Utilisez un package comme spatie/laravel-csp qui génère la politique via un middleware dédié, plus simple à maintenir qu'un en-tête statique."
    DetectedCms.UNKNOWN -> ""
}

private fun conseilCookieSecureParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Sur WordPress, forcez-le via le plugin \"Really Simple SSL\" (option cookies sécurisés), ou dans wp-config.php en s'assurant que FORCE_SSL_ADMIN est activé."
    DetectedCms.JOOMLA -> "Dans Système > Configuration globale > Serveur, activez \"Forcer HTTPS\" — Joomla ajoute alors Secure automatiquement sur ses cookies."
    DetectedCms.DRUPAL -> "Dans settings.php, configurez le gestionnaire de session pour forcer 'cookie_secure' => true."
    DetectedCms.MAGENTO -> "Dans Stores > Configuration > Web > Session Validation Settings, activez \"Use Secure Cookies\"."
    DetectedCms.PRESTASHOP -> "Vérifiez que le SSL est forcé partout (Préférences > Général > Activer SSL partout) ; PrestaShop applique alors Secure à ses cookies."
    DetectedCms.LARAVEL -> "Dans config/session.php, mettez 'secure' => true (ou définissez SESSION_SECURE_COOKIE=true dans le .env)."
    DetectedCms.UNKNOWN -> "En PHP natif : session_set_cookie_params(['secure' => true]). Fonctionne uniquement si le site est entièrement servi en HTTPS."
}

private fun conseilCookieHttpOnlyParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "WordPress active HttpOnly par défaut sur ses cookies d'authentification ; si absent, vérifiez qu'un plugin ou thème ne le désactive pas manuellement."
    DetectedCms.JOOMLA -> "Joomla active HttpOnly par défaut depuis les versions récentes ; vérifiez la configuration de session dans Système > Configuration globale."
    DetectedCms.DRUPAL -> "Dans settings.php, le gestionnaire de session Drupal doit avoir 'cookie_httponly' => true (actif par défaut sur les installations récentes)."
    DetectedCms.MAGENTO -> "Vérifiez php.ini : session.cookie_httponly = 1, en complément des réglages Magento Admin."
    DetectedCms.PRESTASHOP -> "Vérifiez php.ini : session.cookie_httponly = 1 ; PrestaShop hérite de ce réglage serveur pour ses cookies de session."
    DetectedCms.LARAVEL -> "Dans config/session.php, 'http_only' => true (actif par défaut dans les installations standards — à vérifier si modifié)."
    DetectedCms.UNKNOWN -> "En PHP natif : session_set_cookie_params(['httponly' => true])."
}

private fun conseilCookieSameSiteParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Un plugin comme \"HTTP Headers\" permet de forcer SameSite sans modifier le code de WordPress directement."
    DetectedCms.JOOMLA -> "Configurable via le .htaccess ou une extension de sécurité (Admin Tools) qui gère les attributs de cookies globalement."
    DetectedCms.DRUPAL -> "Le module \"Security Kit (seckit)\" permet de définir SameSite sur les cookies de session Drupal."
    DetectedCms.MAGENTO -> "À configurer au niveau du serveur web (nginx/Apache) ou via un module tiers, Magento ne l'expose pas nativement dans son admin."
    DetectedCms.PRESTASHOP -> "À définir via le .htaccess ou la configuration PHP (session.cookie_samesite dans php.ini, PHP 7.3+)."
    DetectedCms.LARAVEL -> "Dans config/session.php, définissez 'same_site' => 'lax' (ou 'strict' selon les besoins de navigation inter-site)."
    DetectedCms.UNKNOWN -> "Configurable via php.ini (session.cookie_samesite, PHP 7.3+) ou directement dans l'en-tête Set-Cookie applicatif."
}

private fun conseilErreursVerbosesParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Dans wp-config.php, assurez-vous que WP_DEBUG est à false en production (souvent oublié après une phase de développement)."
    DetectedCms.JOOMLA -> "Dans Système > Configuration globale > Serveur, le \"Niveau de rapport d'erreur\" doit être réglé sur \"Aucun\" ou \"Simple\" en production, jamais \"Maximum\"."
    DetectedCms.DRUPAL -> "Dans Configuration > Développement > Journalisation et erreurs, désactivez l'affichage des erreurs à l'écran en production (les logs restent utiles, l'affichage public non)."
    DetectedCms.MAGENTO -> "Vérifiez que le mode Magento est \"production\" (bin/magento deploy:mode:show) ; le mode \"developer\" affiche des erreurs détaillées à tout visiteur."
    DetectedCms.PRESTASHOP -> "Dans config/defines.inc.php, assurez-vous que _PS_MODE_DEV_ est à false — c'est la cause la plus fréquente d'erreurs verbeuses sur PrestaShop en production."
    DetectedCms.LARAVEL -> "Dans le .env de production, APP_DEBUG=false est impératif : avec true, Laravel affiche des traces complètes avec chemins serveur et parfois des variables d'environnement."
    DetectedCms.UNKNOWN -> ""
}

private fun conseilCheminsSensiblesParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Pour wp-content/uploads ou les dossiers de sauvegarde, utilisez un fichier .htaccess dédié avec Deny from all plutôt que de compter sur robots.txt."
    DetectedCms.JOOMLA -> "Les dossiers /administrator/logs ou /tmp doivent être protégés par .htaccess, pas seulement exclus de robots.txt."
    DetectedCms.DRUPAL -> "Le dossier /sites/default/files/ ne doit jamais contenir de fichiers de configuration sensibles ; settings.php doit rester hors du document root accessible publiquement si possible."
    DetectedCms.MAGENTO -> "Les dossiers var/, app/etc/ et pub/media/ nécessitent des règles .htaccess strictes — ce sont des cibles fréquentes car ils contiennent souvent des sauvegardes ou la config DB."
    DetectedCms.PRESTASHOP -> "Le dossier /admin (ou son équivalent renommé) ne doit jamais être listé dans robots.txt de façon visible — préférez le renommer plutôt que de le signaler."
    DetectedCms.LARAVEL -> "Vérifiez que le document root du serveur pointe bien vers /public et non vers la racine du projet Laravel — sinon .env et le dossier app/ deviennent potentiellement accessibles directement."
    DetectedCms.UNKNOWN -> ""
}

private fun conseilDirectoryListingParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Vérifiez particulièrement wp-content/uploads/ : c'est le dossier le plus souvent exposé avec listing actif par défaut chez certains hébergeurs mutualisés."
    DetectedCms.JOOMLA -> "Vérifiez /images/ et /media/ en priorité — dossiers fréquemment oubliés lors du durcissement initial."
    DetectedCms.DRUPAL -> "Vérifiez /sites/default/files/ — le listing y est particulièrement risqué si des documents privés y ont été uploadés par erreur."
    DetectedCms.MAGENTO -> "Vérifiez pub/media/ et var/log/ — ce dernier peut contenir des informations sensibles sur les erreurs applicatives."
    DetectedCms.PRESTASHOP -> "Vérifiez /img/ et /upload/ — le dossier d'upload de produits est une cible fréquente de scan de listing."
    DetectedCms.LARAVEL -> "Vérifiez storage/ et bootstrap/cache/ ne sont jamais servis publiquement — seul le contenu de public/ devrait être accessible."
    DetectedCms.UNKNOWN -> ""
}

private fun conseilLogicielServeurParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS, DetectedCms.JOOMLA, DetectedCms.PRESTASHOP ->
        "Ces CMS tournent le plus souvent sur Apache : pensez à ServerTokens Prod dans httpd.conf (pas seulement le .htaccess, qui ne peut pas modifier cette directive)."
    DetectedCms.DRUPAL ->
        "Drupal fonctionne aussi bien sur Apache que nginx : adaptez la directive de masquage à votre serveur réel (server_tokens off; pour nginx)."
    DetectedCms.MAGENTO ->
        "Magento 2 est généralement déployé derrière nginx (recommandation officielle) : server_tokens off; dans le bloc http du nginx.conf."
    DetectedCms.LARAVEL ->
        "Laravel est souvent servi derrière nginx + PHP-FPM : masquez la bannière côté nginx (server_tokens off;) et côté PHP-FPM si exposé directement."
    DetectedCms.UNKNOWN -> ""
}

private fun conseilMixedContentParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Utilisez le plugin \"Really Simple SSL\" qui corrige automatiquement la majorité des liens mixtes, y compris ceux stockés en base par d'anciens articles."
    DetectedCms.JOOMLA -> "Utilisez l'extension \"Force SSL\" ou vérifiez manuellement les champs d'articles contenant des liens absolus en http://."
    DetectedCms.DRUPAL -> "Le module \"Mixed Content Fixer\" réécrit automatiquement les ressources http:// en https:// à la volée dans le HTML généré."
    DetectedCms.MAGENTO -> "Vérifiez les URLs de base configurées (Stores > Configuration > Web > Base URLs) : elles doivent être en https:// pour les deux champs (sécurisé et non sécurisé)."
    DetectedCms.PRESTASHOP -> "Dans Préférences > Général, activez \"Activer SSL\" partout ; vérifiez aussi les URLs codées en dur dans les templates du thème."
    DetectedCms.LARAVEL -> "Si l'app est derrière un proxy/load balancer qui termine le SSL, ajoutez \\URL::forceScheme('https') dans AppServiceProvider pour que toutes les URLs générées soient en https://."
    DetectedCms.UNKNOWN -> "Utilisez un plugin ou script de réécriture automatique des liens, ou corrigez-les manuellement dans le code/les templates."
}

private fun conseilXssParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Utilisez les fonctions d'échappement natives de WordPress selon le contexte : esc_html() pour du texte, esc_attr() pour un attribut HTML, esc_url() pour une URL — jamais d'affichage brut de \$_GET/\$_POST ou de méta-données utilisateur."
    DetectedCms.JOOMLA -> "Utilisez htmlspecialchars() ou les méthodes d'échappement du framework Joomla (JFilterOutput::objectHtmlSafe) avant tout affichage de donnée utilisateur dans un template."
    DetectedCms.DRUPAL -> "Drupal échappe automatiquement via Twig ({{ variable }}) dans la plupart des cas ; vérifiez qu'aucun code ne contourne ça avec |raw ou {!! !!} sans validation préalable."
    DetectedCms.MAGENTO -> "Dans les templates Magento/Blocks, utilisez les méthodes d'échappement du Escaper (escapeHtml(), escapeHtmlAttr()) plutôt qu'un affichage direct des données."
    DetectedCms.PRESTASHOP -> "Dans les templates Smarty, utilisez le modificateur |escape (ou |escape:'html') sur toute variable affichant une donnée utilisateur."
    DetectedCms.LARAVEL -> "Dans Blade, utilisez {{ \$variable }} (échappement automatique) plutôt que {!! \$variable !!} (non échappé) pour toute donnée provenant de l'utilisateur."
    DetectedCms.UNKNOWN -> "Utilisez systématiquement htmlspecialchars() (PHP) ou l'équivalent du framework utilisé avant d'afficher une donnée utilisateur dans le HTML."
}

private fun conseilRedirectionHttpsParCms(cms: DetectedCms): String = when (cms) {
    DetectedCms.WORDPRESS -> "Le plugin \"Really Simple SSL\" gère cette redirection automatiquement sans toucher au .htaccess manuellement."
    DetectedCms.JOOMLA -> "Activez \"Forcer HTTPS\" dans Système > Configuration globale > Serveur, qui gère la redirection côté application en complément du serveur."
    DetectedCms.DRUPAL -> "Le module \"Secure Pages\" ou une règle au niveau du serveur web gèrent cette redirection ; vérifiez aussi \$base_url dans settings.php."
    DetectedCms.MAGENTO -> "Dans Stores > Configuration > Web > Base URLs, configurez les deux URLs (sécurisée/non sécurisée) en https://, en plus de la redirection serveur."
    DetectedCms.PRESTASHOP -> "Activez \"Activer SSL\" ET \"Activer SSL sur tout le site\" dans Préférences > Général — les deux options sont nécessaires pour une redirection complète."
    DetectedCms.LARAVEL -> "Ajoutez un middleware ForceHttps, ou configurez la redirection au niveau du serveur web / load balancer en amont de l'application."
    DetectedCms.UNKNOWN -> ""
}

/**
 * Génère toutes les recommandations d'un rapport global, triées par priorité
 * (urgent d'abord), pour affichage direct dans l'écran de rapport.
 */
fun genererToutesLesRecommandations(rapport: RapportGlobal): List<Recommandation> {
    return rapport.tousLesConstats
        .filter { it.second.niveau != Niveau.CONFORME } // seuls les problèmes ont une solution à proposer
        .map { (nomEtape, constat) -> genererRecommandation(nomEtape, constat, rapport.cmsDetecte) }
        .sortedBy { it.priorite }
}

/**
 * Même logique, mais pour le RapportInjection (sondes XSS/SQL), qui est produit
 * séparément du RapportGlobal (car il dépend du crawl, optionnel).
 */
fun genererRecommandationsInjection(rapportInjection: RapportInjection, cms: DetectedCms): List<Recommandation> {
    return rapportInjection.constats
        .filter { it.niveau != Niveau.CONFORME }
        .map { constat -> genererRecommandation("Tests d'entrée", constat, cms) }
        .sortedBy { it.priorite }
}
