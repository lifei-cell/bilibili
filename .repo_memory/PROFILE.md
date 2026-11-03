---
schema: "repo_memory_profile.v0.2"
layout: "wiki_landing_page.v0.1"
disclosure_model: "progressive_wiki"
repo_name: "Bilibili Cloud"
repo_owner: "lifei-cell"
repo_full_name: "lifei-cell/bilibili"
repo_url: "git@github.com:lifei-cell/bilibili.git"
code_host_provider: "github"
source_repo_path: "D:\\projects-2\\bilibili"
generated_at: "2026-11-03T03:09:00.930Z"
build_mode: "lightweight"
local_head: "5f2196e04e2e041f62171049fae51809d04e2c5c"
local_branch: "master"
working_tree_state: "clean"
trust_state: "draft"
code_host_resource_state: "unavailable"
resources:
  commits: "resources/commits.md"
  prs: "resources/prs.md"
  issues: "resources/issues.md"
raw:
  prepare_report: "raw/prepare-report.json"
  commit_facets: "raw/git-commits.json"
  provider_facets: ""
---

# Bilibili Cloud Repository Wiki

This repository documents a Java 21/Spring microservice and Vue 3 demonstration video platform. It covers account access, video upload and playback, danmu, social interactions, search, and operational reliability. This is a lightweight, document-derived map, not a claim that every documented flow has been executed at this snapshot. See [README](../README.md).

- Repository: `lifei-cell/bilibili`
- Snapshot HEAD: `5f2196e04e2e041f62171049fae51809d04e2c5c` on `master`
- Working tree: clean at incremental detection; local commit entries are anchored to the SHA above.
- Build mode: `lightweight`; representative source and CI were not inspected.

## What This Repo Contains

| Area | Documented role |
| --- | --- |
| `bilibili-gateway`, `bilibili-user-service`, `bilibili-common` | API routing, authentication, and shared backend facilities. |
| `bilibili-video-service`, `bilibili-danmu-service`, `bilibili-social-service` | Media lifecycle, live danmu, and social actions. |
| `bilibili-canal-service`, `bilibili-search-service` | Binlog-driven sync and Elasticsearch read/search surface. |
| `bilibili-web` | Vue community frontend, with a separate Vite development server. |
| `docker-compose*.yml`, `docs/runbooks`, `scripts/e2e`, `loadtest` | Local runtime and documented verification paths. |

## How It Works

The [README](../README.md) routes browser/API requests through Nginx or the Spring gateway to domain services. It describes MySQL for durable domain data, Redis for caching and short-lived state, MinIO for objects, RocketMQ for asynchronous events, and Canal plus Elasticsearch for search projections. [Media governance](./media-and-governance.md) covers upload, HLS, and review. [Reliability and search](./reliability-and-search.md) covers message recovery and projection repair. These are documentation claims; inspect live implementation and run focused checks before relying on them.

## First-Use Path

Check JDK 21, Maven 3.9+, Node 20+, Docker Compose v2, then follow the [README quick start](../README.md#快速启动). For focused checks, use `mvn --batch-mode --no-transfer-progress verify` for the backend and `npm --prefix bilibili-web run quality` for the frontend, when their dependencies are available. [Run and verify](./run-and-verify.md) has task routing and environment caveats.

## Major Areas

| Area | Page | Open for |
| --- | --- | --- |
| Media and governance | [Media and governance](./media-and-governance.md) | Upload, transcode, HLS, review, and playback changes. |
| Reliability and search | [Reliability and search](./reliability-and-search.md) | Outbox/Inbox, RocketMQ, CDC, DLQ, and ES recovery. |
| Runtime and verification | [Run and verify](./run-and-verify.md) | Local setup, frontend routing, Compose, test gates, and load checks. |

## Agent Consumption Rules

Read this page, then only the page relevant to the task. Treat memory and [commit history](./resources/commits.md) as routing context. Verify current code, configuration, and tests before making behavior or release claims. GitHub [PR](./resources/prs.md) and [issue](./resources/issues.md) evidence was unavailable at build time.

## Coding Memory Boundary

This wiki gives a repository map and local history. Task-learned repair rules from coding memory can guide a specific change, but source and focused verification remain authoritative.

## Provider Context

- [Local commits](./resources/commits.md): 58 entries through the snapshot ancestry: 19 original facets in `raw/git-commits.json` and 39 incremental sections citing local Git evidence.
- [PRs/MRs](./resources/prs.md) and [issues](./resources/issues.md): unavailable because `gh` was installed but unauthenticated; no absence claim is made.
- Preparation and provider status: `raw/prepare-report.json`.

## Evidence Inspected

- Snapshot `README.md`, current `README.md`, `docs/MEDIA_GOVERNANCE_RUNBOOK.md`, `docs/runbooks/reliability-closure.md`, `bilibili-web/README.md`, `loadtest/README.md`.
- No representative source, dependency manifests, scripts, or CI were inspected for this lightweight build.
- `raw/git-commits.json`: 19 local commits. No provider facet file was generated.

## Incremental Update Scope

- Processed 5 local commits after `ed2dcb23bfeda3f4d38f216e707f2c87db755746` through the snapshot above under effective history mode `provider`; preserved the previous 53 entries.
- GitHub provider evidence remains unavailable (`auth_required`), confirmed by `gh auth status`; existing PR/issue resources and raw provider evidence were preserved.
- Only the commit index and snapshot metadata were refreshed. Architecture pages retain their original lightweight documentation scope. New commit routing covers project interview materials, Outbox fencing, browser CI/backend journeys, and durable message archival with controlled replay.
- Validation here checks the memory bundle; it does not rerun application tests or establish production behavior.
