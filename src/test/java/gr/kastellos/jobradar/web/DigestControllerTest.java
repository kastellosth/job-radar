package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.service.DigestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DigestController.class)
@TestPropertySource(properties = "radar.api-key=test-key")
class DigestControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DigestService digest;

    @Test
    void sendingNeedsTheApiKey() throws Exception {
        mvc.perform(post("/api/digest")).andExpect(status().isUnauthorized());

        verify(digest, never()).send();
    }

    @Test
    void sendsAndSaysHowManyJobsWereListed() throws Exception {
        when(digest.send()).thenReturn(new DigestService.DigestReport(3));

        mvc.perform(post("/api/digest").header("X-Api-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs").value(3));
    }

    @Test
    void anEmailThatIsNotSetUpIsAConflict() throws Exception {
        when(digest.send()).thenThrow(new DigestService.NotConfiguredException());

        mvc.perform(post("/api/digest").header("X-Api-Key", "test-key"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "The weekly email is off: set DIGEST_TO, MAIL_USERNAME and MAIL_PASSWORD"));
    }

    @Test
    void aMailServerFailureIsABadGatewayWithoutTheServersMessage() throws Exception {
        when(digest.send()).thenThrow(new MailSendException("535 Username me@gmail.com and Password not accepted"));

        mvc.perform(post("/api/digest").header("X-Api-Key", "test-key"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(not(
                        containsString("me@gmail.com"))));
    }
}
