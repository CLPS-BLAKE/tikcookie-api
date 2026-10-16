FROM docker.elastic.co/logstash/logstash:8.18.8

USER root
# Pinned Connector/J; verify bytes from Maven Central before making the image runnable.
RUN mkdir -p /usr/share/logstash/vendor/jar/jdbc \
    && curl --fail --location --retry 3 --output /usr/share/logstash/vendor/jar/jdbc/mysql-connector-j-8.4.0.jar \
       https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar \
    && echo 'd77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5  /usr/share/logstash/vendor/jar/jdbc/mysql-connector-j-8.4.0.jar' | sha256sum --check - \
    && rm -f /usr/share/logstash/pipeline/logstash.conf

COPY --chown=logstash:root config/ /usr/share/logstash/config/
COPY --chown=logstash:root pipeline/ /usr/share/logstash/pipeline/
COPY --chown=logstash:root sql/ /usr/share/logstash/sql/
COPY --chown=logstash:root templates/ /usr/share/logstash/templates/
# Windows checkout-safe; no dependence on executable bits or host bind-mount ownership.
RUN sed -i 's/\r$//' /usr/share/logstash/config/bootstrap.sh \
    && mkdir -p /usr/share/logstash/data/jdbc /usr/share/logstash/data/quarantine \
    && chown -R logstash:root /usr/share/logstash/data
USER logstash
ENTRYPOINT ["/bin/bash", "/usr/share/logstash/config/bootstrap.sh"]
