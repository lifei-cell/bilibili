# Reproducible fallback when the upstream test-image registry is unavailable.
# Source is the peeled commit of RELEASE.2024-12-13T22-19-12Z (AGPL-3.0).
FROM golang:1.23.12 AS build
WORKDIR /src
RUN git init && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth 1 origin 68b004a48f418ed9c4363b9faaf61a90d2f6f502 \
    && git checkout --detach FETCH_HEAD
RUN CGO_ENABLED=0 go build -trimpath -o /minio .

FROM scratch
COPY --from=build /minio /usr/bin/minio
COPY --from=build /src/LICENSE /LICENSE
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
EXPOSE 9000
ENTRYPOINT ["/usr/bin/minio"]
