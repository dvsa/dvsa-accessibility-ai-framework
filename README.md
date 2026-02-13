## AI-Augmented Accessibility Framework

An automated testing and auditing engine combining Playwright, Axe-core, and AWS Bedrock AI to find accessibility violations and provide live GOV.UK Design System (GDS) compliant fixes.

---

### Key Features
- **Intelligent Auto-Fill:** Traverses complex GOV.UK forms using domain-aware logic to reach deep-link pages.
- **Live GDS Scraper:** Dynamically pulls the latest guidance from the GOV.UK Design System to ensure recommendations are always up-to-date.
- **AI Resolution Engine:** Uses AWS Bedrock (Nova lite) to analyze technical Axe violations and rewrite failing HTML into GDS-compliant code.
- **Cumulative Auditing:** Scans multiple pages across a session and merges them into a single, high-fidelity HTML report.
- **Actionable Fixes:** Provides "Download Fix Snippet" buttons for developers to immediately test corrected HTML.

---

### Architecture
The framework is divided into four logical layers:

1. **Execution Layer** (`AXEScanner`)
   - Leverages Playwright to navigate the service and Axe-core to perform technical audits.
   - State Management: Accumulates violations and screenshots in static concurrent maps.
   - Screenshot Mapping: Captures one full-page screenshot per URL for visual evidence.

2. **Context Layer** (`GovUkScraper`)
   - JSoup-based engine scrapes GDS Style and Component pages, providing "Ground Truth" context to prevent AI hallucinations.

3. **Intelligence Layer** (`BedrockAgentAnalyser`)
   - Bridges technical errors and human-readable guidance.
   - Processes violations in batches for AI accuracy and robust API communication.

4. **Presentation Layer** (`HtmlReportGenerator`)
   - Generates standalone, interactive HTML reports.
   - Fixes sourced via AI → Static Knowledge Base → Hardcoded GDS Fallbacks.
   - Visual Audit: Impact-coded badges and modal screenshot viewers.

---

### Setup & Installation

#### Prerequisites
- Java 21+ (see `pom.xml` for version)
- Maven
- AWS credentials (configured for Bedrock access)
- Playwright browsers (run: `mvn playwright:install`)

#### Dependencies
Ensure the following are in your `pom.xml`:
- `com.deque.html.axe-core:playwright`
- `org.jsoup:jsoup`
- `org.json:json`
- `org.slf4j:slf4j-simple` (logging)
- `software.amazon.awssdk:bedrockagentruntime` (Bedrock integration)
- `com.microsoft.playwright:playwright`
- `org.apache.logging.log4j:log4j-core` and `log4j-api`
- `com.fasterxml.jackson.core:jackson-databind` and `jackson-datatype-jsr310`
- `org.apache.commons:commons-lang3`
- `org.junit.jupiter:junit-jupiter-api` and `junit-jupiter-engine`

---

### Usage

#### Running a Scan
Add the scanner to your Playwright test suite:

```java
@Test
void accessibilityAudit() {
   page.navigate("https://your-gov-service.gov.uk");
   AXEScanner.scan(page); // Repeat across multiple pages
}

@AfterAll
static void tearDown() {
   AXEScanner.generateFinalReport(); // Generates the cumulative AI report
}
```

#### Configuration
Pass standards via system properties:
```
-Dstandards.scan=wcag22aa,best-practice
```

---

### Report Output
Reports are generated in:
- `target/reports/accessibility-audit-[timestamp].html`
Screenshots are stored in:
- `target/reports/screenshots/`

---

### Troubleshooting
- Ensure AWS credentials are set up for Bedrock access.
- Run `mvn playwright:install` if browsers are missing.
- Check `target/surefire-reports/` for test logs.

---

### License
See LICENSE file for details.