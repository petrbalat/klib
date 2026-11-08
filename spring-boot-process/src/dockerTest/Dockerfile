# Testovací runtime napodobuje CDC: Debian trixie, JRE 25, uid 1000 a skupiny pro zařízení.
FROM debian:trixie-20261005-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
      openjdk-25-jre-headless tini procps sudo util-linux \
    && for group in audio video render dialout; do getent group "$group" >/dev/null || groupadd --system "$group"; done \
    && useradd --create-home --uid 1000 --user-group --shell /bin/bash pi \
    && usermod -aG audio,video,render,dialout pi \
    && touch /tmp/klib-video-group-only \
    && chown root:video /tmp/klib-video-group-only \
    && chmod 0640 /tmp/klib-video-group-only \
    && install -d -o pi -g pi /run/user/1000 \
    && echo 'pi ALL=(root) NOPASSWD: /usr/bin/sleep' > /etc/sudoers.d/010-pi \
    && chmod 0440 /etc/sudoers.d/010-pi \
    && rm -rf /var/lib/apt/lists/*
# UTF-8 názvy souborů — jinak JVM nenačte třídy lambd v testech s diakritikou v názvu
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV TZ=UTC
# testy s @RequiresDockerTestImage (uživatel pi, skupiny zařízení) běží jen v tomto image
ENV KLIB_PROCESS_DOCKER_TEST=true
WORKDIR /home/pi
USER pi

# Stejný řetězec jako CDC: tini sklízí osiřelé procesy, shell předá řízení JVM přes exec.
# AOT cache se v testovacím image nevytváří; zbytek důležitých JVM voleb odpovídá klientovi.
ENTRYPOINT ["/usr/bin/tini", "-s", "--", "sh", "-c", "exec java -XX:+UseCompactObjectHeaders -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Duser.timezone=$TZ -Djava.awt.headless=true -Dspring.profiles.active=docker \"$@\"", "--"]
