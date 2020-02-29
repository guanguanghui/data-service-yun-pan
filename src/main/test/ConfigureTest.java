import com.google.gson.Gson;
import com.sxw.server.util.ConfigureReader;

public class ConfigureTest {
    public static void main(String[] args) {
        final ConfigureReader cr = ConfigureReader.instance();
        Gson gson = new Gson();
        System.out.println(gson.toJson(cr.getExistUsers()));
    }
}
