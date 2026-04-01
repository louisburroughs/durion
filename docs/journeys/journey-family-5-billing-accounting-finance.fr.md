#### Famille de parcours 5 : Facturation, Comptabilité et Finances

##### Objectif de la famille

Il s'agit d'une famille de parcours, et non d'un parcours unique. Elle combine le passage en caisse au comptoir, les opérations de comptes clients (AR), le support comptable et la gouvernance financière, qui sont des témoignages d'utilisateurs (user stories) distincts avec des rythmes et des acteurs différents.

##### Parcours candidats de la famille

* Du passage en caisse à la finalisation du paiement
* De l'émission de la facture à l'application aux comptes clients (AR)
* Du tri des événements comptables à la résolution du journal
* Clôture de période et révision financière

##### Aperçu des parcours de la famille

| Parcours candidat | Personas dominants | ID de l'histoire | Notes |
| ------ | ------ | ------ | ------ |
| Du passage en caisse à la finalisation du paiement | Associé au support client, Directeur de site | #67, #69, #70, #71, #72, #73 | Parcours clair face au client avec porte de politique de caisse, capture de carte et clôture avec reçu. |
| De l'émission de la facture à l'application aux comptes clients (AR) | Conseiller de service, Associé comptable, Directeur de site | #177, #178, #179, #180, #209, #210, #211, #212 | Parcours opérationnel de facturation et de créances, de la facture provisoire (brouillon) jusqu'au rapprochement des comptes clients. |
| Du tri des événements comptables à la résolution du journal | Associé comptable, Administrateur système | #181, #186, #190, #200, #201, #205, #206 | Surveillance de l'ingestion en back-office, création manuelle d'écritures de journal (JE) et comptabilisation dans le grand livre. |
| Clôture de période et révision financière | Directeur comptable, Contrôleur de gestion, Directeur financier | #188, #189, #191, #198, #199, #202, #203, #204 | Configuration comptable, balance générale, verrouillage de période, rapports financiers et validation d'audit. |

---

#### Parcours : Du passage en caisse à la finalisation du paiement

##### Objectif du passage en caisse au paiement

Ce parcours couvre le cheminement au comptoir, depuis l'évaluation de la politique de passage en caisse jusqu'à la capture de la carte, les exceptions d'annulation/remboursement et la clôture face au client avec la remise du reçu et la confirmation du statut du paiement.

##### Étapes du passage en caisse au paiement

* Porte de la politique de passage en caisse
* Capture de la carte
* Gestion des exceptions
* Confirmation de clôture

##### Lignes d'eau (Swimlanes) du passage en caisse au paiement

| Persona canonique | Étape | ID de l'histoire | Notes |
| ------ | ------ | ------ | ------ |
| Associé au support client | Porte de politique de passage en caisse | #67 | Évaluation de la politique de facturation en caisse — capture du bon de commande (PO) (écriture unique), contournement du PO avec jeton d'élévation, et contrôle du crédit/des conditions avant finalisation. Bloque le passage en caisse lorsque la politique renvoie des blocages. |
| Associé au support client | Capture de la carte | #73 | Initie l'autorisation et la capture de la carte (SALE_CAPTURE ou AUTH_ONLY → CAPTURE). Ne conserve que le jeton/les références de transaction ; déclenche le point d'entrée de la génération du reçu. |
| Directeur de site | Gestion des exceptions | #72 | Annuler les paiements autorisés ou rembourser les paiements capturés/réglés avec un code de motif, un flux d'approbation et un historique d'audit des annulations. |
| Associé au support client | Confirmation de clôture | #71 | Imprimer/envoyer le reçu par e-mail ou supprimer la livraison ("Pas de reçu"). Gère les réimpressions avec un code de motif contrôlé par la politique et un jeton d'élévation. |
| Associé au support client | Confirmation de clôture | #69, #70 | Statut de comptabilisation/ingestion en lecture seule et statut d'application du paiement de la facture sur l'écran des détails de la facture. Répond aux questions des clients et confirme l'application du paiement sans quitter le point de vente (POS). |

---

#### Parcours : De l'émission de la facture à l'application aux comptes clients (AR)

##### Objectif de l'émission de la facture aux comptes clients

