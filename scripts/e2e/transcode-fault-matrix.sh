#!/usr/bin/env bash
# Isolated MySQL/MinIO containers; never connects to the running demo stack.
set -euo pipefail
cd "$(dirname "$0")/../.."
for command in java mvn docker ffmpeg ffprobe; do
  command -v "$command" >/dev/null || { echo "Missing prerequisite: $command" >&2; exit 1; }
done
docker info >/dev/null
source scripts/e2e/prepare-minio-test-image.sh
results="loadtest/results/transcode-fault-matrix-$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$results"
{
  git rev-parse HEAD
  git status --short
  java -version 2>&1
  docker version --format '{{.Server.Version}}'
  docker image inspect "$MINIO_TEST_IMAGE" --format '{{.Id}}'
  ffmpeg -version
} > "$results/environment.txt"
set +e
mvn --batch-mode --no-transfer-progress -pl bilibili-video-service -am verify \
  -Dtranscode.fault.matrix=true 2>&1 | tee "$results/maven.log"
status=${PIPESTATUS[0]}
set -e
find bilibili-video-service/target/failsafe-reports -maxdepth 1 -name '*TranscodeFaultMatrixIT*' \
  -exec cp '{}' "$results/" \;
echo "Transcode fault matrix evidence: $results"
exit "$status"
