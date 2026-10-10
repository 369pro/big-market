# Local Alipay sandbox configuration

`docker-compose-local.yml` loads the sibling `chatgpt-data/.env.payment` file
as Java properties through `SPRING_CONFIG_IMPORT`. The file is mounted read-only
at `/config/payment.properties`; it is not a Docker `env_file`.

Create the local file from `chatgpt-data/.env.payment.example` when setting up a
new environment, and populate the sandbox configuration there. Keep private keys
and the populated file out of version control. The file must exist before starting
`chatgpt-data`; missing configuration fails startup rather than silently disabling
payments after a container recreation.

Compose explicitly sets `PAYMENT_ALIPAY_RETURN_URL` to
`http://localhost:3002/payment/return`, overriding any older return URL in the
properties file. After configuration changes, recreate `chatgpt-data` so Spring
reloads it.

Validation on 2026-10-07: a new sandbox order was created successfully, its signed
return URL matched the frontend return route, and the browser opened the Alipay
sandbox cashier with the expected amount. No payment was submitted in this check.
