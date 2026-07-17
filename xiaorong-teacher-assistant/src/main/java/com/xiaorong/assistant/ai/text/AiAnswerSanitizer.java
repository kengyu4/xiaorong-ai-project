package com.xiaorong.assistant.ai.text;

public final class AiAnswerSanitizer {
    private AiAnswerSanitizer() {}

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("\\*{2,}", "");
    }

    public static Stream stream() {
        return new Stream();
    }

    public static final class Stream {
        private int pendingStars;

        public String accept(String chunk) {
            if (chunk == null || chunk.isEmpty()) return "";
            StringBuilder output = new StringBuilder(chunk.length());
            for (int index = 0; index < chunk.length(); index++) {
                char current = chunk.charAt(index);
                if (current == '*') {
                    pendingStars++;
                    continue;
                }
                flushPending(output);
                output.append(current);
            }
            return output.toString();
        }

        public String finish() {
            StringBuilder output = new StringBuilder(1);
            flushPending(output);
            return output.toString();
        }

        private void flushPending(StringBuilder output) {
            if (pendingStars == 1) {
                output.append('*');
            }
            pendingStars = 0;
        }
    }
}
