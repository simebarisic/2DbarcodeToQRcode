<<<<<<< HEAD
# 2DbarcodeToQRcode
=======
# Barkod → QR kod

Aplikacija koja učitava sliku 1D/2D barkoda (Code128, EAN-13, DataMatrix,
PDF417, Aztec...), dekodira sadržaj pomoću **ZXing** biblioteke, i generira
**QR kod** s istim sadržajem.

## Struktura

```
barcode-to-qr/
├── backend/    Spring Boot 3 (Java 17) - REST API na :8080
└── frontend/   React + Vite - UI na :5173
```

## Pokretanje backenda

```bash
cd backend
mvn spring-boot:run
```

API endpoint: `POST http://localhost:8080/api/convert`
(multipart/form-data, field name: `file`)

Odgovor:
```json
{
  "decodedText": "sadrzaj barkoda",
  "sourceFormat": "CODE_128",
  "qrCodeBase64": "data:image/png;base64,...."
}
```

## Pokretanje frontenda

```bash
cd frontend
npm install
npm run dev
```

Otvori http://localhost:5173 - odaberi sliku barkoda i klikni "Konvertiraj
u QR kod". Frontend koristi relativni `/api` put; Vite dev server ga
proxira na `localhost:8080` (vidi `vite.config.js`), isto kao što nginx
radi u Docker/produkcijskom okruženju - nema potrebe za CORS-om ni u
jednom slučaju.

## HUB-3 → EPC QR (Revolut, N26, ...)

Ako je skenirani barkod hrvatska **HUB-3** uplatnica (PDF417, sadržaj počinje
s `HRVHUB30`), backend ga automatski prepozna, izvuče IBAN, primatelja,
iznos, poziv na broj i opis plaćanja, odredi **BIC banke primatelja** iz
IBAN-a (koristeći službeni HNB popis kodova banaka), te generira **EPC QR
kod** (EPC069-12 / SEPA Credit Transfer / GiroCode standard, verzija 001)
— format koji Revolut, N26, ING i većina europskih bankovnih app-ova
stvarno prepoznaju za skeniranje uplata. **Potvrđeno testirano s
Revolutom.**

Ograničenja:
- EPC QR radi samo za **EUR** transakcije. Ako barkod sadrži drugu valutu,
  backend vraća upozorenje i generira QR sa sirovim sadržajem (koji
  bankovne app-ove najvjerojatnije neće prepoznati).
