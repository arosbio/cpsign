import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class RunMe{
    public static void main(final String[] args) {
        // Try to get detailed information about the model
        try (InputStream propsStream = RunMe.class.getResourceAsStream("cpsign.json");
            ) {
            String propertyContents = readProps();
            String versionKey = "cpsignVersion";
            int startSearchInd = propertyContents.indexOf(versionKey) + versionKey.length() + 2;
            int vStart = propertyContents.indexOf("\"",startSearchInd) + 1;
            int vEnd = propertyContents.indexOf("\"",vStart + 1);
            String versionString = propertyContents.substring(vStart, vEnd);
            final StringBuilder sb = new StringBuilder("%nThis is a ");
            if (propertyContents.contains("precomputedData")){
                sb.append("data set generated");
            } else {
                sb.append("predictive model built");
            }
            sb.append(" by CPSign - Conformal Prediction with the signatures molecular descriptor%n© 2022, Aros Bio AB, www.arosbio.com%n%nThe CPSign program of version ");
            sb.append(versionString).append(" is needed to use this model%n%n");

            System.out.printf(sb.toString());
            return;
        } catch (Exception | Error e){}

        // Fallback in case the above failed
        final String text = "%nThis is a predictive model or data set built with CPSign - Conformal Prediction with the signatures molecular descriptor%n© 2022, Aros Bio AB, www.arosbio.com%n%nThe CPSign program is needed to use this model%n%n";
        System.out.printf(text);
    }

    private static String readProps() throws IOException {
        try (
            InputStream propsStream = RunMe.class.getResourceAsStream("cpsign.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(propsStream, StandardCharsets.UTF_8))){
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
