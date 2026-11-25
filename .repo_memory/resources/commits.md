---
schema: "repo_memory_commit_resource.v0.1"
repo_full_name: "lifei-cell/bilibili"
generated_at: "2026-11-25T08:22:41.966Z"
source: "git_commit_facets"
resource_count: 68
trust_state: "draft_resource"
raw_source: "../raw/git-commits.json"
---

# Commit Resource Snapshot

The original 19 entries come from local Git ancestry at `2d4767fcd216f734918e9a447a3d57b8cf747236`; 49 incremental entries extend the index to `bbcf1bae53ba6bc62350960c9b939c64f0859c4d`. Titles and paths are historical routing evidence, not current behavior proof. `../raw/git-commits.json` retains the original 19 facets; incremental sections cite local commits directly and include changed paths. No runtime tests were run for this memory update.

## Commit 2d4767f: NVIDIA NVENC runtime

- SHA: `2d4767fcd216f734918e9a447a3d57b8cf747236`
- Author / authored: `monster`, `2026-08-27T12:01:19+08:00`
- Modules / path modules: media runtime; `bilibili-video-service`, `docs`, Compose GPU overlay.
- Description: Routes investigations of NVENC availability and deployment to `bilibili-video-service/Dockerfile`, `docker-compose.gpu.nvidia.yml`, and the media governance runbook. Local commit facet supports changed paths, not successful GPU execution; check encoder availability and an actual HLS task.
- Diff / parent count: 3 files, +24/-6; 1 parent.
- Raw lookup: `facetId=commit.2d4767f`

---

## Commit 6afbdcf: adaptive HLS acceleration

- SHA: `6afbdcfb0091c51cb0ef0b39dc428964898e9c0b`
- Author / authored: `monster`, `2026-08-27T10:36:22+08:00`
- Modules / path modules: transcode; `bilibili-video-service`, `docs`.
- Description: Routes HLS rendition, encoder selection, and progressive publication questions to video service encoder and worker files plus the media runbook. Facet records 19 changed files; verify rendition behavior in source and runtime before claiming speed or quality.
- Diff / parent count: 19 files, +882/-150; 1 parent.
- Raw lookup: `facetId=commit.6afbdcf`

---

## Commit 689a669: transcode concurrency

- SHA: `689a6691dc994889756577eafb943f6733b8fdf4`
- Author / authored: `monster`, `2026-08-26T23:04:30+08:00`
- Modules / path modules: transcode; `bilibili-video-service`, Compose.
- Description: Routes worker concurrency and container resource questions to `VideoTranscodeConsumer.java` and `docker-compose.service.yml`. Local facet shows a small configuration/code change; check current limits and throughput under load.
- Diff / parent count: 2 files, +6/-1; 1 parent.
- Raw lookup: `facetId=commit.689a669`

---

## Commit 97daaa5: even HLS dimensions

- SHA: `97daaa566c2d05d212fb40ba95b4a4c31b792187`
- Author / authored: `monster`, `2026-08-26T22:45:50+08:00`
- Modules / path modules: HLS scaling; `bilibili-video-service`.
- Description: Routes odd-size video encode failures to `VideoScaleGeometry`, `FfmpegVideoTranscodeWorker`, and `VideoScaleGeometryTest`. The facet shows paths and a focused test, while current FFmpeg output still needs verification.
- Diff / parent count: 3 files, +55/-10; 1 parent.
- Raw lookup: `facetId=commit.97daaa5`

---

## Commit 7201be9: Nacos Compose readiness

- SHA: `7201be9ab83a0ea1a059178a9cda672d677c59dc`
- Author / authored: `monster`, `2026-08-26T21:30:05+08:00`
- Modules / path modules: startup; root Compose files.
- Description: Routes service startup ordering and Nacos readiness failures to `docker-compose.yml` and `docker-compose.service.yml`. The local facet only records the change; inspect current health checks and container state for a live failure.
- Diff / parent count: 2 files, +16/-8; 1 parent.
- Raw lookup: `facetId=commit.7201be9`

---

## Commit d8b61af: media reliability E2E stability

- SHA: `d8b61af2f5fba062628521d0daeb350229da38b1`
- Author / authored: `monster`, `2026-08-26T15:47:36+08:00`
- Modules / path modules: upload and E2E; `bilibili-user-service`, `bilibili-video-service`, `scripts`.
- Description: Routes Compose E2E upload or MinIO failures to `DirectUploadService`, `MinIOConfig`, and `scripts/e2e/reliability-e2e.ps1`. The commit facet suggests stabilization work; run the current script before claiming the drill passes.
- Diff / parent count: 6 files, +25/-11; 1 parent.
- Raw lookup: `facetId=commit.d8b61af`

---

## Commit 72f7659: production media and governance

- SHA: `72f7659b0728b0ff936f089c61ab0155230df0e6`
- Author / authored: `monster`, `2026-08-26T15:08:03+08:00`
- Modules / path modules: media and review; video service, gateway, common, web.
- Description: Routes presigned upload, HLS, creator review, and admin moderation questions to `bilibili-video-service`, `bilibili-web`, and the media runbook. This broad local commit spans 57 files; inspect the specific flow and tests before relying on a feature claim.
- Diff / parent count: 57 files, +1505/-185; 1 parent.
- Raw lookup: `facetId=commit.72f7659`

---

## Commit f44fa00: Outbox and Compose E2E

- SHA: `f44fa00ea15766b9222ab4d1a65addd23ab25817`
- Author / authored: `monster`, `2026-08-26T10:30:46+08:00`
- Modules / path modules: reliable events; common, Canal, CI, scripts.
- Description: Routes Outbox, Inbox, CDC recovery, and Compose reliability drill work to `bilibili-common`, `bilibili-canal-service`, and `scripts/e2e`. The facet captures a broad historical change; verify idempotency and failure recovery with current source and checks.
- Diff / parent count: 37 files, +1503/-61; 1 parent.
- Raw lookup: `facetId=commit.f44fa00`

---

## Commit 0b75f21: auth and read capacity

- SHA: `0b75f21033a08c373ab997480163c6be6004dfb9`
- Author / authored: `monster`, `2026-08-25T22:38:23+08:00`
- Modules / path modules: authentication and read paths; gateway, common, danmu, web.
- Description: Routes session/cookie handling and sustained read-path changes to `bilibili-gateway`, `bilibili-common`, and `bilibili-web/src/api`. The 52-file facet is broad; identify the exact endpoint and rerun relevant tests before asserting capacity.
- Diff / parent count: 52 files, +1330/-114; 1 parent.
- Raw lookup: `facetId=commit.0b75f21`

---

## Commit 2d0ba80: RocketMQ Docker config

- SHA: `2d0ba80d53a8038ae59d6f15175453fab9a40e95`
- Author / authored: `monster`, `2026-08-25T19:17:38+08:00`
- Modules / path modules: RocketMQ startup; root Compose and README.
- Description: Routes Docker Broker startup failures to `docker-compose.yml` and the README startup instructions. This local facet records a two-file configuration fix; inspect current Broker logs and Compose configuration for a live incident.
- Diff / parent count: 2 files, +6/-5; 1 parent.
- Raw lookup: `facetId=commit.2d0ba80`

---

## Commit 13ace3c: observability and message reliability

- SHA: `13ace3c80cf84240a0d718b436e76c7d7a5729f2`
- Author / authored: `monster`, `2026-08-25T15:17:08+08:00`
- Modules / path modules: tracing, metrics, retry; common and service configs.
- Description: Routes Prometheus, trace propagation, RocketMQ retry, and DLQ questions to `bilibili-common/src/main/resources/observability.yml` and service configurations. Local facet spans 44 files; verify emitted metrics and failure handling in the running stack.
- Diff / parent count: 44 files, +1521/-24; 1 parent.
- Raw lookup: `facetId=commit.13ace3c`

---

## Commit f36be81: engineering baseline

- SHA: `f36be81c5d1250c95a01bc257fb26746ccd7468b`
- Author / authored: `monster`, `2026-08-25T14:20:22+08:00`
- Modules / path modules: CI and tests; `.github/workflows`, common, service and web tests.
- Description: Routes backend/frontend verification and CI gate questions to `.github/workflows`, Maven test setup, and web unit tests. The facet records baseline creation across 50 files, not a current passing gate; execute relevant checks.
- Diff / parent count: 50 files, +4374/-335; 1 parent.
- Raw lookup: `facetId=commit.f36be81`

---

## Commit ed998cc: early cross-service fixes

