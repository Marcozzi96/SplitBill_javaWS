package it.javaWS.enums;

/**
 * Relazione di amicizia tra l'utente autenticato ("me") e un altro utente ("m").
 * Derivata da Friendship: la coppia (user1, user2) è ordinata, mentre
 * userToBeConfirmed indica chi deve accettare la richiesta.
 */
public enum RelazioneAmicizia {
	// stato ACCETTATA
	AMICI,
	// IN_ATTESA con userToBeConfirmed = m (la richiesta l'ho inviata io)
	RICHIESTA_INVIATA,
	// IN_ATTESA con userToBeConfirmed = me (la richiesta l'ho ricevuta)
	RICHIESTA_RICEVUTA,
	// RIFIUTATA con userToBeConfirmed = m (l'altro ha rifiutato la mia richiesta; il reinvio è vietato)
	RICHIESTA_RIFIUTATA,
	// nessuna riga, oppure RIFIUTATA con userToBeConfirmed = me (avevo rifiutato io; il reinvio è consentito)
	NESSUNA
}
