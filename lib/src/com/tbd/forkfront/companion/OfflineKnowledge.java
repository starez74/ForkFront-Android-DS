package com.tbd.forkfront.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * Small, offline, attributed reference pack: a starting point, NOT a full wiki
 * dump or a substitute for verifying rules against the actual NetHack version.
 * Summaries are short editorial paraphrases of linked NetHackWiki articles.
 */
public final class OfflineKnowledge {
  public static final class Note {
    public final String topic;
    public final String facts;
    public final String url;
    public final String keywords;
    public Note(String topic, String facts, String url, String keywords) {
      this.topic = topic;
      this.facts = facts;
      this.url = url;
      this.keywords = keywords;
    }
  }
  private static final Note[] NOTES = {
      new Note("Potion of healing",
          "A potion of healing restores hit points. In NetHack 3.6.x, cursed, uncursed and blessed versions heal 4d4, 6d4 and 8d4 HP respectively. Beatitude matters, and you cannot infer a potion's beatitude from its unidentified appearance.",
          "https://nethackwiki.com/wiki/Potion_of_healing",
          "healing potion hp health wounds restore drink quaff blessed cursed uncursed"),
      new Note("Potion of extra healing",
          "A potion of extra healing restores more HP than an ordinary potion of healing. Check its beatitude and consider keeping strong healing for dangerous encounters.",
          "https://nethackwiki.com/wiki/Potion_of_extra_healing",
          "extra healing potion hp stronger quaff emergency"),
      new Note("Potion of full healing",
          "A potion of full healing is a powerful recovery item. Its precise effects depend on beatitude and game version; verify before relying on it for additional effects.",
          "https://nethackwiki.com/wiki/Potion_of_full_healing",
          "full healing potion hp max emergency"),
      new Note("Healing spells",
          "The healing spell restores hit points to its target. Higher proficiency can add benefits such as curing blindness; unlike some healing potions it does not increase maximum HP.",
          "https://nethackwiki.com/wiki/Spellbook_of_healing",
          "healing spell spellbook cast heal blindness power energy"),
      new Note("Food ration",
          "A food ration provides 800 nutrition in standard NetHack and normally takes several actions to eat. Avoid starting a long meal while surrounded by enemies.",
          "https://nethackwiki.com/wiki/Food_ration",
          "food ration eating nutrition hungry hunger starvation"),
      new Note("Monk",
          "Monks are strong at early unarmed martial arts and face special trade-offs around armor and weapon use. Check monk-specific conduct and combat penalties before changing equipment.",
          "https://nethackwiki.com/wiki/Monk",
          "monk martial arts weaponless unarmed vegetarian armor body armor combat"),
      new Note("Elbereth",
          "Engraving Elbereth can repel some monsters in certain situations, but its protection has restrictions and has changed between NetHack versions. Never assume it will stop every threat.",
          "https://nethackwiki.com/wiki/Elbereth",
          "elbereth engraving monsters flee protective square ward"),
      new Note("Hit points",
          "Hit points measure how much damage your character can take. Use current HP and maximum HP when deciding whether to heal, retreat or avoid further combat.",
          "https://nethackwiki.com/wiki/Hit_points",
          "hp hit points damage danger health survivability recovery"),
      new Note("Identification",
          "Unidentified potions, scrolls and wands may have dangerous effects. Identification is more reliable than guessing from an item's appearance.",
          "https://nethackwiki.com/wiki/Identification",
          "identify unidentified potion scroll wand unknown test"),
      new Note("Conduct",
          "NetHack tracks voluntary conducts such as vegetarian and weaponless play. Consult the conduct screen to see which conducts you are keeping or have broken.",
          "https://nethackwiki.com/wiki/Conduct",
          "conduct vegetarian vegan weaponless monk challenge")
  };

  private OfflineKnowledge() {}

  private static Set<String> words(String value) {
    Set<String> out = new HashSet<>();
    for (String word : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
      if (word.length() >= 3 && !Arrays.asList("the","and","for","are",
          "how","should","what","with","this","that","about","your",
          "can","does","have","when","now").contains(word)) out.add(word);
    return out;
  }

  /**
   * Keyword retrieval with a threshold. No matched source => no claimed
   * factual answer; the model must not invent NetHack game rules.
   */
  public static List<Note> find(String question, int maxResults) {
    if (question == null || maxResults <= 0) return new ArrayList<>();
    Set<String> search = words(question);
    if (search.isEmpty()) return new ArrayList<>();
    List<Note> matches = new ArrayList<>();
    for (Note note : NOTES) {
      int count = 0;
      Set<String> indexed = words(note.topic + " " + note.keywords);
      for (String word : search) if (indexed.contains(word)) count++;
      if (count > 0) matches.add(note);
    }
    matches.sort(Comparator
        .comparingInt((Note n) -> overlap(search, n)).reversed()
        .thenComparing(n -> n.topic));
    return new ArrayList<>(matches.subList(0, Math.min(maxResults, matches.size())));
  }

  private static int overlap(Set<String> search, Note n) {
    int count = 0;
    Set<String> topic = words(n.topic);
    Set<String> indexed = words(n.keywords);
    for (String word : search) {
      if (topic.contains(word)) count += 3;
      else if (indexed.contains(word)) count += 1;
    }
    return count;
  }
}
