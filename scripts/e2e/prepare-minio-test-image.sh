#!/usr/bin/env bash
# Source this file before Testcontainers or the isolated Compose E2E run.
export MINIO_TEST_IMAGE="${MINIO_TEST_IMAGE:-quay.io/minio/minio:RELEASE.2024-12-13T22-19-12Z}"
if ! docker image inspect "$MINIO_TEST_IMAGE" >/dev/null 2>&1 && ! docker pull "$MINIO_TEST_IMAGE"; then
  export MINIO_TEST_IMAGE=bilibili-test-minio:68b004a48f41
  docker image inspect "$MINIO_TEST_IMAGE" >/dev/null 2>&1 || \
    docker build --network host --build-arg HTTP_PROXY --build-arg HTTPS_PROXY \
      --build-arg http_proxy --build-arg https_proxy \
      -t "$MINIO_TEST_IMAGE" -f docker/test/minio.Dockerfile .
fi
docker image inspect "$MINIO_TEST_IMAGE" --format '{{.Id}}'