- SHA: `ed998cc6b443a689b770e73fe7ff990646bf896e`
- Author / authored: `monster`, `2026-08-25T11:28:09+08:00`
- Modules / path modules: web, danmu, video.
- Description: Routes early frontend API and danmu/video fixes to `bilibili-web/src/api`, `bilibili-danmu-service`, and `bilibili-video-service`. The title is generic and 60 files changed, so use the raw file list and current source before drawing a specific conclusion.
- Diff / parent count: 60 files, +2071/-78; 1 parent.
- Raw lookup: `facetId=commit.ed998cc`

---

## Commit e143bc3: frontend addition

- SHA: `e143bc38cae06c258ae046db5a00ce3a2c916985`
- Author / authored: `lifei`, `2026-08-04T13:49:05+08:00`
- Modules / path modules: frontend; `bilibili-web`.
- Description: Routes original Vue application structure, HTTP client, and UI entrypoint questions to `bilibili-web/src/main.ts` and `bilibili-web/src/api/http.ts`. The local facet indicates 30 new files; current web behavior needs live inspection.
- Diff / parent count: 30 files, +3523/-0; 1 parent.
- Raw lookup: `facetId=commit.e143bc3`

---

## Commit 5ef0bca: danmu and demo data fix

- SHA: `5ef0bcae2ebbe9e40791a23ff3daa8de238509fd`
- Author / authored: `lifei`, `2026-08-03T16:00:34+08:00`
- Modules / path modules: danmu and database demo data.
- Description: Routes early danmu mapping or fixture questions to `DanmuMapper.java`, `docker/mysql/init/03-demo-data.sql`, and README. The generic title gives weak intent evidence; inspect diff and current SQL before using it as an explanation.
- Diff / parent count: 3 files, +511/-1; 1 parent.
- Raw lookup: `facetId=commit.5ef0bca`

---

## Commit 802b5a4: early service configuration fixes

- SHA: `802b5a4e0125cf428e6a03fcad5abda9fcdb3569`
- Author / authored: `lifei`, `2026-08-03T13:56:10+08:00`
- Modules / path modules: Canal, common, danmu and gateway configs.
- Description: Routes early service startup or integration settings to `bilibili-canal-service`, `bilibili-common`, and `bilibili-danmu-service`. The generic title and 13-file facet only support changed-path routing; inspect the diff for exact behavior.
- Diff / parent count: 13 files, +732/-246; 1 parent.
- Raw lookup: `facetId=commit.802b5a4`

---

## Commit 70cf252: ignore IDE files

- SHA: `70cf252e99cfab6cb987de0f20affef84140c284`
- Author / authored: `lifei`, `2026-08-03T10:24:09+08:00`
- Modules / path modules: repository hygiene; `.gitignore`, `.idea`.
- Description: Routes why IDE metadata stopped being tracked to `.gitignore` and removed `.idea` paths. The local facet supports that repository-history change; inspect current ignore rules before adding new generated artifacts.
- Diff / parent count: 8 files, +1/-112; 1 parent.
- Raw lookup: `facetId=commit.70cf252`

---

## Commit 485302c: initial broad backend implementation

- SHA: `485302c5a27ef3623928a5deacdf595c8c7caf7a`
- Author / authored: `lifei`, `2026-08-03T10:09:04+08:00`
- Modules / path modules: broad backend; user, video, social, danmu, Canal, gateway.
- Description: Routes origins of the service and API structure to the early service modules and configurations. The title `finish` is weak intent evidence and the facet spans 177 files; inspect exact paths or diff before attributing a behavior to this commit.
- Diff / parent count: 177 files, +8474/-7; 1 parent.
- Raw lookup: `facetId=commit.485302c`

---

## Commit 102d30c: account registration/login foundation

- SHA: `102d30c5231cf4d6298030effee44ec104ae98ba`
- Author / authored: `Gary-Lin6`, `2026-05-26T21:17:44+08:00`
- Modules / path modules: original backend scaffold; user, gateway, common, Docker.
- Description: Routes the first registration/login design and original service scaffold to `bilibili-user-service`, `bilibili-gateway`, and `bilibili-common`. This is the root local commit; current authentication behavior requires source and test verification.
- Diff / parent count: 60 files, +5448/-0; 0 parents.
- Raw lookup: `facetId=commit.102d30c`

---

## Commit a38b5c6: docs: summarize Bilibili highlights and roadmap

- SHA: `a38b5c62b9d092ae0391b807a8a44c0a36a8b619`
- Author / authored: `monster`, `2026-09-16T16:10:47+08:00`
- Modules / path modules: `docs`.
- Description: Project highlights and roadmap documentation; route feature-evidence and planned-work questions to the report, without treating plans as implementation.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show a38b5c62b9d092ae0391b807a8a44c0a36a8b619`; `git diff-tree --no-commit-id --name-only -r a38b5c62b9d092ae0391b807a8a44c0a36a8b619`.
- Changed paths: `docs/reports/2026-09-16-project-highlights-roadmap.md`.

---

## Commit 60a06ac: chore: ignore local repo memory bundle

- SHA: `60a06ac5eff9533984e4926c06a352908de4efba`
- Author / authored: `monster`, `2026-09-16T16:12:50+08:00`
- Modules / path modules: `.gitignore`.
- Description: Local repo-memory ignore policy; route memory tracking questions to .gitignore.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 60a06ac5eff9533984e4926c06a352908de4efba`; `git diff-tree --no-commit-id --name-only -r 60a06ac5eff9533984e4926c06a352908de4efba`.
- Changed paths: `.gitignore`.

---

## Commit 09bf8a4: docs: detail Bilibili optimization tasks

- SHA: `09bf8a4ed205340300117006bb985d0fc107e59c`
- Author / authored: `monster`, `2026-09-16T20:53:12+08:00`
- Modules / path modules: `docs`.
- Description: Expanded optimization task documentation; use the roadmap for historical planning scope.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 09bf8a4ed205340300117006bb985d0fc107e59c`; `git diff-tree --no-commit-id --name-only -r 09bf8a4ed205340300117006bb985d0fc107e59c`.
- Changed paths: `docs/reports/2026-09-16-project-highlights-roadmap.md`.

---

## Commit d09bae3: fix: commit transcode result updates atomically

- SHA: `d09bae32a99fc4600a81525f27fa071926f47c4c`
- Author / authored: `monster`, `2026-09-17T15:21:59+08:00`
- Modules / path modules: `bilibili-video-service`, `docs`.
- Description: Transcode result atomicity; routes transaction-boundary investigations to VideoTranscodeResultService, its consumer, and integration test.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show d09bae32a99fc4600a81525f27fa071926f47c4c`; `git diff-tree --no-commit-id --name-only -r d09bae32a99fc4600a81525f27fa071926f47c4c`.
- Changed paths: `bilibili-video-service/pom.xml`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumer.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoTranscodeResultService.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumerTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/VideoTranscodeResultServiceIT.java`, `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-transcode-result-transaction.md`.

---

## Commit bdd127d: fix: normalize container CPU quota gate

- SHA: `bdd127de3103e2438b66b5878b045ddbb49e15ca`
- Author / authored: `monster`, `2026-09-17T16:45:31+08:00`
- Modules / path modules: `docs`, `loadtest`, `scripts`.
- Description: Container CPU quota normalization; routes capacity gate calculations to resource-normalization.ps1 and write-SLO validation scripts.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show bdd127de3103e2438b66b5878b045ddbb49e15ca`; `git diff-tree --no-commit-id --name-only -r bdd127de3103e2438b66b5878b045ddbb49e15ca`.
- Changed paths: `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-cpu-quota-gate.md`, `loadtest/README.md`, `loadtest/write-slo.json`, `scripts/loadtest/resource-normalization.ps1`, `scripts/loadtest/run-write-slo.ps1`, `scripts/loadtest/test-resource-normalization.ps1`, `scripts/verification/release-validation.ps1`.

---

## Commit f3c831b: fix: fence transcode attempts with database leases

