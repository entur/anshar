package no.rutebanken.anshar.routes.validation;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidationReportTemplateTest {

    @Test
    public void testReportShowsProfileUsedPerValidation() throws Exception {
        JSONObject subscription = new JSONObject();
        subscription.put("name", "test-subscription");

        JSONObject status = new JSONObject();
        status.put("validationActive", true);
        status.put("currentSize", "1 KB");
        status.put("maxSize", "4 MB");
        status.put("currentValidations", 2);
        status.put("maxValidations", 20);
        status.put("validationProfile", "sweden");

        JSONArray validationRefs = new JSONArray();
        validationRefs.add(validation("ref-1", "sweden"));
        // Results stored before the profile was recorded have no profileName
        validationRefs.add(validation("ref-2", null));

        JSONObject body = new JSONObject();
        body.put("subscription", subscription);
        body.put("status", status);
        body.put("validationRefs", validationRefs);

        String html = render(body);

        assertTrue(html.contains("Profile: sweden"), "Current profile should be shown in status");
        assertTrue(html.contains("<th>Profile</th>"), "Profile column should be shown");
        assertTrue(html.contains(">sweden</td>"), "Profile used should be shown per validation");
        assertTrue(html.contains(">-</td>"), "Missing profile should be shown as '-'");
    }

    @Test
    public void testReportShowsMissingDefaultProfile() throws Exception {
        JSONObject subscription = new JSONObject();
        subscription.put("name", "test-subscription");

        JSONObject status = new JSONObject();
        status.put("validationActive", true);
        status.put("currentSize", "1 KB");
        status.put("maxSize", "4 MB");
        status.put("currentValidations", 1);
        status.put("maxValidations", 20);
        // anshar.validation.profile.name is empty
        status.put("validationProfile", "");

        JSONArray validationRefs = new JSONArray();
        validationRefs.add(validation("ref-1", ""));

        JSONObject body = new JSONObject();
        body.put("subscription", subscription);
        body.put("status", status);
        body.put("validationRefs", validationRefs);

        String html = render(body);

        assertTrue(html.contains("Profile: none (no default profile configured)"), "Missing default profile should be explained");
        assertTrue(html.contains(">none</td>"), "Validation without profile should be shown as 'none'");
    }

    private static JSONObject validation(String ref, String profileName) {
        JSONObject schema = new JSONObject();
        schema.put("timestamp", System.currentTimeMillis());
        schema.put("errorCount", 0);
        schema.put("events", new JSONArray());

        JSONObject profile = new JSONObject();
        profile.put("errorCount", 0);
        profile.put("categories", new JSONArray());

        JSONObject validation = new JSONObject();
        validation.put("validationRef", ref);
        validation.put("schema", schema);
        validation.put("profile", profile);
        if (profileName != null) {
            validation.put("profileName", profileName);
        }
        return validation;
    }

    private static String render(JSONObject body) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(ValidationReportTemplateTest.class.getClassLoader(), "templates");
        Template template = cfg.getTemplate("validation-report.ftl");

        StringWriter out = new StringWriter();
        template.process(Map.of("body", body), out);
        return out.toString();
    }
}
