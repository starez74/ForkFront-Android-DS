package com.tbd.forkfront.companion;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Conservative, locally authored reference answers for known high-impact
 * questions. Never claim these are model-generated. Rules can differ between
 * NetHack versions and variants; citations link to the relevant wiki page.
 */
public final class OfflineFactGuard {
  private OfflineFactGuard() {}

  public static OfflineInference.Reply answer(String question) {
    if (question == null) return null;
    String q = question.toLowerCase(Locale.ROOT);
    if (q.contains("elbereth")) {
      return note("Elbereth",
          "Elbereth is a protective engraving of the name on the ground, not a shield "
              + "or an invisibility effect. When effective, it discourages certain monsters "
              + "from attacking you in melee while you occupy that square. It does not stop "
              + "all monsters or ranged threats. Exact restrictions depend on the NetHack "
              + "version and variant; verify them for your game.",
          "https://nethackwiki.com/wiki/Elbereth");
    }
    if ((q.contains("health") || q.contains("hp") || q.contains("hit point"))
        && (q.contains("no healing") || q.contains("without healing")
            || q.contains("no potion") || q.contains("without potion"))) {
      return note("Hit points",
          "With no healing potions, avoid further combat if possible. Retreat from "
              + "immediate danger and recover naturally in a safe place, when feasible. "
              + "Consider healing spells or other recovery resources only if your "
              + "character actually has them. Do not assume a healer's shop exists nearby.",
          "https://nethackwiki.com/wiki/Hit_points");
    }
    if ((q.contains("food") || q.contains("eat") || q.contains("starv"))
        && (q.contains("carry") || q.contains("hunger") || q.contains("starv"))) {
      return note("Food ration",
          "Food rations are a dependable source of nutrition in standard NetHack. "
              + "Carry a supply when available, monitor hunger, and avoid beginning "
              + "a long meal during dangerous combat. Other foods have different "
              + "effects, and character conducts or game variants may affect choices.",
          "https://nethackwiki.com/wiki/Food_ration");
    }
    return null;
  }

  private static OfflineInference.Reply note(String topic, String facts, String url) {
    OfflineKnowledge.Note source = new OfflineKnowledge.Note(topic, facts, url, "");
    List<OfflineKnowledge.Note> notes = Collections.singletonList(source);
    return new OfflineInference.Reply(
        "Curated offline reference (not AI-generated): " + facts, notes, false);
  }
}
