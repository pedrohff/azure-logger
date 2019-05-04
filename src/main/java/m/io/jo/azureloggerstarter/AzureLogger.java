package m.io.jo.azureloggerstarter;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@Configuration
public class AzureLogger {
    private static final String SPL_CHAR_REGEX = "[-+.^:,]";
    private static final String UNDERSCORE = "_";
    private static final String AZURE_LA_URL = "https://%s.ods.opinsights.azure.com/api/logs?api-version=2016-04-01";

    private final RestTemplate restTemplate;
    private final Azure azure;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${spring.application.version}")
    private String appVersion;

    public AzureLogger(RestTemplateBuilder restTemplateBuilder, Azure azure) {
        this.restTemplate = restTemplateBuilder.build();
        this.azure = azure;
    }

    @Async
    public void pushLogsToAzure(String json) {
        if (azure == null || StringUtils.isEmpty(azure.getWorkspaceId()) || StringUtils.isEmpty(azure.getSharedKey()) || !azure.isEnabled()) {
            return;
        }

        try {

            UUID.fromString(azure.getWorkspaceId());
        } catch (NullPointerException | IllegalArgumentException e) {
            return;
        }
        RequestEntity<String> azureLogRqst = constructHttpEntity(json, false);

        restTemplate.exchange(azureLogRqst, String.class);
    }

    public void pushSysLogsToAzure(String json) {

        if (azure == null || StringUtils.isEmpty(azure.getWorkspaceId()) || StringUtils.isEmpty(azure.getSharedKey()) || !azure.isEnabled()) {
            return;
        }

        try {
            UUID.fromString(azure.getWorkspaceId());
        } catch (NullPointerException | IllegalArgumentException e) {
            return;
        }
        // execute in async thread
        new Thread(() -> {
            RequestEntity<String> azureLogRqst = constructHttpEntity(json, true);
            RestTemplate asyncRestTemplate = new RestTemplate();
            asyncRestTemplate.exchange(azureLogRqst, String.class);
        }).start();
    }

    private RequestEntity<String> constructHttpEntity(String json, Boolean isSysLog) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ssZ", Locale.US);
        df.setTimeZone(tz);
        String nowAsISO = df.format(new Date());

        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = fmt.format(Calendar.getInstance().getTime()) + " GMT";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Log-Type", constructAzureLogNm(appName, appVersion, isSysLog));
        httpHeaders.add("x-ms-date", date);
        json = StringUtils.stripAccents(json);
        httpHeaders.add("Authorization", computeAuthHdr(json, isSysLog, date));
        httpHeaders.add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        httpHeaders.set("time-generated-field", nowAsISO);

        String azueLogUrl = String.format(AZURE_LA_URL, azure.getWorkspaceId());

        return new RequestEntity<String>(json, httpHeaders, HttpMethod.POST,
                UriComponentsBuilder.fromHttpUrl(azueLogUrl).build().toUri());
    }

    private String computeAuthHdr(String jsonBody, Boolean isSysLog, String date) {
        Integer bodyLength = jsonBody.length();
        if (!isSysLog) {
            bodyLength = jsonBody.getBytes(StandardCharsets.UTF_8).length;
        }
        String signString = "POST\n" + bodyLength + "\n" + MediaType.APPLICATION_JSON_VALUE + "\n" + "x-ms-date:" + date
                + "\n" + "/api/logs";
        return createAuthorizationHeader(signString);
    }

    private String createAuthorizationHeader(String canonicalizedString) {
        String authStr = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(azure.getSharedKey()), "HmacSHA256"));
            String authKey = new String(Base64.getEncoder().encode(mac.doFinal(canonicalizedString.getBytes("UTF-8"))));
            authStr = "SharedKey " + azure.getWorkspaceId() + ":" + authKey;
        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(this.getClass());
            logger.error("Error while sending message to Log Analytics", e);

        }
        return authStr;
    }

    public static String constructAzureLogNm(String appName, String appVersion, Boolean isSysLog) {
        String logTypeInd = null;
        String filteredAppNm = StringUtils.replaceAll(appName, SPL_CHAR_REGEX, "");
        if (StringUtils.isEmpty(appName)) {
            appName = "default";
        }
        if (StringUtils.isEmpty(appVersion)) {
            appVersion = "v0";
        }
        if (isSysLog) {
            // syslogs - rqst/resp/exceptions
            logTypeInd = "sl";
        } else {
            // applicatoin logs written per operation
            logTypeInd = "al";
        }
        return StringUtils.join(Arrays.asList(filteredAppNm, appVersion, logTypeInd), UNDERSCORE);
    }
}
