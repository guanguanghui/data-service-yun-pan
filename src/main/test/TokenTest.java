import com.sxw.server.util.TokenResolver;

public class TokenTest {
    public static void main(String[] args) {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJqd3QiLCJzdWIiOiJ7XCJhY2NvdW50SWRcIjpcImRkY2NjZjUzLTA3OTUtNDkzZi04ZjljLWEyMmE2NzJhYjQwZFwiLFwiYWNjb3VudFR5cGVcIjowLFwiYXBwXCI6XCJOT05FXCIsXCJjbGllbnRcIjpcIk5PTkVcIixcImVtcHR5XCI6ZmFsc2UsXCJwbGF0Zm9ybVwiOlwiTk9ORVwiLFwidXNlcklkXCI6XCJjNjA3MjExNy0yMDdiLTQ5YzYtYTE5NS03MTkxNTdmNDc4MzNcIixcInVzZXJUeXBlXCI6XCJOT05FXCJ9IiwiZXhwIjoxNTgxNjA3MjQwLCJpc3MiOiJzeGp5In0.sZGgiRFlu21Wn6YB1G7T4r71jhFJjA5UE-EumRAKpyc";
        System.out.println(TokenResolver.readTokenInfo(token));
    }
}