- SHA: `f3c831ba1e577c7ffce39ef58ed8f163a0bff565`
- Author / authored: `monster`, `2026-09-17T17:31:44+08:00`
- Modules / path modules: `bilibili-common`, `bilibili-video-service`, `docs`.
- Description: Transcode database lease fencing; routes stale worker ownership, attempt publication, and HLS storage questions to lease/result services and V6 migration.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show f3c831ba1e577c7ffce39ef58ed8f163a0bff565`; `git diff-tree --no-commit-id --name-only -r f3c831ba1e577c7ffce39ef58ed8f163a0bff565`.
- Changed paths: `bilibili-common/src/main/resources/db/migration/V6__transcode_task_lease.sql`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumer.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/entity/VideoTranscodeTask.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/mapper/VideoTranscodeTaskMapper.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/message/VideoTranscodeMessage.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/producer/VideoTranscodeTaskPublisher.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/FfmpegVideoTranscodeWorker.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/HlsPackageStorage.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoTranscodeLeaseService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoTranscodeResultService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoTranscodeWorker.java`, `bilibili-video-service/src/main/resources/application.yml`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumerTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/mapper/VideoTranscodeTaskLeaseTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/producer/VideoTranscodeTaskPublisherTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/HlsPackageStorageTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/VideoTranscodeResultServiceIT.java`, `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-transcode-lease-fencing.md`.

---

## Commit 24bbfa0: feat(search): rebuild shadow index before alias cutover

- SHA: `24bbfa0a13d008038b68df1bc2e31bc111ae68f6`
- Author / authored: `monster`, `2026-09-17T17:50:09+08:00`
- Modules / path modules: `bilibili-canal-service`, `bilibili-search-service`, `docs`.
- Description: Elasticsearch shadow rebuild and alias cutover; routes index recovery to SearchIndexMaintenanceService, VideoIndexAlias, and write-gate tests.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 24bbfa0a13d008038b68df1bc2e31bc111ae68f6`; `git diff-tree --no-commit-id --name-only -r 24bbfa0a13d008038b68df1bc2e31bc111ae68f6`.
- Changed paths: `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/constant/CanalConstant.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/consumer/CacheSyncConsumer.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/controller/ReliabilityOperationsController.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/document/VideoDocument.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/PublishedVideoSource.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceService.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/VideoIndexAlias.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/VideoIndexWriteGate.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/consumer/CacheSyncConsumerTest.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceServiceTest.java`, `bilibili-search-service/src/main/java/com/gary/bilibili/search/constant/SearchConstant.java`, `bilibili-search-service/src/main/java/com/gary/bilibili/search/document/VideoDocument.java`, `bilibili-search-service/src/main/java/com/gary/bilibili/search/service/VideoSearchIndexInitializer.java`, `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-es-shadow-rebuild.md`, `docs/runbooks/reliability-closure.md`.

---

## Commit d34f071: test(web): cover upload recovery and browser journeys

- SHA: `d34f071b87ec70de38917a1948555484fa6d9ae1`
- Author / authored: `monster`, `2026-09-17T18:20:57+08:00`
- Modules / path modules: `.github`, `bilibili-web`, `docs`.
- Description: Frontend upload recovery and browser journeys; routes chunk upload, danmu reconnection, HTTP handling, and Playwright coverage to web services and E2E tests.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show d34f071b87ec70de38917a1948555484fa6d9ae1`; `git diff-tree --no-commit-id --name-only -r d34f071b87ec70de38917a1948555484fa6d9ae1`.
- Changed paths: `.github/workflows/frontend-browser.yml`, `bilibili-web/.gitignore`, `bilibili-web/README.md`, `bilibili-web/e2e/fixtures/black-2s.webm`, `bilibili-web/e2e/journey.spec.ts`, `bilibili-web/package-lock.json`, `bilibili-web/package.json`, `bilibili-web/playwright.config.ts`, `bilibili-web/src/api/http.test.ts`, `bilibili-web/src/api/http.ts`, `bilibili-web/src/services/chunkUpload.test.ts`, `bilibili-web/src/services/chunkUpload.ts`, `bilibili-web/src/services/danmuConnection.test.ts`, `bilibili-web/src/services/danmuConnection.ts`, `bilibili-web/src/views/UploadView.vue`, `bilibili-web/src/views/VideoView.vue`, `bilibili-web/vitest.config.ts`, `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-frontend-regression.md`.

---

## Commit 18e4a59: docs: summarize Bilibili highlights and next steps

- SHA: `18e4a59c7a9feabbd3d827acfb89290b152857bc`
- Author / authored: `monster`, `2026-09-17T18:26:25+08:00`
- Modules / path modules: `docs`.
- Description: Project highlights and next-step report; historical summary and planning context only.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 18e4a59c7a9feabbd3d827acfb89290b152857bc`; `git diff-tree --no-commit-id --name-only -r 18e4a59c7a9feabbd3d827acfb89290b152857bc`.
- Changed paths: `docs/reports/2026-09-17-project-highlights-next-plan.md`.

---

## Commit 4b2d746: fix: complete P0 release validation gates

- SHA: `4b2d746c2dc952736ad2110bdb5a3a38cdbea993`
- Author / authored: `monster`, `2026-09-17T21:44:43+08:00`
- Modules / path modules: `bilibili-canal-service`, `bilibili-user-service`, `docker-compose.yml`, `docs`.
- Description: P0 release validation gates; routes search maintenance, user API integration checks, and Compose changes to the touched implementation and report.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 4b2d746c2dc952736ad2110bdb5a3a38cdbea993`; `git diff-tree --no-commit-id --name-only -r 4b2d746c2dc952736ad2110bdb5a3a38cdbea993`.
- Changed paths: `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceService.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceServiceTest.java`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `docker-compose.yml`, `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-release-validation-pass.md`.

---

## Commit eb25e00: test: validate worker crash recovery and multi-canal cutover

- SHA: `eb25e00d404eb82193eb73892653bd595800dbb0`
- Author / authored: `monster`, `2026-09-17T22:48:28+08:00`
- Modules / path modules: `bilibili-canal-service`, `bilibili-video-service`, `docker-compose.p1.yml`, `docs`, `scripts`.
- Description: Worker crash recovery and multi-Canal cutover drill; routes cross-instance index coordination to MySqlNamedLock and media recovery to the P1 drill.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show eb25e00d404eb82193eb73892653bd595800dbb0`; `git diff-tree --no-commit-id --name-only -r eb25e00d404eb82193eb73892653bd595800dbb0`.
- Changed paths: `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/consumer/CacheSyncConsumer.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/MySqlNamedLock.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceService.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/VideoIndexWriteGate.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/consumer/CacheSyncConsumerTest.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/service/MySqlNamedLockTest.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceServiceTest.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumer.java`, `bilibili-video-service/src/main/resources/application.yml`, `docker-compose.p1.yml`, `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-es-shadow-rebuild.md`, `docs/reports/2026-09-17-p1-reliability-drill.md`, `docs/reports/2026-09-17-transcode-lease-fencing.md`, `docs/runbooks/reliability-closure.md`, `scripts/e2e/p1-reliability-drill.ps1`.

---

## Commit c3ab63f: feat: add P2 scenario capacity baseline

- SHA: `c3ab63f6f079cb510399c1e141c66b64211d4d54`
- Author / authored: `monster`, `2026-09-18T09:10:09+08:00`
- Modules / path modules: `docker-compose.p2.yml`, `loadtest`, `scripts`.
- Description: P2 scenario capacity baseline; routes HTTP write, danmu, and transcode measurement setup to loadtest scenarios and capacity scripts.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show c3ab63f6f079cb510399c1e141c66b64211d4d54`; `git diff-tree --no-commit-id --name-only -r c3ab63f6f079cb510399c1e141c66b64211d4d54`.
- Changed paths: `docker-compose.p2.yml`, `loadtest/README.md`, `loadtest/capacity-baseline.json`, `loadtest/danmu-baseline.js`, `loadtest/transcode-baseline.js`, `loadtest/write-http-baseline.js`, `scripts/loadtest/capacity-common.ps1`, `scripts/loadtest/p2-capacity-baseline.ps1`.

---

## Commit 5415c2f: fix: pass P2 fixture switches explicitly

- SHA: `5415c2fb7181467eb442a7f1c2709eb9da4967f1`
- Author / authored: `monster`, `2026-09-18T09:11:48+08:00`
- Modules / path modules: `scripts`.
- Description: P2 fixture switch propagation; inspect explicit switch handling in p2-capacity-baseline.ps1 when fixture preparation differs from intended options.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 5415c2fb7181467eb442a7f1c2709eb9da4967f1`; `git diff-tree --no-commit-id --name-only -r 5415c2fb7181467eb442a7f1c2709eb9da4967f1`.
- Changed paths: `scripts/loadtest/p2-capacity-baseline.ps1`.

---

## Commit 053693d: fix: preserve P2 measurement gate fields

