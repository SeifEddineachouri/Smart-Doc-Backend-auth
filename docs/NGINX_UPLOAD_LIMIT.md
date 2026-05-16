# SmartDoc - Fix for `413 Request Entity Too Large`

If document uploads are routed through Nginx (Docker, ingress, or a frontend proxy), the request can be rejected before it reaches Spring Boot.

## Why this happens

The `413 Request Entity Too Large` response is usually emitted by Nginx when the upload exceeds its configured body size limit.

## Fix

Increase the body size limit in the Nginx `http`, `server`, or `location` block that handles the upload route:

```nginx
client_max_body_size 100M;
```

If you use a dedicated upload location, you can scope it there:

```nginx
location /api/v1/documents/upload {
    client_max_body_size 100M;
    proxy_pass http://smartdoc:8087;
}
```

## Spring Boot side

The backend is also configured to accept larger uploads:

- `APP_UPLOAD_MAX_FILE_SIZE` default: `100MB`
- `APP_UPLOAD_MAX_REQUEST_SIZE` default: `100MB`

These values are defined in `src/main/resources/application.properties` and can be overridden per environment.

## Recommended deployment check

1. Update Nginx upload size.
2. Restart/redeploy the proxy container.
3. Ensure the Spring Boot limits are not lower than the Nginx limit.
4. Retest with a large PDF upload.

