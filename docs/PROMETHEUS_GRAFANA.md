# Adding Prometheus and Grafana to SmartDoc

This guide shows how to add Prometheus and Grafana to the SmartDoc project so you can collect Spring Boot metrics and visualize them in dashboards.

## What you will add

- Spring Boot Actuator metrics endpoint
- Prometheus scrape endpoint at `/actuator/prometheus`
- `prometheus` service in `compose.yaml`
- `grafana` service in `compose.yaml`
- Grafana datasource pointing to Prometheus

## 1) Add metrics support to the backend

In `pom.xml`, add these dependencies if they are not already present:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Then expose the Prometheus endpoint in `src/main/resources/application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.metrics.tags.application=smartdoc
```

After that, confirm the backend exposes metrics at:

- `http://localhost:8087/actuator/health`
- `http://localhost:8087/actuator/prometheus`

## 2) Add Prometheus to Docker Compose

Add a new service to `compose.yaml`:

```yaml
  prometheus:
    image: prom/prometheus:v2.54.1
    container_name: smartdoc-prometheus
    restart: unless-stopped
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.path=/prometheus"
      - "--web.enable-lifecycle"
```

Create the config file `prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: smartdoc
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - smartdoc:8087
```

Also add the volume to the bottom of `compose.yaml`:

```yaml
volumes:
  postgres_data:
  prometheus_data:
```

## 3) Add Grafana to Docker Compose

Add this service to `compose.yaml`:

```yaml
  grafana:
    image: grafana/grafana-oss:11.1.4
    container_name: smartdoc-grafana
    restart: unless-stopped
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      prometheus:
        condition: service_started
```

Add the volume at the bottom:

```yaml
volumes:
  postgres_data:
  prometheus_data:
  grafana_data:
```

## 4) Provision the Grafana datasource

Create this file: `grafana/provisioning/datasources/prometheus.yml`

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

If you want dashboards to load automatically, you can also add a dashboard provisioning folder under `grafana/provisioning/dashboards/`.

## 5) Recommended dashboard panels

Start with these common Spring Boot metrics:

- JVM memory usage
- JVM threads
- HTTP request count and latency
- System CPU usage
- Garbage collection count and time
- Connection pool usage if you use PostgreSQL/Hikari

If you use Micrometer, many of these are available automatically once Actuator and the Prometheus registry are enabled.

## 6) Start the stack

```powershell
cd C:\Users\seifa\Documents\smartdoc
docker compose up -d --build
```

Then open:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Default Grafana login in this example:

- user: `admin`
- password: `admin`

## 7) Verify it works

1. Open `http://localhost:8087/actuator/prometheus`
2. Open Prometheus and check `Status > Targets`
3. Confirm the `smartdoc` target is `UP`
4. In Grafana, add a panel using Prometheus metrics such as:
   - `jvm_memory_used_bytes`
   - `http_server_requests_seconds_count`
   - `process_cpu_usage`

## 8) Optional improvements

- Change the Grafana admin password from the default `admin/admin`
- Add a reverse proxy if you want to expose Grafana publicly
- Add alerting rules in Prometheus
- Import a ready-made Spring Boot dashboard JSON
- Add metrics for custom business events using Micrometer counters and timers

## Minimal checklist

- [ ] Add `spring-boot-starter-actuator`
- [ ] Add `micrometer-registry-prometheus`
- [ ] Expose `/actuator/prometheus`
- [ ] Add Prometheus service to `compose.yaml`
- [ ] Add Grafana service to `compose.yaml`
- [ ] Provision the Prometheus datasource in Grafana
- [ ] Confirm metrics show up in Grafana

## Notes for this repository

The current project already uses Docker Compose for `smartdoc`, `AiSmartDoc`, `smartdoc-ai`, `postgres`, `kafka`, and the Angular `frontend`. Prometheus and Grafana fit naturally as additional monitoring services beside the existing stack.

