import org.apache.lucene.queryparser.classic.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;

public class SearchEngine {

    private static Indexer indexer;

    public static void main(String[] args) throws IOException, ParseException {

        indexer.initReader();

        // print statistics

        /*System.out.println("----------------");
        indexer.getDocumentsByTag("aka");
        indexer.getDocumentsByTag("alt_name");
        indexer.getDocumentsByTag("alternate_name");
        indexer.getDocumentsByTag("all_tags");
        System.out.println("----------------");*/

        /*for (int i = 1; i <= 60; i++) {
            System.out.println(i);
            indexer.getDocumentsByFrequency(i);
        }*/

        /*indexer.getDocumentsByFrequency(60);
        System.out.println("----------------");*/

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Enter query: ");
            String query = reader.readLine().toLowerCase();
            if ("".equals(query))
                break;
            List<Document> documents = indexer.getDocuments("title", query, BooleanClause.Occur.MUST);
            if (documents.size() == 0) {
                System.out.println("No alternate names found.");

                List<Document> documentsAlt = indexer.getDocuments("fullText", query, BooleanClause.Occur.SHOULD);
                if (documentsAlt.size() > 0) {
                    System.out.println("Did you mean:");
                    for (int i = 0; (i < documentsAlt.size()) && (i < 10); i++) { // print max 10 suggested documents
                        System.out.println((i + 1) + ". " + documentsAlt.get(i).get("title") + " --- " + documentsAlt.get(i).get("alternateName"));
                    }
                }
                System.out.println("");
                continue;
            }
            else {
                for (int i = 0; (i < documents.size()); i++) {
                    if ((i != 0) && (i % 10 == 0)) { // pause the cycle after displaying 10 documents and wait for user's decision
                        System.out.print("View more? (y/n): ");
                        String more = reader.readLine().toLowerCase();
                        if (more.startsWith("y")) {
                            System.out.println((i + 1) + ". " + documents.get(i).get("title") + " --- " + documents.get(i).get("alternateName"));
                            continue;
                        }
                        else if (more.startsWith("n"))
                            break;
                        else {
                            i--;
                            continue;
                        }
                    }
                    System.out.println((i + 1) + ". " + documents.get(i).get("title") + " --- " + documents.get(i).get("alternateName"));
                }
                System.out.println("");
            }
        }
    }
}