package app.service.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Component
public class SessionValidator {
    private final RestTemplate rest = new RestTemplate();
    @Value("${admin.base-url:http://localhost:8082}") private String adminBaseUrl;
    @Value("${admin.validate-path:/auth/session/validate}") private String validatePath;
    public Result validate(String sessionId){
        try{
            if(sessionId==null||sessionId.isBlank()) return Result.inactive();
            HttpHeaders h=new HttpHeaders(); h.add("X-Session-Id", sessionId);
            ResponseEntity<Map> resp = rest.exchange(URI.create(adminBaseUrl+validatePath), org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<Void>(h), Map.class);
            if(!resp.getStatusCode().is2xxSuccessful()||resp.getBody()==null) return Result.inactive();
            Object a=resp.getBody().get("active"); if(!(a instanceof Boolean b) || !b) return Result.inactive();
            return Result.active(String.valueOf(resp.getBody().get("adminId")));
        }catch(Exception e){ return Result.inactive(); }
    }
    public record Result(boolean active, String adminId){ public static Result inactive(){return new Result(false,null);} public static Result active(String a){return new Result(true,a);} }
}

