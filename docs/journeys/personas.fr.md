# Brouillon de réconciliation des personas

Ce fichier transforme la liste de personas extraite en une structure favorable à la réconciliation.

L'objectif n'est pas de finaliser chaque étiquette pour l'instant. L'objectif est de créer une forme
markdown stable qui nous permette de :

- fusionner les synonymes évidents
- regrouper les variantes de rôle au sein d'une famille canonique
- séparer les responsabilités spécialiste / opérateur / admin-gestionnaire
- signaler les étiquettes ambiguës qui nécessitent encore une décision de nommage

Ces personas sont des archétypes de parcours utilisateur prototype, et non des rôles de sécurité ou
des modèles de permissions. Ils sont destinés à nous aider à identifier les flux de travail pertinents,
la propriété des écrans et les parcours narratifs pour le prototype.

## Règles de travail

- Traiter les synonymes exact de titre de poste comme un seul persona canonique.
- Conserver les sous-domaines fonctionnels lorsqu'ils affectent la propriété du flux de travail.
- Préférer un modèle domaine + responsabilité plutôt qu'une prolifération brute de titres.
- Laisser `Admin` et `Manager` non résolus sauf si le contexte de la story ancre clairement le domaine.
- Permettre à une étiquette brute de se mapper d'abord vers une famille canonique, puis optionnellement
  vers un persona canonique plus précis.
- Ne pas interpréter ces étiquettes comme des définitions RBAC ; un même utilisateur humain peut
  participer à plusieurs parcours.

## Structure canonique proposée

### 1. Service client

#### 1.1 Conseiller service

- Axe de parcours : Prise en charge détaillée du véhicule, coordination des devis et des ordres de
  travail, et affectation des techniciens et des emplacements dans la gestion de l'atelier et
  l'exécution des ordres de travail.

- Persona canonique : `Service Advisor`
- Fusionner dans ce persona :
  - `Service Advisor`
- Garder distinct mais adjacent :
  - `CSR`
  - `Customer Service Representative`
  - `Customer Support Associate`
- Notes :
  - `Service Advisor` est déjà le persona d'exécution dominant côté client et doit rester distinct
    des rôles génériques de support client.

#### 1.2 Support client / Accueil comptoir / Encaissement

- Axe de parcours : Accueil au comptoir, prise en charge légère, recherche du client, travaux CRM
  côté client, et encaissement en fin de transaction incluant la revue de facture, la facturation,
  la collecte de paiement et la clôture.

- Famille de personas canonique : `Customer Support Associate`
- Membres de la famille :
  - `Customer Service Representative`
  - `CSR`
  - `Customer Support Associate`
- Fusionner dans la famille `Customer Support Associate` :
  - `Counter Associate`
  - `Front Desk`
  - `Cashier`
  - `POS Cashier`
  - `POS Clerk`
- Notes :
  - `Customer Support Associate` est le nom de famille pour l'ensemble du parcours accueil-comptoir
    et encaissement.
  - `Customer Support / Front Counter / Checkout` doit actuellement être traité comme une seule
    famille de parcours persona.
  - `CSR` et `Customer Service Representative` restent des variantes de nommage au sein de la même
    famille globale.

#### 1.3 Gestion et relation client

- Axe de parcours : Gestion de la relation client sur la durée, notamment pour les comptes commerciaux
  et à long terme, avec une propriété orientée CRM.

- Famille de personas canonique : `Account Management`
- Persona canonique : `Account Manager`
- Fusionner dans `Account Manager` :
  - `Fleet Account Manager`
- Garder distinct :
  - `Marketing Manager`
- Notes :
  - `Fleet Account Manager` doit actuellement se réconcilier dans `Account Manager`.
  - `Marketing Manager` reste distinct.

### 2. Opérations atelier

#### 2.1 Direction de l'atelier

- Axe de parcours : Direction opérationnelle au niveau du site, coordination des conseillers service,
  gestion de l'atelier, gestion des équipes et exécution des ordres de travail.

- Famille de personas canonique : `Location Management`
- Persona canonique : `Location Manager`
- Fusionner dans `Location Manager` :
  - `Shop Manager`
  - `Store Manager`
  - `Back Office`
  - `Back Office Manager`
  - `Manager`
  - `Approver`
- Garder distinct mais adjacent :
  - `Director`
- Notes :
  - `Location Manager` est le regroupement actuel pour le parcours principal de gestion au niveau
    du site.
  - `Approver` doit actuellement se réconcilier dans `Location Manager` plutôt que rester un persona
    autonome.

