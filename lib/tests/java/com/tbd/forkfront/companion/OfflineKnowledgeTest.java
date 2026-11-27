package com.tbd.forkfront.companion;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class OfflineKnowledgeTest {
  @Test public void healingQuestionFindsCorrectSource() {
    List<OfflineKnowledge.Note> notes =
        OfflineKnowledge.find("Should I drink a healing potion now?", 3);
    assertFalse(notes.isEmpty());
    assertEquals("Potion of healing", notes.get(0).topic);
    assertTrue(notes.get(0).url.startsWith("https://nethackwiki.com/"));
  }

  @Test public void monkQuestionsRetrieveMonkNotes() {
    List<OfflineKnowledge.Note> notes =
        OfflineKnowledge.find("Does monk martial arts work with armor?", 3);
    assertFalse(notes.isEmpty());
    assertEquals("Monk", notes.get(0).topic);
  }

  @Test public void unknownFactsAreNotInvented() {
    assertTrue(OfflineKnowledge.find("xyzzyplugh", 3).isEmpty());
    assertTrue(OfflineKnowledge.find(null, 3).isEmpty());
  }

  @Test public void resultsAreCappedAndHaveAttribution() {
    List<OfflineKnowledge.Note> notes =
        OfflineKnowledge.find("healing food potion monk elbereth HP", 2);
    assertTrue(notes.size() <= 2);
    for (OfflineKnowledge.Note note : notes) {
      assertTrue(note.facts.length() >= 40);
      assertTrue(note.url.startsWith("https://nethackwiki.com/wiki/"));
    }
  }
}
