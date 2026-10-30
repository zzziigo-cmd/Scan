# Guide & Configuration de Déploiement Google Play Store
## Application : **Scan — Audit & Sécurité Web CMS-Aware**
**Version :** 2.4.0 (VersionCode: 1)  
**Package / ApplicationId :** `com.aistudio.scan.zqkvd`  
**Auteur & Développeur Principal :** Redouane Oulaati  
**Contact :** redouaneoulaati@gmail.com | +212646898915  

---

### 1. Fiche du Store (Store Listing Metadata)

- **Titre de l'application :** Scan (ou `Scan - Audit de Sécurité Web`) (≤ 30 caractères)
- **Description courte (≤ 80 caractères) :**
  > Scanner de sécurité web CMS-aware, détection OWASP et tests d'intrusion éthiques.
- **Description complète :**
  > L'application **Scan** est un scanner de vulnérabilités et d'audit de sécurité web professionnel, conçu pour les développeurs, administrateurs système et auditeurs en cybersécurité.
  >
  > Contrairement aux scanners aveugles, son moteur intelligent repose sur un pipeline contextuel en **9 étapes séquentielles adaptées au CMS détecté** (WordPress, Drupal, Joomla, Magento, PrestaShop, Laravel) :
  > 1. Détection passive et fingerprinting CMS
  > 2. Audit SSL/TLS et en-têtes HTTP de durcissement (HSTS, CSP, X-Frame-Options)
  > 3. Contrôle des drapeaux de sécurité des cookies (Secure, HttpOnly, SameSite)
  > 4. Comportement du moteur PHP et erreurs verbeuses
  > 5. Indexation, robots.txt et directory listing sensible
  > 6. Exposition passive des ports de base de données (MySQL 3306)
  > 7. Analyse serveur et réseau (redirections HTTPS, mixed content)
  > 8. Accessibilité ciblée des pages d'administration avec protection anti-faux-positifs
  > 9. Consolidation du score global /100 et synthèse d'audit
  >
  > **Modules Avancés :**
  > - **Tests d'entrée utilisateur non destructifs :** Détection de réflexion XSS et injection d'erreurs SQL sans compromission ni extraction de données.
  > - **Conformité OWASP Top 10 :** Cartographie instantanée selon le référentiel international 2021.
  > - **Audit Architectural :** Chaînes de risques et recommandations d'infrastructure (WAF, segmentation réseau, IaC).
  > - **Solutions Recommandées :** Correctifs concrets et lignes de configuration affichés directement sous chaque constat vulnérable.
  > - **Persistance & Exports :** Base de données locale Room, export JSON complet et génération de rapports synthétiques PDF/Texte.
  > - **Sécurité Renforcée :** Authentification biométrique, double facteur (2FA), chiffrement de bout en bout (E2EE) pour le support et synchronisation cloud / Google Drive.

---

### 2. Configuration des Abonnements Mensuels (Google Play Billing)

L'application intègre des options de monétisation par abonnements périodiques récurrents :

| Produit ID | Intitulé | Tarif Mensuel | Avantages Inclus |
|---|---|---|---|
| `scan_plan_free` | Standard Gratuit | 0,00 €/mois | 3 scans/mois, détection CMS, export JSON simple |
| `scan_plan_pro_monthly` | Pro Sécurité | 19,99 €/mois | Audits illimités, tests d'injection XSS/SQL, matrice OWASP, export PDF, support prioritaire |
| `scan_plan_enterprise_monthly` | Entreprise Audit | 79,99 €/mois | Surveillance continue 24/7, synchronisation cloud automatique, support direct dédié, certificat de conformité |

---

### 3. Permissions Android Déclarées

Conformes aux règles de moindre privilège du Google Play Developer Program :
- `android.permission.INTERNET` : Requêtes HTTP/HTTPS vers les serveurs audités.
- `android.permission.ACCESS_NETWORK_STATE` : Vérification préalable de la connectivité réseau.
- `android.permission.USE_BIOMETRIC` : Déverrouillage sécurisé par empreinte digitale.
- `android.permission.POST_NOTIFICATIONS` : Notifications push pour les alertes de sécurité critiques détectées.
- `android.permission.VIBRATE` : Retour tactile pour alertes de sécurité.

---

### 4. Commandes de Compilation & Génération d'App Bundle (.aab)

Pour générer l'Android App Bundle prêt pour Google Play Console :

```bash
# Vérification et tests unitaires locaux
gradle :app:testDebugUnitTest

# Génération de l'APK de débogage
gradle :app:assembleDebug

# Génération de l'Android App Bundle (AAB) signé pour production
gradle :app:bundleRelease
```
L'artefact final sera disponible sous :
`app/build/outputs/bundle/release/app-release.aab`
