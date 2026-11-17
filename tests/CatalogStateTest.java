import br.com.vegas.independente.CatalogState;
import br.com.vegas.independente.CatalogState.Status;

public class CatalogStateTest {
    public static void main(String[] args) {
        int checked = 0;
        for (Status from : Status.values()) for (Status to : Status.values()) {
            boolean expected = (to == Status.LOADING && from != Status.LOADING) ||
                (from == Status.LOADING && (to == Status.SUCCEEDED || to == Status.FAILED || to == Status.INTERRUPTED));
            if (CatalogState.allows(from, to) != expected) throw new AssertionError(from + " -> " + to);
            checked++;
        }
        if (CatalogState.parse(null) != Status.IDLE || CatalogState.parse("CONNECTED") != Status.IDLE ||
            CatalogState.parse("broken") != Status.IDLE || CatalogState.parse("LOADING") != Status.LOADING)
            throw new AssertionError("safe parse");
        if (CatalogState.allows(null, Status.LOADING) || CatalogState.allows(Status.IDLE, null))
            throw new AssertionError("null transition");
        System.out.println("OK: " + checked + " transition pairs and invalid/null state checks");
    }
}
