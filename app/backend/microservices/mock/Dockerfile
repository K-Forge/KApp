# Prism, built on the machine that runs it.
#
# stoplight/prism is published for linux/amd64 only. On Apple Silicon it ran under emulation, and
# left running while the Mac slept it could turn into a process Docker could not stop. Built from
# npm on a multi-architecture Node image, it is native on Apple Silicon, on Windows and on Linux.
#
# Compose builds this the first time a mock starts. A new version goes in x-prism, at the top of
# docker-compose.yml: the build argument and the image tag together, so every machine rebuilds.
FROM node:24-alpine

ARG PRISM_VERSION=5.16.0
RUN npm install --global --omit=dev "@stoplight/prism-cli@${PRISM_VERSION}" \
    && npm cache clean --force

USER node
ENTRYPOINT ["prism"]