Ce parcours couvre le cheminement de facturation opérationnelle depuis l'examen de la facture provisoire jusqu'à la finalisation, l'émission, l'application du paiement aux comptes clients (AR), et la gestion des exceptions pour les ajustements, les notes de crédit et la traçabilité des remboursements.

##### Étapes de l'émission de la facture aux comptes clients

* Préparation de la facture
* Émission et livraison
* Application et rapprochement des comptes clients (AR)
* Ajustements et exceptions

##### Lignes d'eau (Swimlanes) de l'émission de la facture aux comptes clients

| Persona canonique | Étape | ID de l'histoire | Notes |
| ------ | ------ | ------ | ------ |
| Conseiller de service | Préparation de la facture | #212 | Déclenche le calcul des taxes/frais/totaux en arrière-plan sur la facture provisoire. Affiche les totaux, les taxes par ligne, le statut du calcul, la variance par rapport à l'aperçu du devis, et bloque l'action d'émission lorsque le calcul est incomplet. |
| Associé comptable | Préparation de la facture | #211 | Affiche l'aperçu de traçabilité immuable (bon de travail, devis/version, artefacts d'approbation) et restitue les bloqueurs d'émission fournis par le backend. Bloque l'action d'émission lorsque des bloqueurs sont présents. |
| Associé comptable | Émission et livraison | #209 | Finaliser et émettre une facture provisoire via le backend. Restitue la politique d'émission et les bloqueurs avant l'action ; verrouille la modification après l'émission ; affiche les métadonnées émises et les champs de statut de livraison. |
| Associé comptable | Application et rapprochement AR | #178 | Appliquer un paiement compensé de manière atomique à une ou plusieurs factures éligibles. Allocation explicite requise ; le reste devient un crédit client selon la politique. Soumission idempotente via une clé UUIDv7. |
| Associé comptable | Application et rapprochement AR | #179 | Visibilité de l'ingestion de PaymentReceived avec file de travail des paiements non appliqués/non assignés. Prend en charge l'affectation ponctuelle au client avec justification obligatoire. |
| Directeur de site | Ajustements et exceptions | #210 | Autoriser les ajustements sur les factures provisoires — modifier les lignes ou appliquer une remise au niveau de la facture avec un code de motif requis. Le backend émet InvoiceAdjusted ou CreditMemoIssued. |
| Associé comptable | Ajustements et exceptions | #180 | Examiner les enregistrements d'ingestion InvoiceAdjusted / CreditMemoIssued. Naviguer vers les références de comptabilisation et les écritures de journal. Trier les rejets et les mises en quarantaine. |
| Associé comptable | Ajustements et exceptions | #177 | Lister et visualiser les transactions de remboursement issues des événements RefundIssued. Tracer refundId → paymentId → invoiceId. Enquêter sur les conflits de quarantaine/doublons. En lecture seule ; aucune initiation de remboursement. |

---

#### Parcours : Du tri des événements comptables à la résolution du journal

##### Objectif du tri des événements comptables

Ce parcours couvre le cheminement des opérations comptables de back-office, depuis la surveillance de l'ingestion des événements comptables entrants jusqu'au tri en quarantaine, la création d'écritures de journal manuelles ou dérivées d'événements, et la comptabilisation contrôlée dans le grand livre.

##### Étapes du tri des événements comptables

* Surveillance de l'ingestion des événements
* Tri de la quarantaine et de l'attente
* Création d'écritures de journal
* Comptabilisation dans le grand livre

##### Lignes d'eau (Swimlanes) du tri des événements comptables