#### 2.2 Dispatch et planification

- Axe de parcours : Coordination de la planification et du dispatch, notamment pour les techniciens
  mobiles, avec un flux de travail adjacent à la fonction de conseiller service.

- Famille de personas canonique : `Dispatch`
- Persona canonique : `Dispatcher`
- Variantes de rôle du `Dispatcher` :
  - `Scheduler`
  - `Mobile Lead`
- Notes :
  - `Scheduler` doit actuellement se réconcilier dans `Dispatcher`.
  - `Mobile Lead` doit actuellement se réconcilier côté dispatch / conseiller service plutôt que
    rester un persona de chef technicien distinct.

### 3. Exécution en atelier

#### 3.1 Technicien

- Axe de parcours : Travaux de réparation sur véhicule, suivi de l'avancement du flux de réparation,
  et exécution des ordres de travail côté technicien.

- Persona canonique : `Technician`
- Fusionner dans ce persona :
  - `Technician`
  - `Mechanic`
- Notes :
  - Il s'agit de l'une des fusions les plus solides de l'ensemble actuel.
  - Si nécessaire plus tard, `Mobile Technician` peut devenir un enfant de `Technician`.

#### 3.2 Rôles pièces

- Axe de parcours : Approvisionnement en pièces, émission, disponibilité, et support comptoir pour
  les flux de travaux d'atelier et d'exécution des ordres de travail.

- Famille de personas canonique : `Parts`
- Enfants candidats :
  - `Parts Manager`
  - `Parts Associate`
- Fusionner dans `Parts Associate` :
  - `Parts Counter Staff`
- Notes :
  - Ces personas doivent rester distincts du `Technician`, même lorsque les stories les associent.

### 4. Inventaire et entrepôt

#### 4.1 Gestion des stocks

- Axe de parcours : Contrôle des stocks, gouvernance des stocks, et supervision opérationnelle de
  l'exactitude et de la politique de mouvement d'inventaire.

- Famille de personas canonique : `Inventory Management`
- Enfants candidats :
  - `Inventory Control Manager`
  - `Inventory Staff`
- Variantes de rôle de `Inventory Control Manager` :
  - `Inventory Manager`
  - `Inventory Controller`
  - `Inventory Admin`
- Notes :
  - `Inventory Manager`, `Inventory Controller` et `Inventory Admin` doivent actuellement se
    réconcilier dans `Inventory Control Manager`.

#### 4.2 Opérations entrepôt

- Axe de parcours : Réception physique, stockage, rangement, et manutention au sol des matériaux
  et de l'inventaire.

- Famille de personas canonique : `Warehouse`
- Enfants :
  - `Warehouse Manager`
  - `Warehouse Associate`
- Variantes spécialisées de `Warehouse Associate` :
  - `Receiver`
  - `Stock Clerk`
- Notes :
  - `Receiver` et `Stock Clerk` doivent être traités comme des types spécialisés de
    `Warehouse Associate`, et non comme des personas pairs distincts.

### 5. Comptabilité et finance

Ce domaine bénéficie le plus d'une hiérarchie structurée. La division principale doit être :

- spécialiste
- opérations
- admin / gestionnaire
- supervision / audit

#### 5.1 Spécialiste comptable

- Axe de parcours : Exécution comptable au quotidien, travaux adjacents au grand livre, et opérations
  financières transactionnelles.

- Persona canonique : `Accounting Associate`
- Fusionner dans `Accounting Associate` :
  - `Accountant`
  - `GL Accountant`
  - `GL Specialist`
  - `Accounting Clerk`
  - `Accounts Receivable Clerk`
  - `AP Clerk`
  - `Billing Specialist`
  - `Back Office Accountant`
  - `Payroll Clerk`
- Notes :
  - Les étiquettes GL, AP, AR, facturation, paie et comptable généraliste doivent actuellement se
    réconcilier dans `Accounting Associate`.

#### 5.2 Opérations comptables

- Axe de parcours : surveillance, résolution de problèmes et support opérationnel pour les flux
  d'ingestion comptable, de réconciliation et de traitement financier.

- Persona canonique : `Accounting Associate`
- Fusionner dans `Accounting Associate` :
  - `Accounting Operations User`
  - `Accounting OPS`
  - `Accounting OPS User`
  - `Accounting Operations Analyst`
  - `Accounting OPS Specialist`
  - `Accounting Integration Operator`
  - `Integration Operator`
  - `Finance OPS User`
- Notes :
  - Les étiquettes d'opérations comptables doivent actuellement se réconcilier dans
    `Accounting Associate`.

