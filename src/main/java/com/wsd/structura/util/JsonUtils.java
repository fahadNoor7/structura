package com.wsd.structura.util;

public final class JsonUtils {

	private JsonUtils() {}

	/**
	 * Strips Markdown code fences (```json ... ``` or ``` ... ```) that LLMs
	 * sometimes wrap JSON responses in. Returns the trimmed body, or the original
	 * input if no fence is found.
	 */
	public static String stripCodeFence(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		if (!trimmed.startsWith("```")) {
			return trimmed;
		}
		int firstNewline = trimmed.indexOf('\n');
		if (firstNewline < 0) {
			return trimmed;
		}
		String body = trimmed.substring(firstNewline + 1);
		int closing = body.lastIndexOf("```");
		if (closing < 0) {
			return body.trim();
		}
		return body.substring(0, closing).trim();
	}
}
