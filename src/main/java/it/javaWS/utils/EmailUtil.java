package it.javaWS.utils;

import jakarta.annotation.PostConstruct;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.Data;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Data
public class EmailUtil {

	@Value("${mail.username}")
	private String username;

	@Value("${mail.password}")
	private String password;

	@Value("${mail.smtp.host}")
	private String smtpHost;

	@Value("${mail.smtp.port}")
	private int smtpPort;

	@Value("${mail.smtp.auth}")
	private boolean smtpAuth;
	
	@Value("${environment.open.link}")
	private String openLink;

	@Value("${mail.smtp.starttls.enable}")
	private boolean starttlsEnable;

	Properties props;

	@PostConstruct
	public void init() {
		props = new Properties();
		props.put("mail.smtp.auth", String.valueOf(smtpAuth));
		props.put("mail.smtp.starttls.enable", String.valueOf(starttlsEnable));
		props.put("mail.smtp.host", smtpHost);
		props.put("mail.smtp.port", String.valueOf(smtpPort));
	}

	public void sendEmail(String to, String subject, String body) {
		Session session = Session.getInstance(props, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		try {
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(username));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
			message.setSubject(subject);
			message.setContent(body, "text/html; charset=utf-8");


			Transport.send(message);
			System.out.println("Email inviata con successo a " + to);
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
	           "<p>Accedi su: <a href=\"https://fe-splitbill.vercel.app\">SplitBill</a></p>" +
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


}