- SHA: `053693ddd7d0aae70839f3e3fc7955c6aeab0e30`
- Author / authored: `monster`, `2026-09-18T09:17:51+08:00`
- Modules / path modules: `scripts`.
- Description: P2 measurement gate field preservation; routes missing gate evidence to the capacity orchestration script.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 053693ddd7d0aae70839f3e3fc7955c6aeab0e30`; `git diff-tree --no-commit-id --name-only -r 053693ddd7d0aae70839f3e3fc7955c6aeab0e30`.
- Changed paths: `scripts/loadtest/p2-capacity-baseline.ps1`.

---

## Commit b86f0e6: fix: read k6 rate values from summary

- SHA: `b86f0e6647b29fc008f8f29356e8ed2744b01a87`
- Author / authored: `monster`, `2026-09-18T09:18:33+08:00`
- Modules / path modules: `scripts`.
- Description: k6 summary rate extraction; routes rate interpretation to capacity-common.ps1.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show b86f0e6647b29fc008f8f29356e8ed2744b01a87`; `git diff-tree --no-commit-id --name-only -r b86f0e6647b29fc008f8f29356e8ed2744b01a87`.
- Changed paths: `scripts/loadtest/capacity-common.ps1`.

---

## Commit 74579d1: fix: capture P99 and binary transcode uploads

- SHA: `74579d1bf64e5eb29d26b6c37431954571439e13`
- Author / authored: `monster`, `2026-09-18T09:29:14+08:00`
- Modules / path modules: `loadtest`, `scripts`.
- Description: P99 capture and binary transcode uploads; routes latency summary and media payload measurement issues to read/transcode scenarios and P2 orchestration.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 74579d1bf64e5eb29d26b6c37431954571439e13`; `git diff-tree --no-commit-id --name-only -r 74579d1bf64e5eb29d26b6c37431954571439e13`.
- Changed paths: `loadtest/read-baseline.js`, `loadtest/transcode-baseline.js`, `scripts/loadtest/p2-capacity-baseline.ps1`.

---

## Commit 23f4a51: fix: preserve signed host for P2 transcode uploads

- SHA: `23f4a51ae7c2440f971157fa1a134c02096aae97`
- Author / authored: `monster`, `2026-09-18T09:47:18+08:00`
- Modules / path modules: `loadtest`.
- Description: Presigned upload host preservation; routes signature and host mismatch investigation to transcode-baseline.js.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 23f4a51ae7c2440f971157fa1a134c02096aae97`; `git diff-tree --no-commit-id --name-only -r 23f4a51ae7c2440f971157fa1a134c02096aae97`.
- Changed paths: `loadtest/transcode-baseline.js`.

---

## Commit 9fcd049: docs: record P2 capacity baseline evidence

- SHA: `9fcd049d5b9062e11454d3b26edf9b913335d2e6`
- Author / authored: `monster`, `2026-09-18T09:49:20+08:00`
- Modules / path modules: `docs`.
- Description: P2 capacity evidence report; historical measurements require their original workload and environment context.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 9fcd049d5b9062e11454d3b26edf9b913335d2e6`; `git diff-tree --no-commit-id --name-only -r 9fcd049d5b9062e11454d3b26edf9b913335d2e6`.
- Changed paths: `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-project-highlights-next-plan.md`, `docs/reports/2026-09-18-p2-capacity-baseline.md`.

---

## Commit aaa545c: docs: finalize P2 capacity baseline results

- SHA: `aaa545c575ba03a6cde82079f627d900559f277e`
- Author / authored: `monster`, `2026-09-18T13:36:30+08:00`
- Modules / path modules: `docs`.
- Description: Finalized P2 capacity report; use for historical result provenance, not current throughput claims.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show aaa545c575ba03a6cde82079f627d900559f277e`; `git diff-tree --no-commit-id --name-only -r aaa545c575ba03a6cde82079f627d900559f277e`.
- Changed paths: `docs/reports/2026-09-16-project-highlights-roadmap.md`, `docs/reports/2026-09-17-project-highlights-next-plan.md`, `docs/reports/2026-09-18-p2-capacity-baseline.md`.

---

## Commit 8c70d93: docs: refresh project highlights and improvement plan

- SHA: `8c70d9321bb42cf08f151e23a5a4bff358844e92`
- Author / authored: `monster`, `2026-09-18T14:08:50+08:00`
- Modules / path modules: `docs`.
- Description: Refreshed project highlights and improvement plan; historical prioritization and report navigation.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 8c70d9321bb42cf08f151e23a5a4bff358844e92`; `git diff-tree --no-commit-id --name-only -r 8c70d9321bb42cf08f151e23a5a4bff358844e92`.
- Changed paths: `docs/reports/2026-09-17-project-highlights-next-plan.md`, `docs/reports/2026-09-18-project-highlights-next-plan.md`.

---

## Commit bffc44a: feat: recover adaptive renditions and clean orphaned HLS

- SHA: `bffc44aa546b98ffd7b7ed96c4c76667f4e3dd18`
- Author / authored: `monster`, `2026-09-18T14:53:16+08:00`
- Modules / path modules: `bilibili-common`, `bilibili-user-service`, `bilibili-video-service`, `docs`, `scripts`.
- Description: Adaptive rendition recovery and orphaned HLS cleanup; routes attempt references, recovery scheduling, cleanup, and status API questions to video service and V7 migration.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show bffc44aa546b98ffd7b7ed96c4c76667f4e3dd18`; `git diff-tree --no-commit-id --name-only -r bffc44aa546b98ffd7b7ed96c4c76667f4e3dd18`.
- Changed paths: `bilibili-common/src/main/resources/db/migration/V7__adaptive_rendition_recovery.sql`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `bilibili-video-service/pom.xml`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumer.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/controller/VideoOperationsController.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/entity/VideoTranscodeTask.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/mapper/VideoTranscodeTaskMapper.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/producer/VideoTranscodeTaskPublisher.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/DirectUploadService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/HlsAttemptCleanupTask.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoRenditionRecoveryService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/impl/UploadServiceImpl.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/vo/VideoTranscodeStatusVO.java`, `bilibili-video-service/src/main/resources/application.yml`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/consumer/VideoTranscodeConsumerTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/mapper/HlsAttemptReferenceMapperTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/mapper/VideoTranscodeRecoveryMySqlIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/mapper/VideoTranscodeTaskLeaseTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/producer/VideoTranscodeTaskPublisherTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/HlsAttemptCleanupIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/HlsAttemptCleanupTaskTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/impl/UploadServiceImplTest.java`, `docs/reports/2026-09-18-project-highlights-next-plan.md`, `docs/reports/2026-09-18-transcode-attempt-cleanup-rendition-recovery.md`, `scripts/e2e/p1-reliability-drill.ps1`.

---

## Commit a03ff81: test: verify playback during rendition takeover

- SHA: `a03ff8126d1eba46bac3dfb7c6ca30f6152055d7`
- Author / authored: `monster`, `2026-09-18T18:37:52+08:00`
- Modules / path modules: `docs`, `scripts`.
- Description: Playback during rendition takeover verification; routes the historical drill scenario to p1-reliability-drill.ps1 and its report.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show a03ff8126d1eba46bac3dfb7c6ca30f6152055d7`; `git diff-tree --no-commit-id --name-only -r a03ff8126d1eba46bac3dfb7c6ca30f6152055d7`.
- Changed paths: `docs/reports/2026-09-18-transcode-attempt-cleanup-rendition-recovery.md`, `scripts/e2e/p1-reliability-drill.ps1`.

---

## Commit afd4817: feat: measure index cutover contention and CDC drain

- SHA: `afd48175f308762f100ce0d4bd0e776cf19be1b8`
- Author / authored: `monster`, `2026-09-18T19:34:46+08:00`
- Modules / path modules: `bilibili-canal-service`, `docs`, `scripts`.
- Description: Index cutover contention and CDC drain measurement; routes lock timing and backlog handling to MySqlNamedLock, CacheSyncConsumer, and the P1 drill.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show afd48175f308762f100ce0d4bd0e776cf19be1b8`; `git diff-tree --no-commit-id --name-only -r afd48175f308762f100ce0d4bd0e776cf19be1b8`.
- Changed paths: `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/consumer/CacheSyncConsumer.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/MySqlNamedLock.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/PublishedVideoSource.java`, `bilibili-canal-service/src/main/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceService.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/consumer/CacheSyncConsumerTest.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/service/MySqlNamedLockTest.java`, `bilibili-canal-service/src/test/java/com/gary/bilibili/canal/service/SearchIndexMaintenanceServiceTest.java`, `docs/reports/2026-09-18-index-cutover-lock-cdc-backlog.md`, `docs/runbooks/reliability-closure.md`, `scripts/e2e/p1-reliability-drill.ps1`.

---

## Commit dec8aab: docs: refresh project highlights with recovery and cutover evidence

