# ChainVax: research notes

These are the sources that were actually opened while building the demo (4–5 November 2026), what was read, and how each one shaped the design. **Access** has three values:

- **Read**: the full text was extracted and searched.
- **Partial**: only a landing page or abstract, or part of the document, was captured.
- **Not retrieved**: the download failed, so nothing from it is used.

Links were valid when accessed. Official guidance is kept separate from ChainVax's own design choices.

## 1. Cold-chain guidance (official)

| # | Source | Access | What was used |
|---|--------|--------|---------------|
| 1 | **WHO/PQS/E006/TR06.4**: PQS performance specification for a *30-day electronic refrigerator temperature logger*. Last revised 10 Jan 2022. <https://extranet.who.int/prequal/sites/default/files/document_files/PQS_E006_TR06.4.pdf> | Read | Clause 4.2.13 sets the factory alarm thresholds. For Type 1 (+2 to +8 °C refrigerators), the low alarm is **−0.5 °C or below for 60 minutes** and the high alarm is **+8 °C or above for 10 hours**. These became the thresholds and durations of the `PQS-E006-T1` reference policy. `DEMO-FAST` uses the same thresholds but shortens the durations to 90 s (heat) and 60 s (cold). Clause 4.2.11 ("Mode of operation") says end users must not be able to delete or modify logged temperature data, so ChainVax readings are append-only: the API has no update or delete endpoint for them. Clause 4.2.10 requires the logger to measure at intervals of no more than 10 minutes and log at least hourly. The simulator's 10-second interval is a demo compression of this. |
| 2 | **WHO TRS 961 (2011), Annex 9**: *Model guidance for the storage and transport of time- and temperature-sensitive pharmaceutical products*. <https://www.who.int/docs/default-source/medicines/norms-and-standards/guidelines/distribution/trs961-annex9-modelguidanceforstoragetransport.pdf> | Read (49-page PDF) | Defines a *temperature excursion* as "an excursion event in which a TTSPP is exposed to temperatures outside the range(s) prescribed for storage and/or transport". It also says to "quarantine returned TTSPPs that have been exposed to unacceptable storage and/or transport temperatures and mark for disposal" and "maintain records of all returned TTSPPs". In ChainVax, condemned stock leaves issuable stock and can only be returned to a district store (`CONDEMNED_RETURN_ONLY`). Every status change records the site and custodian at that moment. |
| 3 | **WHO Vaccine Management Handbook, module VMH-E2**: *How to monitor temperatures in the vaccine supply chain*, WHO/IVB/15.04, July 2015. Landing page: <https://www.who.int/publications/i/item/WHO-IVB-15.04>. PDF: <https://iris.who.int/bitstream/handle/10665/183583/WHO_IVB_15.04_eng.pdf> | Partial (about 18,000 characters of the 31-page PDF extracted) | "The temperatures to which vaccines are exposed must be monitored, recorded and reported throughout the vaccine supply chain…" ChainVax therefore stores every reading with its device, site and source (`SIMULATOR`/`LOGGER`). The device table recommends continuous temperature monitoring with alarms for stores and 30-day temperature recorders at district and service level, which is why the demo has one logger per site. The handbook says vaccine vial monitors show "which vaccines can safely be kept after a cold chain break occurs; this minimizes unnecessary vaccine wastage". So AT_RISK is a review state that a district officer can release with a recorded reason (for example a VVM check), not an automatic discard. |
| 4 | **WHO/IVB/06.10**: *Temperature sensitivity of vaccines*. <https://iris.who.int/bitstream/handle/10665/69387/WHO_IVB_06.10_eng.pdf?sequence=1> | Not retrieved (the server returned an HTML page instead of the PDF) | Not used. Per-vaccine heat and freeze sensitivity is **not** modelled. |

**Design choice, not WHO guidance:** ChainVax condemns a batch automatically once an excursion lasts longer than its policy's alarm duration. The PQS durations are *logger alarm settings*. Real discard decisions also weigh VVM status, freeze-sensitivity (shake test) and programme guidance. Both policies are labelled to say this in the UI.

