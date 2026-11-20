package gr.kastellos.jobradar.web;

import gr.kastellos.jobradar.service.DigestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Send the weekly email now, e.g. to check the mail settings after a deploy. Needs the API key like every POST. */
@RestController
@RequestMapping("/api/digest")
public class DigestController {

    private static final Logger log = LoggerFactory.getLogger(DigestController.class);

    private final DigestService digest;

    public DigestController(DigestService digest) {
        this.digest = digest;
    }

    @PostMapping
    public DigestService.DigestReport sendNow() {
        return digest.send();
    }

    @ExceptionHandler(DigestService.NotConfiguredException.class)
    ProblemDetail notConfigured(DigestService.NotConfiguredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** The mail server's own message can include account details, so it goes to the log, not the answer. */
    @ExceptionHandler(MailException.class)
    ProblemDetail mailFailed(MailException e) {
        log.error("Digest could not be sent: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The mail server refused or did not answer. Check MAIL_USERNAME and MAIL_PASSWORD; details are in the log");
    }
}