#### 5.3 Direction finance / comptabilité

- Axe de parcours : Supervision financière, propriété de la politique comptable, approbations, et
  contrôle managérial des processus financiers.

- Famille de personas canonique : `Finance / Accounting Management`
- Enfants :
  - `Controller`
  - `Finance Manager`
  - `Accounting Manager`
- Fusionner dans `Controller` :
  - `Financial Controller`
  - `Finance`
- Fusionner dans `Finance Manager` :
  - `FinanceManager`
- Notes :
  - `Controller` est le regroupement canonique actuel pour les parcours de supervision financière
    et de contrôle de gestion.
  - Les étiquettes admin finance / comptabilité doivent se réconcilier dans `System Administrator`,
    et non rester dans la famille de personas direction finance.
  - Cette famille est le bon foyer pour la gestion, les approbations, l'autorité de configuration
    et la propriété des politiques.

#### 5.4 Audit et supervision

- Axe de parcours : Revue, conformité, traçabilité, et visibilité orientée audit sur les activités
  financières sensibles ou réglementées.

- Famille de personas canonique : `Audit`
- Fusionner ou mapper dans cette famille :
  - `Auditor`
  - `Compliance Auditor`
  - `System Auditor`
- Notes :
  - Garder l'audit distinct du gestionnaire / admin finance, car les stories décrivent des fonctions
    de revue et de contrôle, non une propriété opérationnelle.

### 6. Produit, tarification et administration métier

#### 6.1 Administration produit

- Axe de parcours : Mise en place des produits, maintenance, et gestion administrative des données
  produit utilisées à l'échelle de l'entreprise.

- Famille de personas canonique : `Product Administration`
- Fusionner dans ce persona :
  - `Product Admin`
  - `Product Administrator`

#### 6.2 Tarification

- Axe de parcours : Stratégie tarifaire, maintenance des prix, et flux de gouvernance tarifaire
  qui influencent le comportement côté vente.

- Famille de personas canonique : `Pricing`
- Enfants candidats :
  - `Pricing Administrator`
  - `Pricing Analyst`
  - `Pricing Manager`

#### 6.3 Administration générale

- Axe de parcours : Parcours d'administration système, propriété de configuration, et maintenance
  administrative des capacités système partagées.

- Famille de personas canonique : `System Administration`
- Persona canonique : `System Administrator`
- Fusionner dans `System Administrator` :
  - `Admin`
  - `Admin User`
- Notes :
  - Ce groupe est désormais réservé aux rôles d'administration de type système.
  - `Admin` et `Admin User` doivent actuellement se réconcilier dans `System Administrator`.

#### 6.4 Gestion administrative à portée locale

- Axe de parcours : Propriété administrative des opérations de site et de personnel, fonctionnant
  davantage comme une gestion métier que comme une administration système.

- Famille de personas canonique : `Location Management`
- Persona canonique : `Location Manager`
- Fusionner dans `Location Manager` :
  - `Shop Administrator`
  - `HR Administrator`
  - `OPS Admin`
- Notes :
  - Ces personas doivent être considérés comme des rôles de gestion ou de propriété opérationnelle
    plutôt que des rôles d'administration système.
  - Ils doivent rejoindre le même parcours de gestion de site que `Shop Manager`, `Store Manager`
    et `Back Office`.

### 7. Acteurs techniques / plateforme

- Axe de parcours : Implémentation technique, intégration, support plateforme, et flux de travail
  orientés architecture qui permettent l'écosystème prototype.

- Famille de personas canonique : `Technical / Platform`
- Enfants candidats :
  - `Backend Engineer`
  - `Domain Architect`
  - `Platform Engineer`
  - `Platform Integrator`
  - `Integration Support Engineer`
  - `Moqui Engineer`

### 8. Acteurs non humains

- Axe de parcours : Comportement système automatisé ou piloté à l'exécution, participant aux flux
  de travail sans représenter un persona de parcours humain.

- Famille d'acteurs canonique : `System`
- Acteurs non humains :
  - `System`
  - `System User`
- Notes :
  - `System` ne doit pas être traité comme un persona humain dans les rapports.
  - Ce groupe concerne les acteurs automatisés, les acteurs d'exécution et le comportement piloté
    par le système.

## Fusions à haute confiance