## 2. Deployed system (peer-reviewed)

| # | Source | Access | What was used |
|---|--------|--------|---------------|
| 5 | *Programmatic assessment of electronic Vaccine Intelligence Network (eVIN)*. **PLoS ONE** 15(11): e0241369, 5 Nov 2020. doi:10.1371/journal.pone.0241369. <https://pmc.ncbi.nlm.nih.gov/articles/PMC7643996/> | Read | In India, eVIN "uses a smartphone, a web-based application, temperature loggers and cloud-based server to digitize vaccine stock inventory and storage temperature from every vaccine store and CCP", with "automated alert mechanisms to alert designated staff" when temperatures leave the predefined range. This shaped the architecture: loggers post to a server API, a dashboard shows the state of each site, and alerts go out as digests. The district store → van → health-centre hierarchy follows the same programme structure. |

## 3. Tamper-evident logging and hashing

| # | Source | Access | What was used |
|---|--------|--------|---------------|
| 6 | S. A. Crosby and D. S. Wallach, *Efficient Data Structures for Tamper-Evident Logging*, USENIX Security 2009. <https://www.usenix.org/legacy/event/sec09/tech/full_papers/crosby.pdf> | Read | Its model is an untrusted logger kept honest by auditors who hold its signed *commitments*. A classic hash chain needs linear-size proofs, whereas their history tree gives logarithmic-size ones. ChainVax uses the simple linear chain: `hash = SHA-256(previous_hash + payload)`, starting from a 64-zero genesis value. Full verification is O(n), which is fine for a demo ledger. The current head hash is exposed by the API and on the dashboard, so it *could* be published as a commitment. External anchoring and Merkle (history-tree) proofs are **deferred** (see the README). |
| 7 | B. Schneier and J. Kelsey, *Secure Audit Logs to Support Computer Forensics*, ACM TISSEC, 1999. <https://www.schneier.com/academic/archives/1999/05/secure_audit_logs_to.html> | Partial (abstract page only) | Supplies the framing: logs kept on a machine an attacker may control, where the aim is to "limit his ability to corrupt the log files". ChainVax does **not** implement their MAC and key-evolution scheme. |
| 8 | NIST **FIPS 180-4**, *Secure Hash Standard*. <https://csrc.nist.gov/pubs/fips/180-4/upd1/final> (doi:10.6028/NIST.FIPS.180-4) | Partial (CSRC landing page and abstract) | Hashes are computed with the JDK's `MessageDigest` SHA-256; there is no custom cryptography. `LedgerHasherTest` checks the well-known digest of `"abc"` (`ba7816bf…f20015ad`), which was cross-checked with Python's `hashlib`. |

## 4. Access control

| # | Source | Access | What was used |
|---|--------|--------|---------------|
| 9 | NIST CSRC, *Role Based Access Control* project page. <https://csrc.nist.gov/projects/role-based-access-control> | Partial (overview page) | Permissions are attached to roles (STOREKEEPER, TRANSPORTER, DISTRICT_OFFICER, AUDITOR), not to individual users. Data-level scope checks add own-site limits for storekeepers and transporters. |

## 5. Related work (context only)

| # | Source | Access | What was used |
|---|--------|--------|---------------|
| 10 | B. Yong et al., *An intelligent blockchain-based system for safe vaccine supply and supervision*, **International Journal of Information Management** 52 (2020). Bibliographic record: <https://ideas.repec.org/a/eee/ininma/v52y2020ics0268401219304505.html>. Publisher: <https://www.sciencedirect.com/science/article/abs/pii/S0268401219304505> | Partial (bibliographic and citation page only; the abstract was not captured and the full text is paywalled) | Cited only to show that blockchain approaches exist. ChainVax deliberately uses a single-database hash chain, because a district demo needs no consensus network. No claims are made about that paper's results. |

## 6. Framework documentation