- SHA: `dec8aab5c16474d71686e78dc09cae150c6874f8`
- Author / authored: `monster`, `2026-09-18T19:43:32+08:00`
- Modules / path modules: `docs`.
- Description: Recovery and cutover evidence summary; routes historical highlights to the September 18 report.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show dec8aab5c16474d71686e78dc09cae150c6874f8`; `git diff-tree --no-commit-id --name-only -r dec8aab5c16474d71686e78dc09cae150c6874f8`.
- Changed paths: `docs/reports/2026-09-18-project-highlights-next-plan.md`.

---

## Commit 34bb6dd: docs: review project highlights and priorities

- SHA: `34bb6dd696ad4b4c28a4986cec11144c76b5485b`
- Author / authored: `monster`, `2026-09-20T10:45:00+08:00`
- Modules / path modules: `docs`.
- Description: Project review and priorities; historical review context, not implementation proof.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 34bb6dd696ad4b4c28a4986cec11144c76b5485b`; `git diff-tree --no-commit-id --name-only -r 34bb6dd696ad4b4c28a4986cec11144c76b5485b`.
- Changed paths: `docs/reports/2026-09-20-project-review.md`.

---

## Commit e9c7e38: docs: expand highlights and development roadmap

- SHA: `e9c7e386dfc579519bcdb4ebcd4f28f6ab105ee1`
- Author / authored: `monster`, `2026-09-20T11:08:26+08:00`
- Modules / path modules: `docs`.
- Description: Detailed development roadmap and highlights; routes planned engineering work to the September 20 reports.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show e9c7e386dfc579519bcdb4ebcd4f28f6ab105ee1`; `git diff-tree --no-commit-id --name-only -r e9c7e386dfc579519bcdb4ebcd4f28f6ab105ee1`.
- Changed paths: `docs/reports/2026-09-20-project-highlights-development-plan-detailed.md`, `docs/reports/2026-09-20-project-review.md`.

---

## Commit c9edaff: ci: make release validation reproducible

- SHA: `c9edaffed880f6bb80ad882480b1790bbb26832c`
- Author / authored: `monster`, `2026-09-20T12:52:24+08:00`
- Modules / path modules: `.github`, `.gitignore`, `README.md`, `bilibili-video-service`, `docker-compose.e2e.yml`, `docs`, `loadtest`, `scripts`.
- Description: Reproducible release validation; routes CI setup, E2E Compose, encoder selection, load generation, and release evidence capture to workflow and verification scripts.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show c9edaffed880f6bb80ad882480b1790bbb26832c`; `git diff-tree --no-commit-id --name-only -r c9edaffed880f6bb80ad882480b1790bbb26832c`.
- Changed paths: `.github/workflows/ci.yml`, `.gitignore`, `README.md`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/VideoEncoderSelector.java`, `docker-compose.e2e.yml`, `docs/reports/2026-09-11-release-validation-blocked.md`, `loadtest/write-baseline.js`, `scripts/e2e/compose-helpers.ps1`, `scripts/e2e/reliability-e2e.ps1`, `scripts/verification/release-validation.ps1`.

---

## Commit 1a9e05e: fix: keep release environment evidence compact

- SHA: `1a9e05e235c809857534182a9aa5037c25d86eaf`
- Author / authored: `monster`, `2026-09-20T12:55:07+08:00`
- Modules / path modules: `scripts`.
- Description: Compact release environment evidence; routes evidence formatting and capture to release-validation.ps1.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 1a9e05e235c809857534182a9aa5037c25d86eaf`; `git diff-tree --no-commit-id --name-only -r 1a9e05e235c809857534182a9aa5037c25d86eaf`.
- Changed paths: `scripts/verification/release-validation.ps1`.

---

## Commit f04254c: fix: restore reproducible minio image pulls

- SHA: `f04254c10705029a4238cd3a56a3bb55be995aac`
- Author / authored: `monster`, `2026-09-20T13:22:34+08:00`
- Modules / path modules: `bilibili-video-service`, `docker-compose.yml`, `docs`, `开发手册.md`.
- Description: MinIO image pull reproducibility; routes registry and image selection questions to Compose, HLS cleanup integration test, and the registry-fix report.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show f04254c10705029a4238cd3a56a3bb55be995aac`; `git diff-tree --no-commit-id --name-only -r f04254c10705029a4238cd3a56a3bb55be995aac`.
- Changed paths: `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/HlsAttemptCleanupIT.java`, `docker-compose.yml`, `docs/reports/2026-09-20-minio-registry-ci-fix.md`, `开发手册.md`.

---

## Commit 625ab24: fix: resolve curl deterministically on linux

- SHA: `625ab2460ad13c5400388bf77016aec40ed9a272`
- Author / authored: `monster`, `2026-09-20T13:50:26+08:00`
- Modules / path modules: `docs`, `scripts`.
- Description: Deterministic Linux curl resolution; routes executable lookup portability to compose-helpers.ps1 and the portability verification script.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 625ab2460ad13c5400388bf77016aec40ed9a272`; `git diff-tree --no-commit-id --name-only -r 625ab2460ad13c5400388bf77016aec40ed9a272`.
- Changed paths: `docs/reports/2026-09-20-linux-curl-resolution-ci-fix.md`, `scripts/e2e/compose-helpers.ps1`, `scripts/verification/verify-compose-helpers-portability.ps1`.

---

## Commit b7d7adf: fix: make curl invocation portable on linux

- SHA: `b7d7adf11c6192142ba6dc4bdeb30a9bbbe4c2db`
- Author / authored: `monster`, `2026-09-21T13:07:31+08:00`
- Modules / path modules: `docs`, `scripts`.
- Description: Portable Linux curl invocation; routes wildcard and argument handling to shared Compose helpers, reliability drills, and load scripts.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show b7d7adf11c6192142ba6dc4bdeb30a9bbbe4c2db`; `git diff-tree --no-commit-id --name-only -r b7d7adf11c6192142ba6dc4bdeb30a9bbbe4c2db`.
- Changed paths: `docs/reports/2026-09-20-linux-curl-wildcard-ci-fix.md`, `scripts/e2e/compose-helpers.ps1`, `scripts/e2e/p1-reliability-drill.ps1`, `scripts/e2e/reliability-e2e.ps1`, `scripts/loadtest/capacity-common.ps1`, `scripts/loadtest/run-write-slo.ps1`, `scripts/verification/verify-compose-helpers-portability.ps1`.

---

## Commit c292e6e: fix: harden linux release validation

- SHA: `c292e6eac2b2b156e5b2c2f427f4894744ff40fe`
- Author / authored: `monster`, `2026-09-22T10:09:20+08:00`
- Modules / path modules: `docs`, `scripts`.
- Description: Linux release validation hardening; routes cross-platform command handling to Compose helpers, E2E/P2 scripts, and portability checks.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show c292e6eac2b2b156e5b2c2f427f4894744ff40fe`; `git diff-tree --no-commit-id --name-only -r c292e6eac2b2b156e5b2c2f427f4894744ff40fe`.
- Changed paths: `docs/reports/2026-09-20-linux-curl-wildcard-ci-fix.md`, `scripts/e2e/compose-helpers.ps1`, `scripts/e2e/p1-reliability-drill.ps1`, `scripts/e2e/reliability-e2e.ps1`, `scripts/loadtest/p2-capacity-baseline.ps1`, `scripts/verification/verify-compose-helpers-portability.ps1`.

---

## Commit 1c4af08: feat: add creator analytics center