- `Mechanic` -> `Technician`
- `CSR` -> `Customer Service Representative`
- `Counter Associate`, `Front Desk`, `Cashier`, `POS Cashier`, `POS Clerk` -> `Customer Support Associate`
- `Fleet Account Manager` -> `Account Manager`
- `Scheduler`, `Mobile Lead` -> `Dispatcher`
- `Parts Counter Staff` -> `Parts Associate`
- `Inventory Manager`, `Inventory Controller`, `Inventory Admin` -> `Inventory Control Manager`
- `Accountant`, `GL Accountant`, `GL Specialist`, `Accounting Clerk`, `Accounts Receivable Clerk`, `AP Clerk`, `Billing Specialist`, `Back Office Accountant`, `Payroll Clerk` -> `Accounting Associate`
- `Accounting Operations User`, `Accounting OPS`, `Accounting OPS User`, `Accounting Operations Analyst`, `Accounting OPS Specialist`, `Accounting Integration Operator`, `Integration Operator`, `Finance OPS User` -> `Accounting Associate`
- `Product Admin`, `Product Administrator` -> `Product Administrator`
- `Financial Controller`, `Controller`, `Finance` -> `Controller`
- `Shop Manager`, `Store Manager`, `Back Office`, `Back Office Manager`, `Manager`, `Approver`, `Shop Administrator`, `HR Administrator`, `OPS Admin` -> `Location Manager`
- `FinanceManager` -> `Finance Manager`
- `Admin`, `Admin User` -> `System Administrator`
- `Accounting Admin`, `Finance Admin` -> `System Administrator`
- `System User` -> `System`

## Garder distincts pour l'instant

- `Service Advisor` vs `Customer Support Associate`
- `Dispatcher` vs `Service Advisor`
- `Parts Manager` vs `Parts Associate`
- `Inventory Control Manager` vs `Warehouse Manager`
- `Accounting Associate` vs `Finance / Accounting Management`

## Prochaine étape suggérée

1. Ajouter un champ `canonical_persona` dans l'en-tête ou les métadonnées de chaque story.
2. Conserver `primary_persona` tel qu'il est rédigé, mais ajouter une étiquette canonique normalisée
   à côté.
3. Ajouter un second champ `persona_family` pour permettre les rapports aux deux niveaux.
4. Ajouter un groupe `actor_type = system` distinct pour les acteurs non humains.
5. Lorsqu'une story utilise un titre qui ressemble à un rôle organisationnel, le mapper par propriété
   de parcours plutôt que par permissions ou frontières de sécurité.
6. Normaliser les cas limites restants comme `Director`, `Parts Manager` et `Warehouse Manager`
   lorsque le contexte de la story est suffisamment fort.

## Instantané du regroupement actuel

Il s'agit des grands regroupements bruts après la passe de normalisation actuelle. Ils sont utiles
comme vérification de cohérence, non comme rapport canonique final.

- Service et vente client : 65
- Direction des opérations atelier : 32
- Exécution et pièces : 22
- Inventaire et entrepôt : 16
- Spécialistes comptables : 27
- Opérations comptables : 14
- Finance / admin / supervision : 21
- Admins généraux : 26
- Admins commerciaux et tarification : 4
- Acteurs techniques / système : 9

## Modèle de métadonnées

Utiliser ce modèle lors de l'ajout de métadonnées persona normalisées aux stories ou aux documents
de planification associés.

### Persona de parcours humain

```md
- `primary_persona`: `<étiquette persona rédigée dans la story>`
- `canonical_persona`: `<étiquette persona normalisée>`
- `persona_family`: `<nom de famille normalisé>`
- `actor_type`: `human`
```

Exemple :

```md
- `primary_persona`: `Cashier`
- `canonical_persona`: `Customer Support Associate`
- `persona_family`: `Customer Support Associate`
- `actor_type`: `human`
```

### Acteur non humain

```md
- `primary_persona`: `<étiquette acteur rédigée dans la story>`
- `canonical_persona`: `<étiquette acteur normalisée>`
- `persona_family`: `<nom de famille normalisé ou identique au canonique>`
- `actor_type`: `system`
```

Exemple :

```md
- `primary_persona`: `System User`
- `canonical_persona`: `System`
- `persona_family`: `System`
- `actor_type`: `system`
```

### Intention des champs

- `primary_persona` : L'étiquette rédigée dans la story, telle qu'écrite.
- `canonical_persona` : Le persona de parcours normalisé ou l'étiquette d'acteur utilisée pour la
  réconciliation.
- `persona_family` : Le regroupement de niveau supérieur utilisé pour la planification et les
  rapports prototype.
- `actor_type` : Utiliser `human` pour les personas de parcours utilisateur et `system` pour les
  acteurs automatisés ou non humains.