- BIC se određuje iz prvih 7 znamenki IBAN-a primatelja, pomoću
  [službenog HNB popisa](https://www.hnb.hr/en/most-viewed/-/most_viewed_assets/fHw5lCdsj12P/content/vodeci-brojevi-banaka)
  (21 hrvatska banka). Ako IBAN nije hrvatski ili banka nije na popisu,
  generira se verzija 002 (BIC nije obavezan) uz upozorenje.
- Ako HUB-3 barkod ima **iznos 0** (tzv. otvoreni iznos - platitelj sam
  upisuje koliko uplaćuje, npr. kod Aircash uplata), EPC polje iznosa
  ostaje potpuno prazno umjesto "EUR0.00" - nula je nevažeći iznos za
  većinu bankovnih app-ova (uključujući Revolut) i doveo bi do odbijanja
  cijelog QR koda.
- Hrvatska "šifra namjene" nema pouzdano mapiranje na ISO 20022 purpose
  kodove koje EPC koristi, pa to polje ostaje prazno. Model + poziv na
  broj + opis plaćanja se spajaju u "unstructured remittance" polje kako
  referenca ne bi bila izgubljena.

## Pokretanje s Dockerom (produkcija)

```bash
docker compose up --build
```

Frontend (nginx) će biti dostupan na **http://localhost:8081**, i interno
proxira `/api/*` na backend kontejner - dakle nema CORS-a u produkciji,
sve je isti origin. Backend healthcheck (`/actuator/health`) mora
prijaviti "healthy" prije nego se frontend kontejner digne.

Env varijable (postavi u `.env` pored `docker-compose.yml` ili u shellu
prije `docker compose up`):

| Varijabla | Default | Opis |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8081` | Comma-separated originsi kojima backend vjeruje (relevantno samo ako netko gađa API izravno, mimo nginx proxy-a) |
| `LOG_LEVEL` | `INFO` | Log level za `com.example.barcodeconverter` |

## Robusnije prepoznavanje barkoda (foto uvjeti "iz stvarnog svijeta")

Slike poput fotografije zaslona ili nakošenog papira (moiré, nizak
kontrast, izobličenje) često ne prođu kroz jedan pokušaj dekodiranja.
`BarcodeService` sada isprobava kombinacije:

- **4 rotacije** (0°, 90°, 180°, 270°) - za slike snimljene "na stranu" ili naglavačke
- **3 predobrade** slike - original, grayscale, i pojačani kontrast (pomaže kod fotografiranih ekrana)
- **2 binarizera** (Hybrid i GlobalHistogram) - različiti pragovi za crno/bijelo
- automatsko **smanjenje** prevelikih fotografija (>1800px) radi brzine

Sve kombinacije (do 32) se isprobavaju dok jedna ne uspije; ako nijedna
ne uspije, greška opisuje da probaš jasniju/bliže snimljenu sliku.

**Ograničenje koje softver ne može riješiti:** fotografiranje barkoda
**s monitora** (umjesto papira) stvara moiré interferenciju između
kamere i LCD piksel-grida koja trajno uništava fine detalje gustih 2D
formata (npr. PDF417) - to je gubitak informacije u samoj slici, ne šum
koji se filtrira. Testirano i potvrđeno na stvarnom primjeru (checksum
greška i nakon agresivnog blur+threshold preprocessinga). Ako je barkod
prikazan na ekranu, napravi **screenshot** umjesto fotografije ekrana -
to zaobilazi problem u potpunosti.

## Ostala produkcijska pojačanja

- **GlobalExceptionHandler** - jedinstven `{"error": "..."}` format za sve
  greške (nevažeći barkod, prevelika datoteka, nepodržan tip datoteke,
  neočekivane greške), s server-side logiranjem stack trace-a umjesto
  da ispada iz kontrolera.
- **Validacija tipa datoteke** - prihvaćaju se samo PNG/JPEG/WEBP/BMP/GIF.
- **Spring Boot Actuator** (`/actuator/health`) - health check za Docker/
  orkestraciju (k8s liveness/readiness probe se lako doda na istu putanju).
- **Env-driven konfiguracija** - port, CORS origins, i log level se
  postavljaju kroz env varijable, ne hardkodirano.
- Backend/frontend Docker image-i pokreću se kao **non-root** korisnik.

## Upload PDF-a (npr. bankovni izvod s uplatnicom)

Osim slika, aplikacija prima i **PDF** (npr. cijeli izvod kartice s HUB-3
uplatnicom na dnu, kao PBZ Card izvod). Backend renderira svaku stranicu
PDF-a u sliku (Apache PDFBox, 300 DPI) i kroz nju provuče isti decode
pipeline kao za obične slike - prva stranica na kojoj se nešto pronađe
"pobjeđuje" (do 10 stranica se provjerava, radi performansi kod dugih
izvoda). Rezultat javlja na kojoj je stranici barkod pronađen.

## Git i CI/CD

Repo je spreman za git (`.gitignore` na sve tri razine - root, backend,
frontend - da `target/`, `node_modules/`, `.env` i sl. ne uđu u repo).

`.github/workflows/ci-cd.yml` pokreće se na GitHub Actions:

- **Na svaki push/PR** - `mvn verify` (backend, uključuje `Hub3ParserTest`)
  i `npm run build` (frontend). Ovo hvata greške prije merge-a.
- **Samo na push u `main` ili git tag `vX.Y.Z`** - builda Docker image
  backend + frontend i pusha ih u **GitHub Container Registry** (GHCR),
  besplatno za tvoj repo, bez dodatnih secreta (koristi ugrađeni
  `GITHUB_TOKEN`). Image-i se tagiraju granom, git SHA-om, i (za tagove)
  semver verzijom.

Da ovo počne raditi, samo `git push` u GitHub repo koji ima Actions
omogućen (default) - ništa dodatno nije potrebno konfigurirati.

## Put prema cloudu / home serveru

Kad budeš imao home server, `docker-compose.prod.yml` je već pripremljen
da **povuče** image-e koje je CI izgradio (umjesto da ih builda na
serveru):

```bash
cp .env.example .env   # ispuni GHCR_OWNER/GHCR_REPO
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Par preporuka za taj korak, redom kako bih ih rješavao:

1. **Ne otvaraj port-forward izravno na router.** Za home server, koristi
   **Cloudflare Tunnel** (besplatno) ili **Tailscale** - izlažeš app na
   internet/svojoj mreži bez otvaranja portova na ruteru i bez izlaganja
   svoje kućne IP adrese. Cloudflare Tunnel ti dodatno daje TLS/HTTPS
   automatski.
2. **Reverse proxy s automatskim TLS-om** ako ipak izlažeš izravno -
   [Caddy](https://caddyserver.com/) je najjednostavniji (2-3 linije
   config, Let's Encrypt automatski). Traefik je moćniji ali kompleksniji
   - za jednu app, Caddy je dovoljan.
3. **Watchtower** - kontejner koji sam povuče i restarta novije image-e
   kad ih CI objavi na GHCR, pa ne moraš ručno `pull && up -d` nakon
   svakog push-a.
4. **Osnovni monitoring** - [Uptime Kuma](https://github.com/louislam/uptime-kuma)
   (self-hosted, lagan) koji gađa `/actuator/health` i javi ako app padne.
5. **Backup** - trenutno app nema bazu/perzistentne podatke (sve je
   stateless, obrada u letu), pa nema što posebno backupirati. Ako
   ikad dodaš bazu (npr. povijest konverzija), tad razmisli o tome.
6. **Secrets** - `.env` je već gitignored; na serveru ga postavi ručno
   (ili kroz GitHub Actions "environments" + secrets ako CI kasnije
   radi i deploy, ne samo build).
7. **Verzioniranje** - git tag `v1.0.0` kad prvi put deployaš na
   "produkciju" (home server), pa dalje `v1.1.0` itd. CI ti tada
   automatski objavi taj image s tim tagom - lako se vratiš na prijašnju
   verziju ako nešto zapuca (`IMAGE_TAG=v1.0.0` u `.env`, pa `pull && up -d`).

## Napomene / mogući nadogradnji

- CORS je konfiguriran samo za `http://localhost:5173` (vidi
  `CorsConfig.java`) - promijeni ako frontend hostaš negdje drugdje.
- Max veličina uploada je 10MB (`application.properties`).
- Ako želiš da backend ne vraća QR kao base64 nego kao direktnu PNG
  datoteku (`image/png` response), lako se promijeni u
  `ConvertController` da vraća `ResponseEntity<byte[]>`.
- ZXing prepoznaje format automatski (`MultiFormatReader`) - nije potrebno
  unaprijed znati koji je barkod na slici.
>>>>>>> b22c9e8 (Initial commit)
