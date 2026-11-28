package com.tbd.forkfront.companion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conservative quality gate, not a factual verifier. Prevents obvious
 * repetition and unsupported new claims from being presented as game advice.
 * Never upgrades a model response to "verified".
 */
public final class OfflineAnswerGuard {
  private OfflineAnswerGuard() {}

  private static Set<String> words(String text) {
    Set<String> out = new HashSet<>();
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (word.length() >= 5) out.add(word);
    }
    return out;
  }

  private static boolean repetitive(String answer) {
    String[] raw = answer.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
    Map<String, Integer> bigrams = new HashMap<>();
    int repeatedPairs = 0;
    for (int i = 1; i < raw.length; i++) {
      if (raw[i - 1].length() < 3 || raw[i].length() < 3) continue;
      String pair = raw[i - 1] + " " + raw[i];
      int count = bigrams.containsKey(pair) ? bigrams.get(pair) + 1 : 1;
      bigrams.put(pair, count);
      if (count > 2) repeatedPairs++;
    }
    return repeatedPairs >= 4 || answer.toLowerCase(Locale.ROOT).contains("potion potion");
  }

  private static boolean weakEvidence(List<OfflineKnowledge.Note> notes) {
    if (notes == null || notes.isEmpty()) return true;
    for (OfflineKnowledge.Note note : notes) {
      String facts = note.facts.trim();
      // A concise but meaningful source can support a concise answer.\n      // Short headings, snippets that only say "there are some things to do",
      // and extracted wiki navigation text are not useful game evidence.
      if (facts.length() >= 120 && words(facts).size() >= 12
          && !facts.toLowerCase(Locale.ROOT).contains("may refer to"))
        return false;
    }
    return true;
  }

  // Generic word overlap is not a factuality check. Qwen3 can faithfully
  // paraphrase a reference using synonyms that are absent from the original
  // paragraph. Withholding on that basis rejected correct encumbrance advice.

  public static OfflineInference.Reply screen(String question, OfflineInference.Reply reply) {
    if (reply == null || !reply.generated) return reply;
    String reason = null;
    if (weakEvidence(reply.sources)) reason = "insufficient relevant source detail";
    else if (repetitive(reply.answer)) reason = "repetitive model output";
    // Do not classify vocabulary differences as unsupported gameplay claims.

    if (reason == null) return reply;
    return new OfflineInference.Reply(
        "I cannot give dependable advice from this offline result (" + reason
            + "). The generated answer has been withheld. "
            + "Consult the linked NetHackWiki source or ask a more specific question.",
        reply.sources, false);
  }
}
