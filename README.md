# Structura

**AI-Powered Structured Products Configurator** — a Spring Boot + Vaadin web
application that uses Anthropic Claude to suggest tailored structured
investment products, runs Monte Carlo simulations on them, visualises the
results, and exports a PDF report.

> **Disclaimer:** Structura is an educational/simulation tool. It does **not**
> provide financial advice. This notice is surfaced on every screen and in
> every generated report.

## End-to-end flow

1. **Configure** — the advisor enters a client profile (investment amount,
   risk tolerance, horizon, market view, underlyings).
2. **AI suggestions** — Claude Haiku returns 3–4 tailored structured products
   (name, type, payoff logic, pros/cons). Falls back to a built-in catalog if
   no API key is configured.
3. **Select & simulate** — simulation parameters are auto-derived from the
   profile and chosen product, then a 10,000-path Monte Carlo simulation runs.
4. **Visualise** — payoff curve and return-distribution charts (ApexCharts).
5. **Explain** — Claude turns the raw metrics into a plain-English paragraph.
6. **Export** — a two-page PDF report with profile, product, metrics, AI
   explanation and charts.

## Features

### AI integration (Anthropic Claude)
- **Product suggestions** (`ai/AIService#generateSuggestions`) — system prompt
  asks a "structured products specialist" to return a JSON array of 3–4
  products matching the domain schema.
- **Plain-English explanation** (`ai/AIService#generateExplanation`) — turns
  simulation metrics into a ≤180-word, non-expert explanation covering best/
  worst case.
- **RestClient** configured in `ai/AnthropicConfig` (30s timeout, `x-api-key`,
  `anthropic-version: 2023-06-01`) bound to `ai/AnthropicProperties`.
- **Graceful fallback** (`ai/FallbackProductCatalog`) — if the API key is
  missing/invalid or a call fails, the app still works using three hardcoded
  products / a static explanation.
- **Privacy** — only the structured profile and metrics are sent to the API;
  no PII is stored (session-scoped state).

### Domain model (`domain/`)
- `ClientProfile` (validated: min $1,000 investment, 1–10y horizon, ≤5
  underlyings), `RiskLevel`, `MarketView`, `ProductType` (AUTOCALLABLE,
  REVERSE_CONVERTIBLE, CAPITAL_PROTECTED_NOTE, BARRIER_REVERSE_CONVERTIBLE).
- `StructuredProduct`, `SimulationParameters`, `SimulationResult`.

### Monte Carlo simulation (`simulation/MonteCarloEngine`)
- 10,000 Geometric Brownian Motion paths (252 steps/year, log-space Euler).
- Per-product payoff via `util/PayoffCalculator` (path-sensitive barrier /
  autocall logic for all four product types).
- Computes: expected return, success probability, max loss, 95% VaR, median
  payoff, autocall probability, delta approximation, a 51-point payoff curve,
  and the full 10,000-sample return distribution.
- Parameters derived from the profile by `util/SimulationParameterFactory`
  (volatility/drift/barrier/coupon/participation by risk, market view and
  product type).

### PDF reporting (`pdf/PdfReportService`, Apache PDFBox)
- **Page 1:** header + client name, red disclaimer banner, client profile,
  selected product, simulation metrics, AI explanation.
- **Page 2:** payoff-curve chart and return-distribution histogram (rendered
  server-side by `util/JFreeChartRenderer`), footer disclaimer.
- Unicode→ASCII sanitisation for safe Helvetica rendering.

### UI (Vaadin Flow, `ui/`)
- `MainLayout` — `AppLayout` shell with drawer nav; preloads ApexCharts.
- `ClientInputView` (route `""`) — profile form; async "Generate AI
  Suggestions" on a virtual-thread executor with progress bar.
- `ResultsView` (route `results`) — tabs: **Suggestions** (product cards),
  **Simulator** (run Monte Carlo), **Charts** (ApexCharts), **Report**
  (generate explanation + export PDF). Redirects to input if no suggestions.
- `HowAIHelpedView` (route `how-ai-helped`) — transparency accordion on how AI
  was used (ideation, modelling, prompts, code, safety).
- `WizardState` — `@VaadinSessionScope` holder for profile/suggestions/
  selection/result/explanation.

### Platform
- Java 21 (virtual-thread executor in `config/AsyncConfig` for non-blocking AI
  and simulation calls), Spring Boot 3.5.5, Vaadin 24.8.4, PDFBox 3.0.3,
  JFreeChart 1.5.5, Lombok. Built with the bundled Gradle wrapper (9.5.1).

## Configuration

Runtime config lives in `src/main/resources/application.properties`
(with `application-dev.properties` enabling Vaadin frontend hot-deploy in dev).

| Property | Default | Purpose |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `vaadin.allowed-packages` | `com.vaadin,org.vaadin,com.wsd.structura` | Vaadin scan packages |
| `anthropic.api-key` | `${ANTHROPIC_API_KEY:changeme}` | Claude API key (env-sourced) |
| `anthropic.model` | `claude-haiku-4-5-20251001` | Claude model |
| `anthropic.api-url` | `https://api.anthropic.com/v1/messages` | Messages endpoint |
| `anthropic.anthropic-version` | `2023-06-01` | API version header |
| `anthropic.max-tokens` | `1024` | Max tokens per call |
| `anthropic.timeout-seconds` | `30` | Per-request timeout |
| `spring.mvc.async.request-timeout` | `60s` | Async (AI) request timeout |

### `ANTHROPIC_API_KEY` (required for live AI)

Claude calls read the key from the `ANTHROPIC_API_KEY` environment variable.
Without it the value defaults to `changeme` and the app transparently uses the
fallback catalog/explanation instead of calling the API.

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

Never commit a real key.

## Build & run

```bash
./gradlew build      # compile, run tests, produce the Vaadin production build
./gradlew bootRun    # run the app
```

The app is then available at <http://localhost:8080>.

## CI / security

GitHub Actions workflows live in `.github/workflows/`:

- **`build.yml`** — builds and tests on every push and pull request (Temurin
  JDK 21, Gradle cache).
- **`codeql.yml`** — GitHub CodeQL static analysis (SAST) for Java on push/PR
  to `main` and on a weekly schedule; results appear under the repository's
  **Security → Code scanning** tab.
