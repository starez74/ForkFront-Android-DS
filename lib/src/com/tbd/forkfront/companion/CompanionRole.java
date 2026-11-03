package com.tbd.forkfront.companion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Role lookup from NetHack 5.0 src/role.c rank titles. Exact rank matching
 * avoids confusing a character name with a role, and never guesses if ambiguous.
 */
public final class CompanionRole {
  private static final Map<String, String[]> RANKS;
  public static final String[] OPTIONS = {
      "Auto", "Archeologist", "Barbarian", "Caveman", "Healer",
      "Knight", "Monk", "Priest", "Ranger", "Rogue", "Samurai",
      "Tourist", "Valkyrie", "Wizard"
  };

  static {
    Map<String, String[]> ranks = new LinkedHashMap<>();
    ranks.put("Archeologist", new String[] {"Digger", "Field Worker", "Investigator", "Exhumer", "Excavator", "Spelunker", "Speleologist", "Collector", "Curator");
    ranks.put("Barbarian", new String[] {"Plunderer", "Plunderess", "Pillager", "Bandit", "Brigand", "Raider", "Reaver", "Slayer", "Chieftain", "Chieftainess", "Conqueror", "Conqueress");
    ranks.put("Caveman", new String[] {"Troglodyte", "Aborigine", "Wanderer", "Vagrant", "Wayfarer", "Roamer", "Nomad", "Rover", "Pioneer");
    ranks.put("Healer", new String[] {"Rhizotomist", "Empiric", "Embalmer", "Dresser", "Medicus ossium", "Medica ossium", "Herbalist", "Magister", "Magistra", "Physician", "Chirurgeon");
    ranks.put("Knight", new String[] {"Gallant", "Esquire", "Bachelor", "Sergeant", "Knight", "Banneret", "Chevalier", "Chevaliere", "Seignieur", "Dame", "Paladin");
    ranks.put("Monk", new String[] {"Candidate", "Novice", "Initiate", "Student of Stones", "Student of Waters", "Student of Metals", "Student of Winds", "Student of Fire", "Master");
    ranks.put("Priest", new String[] {"Aspirant", "Acolyte", "Adept", "Priest", "Priestess", "Curate", "Canon", "Canoness", "Lama", "Patriarch", "Matriarch", "High Priest", "High Priestess");
    ranks.put("Rogue", new String[] {"Footpad", "Cutpurse", "Rogue", "Pilferer", "Robber", "Burglar", "Filcher", "Magsman", "Magswoman", "Thief");
    ranks.put("Ranger", new String[] {"Edhel", "Elleth", "Edhel", "Elleth", "Ohtar", "Ohtie", "Kano", "Kanie", "Arandur", "Aranduriel", "Hir", "Hiril", "Aredhel", "Arwen", "Ernil", "Elentariel", "Elentar", "Elentari", "Tenderfoot", "Lookout", "Trailblazer", "Reconnoiterer", "Reconnoiteress", "Scout", "Arbalester", "Archer");
    ranks.put("Samurai", new String[] {"Hatamoto", "Ronin", "Ninja", "Kunoichi", "Joshu", "Ryoshu", "Kokushu", "Daimyo", "Kuge", "Shogun");
    ranks.put("Tourist", new String[] {"Rambler", "Sightseer", "Excursionist", "Peregrinator", "Peregrinatrix", "Traveler", "Journeyer", "Voyager", "Explorer", "Adventurer");
    ranks.put("Valkyrie", new String[] {"Stripling", "Skirmisher", "Fighter", "Man-at-arms", "Woman-at-arms", "Warrior", "Swashbuckler", "Hero", "Heroine", "Champion", "Lord", "Lady");
    ranks.put("Wizard", new String[] {"Evoker", "Conjurer", "Thaumaturge", "Magician", "Enchanter", "Enchantress", "Sorcerer", "Sorceress", "Necromancer", "Wizard", "Mage");
    RANKS = Collections.unmodifiableMap(ranks);
  }

  private CompanionRole() {}

  public static String detect(String statusTitle) {
    if (statusTitle == null) return "Adventurer";
    String t = statusTitle.trim();
    int ix = t.toLowerCase(Locale.ROOT).lastIndexOf(" the ");
    if (ix < 0) return "Adventurer";
    String rank = t.substring(ix + 5).trim();
    String result = null;
    for (Map.Entry<String, String[]> e : RANKS.entrySet()) {
      if (rank.equalsIgnoreCase(e.getKey())) return e.getKey();
      for (String r : e.getValue()) {
        if (rank.equalsIgnoreCase(r)) {
          if (result != null && !result.equals(e.getKey())) return "Adventurer";
          result = e.getKey();
        }
      }
    }
    return result != null ? result : "Adventurer";
  }

  public static String inWorldIntro(String role) {
    switch (role) {
      case "Archeologist": return "Fellow explorer, these records from the old scholars may help:";
      case "Barbarian": return "Warrior, heed this knowledge before you charge:";
      case "Caveman": return "Companion, old wisdom says to study these findings:";
      case "Healer": return "Friend, let us consult the healers' records:";
      case "Knight": return "Noble companion, here is what the chronicles reveal:";
      case "Monk": return "Seeker, consider these lessons from the archive:";
      case "Priest": return "Faithful traveller, these scriptures may guide us:";
      case "Ranger": return "Trail-mate, here is what the scouts have recorded:";
      case "Rogue": return "Partner, here's some information worth keeping quiet:";
      case "Samurai": return "Honoured companion, study these records with care:";
      case "Tourist": return "Fellow wanderer, I found these pages in the guidebook:";
      case "Valkyrie": return "Shieldmate, the sagas speak of these matters:";
      case "Wizard": return "Apprentice, let us consult the arcane reference:";
      default: return "Fellow adventurer, here are some relevant records:";
    }
  }
}
