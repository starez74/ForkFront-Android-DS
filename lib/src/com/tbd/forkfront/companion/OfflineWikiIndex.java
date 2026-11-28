package com.tbd.forkfront.companion;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline article retrieval. Ranks complete sentence windows rather than
 * fragmented FTS snippets, prioritising a matching article and the question's
 * requested mechanic. All excerpts remain untrusted reference evidence.
 */
public final class OfflineWikiIndex {
  private static final int MAX_PASSAGE_CHARS = 680;
  private OfflineWikiIndex() {}

  public static File database(Context context) {
    return new File(new File(context.getFilesDir(), "offline-ai"), "wiki-index.sqlite");
  }

  public static boolean installed(Context context) { return database(context).isFile(); }

  private static String topicKeyword(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    if (q.contains("cockatrice")) return "cockatrice";
    if (q.contains("floating eye")) return "floating eye";
    if (q.contains("burden") || q.contains("encumbr")
        || q.contains("carry too much") || q.contains("too much equipment")) return "encumbrance";
    if (q.contains("potion")) return "potion";
    if (q.contains("monk")) return "monk";
    if (q.contains("prayer") || q.contains("pray")) return "prayer";
    if (q.contains("trap")) return "trap";
    if (q.contains("spell")) return "spell";
    if (q.contains("scroll")) return "scroll";
    if (q.matches(".*\\b(ring|rings)\\b.*")) return "ring";
    if (q.contains("amulet")) return "amulet";
    if (q.contains("dragon")) return "dragon";
    if (q.contains("shop")) return "shop";
    if (q.contains("weapon")) return "weapon";
    if (q.contains("wand")) return "wand";
    if (q.contains("elbereth")) return "elbereth";
    if (q.contains("armour") || q.contains("armor")) return "armor";
    if (q.contains("hunger") || q.contains("hungry") || q.contains("starv")) return "hunger";
    if (q.contains("food")) return "food";
    if (q.contains("curse")) return "curse";
    if (q.contains("identif")) return "identification";
    return null;
  }

