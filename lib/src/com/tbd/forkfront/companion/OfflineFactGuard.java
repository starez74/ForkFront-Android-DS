package com.tbd.forkfront.companion;

import java.util.ArrayList;
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
    if (q.contains("cockatrice") && q.contains("corpse")) {
      return note("Cockatrice",
          "A cockatrice corpse can petrify you through direct bare-handed contact, "
              + "potentially killing you immediately. Do not pick one up or wield "
              + "it without appropriate protection such as gloves; falling while "
              + "wielding it can still be fatal. Avoid eating it. Exact exceptions "
              + "depend on game version and resistances.",
          "https://nethackwiki.com/wiki/Cockatrice");
    }
    if (q.contains("burden") || q.contains("encumbr")
        || q.contains("too much equipment")) {
      return note("Encumbrance",
          "Carrying more than your capacity slows movement and can make escape "
              + "or combat unsafe. Drop, stash or sell low-value heavy items; "
              + "prioritize survival equipment and useful consumables. Check "
              + "your burden status and avoid fighting while heavily loaded.",
          "https://nethackwiki.com/wiki/Encumbrance");
    }
    if (q.contains("potion") && (q.contains("unidentified")
        || q.contains("unknown")) && (q.contains("drink")
        || q.contains("quaff") || q.contains("risk"))) {
      return note("Potions",
          "Drinking an unidentified potion is risky: it could be harmful "
              + "(such as paralysis, blindness, confusion or sickness) "
              + "rather than helpful. Prefer identifying it safely before "
              + "quaffing. Observe its blessed/uncursed/cursed status where "
              + "possible; a pet curse-test indicates curse status only, "
              + "not the potion's identity.",
          "https://nethackwiki.com/wiki/Potion");
    }
    if (q.contains("wand") && (q.contains("identif") || q.contains("unknown"))
        && (q.contains("charge") || q.contains("without"))) {
      return note("Wand identification",
          "To identify a wand without spending one of its charges, use a scroll "
              + "of identify or the spell of identify if available; shopkeeper "
              + "identification services may also be available. Engrave-testing "
              + "a wand generally spends a charge, so it does not satisfy a "
              + "strict no-charges requirement. There is no standard NetHack "
              + "spell called reverse identify.",
          "https://nethackwiki.com/wiki/Identification");
    }
    if (q.contains("floating eye") && (q.contains("melee")
        || q.contains("fight") || q.contains("attack"))) {
      return note("Floating eye",
          "A floating eye can retaliate when you hit it in melee with a "
              + "passive paralyzing gaze. Prolonged paralysis leaves you unable "
              + "to defend yourself against other monsters and can be fatal. "
              + "Avoid striking it in melee without appropriate protection; "
              + "consider ranged attacks instead.",
          "https://nethackwiki.com/wiki/Floating_eye");
    }
    if ((q.contains("armour") || q.contains("armor"))
        && (q.contains("unidentified") || q.contains("unknown"))
        && (q.contains("try") || q.contains("wear") || q.contains("put on"))) {
      return note("Armor",
          "Before wearing unidentified armour, try to determine whether it "
              + "is cursed; cursed armour may be difficult or impossible to "
              + "remove normally. Check blessed/uncursed/cursed status using "
              + "reliable methods when available, and make sure you have a "
              + "way to remove a curse before experimenting. Exact equipment "
              + "behaviour depends on the NetHack version.",
          "https://nethackwiki.com/wiki/Armor");
    }
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

  /**
   * Pet observations indicate curse status, not the actual item identity.
   * This tip is appended deterministically to all object-identification
   * responses, including model-generated and reference-only results.
   */
  public static OfflineInference.Reply withPetIdentificationTip(
      String question, OfflineInference.Reply reply) {
    if (reply == null || !isObjectIdentificationQuestion(question)) return reply;
    String petTip = "Pet curse-test (BUC clue): If your pet is available, place the "
        + "item by itself on the ground in a safe area and observe the pet. "
        + "Pets do not pick up cursed items and generally avoid stepping onto "
        + "their squares; crossing reluctantly is also a warning. A pet picking "
        + "up the item is strong evidence it is not cursed. Not picking it up "
        + "alone does NOT prove it is cursed, because pets can ignore items for "
        + "other reasons. Food, piles of items, movement conditions and pet "
        + "behaviour can complicate the walking test. This helps estimate "
        + "cursed versus non-cursed status, NOT the object's exact identity "
        + "or blessed versus uncursed status.";
    OfflineKnowledge.Note petSource = new OfflineKnowledge.Note(
        "Pet curse-testing", petTip,
        "https://nethackwiki.com/wiki/Curse-testing", "pet cursed BUC identification");
    List<OfflineKnowledge.Note> sources = new ArrayList<>(reply.sources);
    sources.add(petSource);
    return new OfflineInference.Reply(reply.answer + "\n\n" + petTip, sources, reply.generated);
  }

  private static boolean isObjectIdentificationQuestion(String question) {
    if (question == null) return false;
    String q = question.toLowerCase(Locale.ROOT);
    if (q.contains("identif") || q.contains("unidentified")) return true;
    boolean unsure = q.contains("unknown") || q.contains("what is this")
        || q.contains("what's this") || q.contains("figure out");
    boolean object = q.contains("item") || q.contains("object") || q.contains("wand")
        || q.contains("armour") || q.contains("armor") || q.contains("weapon")
        || q.contains("ring") || q.contains("amulet") || q.contains("potion")
        || q.contains("scroll") || q.contains("tool") || q.contains("equipment")
        || q.contains("boots") || q.contains("helmet") || q.contains("cloak");
    return unsure && object;
  }

  private static OfflineInference.Reply note(String topic, String facts, String url) {
    OfflineKnowledge.Note source = new OfflineKnowledge.Note(topic, facts, url, "");
    List<OfflineKnowledge.Note> notes = Collections.singletonList(source);
    return new OfflineInference.Reply(
        "Curated offline reference (not AI-generated): " + facts, notes, false);
  }
}
