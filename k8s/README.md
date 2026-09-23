# Kubernetes - Barcode → QR

Isti uzorak kao u moj-kompic projektu: Kustomize (ugrađen u `kubectl`), lokalni
k3d klaster (k3s u Dockeru) s Traefikom i cert-managerom, te CI koji na svakom
PR-u digne privremeni klaster i testira deploy.

```
k8s/
├── base/                  zajedničko (Deployment/Service/..., bez namespacea i hostova)
└── overlays/
    ├── local/             k3d + Traefik + cert-manager (Ingress, TLS, secret.env)
    └── ci/                GitHub Actions (bez Ingressa, testni secreti)
```

## Pokretanje

Preduvjeti: Docker Desktop + `brew install k3d kubectl`.

```bash
./scripts/k8s-local.sh up        # klaster + cert-manager + build + deploy
./scripts/k8s-local.sh status
./scripts/k8s-local.sh deploy    # nakon promjene koda: rebuild + rollout
./scripts/k8s-local.sh trust-ca  # macOS vjeruje lokalnom CA -> HTTPS bez upozorenja
./scripts/k8s-local.sh stop      # pauza (podaci ostaju); "up" nastavlja
./scripts/k8s-local.sh down      # briše klaster i podatke
```

Adrese:

- https://qr.localtest.me

Svaki projekt ima svoj k3d klaster (`barcode-to-qr`), a svi koriste portove
80/443 - istovremeno radi samo jedan. Skripta te upozori ako je drugi upaljen
(`k3d cluster stop <ime>`).

## Specifično za ovaj projekt

- Frontend (nginx) radi `proxy_pass` na `http://backend:8080` - zato se backend
  Service zove točno `backend`, pa `nginx.conf` radi bez izmjena.
- Nema baze ni tajni, pa nema `secret.env`.
- Backend je stateless - `replicas: 2` bi radilo bez ikakvih drugih promjena.
- Probe koriste Spring Boot Actuator `/actuator/health/liveness` i `/readiness`.

## CI

`.github/workflows/k8s.yml`: validira manifeste (kubeconform), builda image(e),
digne efemerni k3d klaster na runneru, deploya `overlays/ci` i napravi smoke test.
