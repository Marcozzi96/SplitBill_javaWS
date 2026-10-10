package it.javaWS.components;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.entities.ShoppingItem;
import it.javaWS.repositories.ShoppingItemRepository;

/**
 * Runner di backfill una-tantum: quando la colonna position viene aggiunta a
 * shopping_items (ddl-auto: update), Hibernate la popola con il default 0.
 * Questo runner, eseguito all'avvio prima che l'applicazione serva richieste,
 * riassegna posizioni spaziate agli item esistenti preservando l'ordine
 * cronologico (più recente in cima).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShoppingItemPositionBackfillRunner implements ApplicationRunner {

	private static final double POSITION_STEP = 1000.0;
	private static final Logger LOGGER = LoggerFactory.getLogger(ShoppingItemPositionBackfillRunner.class);

	private final ShoppingItemRepository shoppingItemRepository;

	public ShoppingItemPositionBackfillRunner(ShoppingItemRepository shoppingItemRepository) {
		this.shoppingItemRepository = shoppingItemRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		List<ShoppingItem> itemsToBackfill = shoppingItemRepository.findByPositionZero();
		if (itemsToBackfill.isEmpty()) {
			return;
		}

		LOGGER.info("Backfill position per {} articoli della spesa", itemsToBackfill.size());

		Map<Long, List<ShoppingItem>> byGroup = itemsToBackfill.stream()
				.collect(Collectors.groupingBy(item -> item.getGroup().getId()));

		for (List<ShoppingItem> groupItems : byGroup.values()) {
			backfillGroup(groupItems);
		}

		shoppingItemRepository.saveAll(itemsToBackfill);
		LOGGER.info("Backfill position completato");
	}

	private void backfillGroup(List<ShoppingItem> groupItems) {
		groupItems.sort(Comparator.comparing(ShoppingItem::getCreatedAt).reversed());
		int size = groupItems.size();
		for (int i = 0; i < size; i++) {
			// Più recente = valore più alto (in cima).
			groupItems.get(i).setPosition((size - i) * POSITION_STEP);
		}
	}
}