- SHA: `1c4af08f10a03414b55b11ee5acaca90003de884`
- Author / authored: `monster`, `2026-09-22T15:42:47+08:00`
- Modules / path modules: `bilibili-common`, `bilibili-danmu-service`, `bilibili-gateway`, `bilibili-social-service`, `bilibili-user-service`, `bilibili-video-service`, `bilibili-web`, `docker-compose.e2e.yml`, `scripts`.
- Description: Creator analytics center; routes dirty-day events, aggregation, totals/trends/top-video APIs, and Vue dashboard to common analytics, video service, V8 migration, and CreatorAnalyticsView.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show 1c4af08f10a03414b55b11ee5acaca90003de884`; `git diff-tree --no-commit-id --name-only -r 1c4af08f10a03414b55b11ee5acaca90003de884`.
- Changed paths: `bilibili-common/src/main/java/com/gary/bilibili/common/analytics/CreatorAnalyticsDirtyMessage.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/analytics/CreatorAnalyticsDirtyPublisher.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventService.java`, `bilibili-common/src/main/resources/db/migration/V8__creator_analytics.sql`, `bilibili-common/src/test/java/com/gary/bilibili/common/analytics/CreatorAnalyticsDirtyPublisherTest.java`, `bilibili-danmu-service/src/main/java/com/gary/bilibili/danmu/consumer/DanmuPersistConsumer.java`, `bilibili-danmu-service/src/test/java/com/gary/bilibili/danmu/consumer/DanmuPersistConsumerTest.java`, `bilibili-gateway/src/main/resources/application.yml`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/mapper/CollectionMapper.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/mapper/CommentMapper.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/mapper/LikeMapper.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/impl/CollectionServiceImpl.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/impl/CommentServiceImpl.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/impl/LikeServiceImpl.java`, `bilibili-social-service/src/main/resources/application.yml`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/service/impl/CollectionServiceImplTest.java`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/service/impl/CommentServiceImplTest.java`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/service/impl/LikeServiceImplTest.java`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/CreatorAnalyticsDirtyConsumer.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/consumer/VideoViewConsumer.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/controller/CreatorAnalyticsController.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/model/CreatorDailyMetric.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/model/CreatorDirtyDay.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/model/CreatorTopVideo.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/repository/CreatorAnalyticsRepository.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/CreatorAnalyticsAggregator.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/CreatorAnalyticsEventService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/CreatorAnalyticsService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/task/CreatorAnalyticsRebuildTask.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/vo/CreatorAnalyticsTotalVO.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/vo/CreatorAnalyticsVO.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/vo/CreatorDailyTrendVO.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/vo/CreatorTopVideoVO.java`, `bilibili-video-service/src/main/resources/application.yml`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/consumer/CreatorAnalyticsDirtyConsumerTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/consumer/VideoViewConsumerTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/repository/CreatorAnalyticsMySqlIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/CreatorAnalyticsEventServiceTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/CreatorAnalyticsServiceTest.java`, `bilibili-web/e2e/journey.spec.ts`, `bilibili-web/src/App.vue`, `bilibili-web/src/api/index.ts`, `bilibili-web/src/router/index.ts`, `bilibili-web/src/types/api.ts`, `bilibili-web/src/views/CreatorAnalyticsView.vue`, `docker-compose.e2e.yml`, `scripts/e2e/p1-reliability-drill.ps1`, `scripts/e2e/reliability-e2e.ps1`.

---

## Commit ed2dcb2: fix: fence inbox leases and transactional consumption

- SHA: `ed2dcb23bfeda3f4d38f216e707f2c87db755746`
- Author / authored: `monster`, `2026-10-10T23:59:58+08:00`
- Modules / path modules: `bilibili-common`, `bilibili-danmu-service`, `bilibili-user-service`, `bilibili-video-service`, `docs`.
- Description: Inbox lease fencing and transactional consumption; routes stale lease ownership and batch persistence to ReliableMessageExecutor, MessageInboxRepository, lease guard, DanmuBatchPersistService, and V9 migration.
- Evidence strength: local commit metadata and changed paths; test paths and commit titles do not establish passing tests or current runtime behavior.
- Source: packaged detect-updates local commit delta; `git show ed2dcb23bfeda3f4d38f216e707f2c87db755746`; `git diff-tree --no-commit-id --name-only -r ed2dcb23bfeda3f4d38f216e707f2c87db755746`.
- Changed paths: `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageInboxLease.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageInboxLeaseGuard.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageInboxLeaseLostException.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageInboxRepository.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/ReliableMessageExecutor.java`, `bilibili-common/src/main/resources/db/migration/V9__inbox_lease_fencing.sql`, `bilibili-common/src/test/java/com/gary/bilibili/common/reliability/ReliableMessageExecutorTest.java`, `bilibili-danmu-service/src/main/java/com/gary/bilibili/danmu/service/DanmuBatchPersistService.java`, `bilibili-danmu-service/src/test/java/com/gary/bilibili/danmu/service/DanmuBatchPersistServiceTest.java`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/reliability/MessageInboxLeaseMySqlIT.java`, `docs/reports/2026-10-10-inbox-lease-fencing.md`, `docs/runbooks/reliability-closure.md`.

---

## Commit f7721df: docs: add evidence-based backend interview materials

- SHA: `f7721df9b629645b9e14300564739fcdd3535e81`
- Author / authored: `monster`, `2026-11-01T22:21:44+08:00`
- Modules / path modules: `README.md`, `docs`.
- Description: Backend interview evidence guide; routes media recovery, message consistency, and search recovery explanations to docs/interview/01-media-recovery.md, 02-message-consistency.md, and 03-search-recovery.md. Documentation additions do not establish fresh test results.
- Evidence strength: local commit metadata and changed paths; source behavior and application tests were not verified during this memory update.
- Source: packaged detect-updates local commit delta; `git show f7721df9b629645b9e14300564739fcdd3535e81`; `git diff-tree --no-commit-id --name-only -r f7721df9b629645b9e14300564739fcdd3535e81`.
- Changed paths: `README.md`, `docs/interview/01-media-recovery.md`, `docs/interview/02-message-consistency.md`, `docs/interview/03-search-recovery.md`, `docs/interview/README.md`.

---

## Commit 763f1e6: docs: expand project interview guide across all major modules

- SHA: `763f1e6620e7662b5d6bc8751ad2a9e469dbc530`
- Author / authored: `monster`, `2026-11-01T22:42:19+08:00`
- Modules / path modules: `README.md`, `docs`.
- Description: Whole-project interview guide expansion; routes authentication, resumable uploads, danmu, social interactions, creator analytics, governance, caching, gateway capacity, observability, frontend playback, architecture, and encoding questions to docs/interview/04 through 15. Use these as explanation entrypoints and verify implementation claims independently.
- Evidence strength: local commit metadata and changed paths; source behavior and application tests were not verified during this memory update.
- Source: packaged detect-updates local commit delta; `git show 763f1e6620e7662b5d6bc8751ad2a9e469dbc530`; `git diff-tree --no-commit-id --name-only -r 763f1e6620e7662b5d6bc8751ad2a9e469dbc530`.
- Changed paths: `README.md`, `docs/interview/04-auth-session.md`, `docs/interview/05-upload-resume.md`, `docs/interview/06-danmu-realtime.md`, `docs/interview/07-social-interactions.md`, `docs/interview/08-creator-analytics.md`, `docs/interview/09-content-governance.md`, `docs/interview/10-read-cache-search.md`, `docs/interview/11-gateway-capacity.md`, `docs/interview/12-observability-recovery.md`, `docs/interview/13-frontend-playback.md`, `docs/interview/14-architecture-quality.md`, `docs/interview/15-media-encoding.md`, `docs/interview/README.md`.

---

## Commit aeaf483: fix: fence outbox dispatch leases and stale completion