| # | Source | Access | What was used |
|---|--------|--------|---------------|
| 11 | Spring Security reference: *OAuth 2.0 Resource Server: JWT*. <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html> | Read | `NimbusJwtDecoder.withSecretKey(…).macAlgorithm(HS256)`, `JwtAuthenticationConverter` with `setAuthoritiesClaimName("roles")` and the `ROLE_` prefix, a stateless bearer-token API, and `JwtEncoder` for issuing tokens. |
| 12 | Spring Boot reference: *Caching*. <https://docs.spring.io/spring-boot/reference/io/caching.html> | Read | `spring.cache.type` (`simple` by default, `redis` in the `redis` profile), predeclared `cache-names`, and Redis TTL and key prefix. |
| 13 | Spring Data JPA reference: *Locking*. <https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html> | Read | `@Version` optimistic locking on batches and policies, and `@Lock(PESSIMISTIC_WRITE)` on the single-row ledger head so that appends are serialised. |
| 14 | springdoc-openapi documentation and FAQ. <https://springdoc.org/> and <https://springdoc.org/faq.html> | Read | springdoc 3.x targets Spring Boot 4. A bearer security scheme is set up so Swagger UI can call secured endpoints. |
| 15 | Spring Boot 4.0 Migration Guide. <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide> | Partial (about 3 KB extracted) | Background for Boot 4's modular starters. The exact test-annotation packages were then confirmed from the jars in the local Maven repository. |
| 16 | Hibernate ORM 7.0 User Guide (Envers chapter). <https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html> | Partial (only the start of the page was captured) | Envers configuration (`_aud` suffix, custom revision entity). The behaviour was then verified by running the app and `enversHistoryAttributesEachChangeToItsActor`. |
| 17 | Spring Boot project page. <https://spring.io/projects/spring-boot> | Read | Release-line context only. Exact versions were taken from Maven Central metadata: Spring Boot 4.1.2, springdoc 3.2.0, zonky embedded-postgres 2.2.2 with PostgreSQL binaries 17.11.0, and Maven 3.9.16. |

## 7. Repositories inspected before reuse

Licences come from the GitHub API and activity is the last push date, both checked on 5 Nov 2026.

| Repository | Licence | Last push | What was inspected | Reuse decision |
|------------|---------|-----------|--------------------|----------------|
| [spring-projects/spring-security-samples](https://github.com/spring-projects/spring-security-samples), `servlet/spring-boot/java/jwt/login` | none detected by the GitHub API | 2026-10-09 | `RestConfig.java` and `TokenController.java` (issuing tokens with `JwtEncoder`/`NimbusJwtEncoder`) | **Pattern reference only; no code copied.** ChainVax's `TokenService` and `SecurityConfig` were written separately: HS256 instead of RSA, `roles`/`site`/`device` claims, device tokens, and an injected `Clock`. |
| [zonkyio/embedded-postgres](https://github.com/zonkyio/embedded-postgres) | Apache-2.0 | 2026-03-12 | README, releases, and `EmbeddedPostgres.java` (initdb with `-E UTF-8`, port 0, the shutdown hook only runs on a normal JVM exit) | Used **unmodified as a Maven dependency** to run a real PostgreSQL 17 in-process, so no install is needed. |
| [springdoc/springdoc-openapi](https://github.com/springdoc/springdoc-openapi) | Apache-2.0 | 2026-10-24 | Release notes (3.x for Boot 4) | Maven dependency. |
| [OpenLMIS/openlmis-cce](https://github.com/OpenLMIS/openlmis-cce) | **AGPL-3.0** | 2026-10-16 | `domain/Alert.java` (alert model for cold-chain equipment) | **Design reference only; no code copied.** Copying code would bring in AGPL obligations. |
| [OpenLMIS/openlmis-stockmanagement](https://github.com/OpenLMIS/openlmis-stockmanagement) | **AGPL-3.0** | 2026-11-03 | Licence and activity only | Not used. |
| [flyway/flyway](https://github.com/flyway/flyway) | Apache-2.0 | n/a | Licence | Maven dependency. |

## 8. What was not researched or not used

- No vendor logger protocol (for example a remote temperature monitoring gateway API) was studied. The ingestion API is a generic JSON batch endpoint.
- No source was reviewed for vaccine-specific stability budgets. Batches are not assigned per-product sensitivity curves.
