# Moteur de scan intelligent — Pipeline CMS-aware

## Principe général

Contrairement à un scanner qui teste une grosse liste de chemins/signatures à l'aveugle,
ce moteur avance par étapes séquentielles où **chaque étape nourrit la suivante** avec
une sortie typée :

```
CmsDetectionResult → SslHeadersReport → CookieReport → PhpBehaviorReport
→ IndexationReport → MysqlExposureReport → ServerNetworkReport → AdminPageReport
→ RapportGlobal → RapportArchitectural
```

Chaque étape ne teste que ce qui est pertinent compte tenu du contexte détecté en amont
(ex : pas de test `/wp-login.php` sur un site qui n'est pas WordPress).

## Fichiers — package `com.example.security`

| Fichier | Rôle |
|---|---|
| `CmsAwareAdminScanner.kt` | Étape 1 : détection de CMS (fingerprinting) + verrou de propriété (`OwnershipGate`) |
| `Step2_SslAndHeadersScan.kt` | Étape 2 : SSL/TLS + en-têtes de sécurité |
| `Step3_CookieScan.kt` | Étape 3 : sécurité des cookies (Secure, HttpOnly, SameSite) |
| `Step4_PhpBehaviorScan.kt` | Étape 4 : comportement PHP (version exposée, erreurs verbeuses) |
| `Step5_IndexationScan.kt` | Étape 5 : robots.txt, sitemap, directory listing |
| `Step6_MysqlExposureScan.kt` | Étape 6 : exposition du port MySQL (lecture passive de bannière uniquement) |
| `Step7_ServerNetworkScan.kt` | Étape 7 : disponibilité, logiciel serveur, redirection HTTPS, mixed content |
| `Step8_AdminPageAccessibilityScan.kt` | Étape 8 : accessibilité de la page admin (chemins ciblés par CMS, vérification de contenu anti-faux-positifs) |
| `Step9_FinalReportAndOrchestrator.kt` | Étape 9 : assemblage du `RapportGlobal` (score /100) + `runPipelineComplet()` qui enchaîne tout |
| `DiagnosticEngine.kt` | Transforme chaque blocage (timeout, 403, 429...) en explication justifiée (pare-feu, WAF, authentification...) plutôt qu'un message d'erreur brut |
| `RecommendationEngine.kt` | Pour chaque constat : explication + solution concrète, contextualisée par CMS (WordPress, Joomla, Drupal, Magento, PrestaShop, Laravel) |
| `ArchitectureAnalysisEngine.kt` | Corrèle les constats en "chaînes de risque" combinées + propose des solutions architecturales (WAF, segmentation réseau, IaC...) |
| `Crawler.kt` | Explore l'application depuis l'URL de démarrage (liens internes, formulaires, paramètres d'URL) ; se limite aux ports 80/443, sinon signale qu'une analyse personnalisée est requise |
| `OwaspMapper.kt` | Classe tous les constats du pipeline dans les catégories officielles OWASP Top 10 (2021), avec description + conseil de résolution par catégorie |
| `InjectionProbeEngine.kt` | Sondes **non destructives** sur les entrées découvertes par le crawler : détection de réflexion XSS (marqueur inoffensif, jamais de script exécuté) et d'erreurs SQL exposées (simple apostrophe, aucune extraction de données) — alimente la catégorie OWASP A03 |

## Fichiers — package `com.example.ui`

| Fichier | Rôle |
|---|---|
| `Step10_ScanProgressScreen.kt` | Écran affichant la progression du pipeline étape par étape |
| `Step11_ReportScreen.kt` | Écran de rapport : donut chart, barres par catégorie, fiches problème/solution dépliables |
| `Step12_ArchitectureScreen.kt` | Écran d'analyse architecturale : chaînes de risque + axes structurels |
| `Step13_OwaspScreen.kt` | Écran de conformité OWASP Top 10 : vue d'ensemble des 10 catégories + détail des constats liés à chacune |
| `Step14_InjectionScreen.kt` | Écran de détail des tests d'entrée : statistiques du crawl, résultats champ par champ, et solution recommandée dépliable pour chaque champ vulnérable (réutilise `RecommendationEngine`) |
| `SecurityAuditFlow.kt` | Point d'entrée qui relie les 5 écrans (Progression → Rapport → OWASP / Architecture / Tests d'entrée), porte `RapportComplet` de bout en bout |

## Exploration + scan + tests d'entrée combinés

`runPipelineCompletAvecCrawl(urlDemarrage, domain, ownership)` (dans `Step9_FinalReportAndOrchestrator.kt`)
enchaîne désormais trois phases :
1. `Crawler` — repère les entrées utilisateur (formulaires, paramètres d'URL)
2. `InjectionProbeEngine` — teste chaque entrée trouvée avec des sondes non destructives
   (réflexion XSS, erreur SQL exposée), plafonné à 20 entrées par défaut
3. Le pipeline de scan complet habituel (étapes 1 à 9)

Résultat : `RapportComplet` (rapport de scan + rapport de crawl + rapport d'injection, ce dernier
`null` si aucune entrée n'a été trouvée). Si le port de `urlDemarrage` n'est pas 80/443, le crawl
et les tests d'entrée sont sautés — conforme à la contrainte "analyse personnalisée requise" pour
les ports non standards.

**Garde-fous du module de tests d'entrée** (voir les commentaires en tête de `InjectionProbeEngine.kt`) :
chaque entrée n'est testée qu'une seule fois par type de sonde, le marqueur XSS est inoffensif même
s'il s'exécute (pas de `<script>`), et la sonde SQL n'injecte qu'une apostrophe isolée sans jamais
tenter d'extraire de données.

## Points d'intégration à faire avant compilation

1. **`OwnershipGate.resolveTxtRecords()`** (dans `CmsAwareAdminScanner.kt`) est un `TODO()` —
   à relier à la lib DNS déjà utilisée dans Webscan pour la vérification de propriété par DNS TXT.
2. Dépendances Gradle nécessaires : `okhttp3`, `kotlinx-coroutines`, Compose Material3,
   `androidx.compose.material:material-icons-extended` (pour les icônes utilisées).
3. Le `TargetedAdminScanner` du fichier 1 (classe historique) n'est plus utilisé par
   l'orchestrateur final (`runPipelineComplet`), qui a sa propre logique de récupération
   de page d'accueil (`fetchHomePage`) — à nettoyer si redondant avec le reste du projet Webscan.

## Ce qui a été corrigé pendant le développement (traçabilité pour la soutenance)

- **Faux positifs sur la détection admin** : un code HTTP 200 seul ne suffit plus à conclure
  qu'une page admin est exposée — le contenu est vérifié (présence d'un champ password, etc.).
- **Parsing des cookies** : capture individuelle via `OkHttp.headers.values("Set-Cookie")`
  au lieu d'un découpage par regex fragile sur des en-têtes fusionnés.
- **Ordre `resp.close()`** corrigé dans l'étape 8 (les en-têtes étaient lus après fermeture
  de la réponse).
- **Diagnostic des blocages** : chaque échec de connexion ou code HTTP de refus est classifié
  (pare-feu, WAF, authentification, rate limiting...) plutôt que rapporté comme une erreur générique.

## Prochaines étapes suggérées

- Persistance Room du `RapportGlobal` pour l'historique
- Export PDF/JSON du rapport (déjà présent dans l'app Webscan existante, à relier)
- Génération périodique automatique via WorkManager (déjà présent dans l'app Webscan existante)
- Persistance Room et export restent les principaux chantiers restants (voir sections ci-dessus)
