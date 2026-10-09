# Stejné podmínky jako produkční kontejner CDC klienta (client.Dockerfile): Debian trixie,
# tini jako init (uklízí zombie osiřelých potomků), uid 1000 a sudo jen na vybrané binárky.
FROM debian:trixie-20261005-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
      openjdk-25-jre-headless tini procps sudo \
    && useradd -m -u 1000 app \
    && echo 'app ALL=(root) NOPASSWD: /usr/bin/sleep' > /etc/sudoers.d/010-app \
    && rm -rf /var/lib/apt/lists/*
# UTF-8 názvy souborů — jinak JVM nenačte třídy lambd v testech s diakritikou v názvu
ENV LANG=C.UTF-8
USER app
WORKDIR /home/app
ENTRYPOINT ["/usr/bin/tini", "-s", "--"]
