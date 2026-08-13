package it.javaWS.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
public class EmailUtil {

	private final JavaMailSender mailSender;

	@Value("${app.open-link}")
	private String openLink;

	@Value("${spring.mail.username}")
	private String fromEmail;

	public EmailUtil(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	public void sendEmail(String to, String subject, String body) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(fromEmail);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(body, true);

			mailSender.send(message);
		} catch (MessagingException e) {
			throw new RuntimeException("Errore durante l'invio dell'email", e);
		}
	}

	public String creaCorpoEmailBenvenuto(String nomeUtente) {
		return "<html>" +
				"<body>" +
				"<p>Ciao " + nomeUtente + ",</p>" +
				"<p>Benvenuto su <strong>SplitBill</strong>! Siamo felici di averti con noi.</p>" +
				"<p>Inizia subito a dividere le spese in modo semplice e veloce.</p>" +
				"<p>Accedi su: <a href=\"" + openLink + "\">SplitBill</a></p>" +
				"<br>" +
				"<p>Il team di Composizioni&Co</p>" +
				"</body>" +
				"</html>";
	}

	public String creaCorpoEmailConferma(String nomeUtente, String token) {
		String linkConferma = openLink + "/auth/confirmEmail?token=" + token;

		return "<html>" +
				"<body>" +
				"<p>Ciao " + nomeUtente + ",</p>" +
				"<p>Grazie per esserti registrato su <strong>SplitBill</strong>!</p>" +
				"<p>Per completare la registrazione, conferma il tuo indirizzo email cliccando sul link qui sotto:</p>" +
				"<p><a href=\"" + linkConferma + "\">Conferma la tua email</a></p>" +
				"<br>" +
				"<p>Se non hai richiesto questa registrazione, puoi ignorare questa email.</p>" +
				"<br>" +
				"<p>Il team di Composizioni&Co</p>" +
				"</body>" +
				"</html>";
	}

	public String creaCorpoEmailResetPassword(String nomeUtente, String token) {
		String linkReset = openLink + "/resetPassword?token=" + token;

		return "<html>" +
				"<body>" +
				"<p>Ciao " + nomeUtente + ",</p>" +
				"<p>Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account <strong>SplitBill</strong>.</p>" +
				"<p>Per scegliere una nuova password, clicca sul link qui sotto (valido per 15 minuti):</p>" +
				"<p><a href=\"" + linkReset + "\">Reimposta la tua password</a></p>" +
				"<br>" +
				"<p>Se non hai richiesto tu il reset della password, puoi ignorare questa email.</p>" +
				"<br>" +
				"<p>Il team di Composizioni&Co</p>" +
				"</body>" +
				"</html>";
	}
}
