package com.vide.vibe.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends emails on a background thread (@Async) so the calling HTTP request
 * returns immediately — SMTP latency never blocks the user.
 *
 * Requires @EnableAsync on the application class (see VibeApplication.java).
 */
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@vibe.example.com}")
    private String fromAddress;

    /**
     * Fire-and-forget: returns instantly, email is delivered in the background.
     */
    @Async
    public void sendClaimEmail(String to, String appName, String token) {
        String verifyUrl = baseUrl + "/claim/verify/" + token;

        if (mailSender == null) {
            System.out.printf(
                    "%n[EMAIL — not configured]%nTo:      %s%nSubject: Verify ownership of \"%s\"%nLink:    %s%n%n",
                    to, appName, verifyUrl
            );
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Verify your ownership of \"" + appName + "\" on Vibe");
            helper.setText(buildHtml(appName, verifyUrl), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("[EmailService] Failed to send claim email to " + to + ": " + e.getMessage());
        }
    }

    private String buildHtml(String appName, String verifyUrl) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
            </head>
            <body style="margin:0;padding:0;background:#f5f5f5;font-family:Inter,system-ui,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:40px 16px;">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:16px;padding:40px 36px;border:1.5px solid #e0e0e0;">
                    <tr>
                      <td style="padding-bottom:28px;">
                        <span style="font-size:1.1rem;font-weight:800;letter-spacing:-0.5px;color:#111;">Vibe</span>
                      </td>
                    </tr>
                    <tr>
                      <td style="font-size:1.35rem;font-weight:700;color:#111;padding-bottom:12px;line-height:1.3;">
                        Verify your ownership
                      </td>
                    </tr>
                    <tr>
                      <td style="font-size:0.9rem;color:#555;line-height:1.6;padding-bottom:28px;">
                        You (or someone using this address) submitted
                        <strong style="color:#111;">%s</strong> to Vibe.<br/><br/>
                        Click the button below to confirm you own this email and claim your listing.
                        The link is valid for <strong>24 hours</strong>.
                      </td>
                    </tr>
                    <tr>
                      <td style="padding-bottom:32px;">
                        <a href="%s"
                           style="display:inline-block;background:#111;color:#fff;
                                  padding:0.75rem 2.25rem;border-radius:50px;
                                  text-decoration:none;font-size:0.9rem;font-weight:600;">
                          Verify Ownership
                        </a>
                      </td>
                    </tr>
                    <tr>
                      <td style="font-size:0.75rem;color:#aaa;padding-bottom:24px;word-break:break-all;">
                        Or copy this link into your browser:<br/>
                        <a href="%s" style="color:#2563eb;">%s</a>
                      </td>
                    </tr>
                    <tr>
                      <td style="font-size:0.72rem;color:#bbb;border-top:1px solid #eee;padding-top:20px;">
                        If you didn't submit this app you can safely ignore this email.
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(appName, verifyUrl, verifyUrl, verifyUrl);
    }
}