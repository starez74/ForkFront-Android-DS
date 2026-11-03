package com.tbd.forkfront.companion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CompanionRoleTest {
  @Test public void detectsKnightFromNetHackRank() {
    assertEquals("Knight", CompanionRole.detect("Guy the Gallant"));
    assertEquals("Knight", CompanionRole.detect("Arthur the Chevalier"));
  }

  @Test public void detectsOtherRolesAndFemaleRanks() {
    assertEquals("Wizard", CompanionRole.detect("Merlin the Evoker"));
    assertEquals("Valkyrie", CompanionRole.detect("Brunhilde the Stripling"));
    assertEquals("Barbarian", CompanionRole.detect("Xena the Plunderess"));
    assertEquals("Ranger", CompanionRole.detect("Elven the Elleth"));
  }

  @Test public void doesNotGuessUnknownTitles() {
    assertEquals("Adventurer", CompanionRole.detect(null));
    assertEquals("Adventurer", CompanionRole.detect("No status yet"));
    assertEquals("Adventurer", CompanionRole.detect("Player the ImaginaryRank"));
  }

  @Test public void inWorldVoiceHasCharacterFlavor() {
    assertTrue(CompanionRole.inWorldIntro("Knight").contains("Noble"));
    assertTrue(CompanionRole.inWorldIntro("Wizard").contains("arcane"));
  }
}