- SHA: `aeaf483649ace9e8ac3dcef15cfb1ea4622c357a`
- Author / authored: `monster`, `2026-11-02T15:52:41+08:00`
- Modules / path modules: `bilibili-common`, `bilibili-user-service`, `bilibili-video-service`, `docs`.
- Description: Outbox dispatch lease fencing and stale completion; routes ownership-token and lease recovery investigations to OutboxEventLease, OutboxEventPublisher, OutboxEventRepository, V10__outbox_lease_fencing.sql, and OutboxLeaseMySqlIT. The local title and paths identify the change surface, not proven concurrency correctness.
- Evidence strength: local commit metadata and changed paths; source behavior and application tests were not verified during this memory update.
- Source: packaged detect-updates local commit delta; `git show aeaf483649ace9e8ac3dcef15cfb1ea4622c357a`; `git diff-tree --no-commit-id --name-only -r aeaf483649ace9e8ac3dcef15cfb1ea4622c357a`.
- Changed paths: `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventLease.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventPublisher.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventRepository.java`, `bilibili-common/src/main/resources/db/migration/V10__outbox_lease_fencing.sql`, `bilibili-common/src/test/java/com/gary/bilibili/common/reliability/OutboxEventPublisherTest.java`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/reliability/OutboxLeaseMySqlIT.java`, `docs/runbooks/outbox-lease-fencing.md`.

---

## Commit 7dc77b9: test: add browser CI gates and real backend journey

- SHA: `7dc77b980289864be052b81dfbcdb2ccc8875265`
- Author / authored: `monster`, `2026-11-02T22:02:35+08:00`
- Modules / path modules: `.github`, `README.md`, `bilibili-web`, `docs`, `scripts`.
- Description: Browser CI gates and real-backend journey; routes Playwright fixture versus backend coverage to journey.spec.ts, journey.real.spec.ts, playwright.real.config.ts, browser-e2e.ps1, release-validation.ps1, and the CI workflow. Test additions and the historical report do not prove the browser suite passes in the current environment.
- Evidence strength: local commit metadata and changed paths; source behavior and application tests were not verified during this memory update.
- Source: packaged detect-updates local commit delta; `git show 7dc77b980289864be052b81dfbcdb2ccc8875265`; `git diff-tree --no-commit-id --name-only -r 7dc77b980289864be052b81dfbcdb2ccc8875265`.
- Changed paths: `.github/workflows/ci.yml`, `README.md`, `bilibili-web/e2e/journey.real.spec.ts`, `bilibili-web/e2e/journey.spec.ts`, `bilibili-web/package-lock.json`, `bilibili-web/package.json`, `bilibili-web/playwright.config.ts`, `bilibili-web/playwright.real.config.ts`, `bilibili-web/tsconfig.e2e.json`, `docs/reports/2026-11-02-outbox-browser-ci.md`, `scripts/e2e/browser-e2e.ps1`, `scripts/verification/release-validation.ps1`.

---

## Commit 5f2196e: feat: add durable message archival and controlled replay

- SHA: `5f2196e04e2e041f62171049fae51809d04e2c5c`
- Author / authored: `monster`, `2026-11-03T10:51:43+08:00`
- Modules / path modules: `.github`, `README.md`, `bilibili-common`, `bilibili-user-service`, `bilibili-video-service`, `docker`, `docs`.
- Description: Durable message archival and controlled replay; routes retention, failure operations, replay authorization, and lifecycle scheduling to MessageArchiveRepository, MessageLifecycleTask, MessageReplayService, OperationsAuthorization, V11__message_lifecycle.sql, MySQL archive/replay tests, and the message-lifecycle runbook. Alert configuration and historical report paths provide investigation routes; runtime durability and replay safety require focused verification.
- Evidence strength: local commit metadata and changed paths; source behavior and application tests were not verified during this memory update.
- Source: packaged detect-updates local commit delta; `git show 5f2196e04e2e041f62171049fae51809d04e2c5c`; `git diff-tree --no-commit-id --name-only -r 5f2196e04e2e041f62171049fae51809d04e2c5c`.
- Changed paths: `.github/workflows/ci.yml`, `README.md`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/FailedMessageAdminController.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/FailedMessageAdminService.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/FailedMessageRepository.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageArchiveRepository.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageInboxRepository.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageLifecyclePolicy.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageLifecycleTask.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageOperationsErrors.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageReplayRequest.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/MessageReplayService.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OperationsAuthorization.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventPublisher.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventRepository.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxOperationsController.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxOperationsRepository.java`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/ReliableMessageExecutor.java`, `bilibili-common/src/main/resources/db/migration/V11__message_lifecycle.sql`, `bilibili-common/src/main/resources/observability.yml`, `bilibili-common/src/test/java/com/gary/bilibili/common/reliability/FailedMessageAdminServiceTest.java`, `bilibili-common/src/test/java/com/gary/bilibili/common/reliability/MessageLifecycleTaskTest.java`, `bilibili-common/src/test/java/com/gary/bilibili/common/reliability/MessageOperationsControllerTest.java`, `bilibili-common/src/test/java/com/gary/bilibili/common/reliability/OutboxEventPublisherTest.java`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/reliability/MessageArchiveMySqlIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/reliability/MessageLifecycleMySqlSupport.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/reliability/MessageReplayMySqlIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/reliability/OutboxLeaseMySqlIT.java`, `docker/observability/alerts.yml`, `docker/observability/tests/message-lifecycle.test.yml`, `docs/reports/2026-11-02-message-lifecycle.md`, `docs/runbooks/message-lifecycle.md`, `docs/runbooks/observability-and-reliability.md`.

---

## Commit b50252f: docs: update repo memory through 5f2196e

- SHA: `b50252f1b0210b02345cc72b1c32fd7ddb0b24f6`
- Author / authored: `monster`, `2026-11-03T11:09:12+08:00`
- Modules / path modules: repo memory; `.repo_memory`.
- Description: Refreshes the repository wiki metadata and local commit resource through 5f2196e. Use this entry to locate the preceding memory update and its evidence scope.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show b50252f1b0210b02345cc72b1c32fd7ddb0b24f6`; `git diff-tree --no-commit-id --name-only -r b50252f1b0210b02345cc72b1c32fd7ddb0b24f6`.
- Changed paths: `.repo_memory/PROFILE.md`, `.repo_memory/resources/commits.md`.

---

## Commit 050e390: refactor: separate shared infrastructure and upload responsibilities

- SHA: `050e39010f8d2fe744352aa7ba62fa56e3c56b7e`
- Author / authored: `monster`, `2026-11-03T18:18:04+08:00`
- Modules / path modules: shared infrastructure and upload; `README.md`, `bilibili-canal-service`, `bilibili-common-messaging`, `bilibili-common-observability`, `bilibili-common-reliability`, `bilibili-common-storage`, `bilibili-common-web`, `bilibili-common`, `bilibili-danmu-service`, `bilibili-gateway`, `bilibili-search-service`, `bilibili-social-service`, `bilibili-user-service`, `bilibili-video-service`, `docs`, `pom.xml`.
- Description: Routes shared dependency boundaries to the reactor POM and the observability, web, reliability, messaging, and storage modules. The architecture guide records the split of UploadServiceImpl into session, chunk validation, storage, merge transaction, and transcode-task responsibilities, with object reuse on retry.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show 050e39010f8d2fe744352aa7ba62fa56e3c56b7e`; `git diff-tree --no-commit-id --name-only -r 050e39010f8d2fe744352aa7ba62fa56e3c56b7e`.
- Changed paths: `bilibili-common-messaging/pom.xml`, `bilibili-common-messaging/src/main/java/com/gary/bilibili/common/reliability/OutboxEventPublisher.java`, `bilibili-common-observability/pom.xml`, `bilibili-common-reliability/pom.xml`, `bilibili-common-storage/pom.xml`, `bilibili-common-storage/src/main/java/com/gary/bilibili/common/storage/MinioObjectStorage.java`, `bilibili-common-web/pom.xml`, `bilibili-common/pom.xml`, `bilibili-common/src/main/java/com/gary/bilibili/common/reliability/OutboxEventPublisher.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/impl/UploadServiceImpl.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/upload/UploadChunkValidator.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/upload/UploadMergeService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/upload/UploadSessionManager.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/upload/UploadTranscodeTasks.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/upload/UploadMergeServiceTest.java`, `docs/architecture/shared-modules-and-upload.md`, `pom.xml` (selected from 104 total).

---

## Commit ad0204f: test: verify transcode fault recovery and process cleanup

- SHA: `ad0204fcf225a312a85747b842b6dad1e13119df`
- Author / authored: `monster`, `2026-11-04T08:49:07+08:00`
- Modules / path modules: transcode cleanup and fault testing; `.gitattributes`, `.github`, `README.md`, `bilibili-video-service`, `docker`, `docs`, `scripts`.
- Description: Routes FFmpeg process termination and attempt-scoped HLS cleanup to MediaProcessRunner, FfmpegVideoTranscodeWorker, and their tests. For the fault matrix and isolated MinIO test image, see the transcode fault runbook and E2E scripts.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show ad0204fcf225a312a85747b842b6dad1e13119df`; `git diff-tree --no-commit-id --name-only -r ad0204fcf225a312a85747b842b6dad1e13119df`.
- Changed paths: `.gitattributes`, `.github/workflows/ci.yml`, `README.md`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/FfmpegVideoTranscodeWorker.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/MediaProcessRunner.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/HlsAttemptCleanupIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/MediaProcessRunnerTest.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/TranscodeFaultDatabase.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/TranscodeFaultMatrixIT.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/TranscodeFaultMedia.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/service/TranscodeFaultProxy.java`, `docker/test/minio.Dockerfile`, `docs/reports/2026-11-04-transcode-fault-matrix.md`, `docs/runbooks/transcode-fault-matrix.md`, `scripts/e2e/prepare-minio-test-image.sh`, `scripts/e2e/transcode-fault-matrix.sh`.

---

## Commit 45d583c: chore: consolidate CI gates and document project delivery

