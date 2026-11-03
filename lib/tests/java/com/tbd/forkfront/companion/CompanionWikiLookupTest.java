package com.tbd.forkfront.companion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pure string checks; no network access or Android UI required. */
public final class CompanionWikiLookupTest {
  @Test public void rejectsUnexpandedWikiTemplateText() {
    assertEquals("", CompanionWikiLookup.cleanExtract("{{potion |name=healing"));
    assertEquals("", CompanionWikiLookup.cleanExtract("Some intro {{potion}} more"));
  }

  @Test public void keepsReadableWikiIntroductions() {
    assertEquals("A potion restores hit points.",
        CompanionWikiLookup.cleanExtract(" A potion\nrestores  hit points. "));
  }

  @Test public void shortensLongIntroductions() {
    String longText = "A".repeat(800);
    assertEquals(701, CompanionWikiLookup.cleanExtract(longText).length());
  }

  @Test public void extractsUserSearchTerms() {
    assertEquals("healing potion",
        CompanionWikiLookup.extractKeywords("what about healing potion?"));
  }
}