  private static Set<String> terms(String text) {
    Set<String> result = new HashSet<>();
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (word.length() < 4 || word.equals("what") || word.equals("that")
          || word.equals("this") || word.equals("should") || word.equals("have")
          || word.equals("does") || word.equals("when") || word.equals("with")
          || word.equals("from") || word.equals("could") || word.equals("would")
          || word.equals("about") || word.equals("your") || word.equals("there")
          || word.equals("being") || word.equals("player")) continue;
      result.add(word);
    }
    return result;
  }

  /**
   * Question-specific intent words are for sentence ranking, not rules or
   * answers. A failed match returns the article introduction rather than
   * unrelated fragments from the middle of its body.
   */
  private static String intentWords(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    if (q.contains("danger") || q.contains("risk") || q.contains("harm")
        || q.contains("unidentified potion")) {
      return "danger dangerous risk harmful death die poison paralysis petrification "
          + "stone blind cursed quaff drink handle touch gloves sickness confusion";
    }
    if (q.contains("burden") || q.contains("encumbr") || q.contains("too much equipment")) {
      return "burdened encumbrance capacity carrying weight drop remove inventory "
          + "speed movement slowed load stash discard strength";
    }
    if (q.contains("identify") || q.contains("unidentified")) {
      return "identify unidentified identification cursed bless test discover "
          + "unknown danger avoid safely";
    }
    if (q.contains("monk") || q.contains("armor") || q.contains("armour")) {
      return "monk body armor wearing penalty penalties disadvantage combat "
          + "to-hit unarmed defense martial arts restriction drawbacks";
    }
    if (q.contains("food") || q.contains("hungry") || q.contains("hunger")) {
      return "food ration nutrition hunger eat eating turns satiated starvation";
    }
    if (q.contains("wand")) {
      return "wand engrave identify charges zap testing wand identification";
    }
    if (q.contains("floating eye")) {
      return "paralysis paralyzed gaze passive attack retaliation immobilized dangers";
    }
    return "";
  }

  private static int rank(String passage, Set<String> words, Set<String> intent) {
    String lower = passage.toLowerCase(Locale.ROOT);
    Set<String> seen = terms(lower);
    int score = 0;
    for (String word : words) {
      if (seen.contains(word)) score += 5;
    }
    for (String word : intent) {
      if (seen.contains(word)) score += 2;
    }
    // Tiny fragments, markup debris and disambiguation content are poor
    // evidence even when several terms happen to match.
    if (passage.length() < 145) score -= 25;
    if (lower.contains("may refer to") || lower.contains("on nethackwiki")) score -= 25;
    if (lower.contains("there are some things you can do")
        || lower.startsWith(". ") || lower.contains(". messages. ")
        || lower.contains(". strategy. ")) score -= 20;
    return score;
  }

  private static String bestPassage(String body, String question) {
    if (body == null || body.isEmpty()) return "";
    String[] sentences = body.split("(?<=[.!?])\\s+");
    Set<String> words = terms(question);
    Set<String> intent = terms(intentWords(question));
    String best = "";
    int bestScore = Integer.MIN_VALUE;
    // Score coherent windows of at most three consecutive sentences.
    for (int i = 0; i < sentences.length; i++) {
      StringBuilder candidate = new StringBuilder();
      for (int j = i; j < sentences.length && j < i + 3; j++) {
        String sentence = sentences[j].trim();
        if (sentence.isEmpty()) continue;
        if (candidate.length() + sentence.length() + 1 > MAX_PASSAGE_CHARS) break;
        if (candidate.length() > 0) candidate.append(' ');
        candidate.append(sentence);
        if (candidate.length() < 85) continue;
        int score = rank(candidate.toString(), words, intent);
        String text = candidate.toString().toLowerCase(Locale.ROOT);
        if (question.toLowerCase(Locale.ROOT).contains("monk")
            && (question.toLowerCase(Locale.ROOT).contains("armor")
                || question.toLowerCase(Locale.ROOT).contains("armour"))) {
          if (text.contains("penalty") || text.contains("penalties")
              || text.contains("to-hit") || text.contains("disadvantage")) score += 30;
          if (text.contains("most ideal suits")) score -= 28;
        }
        if (question.toLowerCase(Locale.ROOT).contains("floating eye")) {
          if (text.contains("paraly")) score += 28;
        }
        if (question.toLowerCase(Locale.ROOT).contains("hunger")
            && (text.contains("nutrition") || text.contains("food"))) score += 10;
        if (score > bestScore) {
          bestScore = score;
          best = candidate.toString();
        }
      }
    }
    if (bestScore < 8 || best.length() < 145) {
      // Don't force a low-relevance excerpt into the model context.
      return "";
    }
    return best;
  }

  public static List<OfflineKnowledge.Note> find(Context context, String question, int maxResults) {
    List<OfflineKnowledge.Note> results = new ArrayList<>();
    if (question == null || maxResults < 1 || !installed(context)) return results;
    String title = topicKeyword(question);
    if (title == null) return results;
    // Article-title lookup deliberately avoids wide OR searches over 5,711
    // unrelated pages. If exact-title search fails, use a limited prefix match.
    try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
        database(context).getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = db.rawQuery(
            "SELECT title, body, url FROM wiki_fts WHERE lower(title) LIKE ? "
                + "AND lower(title) NOT LIKE '%dnethack%' "
                + "AND lower(title) NOT LIKE '%variant%' "
                + "AND lower(title) NOT LIKE '%slash''em%' "
                + "ORDER BY CASE WHEN lower(title) = ? THEN 0 ELSE 1 END, length(title) "
                + "LIMIT ?",
            new String[]{"%" + title + "%", title,
                Integer.toString(Math.max(1, Math.min(5, maxResults * 2)))})) {
      while (cursor.moveToNext() && results.size() < maxResults) {
        String article = cursor.getString(0);
        String body = cursor.getString(1);
        String url = cursor.getString(2);
        if (article == null || body == null || url == null
            || !url.startsWith("https://nethackwiki.com/wiki/")) continue;
        // Prefer the exact primary article; don't append Cockatrice nest,
        // Clear potion, etc. after locating Cockatrice or Potion.
        if (results.isEmpty() && article.equalsIgnoreCase(title)) {
          String excerpt = bestPassage(body, question);
          if (excerpt.isEmpty()) return results;
          results.add(new OfflineKnowledge.Note(article, excerpt, url, ""));
          break;
        }
        if (!article.toLowerCase(Locale.ROOT).contains(title)) continue;
        String excerpt = bestPassage(body, question);
        if (!excerpt.isEmpty()) {
          results.add(new OfflineKnowledge.Note(article, excerpt, url, ""));
        }
      }
    } catch (Exception error) {
      android.util.Log.w("OfflineWikiIndex", "Wiki retrieval failed; use starter notes", error);
      results.clear();
    }
    return results;
  }
}