- SHA: `45d583c18d48b83bd1380d412261dd3632b7e98f`
- Author / authored: `monster`, `2026-11-18T09:17:06+08:00`
- Modules / path modules: CI and project delivery; `.github`, `README.md`, `docs`.
- Description: Routes consolidated release gates and the removal of the standalone Playwright workflow to ci.yml. PROJECT_DELIVERY.md, the wrap-up report, and the frontend playback interview note provide the handoff and documented verification paths.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show 45d583c18d48b83bd1380d412261dd3632b7e98f`; `git diff-tree --no-commit-id --name-only -r 45d583c18d48b83bd1380d412261dd3632b7e98f`.
- Changed paths: `.github/workflows/ci.yml`, `.github/workflows/frontend-browser.yml`, `README.md`, `docs/PROJECT_DELIVERY.md`, `docs/interview/13-frontend-playback.md`, `docs/reports/2026-11-18-project-wrap-up.md`.

---

## Commit 58a8758: chore: initialize AOCI and scan repository baseline

- SHA: `58a87588d6d41624f5e7aa14c7d2871ba8716d14`
- Author / authored: `monster`, `2026-11-18T23:32:14+08:00`
- Modules / path modules: AOCI initialization; `.aoci`, `.gitignore`, `AGENTS.md`, `aoci.code.txt`, `aoci.meta.txt`, `aoci.txt`.
- Description: Routes the first AOCI repository baseline and its managed scope to .aoci/config.json and .aoci/baseline.json. AGENTS.md and the root, meta, and code index files establish the initial repository indexing layout.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show 58a87588d6d41624f5e7aa14c7d2871ba8716d14`; `git diff-tree --no-commit-id --name-only -r 58a87588d6d41624f5e7aa14c7d2871ba8716d14`.
- Changed paths: `.aoci/.gitignore`, `.aoci/baseline.json`, `.aoci/config.json`, `.gitignore`, `AGENTS.md`, `aoci.code.txt`, `aoci.meta.txt`, `aoci.txt`.

---

## Commit c25605b: docs: build AOCI repository index

- SHA: `c25605b29e41a5a08b5501eae1a66fcae1d08655`
- Author / authored: `monster`, `2026-11-19T00:02:35+08:00`
- Modules / path modules: AOCI code index; `.aoci`, `aoci.code.txt`.
- Description: Builds the code-volume index after initialization and records its baseline in .aoci/baseline.json. Use the commit to find the first populated code index and its historical coverage.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show c25605b29e41a5a08b5501eae1a66fcae1d08655`; `git diff-tree --no-commit-id --name-only -r c25605b29e41a5a08b5501eae1a66fcae1d08655`.
- Changed paths: `.aoci/baseline.json`, `aoci.code.txt`.

---

## Commit 4785c4a: docs: index Bilibili MySQL schema with AOCI

- SHA: `4785c4a994dab0159993d359046233aa0a346b86`
- Author / authored: `monster`, `2026-11-19T19:49:41+08:00`
- Modules / path modules: AOCI database index; `.aoci`, `aoci.database.txt`, `aoci.txt`.
- Description: Adds a MySQL schema volume in aoci.database.txt and a separate .aoci/database-baseline.json. The root index and AOCI configuration show where the database volume was registered.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show 4785c4a994dab0159993d359046233aa0a346b86`; `git diff-tree --no-commit-id --name-only -r 4785c4a994dab0159993d359046233aa0a346b86`.
- Changed paths: `.aoci/baseline.json`, `.aoci/config.json`, `.aoci/database-baseline.json`, `aoci.database.txt`, `aoci.txt`.

---

## Commit 48d1a7a: fix: reconcile interaction counts and restore release gates

- SHA: `48d1a7a75d2a73c08263542f99d348d95f6a4fed`
- Author / authored: `monster`, `2026-11-25T09:47:33+08:00`
- Modules / path modules: interaction counts and release validation; `.aoci`, `.github`, `aoci.code.txt`, `bilibili-common-reliability`, `bilibili-common`, `bilibili-social-service`, `bilibili-user-service`, `bilibili-video-service`, `docker-compose.e2e.yml`, `docs`, `scripts`.
- Description: Routes transactional like, collection, and comment counts to VideoInteractionStats, the social service implementations, and the governance deletion path. Flyway V12 reconciles historical counts from source records. VideoInteractionStatsMySqlIT, the E2E Compose overlay, and release-validation.ps1 identify the associated checks.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show 48d1a7a75d2a73c08263542f99d348d95f6a4fed`; `git diff-tree --no-commit-id --name-only -r 48d1a7a75d2a73c08263542f99d348d95f6a4fed`.
- Changed paths: `.aoci/baseline.json`, `.github/workflows/ci.yml`, `aoci.code.txt`, `bilibili-common-reliability/src/main/java/com/gary/bilibili/common/social/VideoInteractionStats.java`, `bilibili-common/src/main/resources/db/migration/V12__reconcile_video_interaction_stats.sql`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/constant/SocialConstant.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/mapper/CommentMapper.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/mapper/SocialStatsMapper.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/SocialStatsService.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/impl/CollectionServiceImpl.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/impl/CommentServiceImpl.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/service/impl/LikeServiceImpl.java`, `bilibili-social-service/src/main/java/com/gary/bilibili/social/task/SocialStatsSyncTask.java`, `bilibili-social-service/src/main/resources/application.yml`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/service/impl/CollectionServiceImplTest.java`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/service/impl/CommentServiceImplTest.java`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/service/impl/LikeServiceImplTest.java`, `bilibili-social-service/src/test/java/com/gary/bilibili/social/task/SocialStatsSyncTaskTest.java`, `bilibili-user-service/src/test/java/com/gary/bilibili/user/api/UserApiIT.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/mapper/ContentReportMapper.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/ContentGovernanceService.java`, `bilibili-video-service/src/test/java/com/gary/bilibili/video/repository/VideoInteractionStatsMySqlIT.java`, `docker-compose.e2e.yml`, `docs/PROJECT_DELIVERY.md`, `docs/interview/07-social-interactions.md`, `scripts/e2e/prepare-minio-test-image.sh`, `scripts/e2e/reliability-e2e.ps1`, `scripts/verification/release-validation.ps1`.

---

## Commit d10da2c: fix: allow real browser media requests in release tests

- SHA: `d10da2c923d23d74b8f33fa25d8c650af7db3451`
- Author / authored: `monster`, `2026-11-25T10:02:55+08:00`
- Modules / path modules: real browser E2E and MinIO CORS; `.aoci`, `aoci.code.txt`, `docker-compose.e2e.yml`.
- Description: Adds a MinIO CORS origin allowlist to the release E2E Compose overlay. Inspect MINIO_CORS_ALLOW_ORIGIN and MINIO_API_CORS_ALLOW_ORIGIN when diagnosing local Playwright cross-origin media requests.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show d10da2c923d23d74b8f33fa25d8c650af7db3451`; `git diff-tree --no-commit-id --name-only -r d10da2c923d23d74b8f33fa25d8c650af7db3451`.
- Changed paths: `.aoci/baseline.json`, `aoci.code.txt`, `docker-compose.e2e.yml`.

---

## Commit bbcf1ba: feat: manage creator submissions and resubmit rejected videos

- SHA: `bbcf1bae53ba6bc62350960c9b939c64f0859c4d`
- Author / authored: `monster`, `2026-11-25T11:18:37+08:00`
- Modules / path modules: creator submissions and review; `.aoci`, `aoci.code.txt`, `bilibili-video-service`, `bilibili-web`, `docs`.
- Description: Routes creator submission listing and rejected-video resubmission to CreatorSubmissionController, CreatorSubmissionService, ContentRiskService, and the Vue submission views. The runbook describes reusing existing media, repeating risk checks, and returning to admin review. The real Playwright journey identifies resubmission, authorization, duplicate handling, and search-visibility checks.
- Evidence strength: local commit metadata and changed paths; application behavior was not revalidated.
- Source: packaged detect-updates local commit delta; `git show bbcf1bae53ba6bc62350960c9b939c64f0859c4d`; `git diff-tree --no-commit-id --name-only -r bbcf1bae53ba6bc62350960c9b939c64f0859c4d`.
- Changed paths: `.aoci/baseline.json`, `aoci.code.txt`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/controller/CreatorSubmissionController.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/dto/VideoResubmitDTO.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/mapper/VideoMapper.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/model/VideoTags.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/ContentRiskService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/CreatorSubmissionService.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/service/impl/VideoServiceImpl.java`, `bilibili-video-service/src/main/java/com/gary/bilibili/video/vo/CreatorSubmissionVO.java`, `bilibili-web/e2e/journey.real.spec.ts`, `bilibili-web/src/api/index.ts`, `bilibili-web/src/components/SubmissionResubmitForm.vue`, `bilibili-web/src/router/index.ts`, `bilibili-web/src/types/api.ts`, `bilibili-web/src/views/CreatorSubmissionsView.vue`, `bilibili-web/src/views/ProfileView.vue`, `bilibili-web/src/views/UploadView.vue`, `docs/MEDIA_GOVERNANCE_RUNBOOK.md`.
