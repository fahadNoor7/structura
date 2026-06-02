package com.wsd.structura.pdf;

import com.wsd.structura.domain.ClientProfile;
import com.wsd.structura.domain.SimulationResult;
import com.wsd.structura.domain.StructuredProduct;
import com.wsd.structura.util.JFreeChartRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfReportService {

	private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
	private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
	private static final PDFont ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

	private static final float MARGIN = 50f;
	private static final float LINE_GAP = 14f;
	private static final Color NAVY = new Color(11, 37, 69);
	private static final Color DANGER_BG = new Color(252, 226, 226);
	private static final Color DANGER_BORDER = new Color(180, 35, 24);

	private static final String DISCLAIMER =
			"This tool is for simulation and educational purposes only. It does not constitute "
					+ "financial advice or a solicitation to buy or sell any financial instrument. "
					+ "Past simulation results are not indicative of future performance. Structured "
					+ "products involve risk of capital loss. Consult a licensed financial advisor "
					+ "before making any investment decisions.";

	private final JFreeChartRenderer chartRenderer;

	public PdfReportService(JFreeChartRenderer chartRenderer) {
		this.chartRenderer = chartRenderer;
	}

	public byte[] generateReport(ClientProfile profile,
	                             StructuredProduct product,
	                             SimulationResult result,
	                             String aiExplanation) {
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Cursor c = newPage(doc);

			drawHeader(c, profile);
			drawDisclaimerBanner(c);
			drawClientProfile(c, profile);
			drawProduct(c, product);
			drawMetrics(c, result);
			drawExplanation(c, aiExplanation);

			// Charts on a fresh page so they are unclipped
			closePage(c);
			Cursor chartCursor = newPage(doc);
			drawCenteredSectionHeader(chartCursor, "Payoff & Distribution");
			drawImage(chartCursor, chartRenderer.payoffCurveImage(result), 480f, 270f);
			drawImage(chartCursor, chartRenderer.histogramImage(result), 480f, 270f);
			drawFooter(chartCursor);
			closePage(chartCursor);

			doc.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to generate PDF report", e);
		}
	}

	// ---------- sections ----------

	private void drawHeader(Cursor c, ClientProfile profile) throws IOException {
		c.stream.setNonStrokingColor(NAVY);
		writeText(c, "Structura — Structured Products Report", BOLD, 20f);
		c.cursorY -= 6;
		c.stream.setNonStrokingColor(Color.GRAY);
		String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		String client = profile.getClientName() == null || profile.getClientName().isBlank()
				? "(no client name)" : profile.getClientName();
		writeText(c, "Prepared " + when + "  ·  Client: " + client, REGULAR, 10f);
		c.stream.setNonStrokingColor(Color.BLACK);
		c.cursorY -= 8;
	}

	private void drawDisclaimerBanner(Cursor c) throws IOException {
		float boxHeight = 48f;
		float boxY = c.cursorY - boxHeight;
		c.stream.setNonStrokingColor(DANGER_BG);
		c.stream.addRect(MARGIN, boxY, contentWidth(), boxHeight);
		c.stream.fill();
		c.stream.setStrokingColor(DANGER_BORDER);
		c.stream.setLineWidth(0.8f);
		c.stream.addRect(MARGIN, boxY, contentWidth(), boxHeight);
		c.stream.stroke();
		c.cursorY = boxY + boxHeight - 14f;
		c.stream.setNonStrokingColor(DANGER_BORDER);
		writeText(c, "DISCLAIMER", BOLD, 9f);
		c.stream.setNonStrokingColor(Color.BLACK);
		c.cursorY -= 2;
		writeWrapped(c, DISCLAIMER, REGULAR, 9f, contentWidth() - 16f);
		c.cursorY = boxY - 14f;
	}

	private void drawClientProfile(Cursor c, ClientProfile p) throws IOException {
		drawSectionHeader(c, "Client Profile");
		drawRow(c, "Investment Amount", String.format("$%,.2f", p.getInvestmentAmount()));
		drawRow(c, "Risk Tolerance", p.getRiskLevel().name());
		drawRow(c, "Horizon", p.getHorizonYears() + " years");
		drawRow(c, "Market View", p.getMarketView().name());
		drawRow(c, "Underlyings", String.join(", ", p.getUnderlyings()));
	}

	private void drawProduct(Cursor c, StructuredProduct p) throws IOException {
		drawSectionHeader(c, "Selected Product");
		drawRow(c, "Name", p.getName());
		drawRow(c, "Type", p.getType().name());
		c.cursorY -= 4;
		writeWrapped(c, "Description: " + p.getDescription(), REGULAR, 10f, contentWidth());
		c.cursorY -= 4;
		writeWrapped(c, "Payoff: " + p.getPayoffLogic(), REGULAR, 10f, contentWidth());
		c.cursorY -= 4;
		writeWrapped(c, "Pros: " + String.join("; ", p.getPros()), REGULAR, 10f, contentWidth());
		c.cursorY -= 4;
		writeWrapped(c, "Cons: " + String.join("; ", p.getCons()), REGULAR, 10f, contentWidth());
	}

	private void drawMetrics(Cursor c, SimulationResult r) throws IOException {
		drawSectionHeader(c, "Simulation Metrics (10,000 paths)");
		drawRow(c, "Expected Return", percent(r.getExpectedReturn()));
		drawRow(c, "Success Probability", percent(r.getSuccessProbability()));
		drawRow(c, "Max Simulated Loss", percent(r.getMaxLoss()));
		drawRow(c, "95% Value-at-Risk", percent(r.getVar95()));
		drawRow(c, "Median Payoff", String.format("$%,.2f", r.getMedianPayoff()));
		drawRow(c, "Autocall Probability", percent(r.getAutocallProbability()));
		drawRow(c, "Delta (per 1% bump)", String.format("%.4f", r.getDeltaApproximation()));
	}

	private void drawExplanation(Cursor c, String text) throws IOException {
		drawSectionHeader(c, "AI-Generated Explanation");
		writeWrapped(c, text == null ? "(no explanation generated)" : text,
				ITALIC, 10f, contentWidth());
		drawFooter(c);
	}

	private void drawFooter(Cursor c) throws IOException {
		c.stream.setNonStrokingColor(Color.GRAY);
		c.stream.beginText();
		c.stream.setFont(REGULAR, 7f);
		c.stream.newLineAtOffset(MARGIN, 30f);
		c.stream.showText(sanitize("Structura — simulation only, not financial advice."));
		c.stream.endText();
		c.stream.setNonStrokingColor(Color.BLACK);
	}

	// ---------- helpers ----------

	private Cursor newPage(PDDocument doc) throws IOException {
		PDPage page = new PDPage(PDRectangle.A4);
		doc.addPage(page);
		PDPageContentStream stream = new PDPageContentStream(doc, page);
		Cursor c = new Cursor();
		c.doc = doc;
		c.page = page;
		c.stream = stream;
		c.cursorY = page.getMediaBox().getHeight() - MARGIN;
		return c;
	}

	private void closePage(Cursor c) throws IOException {
		c.stream.close();
	}

	private void drawSectionHeader(Cursor c, String title) throws IOException {
		c.cursorY -= 6;
		c.stream.setNonStrokingColor(NAVY);
		writeText(c, title, BOLD, 13f);
		c.stream.setNonStrokingColor(NAVY);
		c.stream.setLineWidth(0.6f);
		c.stream.moveTo(MARGIN, c.cursorY + 4);
		c.stream.lineTo(MARGIN + contentWidth(), c.cursorY + 4);
		c.stream.stroke();
		c.stream.setNonStrokingColor(Color.BLACK);
		c.cursorY -= 4;
	}

	private void drawCenteredSectionHeader(Cursor c, String title) throws IOException {
		c.stream.setNonStrokingColor(NAVY);
		writeText(c, title, BOLD, 16f);
		c.stream.setNonStrokingColor(Color.BLACK);
		c.cursorY -= 6;
	}

	private void drawRow(Cursor c, String label, String value) throws IOException {
		float labelX = MARGIN;
		float valueX = MARGIN + 160f;
		float y = c.cursorY;
		c.stream.beginText();
		c.stream.setFont(BOLD, 10f);
		c.stream.newLineAtOffset(labelX, y);
		c.stream.showText(sanitize(label));
		c.stream.endText();
		c.stream.beginText();
		c.stream.setFont(REGULAR, 10f);
		c.stream.newLineAtOffset(valueX, y);
		c.stream.showText(sanitize(value));
		c.stream.endText();
		c.cursorY -= LINE_GAP;
	}

	private void drawImage(Cursor c, byte[] png, float width, float height) throws IOException {
		float x = MARGIN + (contentWidth() - width) / 2;
		float y = c.cursorY - height;
		PDImageXObject image = PDImageXObject.createFromByteArray(c.doc, png, "chart");
		c.stream.drawImage(image, x, y, width, height);
		c.cursorY = y - 10f;
	}

	private void writeText(Cursor c, String text, PDFont font, float size) throws IOException {
		c.stream.beginText();
		c.stream.setFont(font, size);
		c.stream.newLineAtOffset(MARGIN, c.cursorY);
		c.stream.showText(sanitize(text));
		c.stream.endText();
		c.cursorY -= size + 2;
	}

	private void writeWrapped(Cursor c, String text, PDFont font, float size, float maxWidth) throws IOException {
		List<String> lines = wrap(text, font, size, maxWidth);
		for (String line : lines) {
			c.stream.beginText();
			c.stream.setFont(font, size);
			c.stream.newLineAtOffset(MARGIN, c.cursorY);
			c.stream.showText(line);
			c.stream.endText();
			c.cursorY -= size + 3;
		}
	}

	private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
		List<String> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			out.add("");
			return out;
		}
		String[] words = text.split("\\s+");
		StringBuilder line = new StringBuilder();
		for (String word : words) {
			String tentative = line.length() == 0 ? word : line + " " + word;
			float w = font.getStringWidth(sanitize(tentative)) / 1000f * size;
			if (w > maxWidth && line.length() > 0) {
				out.add(sanitize(line.toString()));
				line = new StringBuilder(word);
			} else {
				if (line.length() > 0) line.append(' ');
				line.append(word);
			}
		}
		if (line.length() > 0) {
			out.add(sanitize(line.toString()));
		}
		return out;
	}

	/**
	 * Helvetica + WinAnsiEncoding can only encode a limited character set. Map
	 * common Unicode characters to ASCII equivalents and replace anything else
	 * outside the printable-ASCII range with '?' so we never throw on encode.
	 */
	private static String sanitize(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			sb.append(mapChar(c));
		}
		return sb.toString();
	}

	private static String mapChar(char c) {
		return switch (c) {
			// Dashes and minus signs
			case '‐', '‑', '‒', '–', '—', '―', '−' -> "-";
			// Quotation marks
			case '‘', '’', '‚', '‛' -> "'";
			case '“', '”', '„', '‟' -> "\"";
			// Bullets and ellipsis
			case '•', '‣', '◦', '·' -> "*";
			case '…' -> "...";
			// Whitespace variants
			case ' ', ' ', ' ', ' ', ' ', '​', ' ' -> " ";
			// Math / symbols
			case '×' -> "x";
			case '÷' -> "/";
			case '≤' -> "<=";
			case '≥' -> ">=";
			case '≠' -> "!=";
			case '±' -> "+/-";
			case '√' -> "sqrt";
			case '∞' -> "inf";
			case '≈' -> "~";
			// Currency we map to letters; '$', '£', '¥', '€' are in WinAnsi already
			case '€', '£', '¥' -> String.valueOf(c);
			// Greek letters used in finance text
			case 'α' -> "a";
			case 'β' -> "b";
			case 'γ' -> "g";
			case 'δ' -> "d";
			case 'ε' -> "e";
			case 'μ' -> "u";
			case 'π' -> "pi";
			case 'σ' -> "s";
			case 'τ' -> "t";
			case 'Δ' -> "D";
			// Warning glyph
			case '⚠' -> "!";
			default -> {
				if (c == '\n' || c == '\t' || c == '\r') {
					yield String.valueOf(c);
				}
				if (c >= 0x20 && c <= 0x7E) {
					yield String.valueOf(c);
				}
				// Latin-1 supplement is mostly WinAnsi-safe; allow through
				if (c >= 0xA0 && c <= 0xFF) {
					yield String.valueOf(c);
				}
				yield "?";
			}
		};
	}

	private static String percent(double v) {
		return String.format("%.2f%%", v * 100.0);
	}

	private static float contentWidth() {
		return PDRectangle.A4.getWidth() - 2 * MARGIN;
	}

	private static final class Cursor {
		PDDocument doc;
		PDPage page;
		PDPageContentStream stream;
		float cursorY;
	}
}
