import com.tbd.forkfront.companion.OfflineKnowledge;
import java.util.List;

public final class OfflineKnowledgeSmoke {
  public static void main(String[] args) {
    List<OfflineKnowledge.Note> healing =
        OfflineKnowledge.find("Should I drink my healing potion now?", 3);
    if (healing.isEmpty() || !"Potion of healing".equals(healing.get(0).topic))
      throw new AssertionError("Healing reference retrieval failed");
    List<OfflineKnowledge.Note> monk =
        OfflineKnowledge.find("Monk martial arts unarmed armor", 3);
    if (monk.isEmpty() || !"Monk".equals(monk.get(0).topic))
      throw new AssertionError("Monk reference retrieval failed");
    if (!OfflineKnowledge.find("xyzzyplugh", 3).isEmpty())
      throw new AssertionError("Unknown question must have no invented source");
    for (OfflineKnowledge.Note note :
        OfflineKnowledge.find("healing food monk", 3)) {
      if (!note.url.startsWith("https://nethackwiki.com/wiki/"))
        throw new AssertionError("Missing wiki attribution");
    }
    System.out.println("PASS: offline knowledge retrieval, uncertainty and attribution");
  }
}
