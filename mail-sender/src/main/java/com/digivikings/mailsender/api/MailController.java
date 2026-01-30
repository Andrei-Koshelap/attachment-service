package com.digivikings.mailsender.api;

import com.digivikings.mailsender.service.MailSendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mail")
public class MailController {

    private final MailSendService mailSendService;

    public MailController(MailSendService mailSendService) {
        this.mailSendService = mailSendService;
    }

    @PostMapping("/notify")
    public ResponseEntity<Void> notify(@Valid @RequestBody MailNotifyRequest req) {
        mailSendService.send(req.recipient(), req.subject(), req.message());
        return ResponseEntity.accepted().build();
    }
}
