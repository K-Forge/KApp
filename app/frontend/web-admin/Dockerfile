# Multi-stage build for the admin/dev portal: build with Node, serve the static output with
# nginx. Not published anywhere - this exists purely so `docker compose --profile dev up` gets a
# teammate a running portal without them ever running `pnpm install`.
#
# Build context is the REPO ROOT (see the `context: ../../..` in docker-compose.yml's web-admin
# service), not this directory. The portal needs docs/api at build time - scripts/generate-openapi.mjs
# bundles the five OpenAPI contracts as JSON so the API console and role inspector can import them
# as ordinary modules - and that directory lives three levels above app/frontend/web-admin, the
# same relative reach the Prism mock services already need into it.
#
# Every COPY below names an explicit subpath rather than the whole app/frontend/web-admin
# directory, so the host's own node_modules (installed for macOS/whatever the developer's laptop
# runs) never lands in the image and shadows the Linux build performed here.

FROM node:22-alpine AS build
WORKDIR /repo/app/frontend/web-admin

# Installed from just the manifest first so an unrelated source change doesn't bust the dependency
# layer. There is no committed pnpm-lock.yaml - lockfiles are gitignored repo-wide - so this
# resolves fresh rather than replaying one.
COPY app/frontend/web-admin/package.json ./
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate && pnpm install

COPY app/frontend/web-admin/angular.json app/frontend/web-admin/tsconfig*.json ./
COPY app/frontend/web-admin/public ./public
COPY app/frontend/web-admin/src ./src
COPY app/frontend/web-admin/scripts ./scripts
COPY docs/api /repo/docs/api

RUN pnpm run build

# -------------------------------------------------------------------------------------------
FROM nginx:1.31-alpine AS runtime

COPY app/frontend/web-admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/app/frontend/web-admin/dist/web-admin/browser /usr/share/nginx/html

# nginx runs as its own user and serves nothing it cannot read. A file that arrives with a
# restrictive mode - which is what happens when one is copied from somewhere else with `cp -p`,
# or checked out under a tight umask - is served as 403, which looks like a configuration
# problem and is not. Normalising here costs nothing and removes the whole class.
RUN find /usr/share/nginx/html -type f -exec chmod 644 {} + \
 && find /usr/share/nginx/html -type d -exec chmod 755 {} +

EXPOSE 80