| Persona canonique | Étape | ID de l'histoire | Notes |
| ------ | ------ | ------ | ------ |
| Associé comptable | Surveillance de l'ingestion des événements | #181 | Surveillance de l'ingestion de InvoiceIssued — lister/filtrer par processingStatus et idempotencyOutcome, voir les détails avec les références de comptabilisation, réessayer de manière asynchrone facultative pour les enregistrements éligibles. |
| Associé comptable | Surveillance de l'ingestion des événements | #205 | Surveillance générale de l'ingestion des événements comptables — valider l'exhaustivité/l'intégrité à travers les types d'événements. Inspecter les échecs de schéma, les indicateurs de quarantaine et les références de comptabilisation. |
| Associé comptable | Surveillance de l'ingestion des événements | #206 | Visibilité des résultats de l'idempotence et de la déduplication (NEW, DUPLICATE_IGNORED, DUPLICATE_CONFLICT). Déclencher une nouvelle tentative asynchrone sécurisée avec justification requise et interrogation du statut du travail. |
| Administrateur système | Tri de la quarantaine et de l'attente | #186 | File d'attente/quarantaine d'ingestion (événements QUARANTINED/REJECTED). Voir les détails de l'échec, déclencher une nouvelle tentative asynchrone idempotente avec justification. Contrôle strict des autorisations sur la visualisation, la charge utile brute et les actions de nouvelle tentative. |
| Associé comptable | Création d'écritures de journal | #201 | Visualiser le brouillon d'écriture de journal généré à partir d'un événement source. Affiche la traçabilité de l'en-tête (eventId, version de la règle de mappage) et les lignes équilibrées par devise. Lecture seule ; valide le solde avant le flux de travail de comptabilisation. |
| Associé comptable | Création d'écritures de journal | #190 | Créer une écriture de journal manuelle avec le code de motif requis, des lignes de débit/crédit équilibrées, et des contrôles de période. Validation côté serveur et côté client. Résulte en un enregistrement POSTED immuable. |
| Associé comptable | Comptabilisation dans le grand livre | #200 | Publier une écriture de journal dans le grand livre avec vérification d'éligibilité de la période. Gère le succès atomique, le rejet de période fermée, et les conflits de simultanéité. Affiche le résultat de la publication avec la référence JE (écriture de journal) et la liaison à l'événement source. |

---

#### Parcours : Clôture de période et révision financière

##### Objectif de la clôture de période et des finances

Ce parcours couvre le cheminement dirigé par la direction, de la configuration et la gouvernance comptables jusqu'à la validation de la balance générale, le verrouillage de la période, la génération des états financiers, et la validation auditable par les contrôleurs et la direction financière.

##### Étapes de la clôture de période et des finances

* Configuration et gouvernance comptables
* Révision de pré-clôture
* Exécution de la clôture de période
* Piste d'audit et validation

##### Lignes d'eau (Swimlanes) de la clôture de période et des finances

| Persona canonique | Étape | ID de l'histoire | Notes |
| ------ | ------ | ------ | ------ |
| Directeur financier | Configuration et gouvernance comptables | #204 | Créer et maintenir le plan comptable (comptes du grand livre ou GL) — créer, modifier des champs limités, désactiver avec datation d'entrée en vigueur, métadonnées d'audit. |
| Directeur financier | Configuration et gouvernance comptables | #199 | Configurer la comptabilité d'exercice par rapport à la comptabilité de caisse par unité commerciale avec limite de période fiscale en vigueur. Historique de base auditable ; contrôlé par des autorisations. |
| Contrôleur de gestion | Configuration et gouvernance comptables | #203 | Gérer les catégories de comptabilisation, les clés de mappage et les mappages GL en vigueur. Validation d'absence de chevauchement ; historique d'audit par catégorie. |
| Contrôleur de gestion | Configuration et gouvernance comptables | #202 | Créer, versionner, valider, publier et archiver les ensembles de règles de comptabilisation (EventType → écritures comptables). Empêche la publication lorsque le backend signale un déséquilibre. |
| Contrôleur de gestion | Révision de pré-clôture | #198 | Générer la balance générale pour une période comptable sélectionnée avec des filtres de compte/dimension. Exploration (Drilldown) : Balance générale → lignes du grand livre → écriture de journal → événement source. Exportation CSV. |
| Contrôleur de gestion | Révision de pré-clôture | #189 | Générer le compte de résultat (P&L) et le bilan avec exploration des comptes contributeurs, des lignes de journal et des références d'événements sources. Exportation avec application du contrôle d'accès. |
| Directeur comptable | Exécution de la clôture de période | #191 | Créer, fermer, et (avec autorisation élevée) rouvrir des périodes comptables par unité commerciale. Motif obligatoire et autorisation élevée pour la réouverture. Historique d'audit d'ouverture/fermeture de période. |
| Contrôleur de gestion | Piste d'audit et validation | #188 | Visionneuse de traçabilité et d'explicabilité du grand livre en lecture seule — Événement source → Version de mappage → Version de règle → Écriture de journal → Lignes du grand livre, avec navigation dans la chaîne d'annulation. Immutabilité explicitement affichée dans l'interface utilisateur (UI). |
